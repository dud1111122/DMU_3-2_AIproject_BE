package com.dmu.dshorts.domain.learning.service;

import com.dmu.dshorts.domain.learning.dto.QuizAttemptDetail;
import com.dmu.dshorts.domain.learning.dto.QuizAttemptDetail.AttemptQuestion;
import com.dmu.dshorts.domain.learning.dto.QuizAttemptSummary;
import com.dmu.dshorts.domain.learning.dto.QuizResponse;
import com.dmu.dshorts.domain.learning.dto.QuizResponse.QuizOption;
import com.dmu.dshorts.domain.learning.dto.QuizResponse.QuizQuestion;
import com.dmu.dshorts.domain.learning.dto.QuizSubmitRequest;
import com.dmu.dshorts.domain.learning.dto.QuizSubmitResponse;
import com.dmu.dshorts.domain.learning.dto.QuizSubmitResponse.QuestionResult;
import com.dmu.dshorts.domain.learning.entity.AnswerOption;
import com.dmu.dshorts.domain.learning.entity.QuizAnswer;
import com.dmu.dshorts.domain.learning.entity.QuizAttempt;
import com.dmu.dshorts.domain.learning.entity.ReviewReason;
import com.dmu.dshorts.domain.learning.repository.QuizAnswerRepository;
import com.dmu.dshorts.domain.learning.repository.QuizAttemptRepository;
import com.dmu.dshorts.domain.learning.repository.QuizAttemptRepository.AttemptRow;
import com.dmu.dshorts.domain.learning.service.QuizContentLoader.LoadedQuestion;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.global.common.Cursors;
import com.dmu.dshorts.global.common.Cursors.TimeIdCursor;
import com.dmu.dshorts.global.common.Ids;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import com.dmu.dshorts.global.error.FieldErrorDetail;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.visibility.VisibilityService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Post quiz: public read, server grading, member attempt history. */
@Service
public class QuizService {

    private final VisibilityService visibilityService;
    private final QuizContentLoader contentLoader;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAnswerRepository answerRepository;
    private final ReviewItemService reviewItemService;

    public QuizService(VisibilityService visibilityService, QuizContentLoader contentLoader,
                       QuizAttemptRepository attemptRepository, QuizAnswerRepository answerRepository,
                       ReviewItemService reviewItemService) {
        this.visibilityService = visibilityService;
        this.contentLoader = contentLoader;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.reviewItemService = reviewItemService;
    }

    /** {@code GET /posts/{postId}/quiz}: prompts and options only. */
    @Transactional(readOnly = true)
    public QuizResponse getQuiz(Long postId) {
        visibilityService.requireVisiblePost(postId);
        List<QuizQuestion> questions = contentLoader.load(postId).stream()
                .map(loaded -> new QuizQuestion(Ids.str(loaded.question().getId()),
                        Ids.str(loaded.keyPoint().getId()), loaded.keyPoint().getPointOrder(),
                        loaded.question().getPrompt(), options(loaded.options())))
                .toList();
        return new QuizResponse(Ids.str(postId), questions.size(), questions);
    }

    /**
     * {@code POST /posts/{postId}/quiz/attempts}. Validates that every question of the post is answered exactly
     * once with an existing option, grades on the server, and for members stores the attempt, all answers and
     * QUIZ_WRONG review items in this one transaction. Guests ({@code user == null}) write nothing.
     */
    @Transactional
    public QuizSubmitResponse submit(Long postId, QuizSubmitRequest request, AuthUser user) {
        visibilityService.requireVisiblePost(postId);
        List<LoadedQuestion> questions = contentLoader.load(postId);
        if (questions.isEmpty()) {
            throw ApiException.badRequest("이 게시물에는 퀴즈가 없습니다.");
        }
        Map<Long, Integer> selected = validateAnswers(questions, request);

        List<QuestionResult> results = new ArrayList<>(questions.size());
        int correctCount = 0;
        for (LoadedQuestion loaded : questions) {
            int choice = selected.get(loaded.question().getId());
            boolean correct = choice == loaded.question().getCorrectOptionOrder();
            if (correct) {
                correctCount++;
            }
            results.add(new QuestionResult(Ids.str(loaded.question().getId()), Ids.str(loaded.keyPoint().getId()),
                    loaded.keyPoint().getPointOrder(), choice, correct, loaded.question().getCorrectOptionOrder(),
                    loaded.question().getExplanation()));
        }
        int totalCount = questions.size();
        if (user == null) {
            return new QuizSubmitResponse(null, false, correctCount, totalCount, results);
        }

        QuizAttempt attempt = attemptRepository.save(
                new QuizAttempt(user.id(), postId, (short) correctCount, (short) totalCount));
        List<QuizAnswer> answers = new ArrayList<>(questions.size());
        for (LoadedQuestion loaded : questions) {
            Long questionId = loaded.question().getId();
            int choice = selected.get(questionId);
            answers.add(new QuizAnswer(attempt.getId(), questionId, postId, (short) choice,
                    choice == loaded.question().getCorrectOptionOrder()));
        }
        answerRepository.saveAll(answers);
        for (LoadedQuestion loaded : questions) {
            if (selected.get(loaded.question().getId()) != loaded.question().getCorrectOptionOrder()) {
                reviewItemService.openReview(user.id(), postId, loaded.keyPoint().getId(), ReviewReason.QUIZ_WRONG);
            }
        }
        return new QuizSubmitResponse(Ids.str(attempt.getId()), true, correctCount, totalCount, results);
    }

