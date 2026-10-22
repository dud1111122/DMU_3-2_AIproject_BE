package com.dmu.dshorts.domain.learning.service;

import com.dmu.dshorts.domain.learning.entity.AnswerOption;
import com.dmu.dshorts.domain.learning.entity.Question;
import com.dmu.dshorts.domain.learning.repository.AnswerOptionRepository;
import com.dmu.dshorts.domain.learning.repository.LearningKeyPointQueries;
import com.dmu.dshorts.domain.learning.repository.QuestionRepository;
import com.dmu.dshorts.domain.post.entity.KeyPoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Loads the quiz of a post: questions with their key point and options, ordered by key point order.
 * Callers must check visibility first and run inside a transaction.
 */
@Component
class QuizContentLoader {

    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final LearningKeyPointQueries keyPointQueries;

    QuizContentLoader(QuestionRepository questionRepository, AnswerOptionRepository answerOptionRepository,
                      LearningKeyPointQueries keyPointQueries) {
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.keyPointQueries = keyPointQueries;
    }

    record LoadedQuestion(Question question, KeyPoint keyPoint, List<AnswerOption> options) {

        boolean hasOption(int optionOrder) {
            return options.stream().anyMatch(option -> option.getOptionOrder() == optionOrder);
        }
    }

    List<LoadedQuestion> load(Long postId) {
        Map<Long, Question> questionsByKeyPoint = questionRepository.findByPostId(postId).stream()
                .collect(Collectors.toMap(Question::getKeyPointId, q -> q));
        if (questionsByKeyPoint.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AnswerOption>> optionsByQuestion = new HashMap<>();
        List<Long> questionIds = questionsByKeyPoint.values().stream().map(Question::getId).toList();
        for (AnswerOption option : answerOptionRepository.findByQuestionIdInOrderByQuestionIdAscOptionOrderAsc(
                questionIds)) {
            optionsByQuestion.computeIfAbsent(option.getQuestionId(), id -> new ArrayList<>()).add(option);
        }
        List<LoadedQuestion> result = new ArrayList<>();
        for (KeyPoint keyPoint : keyPointQueries.findByPostIdOrderByPointOrderAsc(postId)) {
            Question question = questionsByKeyPoint.get(keyPoint.getId());
            if (question != null) {
                result.add(new LoadedQuestion(question, keyPoint,
                        optionsByQuestion.getOrDefault(question.getId(), List.of())));
            }
        }
        return result;
    }
}
