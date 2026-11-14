package com.dmu.dshorts.domain.generation.service;

import com.dmu.dshorts.domain.generation.entity.GenerationJob;
import com.dmu.dshorts.domain.generation.entity.GenerationJobStatus;
import com.dmu.dshorts.domain.generation.repository.GenerationJobRepository;
import com.dmu.dshorts.domain.generation.repository.GenerationPersonaQueries;
import com.dmu.dshorts.domain.admin.media.MediaStorage;
import com.dmu.dshorts.domain.admin.media.MediaStorage.StoredImage;
import com.dmu.dshorts.domain.generation.seed.SeedData.PostSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.SectionSeed;
import com.dmu.dshorts.domain.generation.service.GenerationPlan.PlannedJob;
import com.dmu.dshorts.domain.generation.service.GenerationPromptBuilder.PromptInput;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.global.ai.AiClient;
import com.dmu.dshorts.global.ai.AiClient.GeneratedImage;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Admin preparation command (기획.md §6): one {@code generation_jobs} row per post, fixed
 * {@code request_key = <runKey>:<personaId>:<slot>}, subject/topic fixed at job creation.
 *
 * <p>Per job: claim (short tx: PENDING/FAILED → RUNNING, attempt+1) → AI call with NO open transaction →
 * strict parse + {@link PostDraftValidator} → ONE transaction saving the post, topic links, cards, key points,
 * questions, options, sources and the job's SUCCEEDED state. Any failure records FAILED + error + finished_at
 * for that job only; posts of other jobs are kept. Re-running the same run key never touches SUCCEEDED jobs.
 */
@Service
public class PostPreparationService {

    private static final Logger log = LoggerFactory.getLogger(PostPreparationService.class);

    /** Run key: letters, digits, '.', '-', '_' (e.g. {@code demo-v1}); keeps request_key within 100 chars. */
    static final Pattern RUN_KEY = Pattern.compile("[A-Za-z0-9._-]{1,60}");
    static final String INTERRUPTED_MESSAGE = "서버 재시작으로 중단된 작업입니다. 준비 명령을 다시 실행하세요.";
    private static final int MAX_ERROR_LENGTH = 1000;

    private final AiClient aiClient;
    private final GenerationJobRepository jobRepository;
    private final GenerationPersonaQueries personaQueries;
    private final PersonaRepository personaRepository;
    private final TopicRepository topicRepository;
    private final GenerationPlan plan;
    private final PostWriter postWriter;
    private final TransactionTemplate tx;
    private final MediaStorage media;

    public PostPreparationService(AiClient aiClient,
                                  GenerationJobRepository jobRepository,
                                  GenerationPersonaQueries personaQueries,
                                  PersonaRepository personaRepository,
                                  TopicRepository topicRepository,
                                  GenerationPlan plan,
                                  PostWriter postWriter,
                                  TransactionTemplate tx,
                                  MediaStorage media) {
        this.aiClient = aiClient;
        this.jobRepository = jobRepository;
        this.personaQueries = personaQueries;
        this.personaRepository = personaRepository;
        this.topicRepository = topicRepository;
        this.plan = plan;
        this.postWriter = postWriter;
        this.tx = tx;
        this.media = media;
    }

    /** Outcome of one command run. {@code statusCounts} covers every job of the run key after the run. */
    public record PreparationResult(String runKey, int createdJobs, int attemptedJobs, int succeeded, int failed,
                                    Map<GenerationJobStatus, Integer> statusCounts) {

        public boolean allSucceeded() {
            int total = statusCounts.values().stream().mapToInt(Integer::intValue).sum();
            return total > 0 && statusCounts.getOrDefault(GenerationJobStatus.SUCCEEDED, 0) == total;
        }
    }

    /**
     * Creates missing jobs for every active persona, then runs the PENDING and FAILED jobs of this run key.
     *
     * @throws ApiException 400 for an invalid run key, 503 AI_NOT_CONFIGURED before anything is written
     */
    public PreparationResult prepare(String runKey) {
        if (runKey == null || !RUN_KEY.matcher(runKey).matches()) {
            throw ApiException.validation("runKey", "실행 키는 영문·숫자·.-_ 1~60자여야 합니다.");
        }
        if (!aiClient.configured()) {
            throw new ApiException(ErrorCode.AI_NOT_CONFIGURED);
        }
        int created = createJobs(runKey);
        List<Long> runnable = tx.execute(status -> jobRepository
                .findByRequestKeyStartingWithAndStatusInOrderByIdAsc(prefix(runKey),
                        List.of(GenerationJobStatus.PENDING, GenerationJobStatus.FAILED))
                .stream().map(GenerationJob::getId).toList());
        log.info("Preparation '{}': {} new jobs, {} to run", runKey, created, runnable.size());

        int succeeded = 0;
        int failed = 0;
        for (Long jobId : runnable) {
            GenerationJobStatus outcome = runJob(jobId);
            if (outcome == GenerationJobStatus.SUCCEEDED) {
                succeeded++;
            } else if (outcome == GenerationJobStatus.FAILED) {
                failed++;
            }
        }
        Map<GenerationJobStatus, Integer> counts = statusCounts(runKey);
        log.info("Preparation '{}' finished: {} succeeded, {} failed in this run; totals {}",
                runKey, succeeded, failed, counts);
        return new PreparationResult(runKey, created, runnable.size(), succeeded, failed, counts);
    }