    /** {@code GET /users/me/quiz-attempts}: newest first, only attempts on currently visible posts. */
    @Transactional(readOnly = true)
    public PageResponse<QuizAttemptSummary> listMyAttempts(Long userId, String cursor, Integer limitParam) {
        int limit = Cursors.limit(limitParam);
        TimeIdCursor after = Cursors.decodeTimeId(cursor);
        PageRequest page = PageRequest.ofSize(limit + 1);
        List<AttemptRow> rows = after == null
                ? attemptRepository.findMyVisibleFirstPage(userId, page)
                : attemptRepository.findMyVisibleAfter(userId, after.time(), after.id(), page);
        return PageResponse.slice(rows, limit, row -> Cursors.encode(row.getSubmittedAt(), row.getId()),
                QuizAttemptSummary::from);
    }

    /** {@code GET /users/me/quiz-attempts/{id}}: own attempt on a visible post, else 404. */
    @Transactional(readOnly = true)
    public QuizAttemptDetail getMyAttempt(Long userId, Long attemptId) {
        QuizAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(ApiException::notFound);
        Post post = visibilityService.getVisiblePost(attempt.getPostId());
        Map<Long, QuizAnswer> answers = answerRepository.findByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(QuizAnswer::getQuestionId, Function.identity()));
        List<AttemptQuestion> questions = new ArrayList<>();
        for (LoadedQuestion loaded : contentLoader.load(attempt.getPostId())) {
            QuizAnswer answer = answers.get(loaded.question().getId());
            if (answer == null) {
                continue;
            }
            questions.add(new AttemptQuestion(Ids.str(loaded.question().getId()),
                    Ids.str(loaded.keyPoint().getId()), loaded.keyPoint().getPointOrder(),
                    loaded.keyPoint().getText(), loaded.question().getPrompt(), options(loaded.options()),
                    answer.getSelectedOptionOrder(), answer.isCorrect(), loaded.question().getCorrectOptionOrder(),
                    loaded.question().getExplanation()));
        }
        return new QuizAttemptDetail(Ids.str(attempt.getId()), Ids.str(post.getId()), post.getTitle(),
                Ids.str(post.getPersona().getId()), post.getPersona().getDisplayName(), attempt.getCorrectCount(),
                attempt.getTotalCount(), attempt.getSubmittedAt(), questions);
    }

    /** Returns questionId → selected option, or throws 400 VALIDATION_FAILED listing every bad field. */
    private static Map<Long, Integer> validateAnswers(List<LoadedQuestion> questions, QuizSubmitRequest request) {
        if (request == null || request.answers() == null || request.answers().isEmpty()) {
            throw ApiException.validation("answers", "모든 문항의 답을 제출해야 합니다.");
        }
        Map<Long, LoadedQuestion> byId = new HashMap<>();
        for (LoadedQuestion loaded : questions) {
            byId.put(loaded.question().getId(), loaded);
        }
        List<FieldErrorDetail> errors = new ArrayList<>();
        Map<Long, Integer> selected = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        List<QuizSubmitRequest.Answer> answers = request.answers();
        for (int i = 0; i < answers.size(); i++) {
            QuizSubmitRequest.Answer answer = answers.get(i);
            String prefix = "answers[" + i + "]";
            if (answer == null) {
                errors.add(new FieldErrorDetail(prefix, "답이 비어 있습니다."));
                continue;
            }
            Long questionId;
            try {
                questionId = Ids.parseBodyId(answer.questionId(), prefix + ".questionId");
            } catch (ApiException ex) {
                errors.addAll(ex.getFields());
                continue;
            }
            LoadedQuestion loaded = byId.get(questionId);
            if (loaded == null) {
                errors.add(new FieldErrorDetail(prefix + ".questionId", "이 게시물의 문항이 아닙니다."));
                continue;
            }
            if (!seen.add(questionId)) {
                errors.add(new FieldErrorDetail(prefix + ".questionId", "같은 문항에 답을 두 번 제출했습니다."));
                continue;
            }
            Integer option = answer.selectedOptionOrder();
            if (option == null || !loaded.hasOption(option)) {
                errors.add(new FieldErrorDetail(prefix + ".selectedOptionOrder", "이 문항에 없는 보기입니다."));
                continue;
            }
            selected.put(questionId, option);
        }
        List<String> missing = questions.stream()
                .map(loaded -> loaded.question().getId())
                .filter(id -> !seen.contains(id))
                .map(Ids::str)
                .toList();
        if (!missing.isEmpty()) {
            errors.add(new FieldErrorDetail("answers",
                    "답하지 않은 문항이 있습니다: " + String.join(", ", missing)));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "퀴즈 답안이 올바르지 않습니다.").withFields(errors);
        }
        return selected;
    }

    private static List<QuizOption> options(List<AnswerOption> options) {
        return options.stream().map(option -> new QuizOption(option.getOptionOrder(), option.getText())).toList();
    }
}
