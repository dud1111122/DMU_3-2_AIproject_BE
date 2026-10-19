package com.dmu.dshorts.domain.study.repository;

import com.dmu.dshorts.domain.study.entity.LearningAssessment;
import com.dmu.dshorts.domain.study.entity.LearningAssessmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LearningAssessmentRepository extends JpaRepository<LearningAssessment, LearningAssessmentId> {

    List<LearningAssessment> findByConversationId(Long conversationId);
}
