package com.dmu.dshorts.domain.quiz.repository;

import com.dmu.dshorts.domain.quiz.entity.Quiz;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface QuizRepository extends JpaRepository<Quiz,String> { Optional<Quiz> findByPostId(String postId); }
