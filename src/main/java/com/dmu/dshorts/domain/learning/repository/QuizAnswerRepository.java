package com.dmu.dshorts.domain.learning.repository;

import com.dmu.dshorts.domain.learning.entity.QuizAnswer;
import com.dmu.dshorts.domain.learning.entity.QuizAnswerId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, QuizAnswerId> {

    List<QuizAnswer> findByAttemptId(Long attemptId);
}