    /** Marks jobs left RUNNING by a previous process as FAILED. Called once at startup. */
    public int failInterruptedJobs() {
        Integer count = tx.execute(status -> jobRepository.failRunningJobs(GenerationJobStatus.RUNNING,
                GenerationJobStatus.FAILED, INTERRUPTED_MESSAGE, Instant.now()));
        return count == null ? 0 : count;
    }

    /** Creates the jobs that do not exist yet (existing keys keep their fixed subject/topic). */
    int createJobs(String runKey) {
        Integer created = tx.execute(status -> {
            int count = 0;
            for (Persona persona : personaQueries.findActivePersonas()) {
                List<Topic> topics = topicRepository.findPublicTopicsOfPersona(persona.getId());
                if (topics.isEmpty()) {
                    log.warn("Preparation '{}': persona {} has no active topic, skipped", runKey, persona.getUsername());
                    continue;
                }
                for (PlannedJob planned : plan.planFor(persona, topics)) {
                    String key = prefix(runKey) + persona.getId() + ":" + planned.slot();
                    if (!jobRepository.existsByRequestKey(key)) {
                        jobRepository.save(new GenerationJob(key, persona.getId(), planned.topic().getId(),
                                planned.subject()));
                        count++;
                    }
                }
            }
            return count;
        });
        return created == null ? 0 : created;
    }

    /** Runs one job; returns its final status, or null if it was not runnable (e.g. already SUCCEEDED). */
    GenerationJobStatus runJob(Long jobId) {
        Claim claim;
        try {
            claim = tx.execute(status -> claim(jobId));
        } catch (RuntimeException ex) {
            log.warn("Job {} could not be claimed: {}", jobId, ex.toString());
            return null;
        }
        if (claim == null) {
            return null;
        }
        String model = null;
        try {
            if (claim.problem() != null) {
                throw new InvalidPostDraftException(claim.problem());
            }
            // AI call: no transaction is open here.
            JsonNode output = aiClient.completeJson(GenerationPromptBuilder.build(claim.input()));
            model = aiClient.modelName();
            PostSeed draft = AiPostParser.parse(output, claim.personaUsername(), claim.input().topicName());
            PostDraftValidator.validate(draft, claim.topicsByName().keySet());
            // Image calls and file writes happen outside any transaction; a failure fails this job only.
            PostSeed illustrated = withCardImages(jobId, draft);
            String aiModel = model;
            Long postId = tx.execute(status -> saveSuccess(jobId, illustrated, claim, aiModel));
            log.info("Job {} ({}) succeeded: post {}", jobId, claim.requestKey(), postId);
            return GenerationJobStatus.SUCCEEDED;
        } catch (RuntimeException ex) {
            String message = describe(ex);
            log.warn("Job {} ({}) failed: {}", jobId, claim.requestKey(), message);
            String aiModel = model;
            tx.executeWithoutResult(status -> saveFailure(jobId, message, aiModel));
            return GenerationJobStatus.FAILED;
        }
    }

    /** Card background size; portrait suits a phone-first card layout. */
    static final String CARD_IMAGE_SIZE = "1024x1536";
    static final int CARD_IMAGE_WIDTH = 1024;
    static final int CARD_IMAGE_HEIGHT = 1536;

    /**
     * Generates one background image per card that has an {@code imagePrompt}, stores it under
     * {@code cards/<jobId>/<n>.jpg} and marks it AI-generated. Returns the draft unchanged when no image model
     * is configured, so text-only posts still save.
     */
    private PostSeed withCardImages(Long jobId, PostSeed draft) {
        if (!aiClient.imageConfigured()) {
            return draft;
        }
        List<SectionSeed> cards = new ArrayList<>();
        for (int i = 0; i < draft.sections().size(); i++) {
            SectionSeed card = draft.sections().get(i);
            if (card.imagePrompt() == null) {
                cards.add(card);
                continue;
            }
            GeneratedImage image = aiClient.generateImage(card.imagePrompt(), CARD_IMAGE_SIZE);
            StoredImage stored = media.saveGenerated("cards/" + jobId + "/" + (i + 1) + ".jpg",
                    image.bytes(), image.mime(), CARD_IMAGE_WIDTH, CARD_IMAGE_HEIGHT);
            String alt = card.imageAlt() == null || card.imageAlt().isBlank()
                    ? (card.title() == null ? draft.title() : card.title()) : card.imageAlt();
            cards.add(new SectionSeed(card.title(), card.body(), card.citationType(), card.quoteAttribution(),
                    stored.path(), alt, stored.mime(), stored.width(), stored.height(), true, "AI 생성 이미지",
                    card.imagePrompt()));
        }
        return new PostSeed(draft.personaUsername(), draft.topics(), draft.title(), draft.summary(),
                draft.caption(), draft.publishedHoursAgo(), cards, draft.keyPoints(), draft.sources());
    }

