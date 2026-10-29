package com.dmu.dshorts.domain.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.dmu.dshorts.domain.generation.entity.GenerationJob;
import com.dmu.dshorts.domain.generation.entity.GenerationJobStatus;
import com.dmu.dshorts.domain.generation.repository.GenerationJobRepository;
import com.dmu.dshorts.domain.generation.service.GenerationPlan;
import com.dmu.dshorts.domain.generation.service.PostPreparationService;
import com.dmu.dshorts.domain.generation.service.PostPreparationService.PreparationResult;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.global.ai.AiClient;
import com.dmu.dshorts.global.ai.AiMessage;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import com.dmu.dshorts.support.IntegrationTestSupport;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Preparation command with a mocked AI: idempotency, per-post failure isolation, retries, startup recovery. */
class PostPreparationIntegrationTest extends IntegrationTestSupport {

    private static final AtomicInteger RUN_SEQUENCE = new AtomicInteger();
    /** Subjects from seed/generation-plan.json (socrates slot 1, marie.curie slot 1). */
    static final String FAILING_SUBJECT = "아는 것과 안다고 믿는 것";
    static final String MALFORMED_SUBJECT = "보이지 않는 현상을 어떻게 연구할까";

    @MockitoBean
    AiClient aiClient;

    @Autowired
    PostPreparationService preparationService;
    @Autowired
    GenerationPlan plan;
    @Autowired
    GenerationJobRepository jobRepository;
    @Autowired
    PersonaRepository personaRepository;
    @Autowired
    TopicRepository topicRepository;
    @Autowired
    PostRepository postRepository;

    private volatile String failingSubject;
    private volatile String malformedSubject;

    private static String newRunKey() {
        return "t" + RUN_SEQUENCE.incrementAndGet() + "-" + Long.toString(System.nanoTime(), 36);
    }

    /** AI is configured; the answer is valid except for the subjects set in the fields above. */
    private void aiWorks() throws Exception {
        when(aiClient.configured()).thenReturn(true);
        when(aiClient.modelName()).thenReturn("test-model");
        when(aiClient.completeJson(anyList())).thenAnswer(invocation -> {
            List<AiMessage> messages = invocation.getArgument(0);
            String user = messages.get(messages.size() - 1).content();
            if (failingSubject != null && user.contains("- 소재: " + failingSubject + "\n")) {
                throw new ApiException(ErrorCode.AI_FAILED, "upstream down");
            }
            if (malformedSubject != null && user.contains("- 소재: " + malformedSubject + "\n")) {
                return objectMapper.valueToTree(Map.of("sections", "not-an-array", "keyPoints", List.of()));
            }
            return objectMapper.valueToTree(GenerationFixtures.validAiPost());
        });
    }

    private int expectedJobs() {
        return (int) personaRepository.findAll().stream().filter(Persona::isVisible).count()
                * plan.slotsPerPersona();
    }

    private List<GenerationJob> jobsOf(String runKey) {
        return jobRepository.findByRequestKeyStartingWithOrderByIdAsc(runKey + ":");
    }

    @Test
    void aiNotConfiguredFailsBeforeAnythingIsWritten() {
        when(aiClient.configured()).thenReturn(false);
        String runKey = newRunKey();
        assertThatThrownBy(() -> preparationService.prepare(runKey))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(jobsOf(runKey)).isEmpty();
    }

