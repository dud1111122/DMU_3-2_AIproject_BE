package com.dmu.dshorts.domain.learning.repository;

import com.dmu.dshorts.domain.learning.entity.QuizAnswer;
import com.dmu.dshorts.domain.learning.entity.QuizAnswerId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, QuizAnswerId> {

    List<QuizAnswer> findByAttemptId(Long attemptId);

    long countByAttemptId(Long attemptId);
}