    /** Snapshot taken in the claim transaction; {@code problem} != null means the job cannot run. */
    private record Claim(String requestKey, String personaUsername, PromptInput input,
                         Map<String, Topic> topicsByName, String problem) {
    }

    private Claim claim(Long jobId) {
        GenerationJob job = jobRepository.findForUpdate(jobId).orElse(null);
        if (job == null || (job.getStatus() != GenerationJobStatus.PENDING
                && job.getStatus() != GenerationJobStatus.FAILED)) {
            return null;
        }
        job.setStatus(GenerationJobStatus.RUNNING);
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setErrorMessage(null);
        job.setFinishedAt(null);
        job.setResultPostId(null);

        Persona persona = personaRepository.findById(job.getPersonaId()).orElse(null);
        Topic jobTopic = topicRepository.findById(job.getTopicId()).orElse(null);
        if (persona == null || !persona.isVisible()) {
            return new Claim(job.getRequestKey(), null, null, Map.of(), "활성 페르소나가 아니어서 생성하지 않았습니다.");
        }
        if (jobTopic == null || jobTopic.isDeleted()) {
            return new Claim(job.getRequestKey(), persona.getUsername(), null, Map.of(),
                    "작업 주제가 삭제되어 생성하지 않았습니다.");
        }
        // Allowed post topics: the persona's active topics plus the job's fixed representative topic.
        Map<String, Topic> topicsByName = new LinkedHashMap<>();
        for (Topic topic : topicRepository.findPublicTopicsOfPersona(persona.getId())) {
            topicsByName.put(topic.getName(), topic);
        }
        topicsByName.putIfAbsent(jobTopic.getName(), jobTopic);

        PromptInput input = new PromptInput(persona.getDisplayName(), persona.getEra(), persona.getPerspective(),
                persona.getVoice(), persona.getKnowledgeScope(), persona.getProhibitions(),
                persona.getConversationRules(), persona.getPostStyle(), jobTopic.getName(), job.getSubject(),
                List.copyOf(topicsByName.keySet()));
        return new Claim(job.getRequestKey(), persona.getUsername(), input, topicsByName, null);
    }

    private Long saveSuccess(Long jobId, PostSeed draft, Claim claim, String aiModel) {
        GenerationJob job = jobRepository.findForUpdate(jobId).orElseThrow();
        if (job.getStatus() != GenerationJobStatus.RUNNING) {
            throw new IllegalStateException("job is no longer RUNNING: " + job.getStatus());
        }
        Persona persona = personaRepository.findById(job.getPersonaId()).orElseThrow();
        List<Topic> topics = draft.topics().stream().map(claim.topicsByName()::get).toList();
        Instant now = Instant.now();
        Post post = postWriter.save(draft, persona, topics, now, aiModel);
        job.setStatus(GenerationJobStatus.SUCCEEDED);
        job.setResultPostId(post.getId());
        job.setAiModel(aiModel);
        job.setErrorMessage(null);
        job.setFinishedAt(now);
        return post.getId();
    }

    private void saveFailure(Long jobId, String message, String aiModel) {
        jobRepository.findForUpdate(jobId).ifPresent(job -> {
            job.setStatus(GenerationJobStatus.FAILED);
            job.setResultPostId(null);
            job.setErrorMessage(message);
            if (aiModel != null) {
                job.setAiModel(aiModel);
            }
            job.setFinishedAt(Instant.now());
        });
    }

    private Map<GenerationJobStatus, Integer> statusCounts(String runKey) {
        Map<GenerationJobStatus, Integer> counts = new EnumMap<>(GenerationJobStatus.class);
        tx.executeWithoutResult(status -> jobRepository.findByRequestKeyStartingWithOrderByIdAsc(prefix(runKey))
                .forEach(job -> counts.merge(job.getStatus(), 1, Integer::sum)));
        return counts;
    }

    private static String describe(RuntimeException ex) {
        String message;
        if (ex instanceof InvalidPostDraftException) {
            message = "AI 출력 형식 검증 실패: " + ex.getMessage();
        } else if (ex instanceof ApiException api) {
            message = api.getCode() + ": " + api.getMessage();
        } else {
            message = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    private static String prefix(String runKey) {
        return runKey + ":";
    }
}
