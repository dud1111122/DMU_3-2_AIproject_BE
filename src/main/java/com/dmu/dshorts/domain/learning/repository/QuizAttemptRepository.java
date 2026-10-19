package com.dmu.dshorts.domain.learning.repository;

import com.dmu.dshorts.domain.learning.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
}
