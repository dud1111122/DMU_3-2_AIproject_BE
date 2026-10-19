package com.dmu.dshorts.domain.learning.repository;

import com.dmu.dshorts.domain.learning.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByPostId(Long postId);
}