    @Test
    void invalidRunKeyIsRejected() {
        assertThatThrownBy(() -> preparationService.prepare("bad key:1"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void preparationCreatesThreeJobsPerPersonaAndRerunNeverDuplicatesSuccess() throws Exception {
        aiWorks();
        String runKey = newRunKey();
        int expected = expectedJobs();

        PreparationResult first = preparationService.prepare(runKey);
        assertThat(first.createdJobs()).isEqualTo(expected);
        assertThat(first.succeeded()).isEqualTo(expected);
        assertThat(first.allSucceeded()).isTrue();

        for (GenerationJob job : jobsOf(runKey)) {
            assertThat(job.getStatus()).isEqualTo(GenerationJobStatus.SUCCEEDED);
            assertThat(job.getResultPostId()).isNotNull();
            assertThat(job.getAiModel()).isEqualTo("test-model");
            assertThat(job.getFinishedAt()).isNotNull();
            assertThat(job.getErrorMessage()).isNull();
            assertThat(job.getAttemptCount()).isEqualTo(1);
            assertThat(postRepository.findById(job.getResultPostId())).isPresent();
        }
        long postsAfterFirstRun = postRepository.count();

        PreparationResult second = preparationService.prepare(runKey);
        assertThat(second.createdJobs()).isZero();
        assertThat(second.attemptedJobs()).isZero();
        assertThat(postRepository.count()).isEqualTo(postsAfterFirstRun);
        assertThat(jobsOf(runKey)).allSatisfy(job -> assertThat(job.getAttemptCount()).isEqualTo(1));
    }

    @Test
    void oneFailingPostKeepsTheOthersAndRerunRetriesOnlyTheFailedJob() throws Exception {
        aiWorks();
        failingSubject = FAILING_SUBJECT;
        String runKey = newRunKey();
        int expected = expectedJobs();

        PreparationResult first = preparationService.prepare(runKey);
        assertThat(first.failed()).isEqualTo(1);
        assertThat(first.succeeded()).isEqualTo(expected - 1);
        assertThat(first.allSucceeded()).isFalse();

        List<GenerationJob> failed = jobsOf(runKey).stream()
                .filter(job -> job.getStatus() == GenerationJobStatus.FAILED).toList();
        assertThat(failed).hasSize(1);
        GenerationJob failedJob = failed.get(0);
        assertThat(failedJob.getErrorMessage()).contains("upstream down");
        assertThat(failedJob.getResultPostId()).isNull();
        assertThat(failedJob.getFinishedAt()).isNotNull();
        assertThat(failedJob.getSubject()).isEqualTo(FAILING_SUBJECT);
        long succeededBefore = jobsOf(runKey).stream()
                .filter(job -> job.getStatus() == GenerationJobStatus.SUCCEEDED).count();

        // The AI works again: only the failed job runs; successful jobs keep their posts.
        // The stub reads the field on each call, so clearing it is enough (re-stubbing would call the old stub).
        failingSubject = null;
        PreparationResult retry = preparationService.prepare(runKey);
        assertThat(retry.createdJobs()).isZero();
        assertThat(retry.attemptedJobs()).isEqualTo(1);
        assertThat(retry.allSucceeded()).isTrue();

        GenerationJob retried = jobRepository.findById(failedJob.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(GenerationJobStatus.SUCCEEDED);
        assertThat(retried.getAttemptCount()).isEqualTo(2);
        assertThat(retried.getErrorMessage()).isNull();
        assertThat(retried.getResultPostId()).isNotNull();
        long succeededAfter = jobsOf(runKey).stream()
                .filter(job -> job.getStatus() == GenerationJobStatus.SUCCEEDED).count();
        assertThat(succeededAfter).isEqualTo(succeededBefore + 1);
    }

    @Test
    void invalidAiJsonFailsOnlyThatJobWithAReadableMessage() throws Exception {
        aiWorks();
        malformedSubject = MALFORMED_SUBJECT;
        String runKey = newRunKey();

        PreparationResult result = preparationService.prepare(runKey);
        assertThat(result.failed()).isEqualTo(1);
        GenerationJob bad = jobsOf(runKey).stream()
                .filter(job -> job.getStatus() == GenerationJobStatus.FAILED).findFirst().orElseThrow();
        assertThat(bad.getSubject()).isEqualTo(MALFORMED_SUBJECT);
        assertThat(bad.getErrorMessage()).startsWith("AI 출력 형식 검증 실패").contains("sections");
        assertThat(bad.getResultPostId()).isNull();
    }

    @Test
    void runningJobsLeftByAStoppedProcessBecomeFailedOnStartup() {
        Persona socrates = personaRepository.findByUsername("socrates").orElseThrow();
        var topic = topicRepository.findByName("철학").orElseThrow();
        String runKey = newRunKey();
        String key = runKey + ":" + socrates.getId() + ":1";
        GenerationJob job = new GenerationJob(key, socrates.getId(), topic.getId(), "중단된 작업");
        job.setStatus(GenerationJobStatus.RUNNING);
        jobRepository.save(job);

        int changed = preparationService.failInterruptedJobs();

        assertThat(changed).isGreaterThanOrEqualTo(1);
        GenerationJob reloaded = jobRepository.findByRequestKey(key).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(GenerationJobStatus.FAILED);
        assertThat(reloaded.getErrorMessage()).contains("재시작");
        assertThat(reloaded.getFinishedAt()).isNotNull();
        assertThat(reloaded.getResultPostId()).isNull();
    }
}
