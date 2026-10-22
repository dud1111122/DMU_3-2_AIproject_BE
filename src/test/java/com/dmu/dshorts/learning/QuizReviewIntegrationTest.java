package com.dmu.dshorts.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dmu.dshorts.domain.learning.entity.AnswerOption;
import com.dmu.dshorts.domain.learning.entity.Question;
import com.dmu.dshorts.domain.learning.entity.QuizAttempt;
import com.dmu.dshorts.domain.learning.entity.ReviewItem;
import com.dmu.dshorts.domain.learning.entity.ReviewReason;
import com.dmu.dshorts.domain.learning.entity.ReviewStatus;
import com.dmu.dshorts.domain.learning.repository.AnswerOptionRepository;
import com.dmu.dshorts.domain.learning.repository.QuestionRepository;
import com.dmu.dshorts.domain.learning.repository.QuizAnswerRepository;
import com.dmu.dshorts.domain.learning.repository.QuizAttemptRepository;
import com.dmu.dshorts.domain.learning.repository.ReviewItemRepository;
import com.dmu.dshorts.domain.learning.service.ReviewItemService;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.entity.KeyPoint;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.entity.PostSection;
import com.dmu.dshorts.domain.post.entity.PostStatus;
import com.dmu.dshorts.domain.post.repository.KeyPointRepository;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.post.repository.PostSectionRepository;
import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.domain.user.entity.UserRole;
import com.dmu.dshorts.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

class QuizReviewIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "quiz-test-password";
    private static final AtomicInteger PERSONA_SEQ = new AtomicInteger();

    @Autowired
    TransactionTemplate tx;
    @Autowired
    PersonaRepository personaRepository;
    @Autowired
    PostRepository postRepository;
    @Autowired
    PostSectionRepository sectionRepository;
    @Autowired
    KeyPointRepository keyPointRepository;
    @Autowired
    QuestionRepository questionRepository;
    @Autowired
    AnswerOptionRepository optionRepository;
    @Autowired
    QuizAttemptRepository attemptRepository;
    @Autowired
    QuizAnswerRepository answerRepository;
    @Autowired
    ReviewItemRepository reviewItemRepository;
    @Autowired
    ReviewItemService reviewItemService;

    /** Own quiz post: question i has correct option {@code correct[i]}. */
    record Fixture(Long postId, Long personaId, List<Long> questionIds, List<Long> keyPointIds, int[] correct) {

        List<Map<String, Object>> answers(int... choices) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (int i = 0; i < choices.length; i++) {
                list.add(answer(questionIds.get(i), choices[i]));
            }
            return list;
        }

        List<Map<String, Object>> allCorrect() {
            return answers(correct);
        }

        int wrong(int index) {
            return correct[index] == 1 ? 2 : 1;
        }
    }

    static Map<String, Object> answer(Long questionId, Object option) {
        Map<String, Object> map = new HashMap<>();
        map.put("questionId", String.valueOf(questionId));
        map.put("selectedOptionOrder", option);
        return map;
    }

    private Fixture createQuizPost(Persona existingPersona, int... correct) {
        return tx.execute(status -> {
            Persona persona = existingPersona;
            if (persona == null) {
                persona = personaRepository.save(new Persona(
                        "quiz.p" + System.nanoTime() % 100_000 + "." + PERSONA_SEQ.incrementAndGet(), "퀴즈 인물"));
            }
            Post post = postRepository.save(new Post(persona, "퀴즈 테스트 게시물"));
            sectionRepository.save(new PostSection(post.getId(), (short) 1, "카드 1"));
            sectionRepository.save(new PostSection(post.getId(), (short) 2, "카드 2"));
            List<Long> questionIds = new ArrayList<>();
            List<Long> keyPointIds = new ArrayList<>();
            for (int i = 0; i < correct.length; i++) {
                KeyPoint keyPoint = keyPointRepository.save(new KeyPoint(post.getId(), (short) (i + 1), "포인트 " + (i + 1)));
                Question question = questionRepository.save(new Question(post.getId(), keyPoint.getId(),
                        "문항 " + (i + 1), "해설 " + (i + 1), (short) correct[i]));
                for (short order = 1; order <= 4; order++) {
                    optionRepository.save(new AnswerOption(question.getId(), order, "보기 " + order));
                }
                questionIds.add(question.getId());
                keyPointIds.add(keyPoint.getId());
            }
            return new Fixture(post.getId(), persona.getId(), questionIds, keyPointIds, correct);
        });
    }

    private Fixture createQuizPost(int... correct) {
        return createQuizPost(null, correct);
    }

    private MockHttpSession newMember() throws Exception {
        User user = createUser("quiz", PASSWORD, UserRole.USER);
        return login(user.getUsername(), PASSWORD);
    }

    private Long userIdOf(MockHttpSession session) throws Exception {
        JsonNode me = readJson(mockMvc.perform(get("/api/v1/users/me").session(session)).andReturn());
        return Long.valueOf(me.path("data").path("id").asText());
    }

    private ResultActions submit(Long postId, Object answers, MockHttpSession session) throws Exception {
        var request = post("/api/v1/posts/{id}/quiz/attempts", postId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("answers", answers)));
        return mockMvc.perform(session == null ? request.with(csrf()) : request.with(withSession(session)));
    }

    private ResultActions patchReview(Long reviewId, String status, MockHttpSession session) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/me/review-items/{id}", reviewId).with(withSession(session))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", status))));
    }

    private long attemptsOnPost(Long postId) {
        return attemptRepository.findAll().stream().filter(a -> a.getPostId().equals(postId)).count();
    }

    private long reviewsOnPost(Long postId) {
        return reviewItemRepository.findAll().stream().filter(r -> r.getPostId().equals(postId)).count();
    }

    private void updatePost(Long postId, java.util.function.Consumer<Post> change) {
        tx.executeWithoutResult(status -> change.accept(postRepository.findById(postId).orElseThrow()));
    }

    // ---------------------------------------------------------------- quiz read

    @Test
    void seedQuizHidesAnswersAndExplanations() throws Exception {
        Long postId = tx.execute(s -> postRepository.findAll().stream()
                .filter(p -> p.getPersona().getUsername().equals("nietzsche"))
                .findFirst().orElseThrow().getId());
        var result = mockMvc.perform(get("/api/v1/posts/{id}/quiz", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(String.valueOf(postId)))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.questions.length()").value(2))
                .andExpect(jsonPath("$.data.questions[0].pointOrder").value(1))
                .andExpect(jsonPath("$.data.questions[0].questionId").isString())
                .andExpect(jsonPath("$.data.questions[0].options.length()").value(4))
                .andExpect(jsonPath("$.data.questions[0].options[0].optionOrder").value(1))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("correctOptionOrder").doesNotContain("explanation");
    }

    @Test
    void unknownPostQuizIs404() throws Exception {
        mockMvc.perform(get("/api/v1/posts/{id}/quiz", 999_999_999L)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/posts/abc/quiz")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- guest submit

    @Test
    void guestSubmissionIsGradedButWritesNothing() throws Exception {
        Fixture f = createQuizPost(2, 3, 4);
        long attemptsBefore = attemptRepository.count();
        long reviewsBefore = reviewItemRepository.count();
        long answersBefore = answerRepository.count();

        submit(f.postId(), f.answers(2, 1, 4), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").isEmpty())
                .andExpect(jsonPath("$.data.persisted").value(false))
                .andExpect(jsonPath("$.data.correctCount").value(2))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.results[0].isCorrect").value(true))
                .andExpect(jsonPath("$.data.results[1].isCorrect").value(false))
                .andExpect(jsonPath("$.data.results[1].selectedOptionOrder").value(1))
                .andExpect(jsonPath("$.data.results[1].correctOptionOrder").value(3))
                .andExpect(jsonPath("$.data.results[1].explanation").value("해설 2"))
                .andExpect(jsonPath("$.data.results[2].isCorrect").value(true));

        assertThat(attemptRepository.count()).isEqualTo(attemptsBefore);
        assertThat(reviewItemRepository.count()).isEqualTo(reviewsBefore);
        assertThat(answerRepository.count()).isEqualTo(answersBefore);
        assertThat(attemptsOnPost(f.postId())).isZero();
    }

    // ---------------------------------------------------------------- member submit

    @Test
    void memberAllCorrectStoresAttemptWithoutReviews() throws Exception {
        Fixture f = createQuizPost(1, 4);
        MockHttpSession member = newMember();
        JsonNode body = readJson(submit(f.postId(), f.allCorrect(), member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted").value(true))
                .andExpect(jsonPath("$.data.attemptId").isString())
                .andExpect(jsonPath("$.data.correctCount").value(2))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andReturn());
        Long attemptId = Long.valueOf(body.path("data").path("attemptId").asText());
        QuizAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(attempt.getCorrectCount()).isEqualTo((short) 2);
        assertThat(answerRepository.countByAttemptId(attemptId)).isEqualTo(2);
        assertThat(reviewItemRepository.findByUserIdAndPostId(userIdOf(member), f.postId())).isEmpty();
    }

    @Test
    void memberWrongAnswersStoreAttemptAnswersAndOpenReviews() throws Exception {
        Fixture f = createQuizPost(1, 2, 3);
        MockHttpSession member = newMember();
        Long userId = userIdOf(member);
        JsonNode body = readJson(submit(f.postId(), f.answers(1, f.wrong(1), f.wrong(2)), member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correctCount").value(1))
                .andReturn());
        Long attemptId = Long.valueOf(body.path("data").path("attemptId").asText());

        var answers = answerRepository.findByAttemptId(attemptId);
        assertThat(answers).hasSize(3);
        assertThat(answers.stream().filter(a -> a.isCorrect()).count()).isEqualTo(1);

        List<ReviewItem> reviews = reviewItemRepository.findByUserIdAndPostId(userId, f.postId());
        assertThat(reviews).extracting(ReviewItem::getKeyPointId)
                .containsExactlyInAnyOrder(f.keyPointIds().get(1), f.keyPointIds().get(2));
        assertThat(reviews).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo(ReviewStatus.OPEN);
            assertThat(r.getReason()).isEqualTo(ReviewReason.QUIZ_WRONG);
            assertThat(r.getCompletedAt()).isNull();
        });

        // detail of my attempt
        mockMvc.perform(get("/api/v1/users/me/quiz-attempts/{id}", attemptId).session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").value(String.valueOf(attemptId)))
                .andExpect(jsonPath("$.data.postId").value(String.valueOf(f.postId())))
                .andExpect(jsonPath("$.data.correctCount").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.questions.length()").value(3))
                .andExpect(jsonPath("$.data.questions[1].selectedOptionOrder").value(f.wrong(1)))
                .andExpect(jsonPath("$.data.questions[1].isCorrect").value(false))
                .andExpect(jsonPath("$.data.questions[1].correctOptionOrder").value(2))
                .andExpect(jsonPath("$.data.questions[1].explanation").value("해설 2"))
                .andExpect(jsonPath("$.data.questions[1].keyPointText").value("포인트 2"))
                .andExpect(jsonPath("$.data.questions[1].options.length()").value(4));

        // review list
        mockMvc.perform(get("/api/v1/users/me/review-items?status=OPEN").session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].postTitle").value("퀴즈 테스트 게시물"))
                .andExpect(jsonPath("$.data.items[0].reason").value("QUIZ_WRONG"))
                .andExpect(jsonPath("$.data.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.items[0].keyPointText").isString());
        mockMvc.perform(get("/api/v1/users/me/review-items?status=DONE").session(member))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void doneReviewIsReopenedByWrongAnswerAndNotCompletedByCorrectOne() throws Exception {
        Fixture f = createQuizPost(3, 3);
        MockHttpSession member = newMember();
        Long userId = userIdOf(member);
        submit(f.postId(), f.answers(f.wrong(0), 3), member).andExpect(status().isOk());
        ReviewItem item = reviewItemRepository.findByUserIdAndKeyPointId(userId, f.keyPointIds().get(0)).orElseThrow();

        patchReview(item.getId(), "DONE", member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(String.valueOf(item.getId())))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.completedAt").isString());
        ReviewItem done = reviewItemRepository.findById(item.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(ReviewStatus.DONE);
        assertThat(done.getCompletedAt()).isNotNull();

        // correct answers never auto-complete / touch the review; wrong answer reopens the same row
        submit(f.postId(), f.allCorrect(), member).andExpect(status().isOk());
        assertThat(reviewItemRepository.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ReviewStatus.DONE);

        submit(f.postId(), f.answers(f.wrong(0), f.wrong(1)), member).andExpect(status().isOk());
        ReviewItem reopened = reviewItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(ReviewStatus.OPEN);
        assertThat(reopened.getCompletedAt()).isNull();
        assertThat(reopened.getUpdatedAt()).isAfterOrEqualTo(done.getUpdatedAt());

        submit(f.postId(), f.answers(f.wrong(0), f.wrong(1)), member).andExpect(status().isOk());
        assertThat(reviewItemRepository.findByUserIdAndPostId(userId, f.postId())).hasSize(2);

        submit(f.postId(), f.allCorrect(), member).andExpect(status().isOk());
        assertThat(reviewItemRepository.findByUserIdAndPostId(userId, f.postId()))
                .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(ReviewStatus.OPEN));

        // manual OPEN clears completedAt
        patchReview(item.getId(), "DONE", member).andExpect(status().isOk());
        patchReview(item.getId(), "OPEN", member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.completedAt").isEmpty());
        patchReview(item.getId(), "FINISHED", member)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("status"));
    }

    @Test
    void reviewUpsertServiceKeepsOneRowPerKeyPoint() {
        Fixture f = createQuizPost(1, 1);
        User user = createUser("quizsvc", PASSWORD, UserRole.USER);
        tx.executeWithoutResult(s -> reviewItemService.openReview(user.getId(), f.postId(), f.keyPointIds().get(0),
                ReviewReason.QUIZ_WRONG));
        tx.executeWithoutResult(s -> reviewItemService.openReview(user.getId(), f.postId(), f.keyPointIds().get(0),
                ReviewReason.CHAT_MISSED));
        List<ReviewItem> rows = reviewItemRepository.findByUserIdAndPostId(user.getId(), f.postId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getReason()).isEqualTo(ReviewReason.CHAT_MISSED);
        assertThat(rows.get(0).getStatus()).isEqualTo(ReviewStatus.OPEN);
    }

    // ---------------------------------------------------------------- validation

    @Test
    void invalidSubmissionsAre400AndWriteNothing() throws Exception {
        Fixture f = createQuizPost(1, 2, 3);
        Fixture other = createQuizPost(1, 1);
        MockHttpSession member = newMember();
        List<Long> q = f.questionIds();

        // missing answer
        submit(f.postId(), f.answers(1, 2), member)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields[0].field").value("answers"));
        // duplicate answer
        submit(f.postId(), List.of(answer(q.get(0), 1), answer(q.get(1), 2), answer(q.get(2), 3),
                answer(q.get(0), 2)), member)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("answers[3].questionId"));
        // foreign question (belongs to another post)
        submit(f.postId(), List.of(answer(q.get(0), 1), answer(q.get(1), 2), answer(q.get(2), 3),
                answer(other.questionIds().get(0), 1)), member)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("answers[3].questionId"));
        // foreign / missing option
        submit(f.postId(), List.of(answer(q.get(0), 5), answer(q.get(1), 0), answer(q.get(2), null)), member)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.length()").value(3))
                .andExpect(jsonPath("$.error.fields[0].field").value("answers[0].selectedOptionOrder"))
                .andExpect(jsonPath("$.error.fields[1].field").value("answers[1].selectedOptionOrder"))
                .andExpect(jsonPath("$.error.fields[2].field").value("answers[2].selectedOptionOrder"));
        // malformed question id
        submit(f.postId(), List.of(answer(q.get(0), 1), answer(q.get(1), 2),
                Map.of("questionId", "abc", "selectedOptionOrder", 1)), member)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("answers[2].questionId"));
        // empty answers
        submit(f.postId(), List.of(), member)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("answers"));

        assertThat(attemptsOnPost(f.postId())).isZero();
        assertThat(reviewsOnPost(f.postId())).isZero();
    }

    // ---------------------------------------------------------------- ownership and auth

    @Test
    void otherUsersAttemptsAndReviewsAre404() throws Exception {
        Fixture f = createQuizPost(1, 1);
        MockHttpSession owner = newMember();
        MockHttpSession stranger = newMember();
        JsonNode body = readJson(submit(f.postId(), f.answers(2, 2), owner).andReturn());
        String attemptId = body.path("data").path("attemptId").asText();
        Long reviewId = reviewItemRepository.findByUserIdAndPostId(userIdOf(owner), f.postId()).get(0).getId();

        mockMvc.perform(get("/api/v1/users/me/quiz-attempts/{id}", attemptId).session(stranger))
                .andExpect(status().isNotFound());
        patchReview(reviewId, "DONE", stranger).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/me/quiz-attempts").session(stranger))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mockMvc.perform(get("/api/v1/users/me/review-items").session(stranger))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        assertThat(reviewItemRepository.findById(reviewId).orElseThrow().getStatus()).isEqualTo(ReviewStatus.OPEN);
    }

    @Test
    void memberEndpointsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/quiz-attempts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me/quiz-attempts/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me/review-items")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/users/me/review-items/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void attemptHistoryIsNewestFirstWithCursor() throws Exception {
        Fixture f = createQuizPost(1, 1);
        MockHttpSession member = newMember();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ids.add(readJson(submit(f.postId(), f.allCorrect(), member).andReturn())
                    .path("data").path("attemptId").asText());
        }
        JsonNode page1 = readJson(mockMvc.perform(get("/api/v1/users/me/quiz-attempts?limit=2").session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].attemptId").value(ids.get(2)))
                .andExpect(jsonPath("$.data.items[1].attemptId").value(ids.get(1)))
                .andExpect(jsonPath("$.data.items[0].postTitle").value("퀴즈 테스트 게시물"))
                .andExpect(jsonPath("$.data.items[0].correctCount").value(2))
                .andReturn());
        String cursor = page1.path("data").path("nextCursor").asText();
        assertThat(cursor).isNotBlank();
        mockMvc.perform(get("/api/v1/users/me/quiz-attempts").param("limit", "2").param("cursor", cursor)
                        .session(member))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].attemptId").value(ids.get(0)))
                .andExpect(jsonPath("$.data.nextCursor").isEmpty());
        mockMvc.perform(get("/api/v1/users/me/quiz-attempts?cursor=garbage!").session(member))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- visibility

    @Test
    void hiddenPostBlocksQuizAndIsFilteredFromHistory() throws Exception {
        Fixture f = createQuizPost(1, 1);
        MockHttpSession member = newMember();
        String attemptId = readJson(submit(f.postId(), f.answers(2, 1), member).andReturn())
                .path("data").path("attemptId").asText();
        Long reviewId = reviewItemRepository.findByUserIdAndPostId(userIdOf(member), f.postId()).get(0).getId();

        updatePost(f.postId(), p -> p.setStatus(PostStatus.HIDDEN));

        mockMvc.perform(get("/api/v1/posts/{id}/quiz", f.postId())).andExpect(status().isNotFound());
        submit(f.postId(), f.allCorrect(), member).andExpect(status().isNotFound());
        submit(f.postId(), f.allCorrect(), null).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/me/quiz-attempts/{id}", attemptId).session(member))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/me/quiz-attempts").session(member))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mockMvc.perform(get("/api/v1/users/me/review-items").session(member))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        patchReview(reviewId, "DONE", member).andExpect(status().isNotFound());

        updatePost(f.postId(), p -> p.setStatus(PostStatus.PUBLISHED));
        mockMvc.perform(get("/api/v1/users/me/quiz-attempts").session(member))
                .andExpect(jsonPath("$.data.items.length()").value(1));

        updatePost(f.postId(), p -> p.setDeletedAt(Instant.now()));
        mockMvc.perform(get("/api/v1/posts/{id}/quiz", f.postId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/me/review-items").session(member))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void inactivePersonaBlocksQuizAndHistory() throws Exception {
        Fixture f = createQuizPost(1, 1);
        MockHttpSession member = newMember();
        String attemptId = readJson(submit(f.postId(), f.answers(2, 2), member).andReturn())
                .path("data").path("attemptId").asText();

        tx.executeWithoutResult(s -> personaRepository.findById(f.personaId()).orElseThrow().setActive(false));

        mockMvc.perform(get("/api/v1/posts/{id}/quiz", f.postId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/me/quiz-attempts/{id}", attemptId).session(member))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/me/quiz-attempts").session(member))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mockMvc.perform(get("/api/v1/users/me/review-items?status=OPEN").session(member))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }
}
