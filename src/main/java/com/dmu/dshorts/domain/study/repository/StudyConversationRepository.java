package com.dmu.dshorts.domain.study.repository;

import com.dmu.dshorts.domain.study.entity.StudyConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StudyConversationRepository extends JpaRepository<StudyConversation, Long> {

    Optional<StudyConversation> findByUserIdAndPostId(Long userId, Long postId);

    /** Owner-scoped lookup: another user's conversation looks like a missing one (404). */
    Optional<StudyConversation> findByIdAndUserId(Long id, Long userId);
}
