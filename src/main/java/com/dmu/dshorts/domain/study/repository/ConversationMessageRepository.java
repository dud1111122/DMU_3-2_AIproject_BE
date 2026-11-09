package com.dmu.dshorts.domain.study.repository;

import com.dmu.dshorts.domain.study.entity.ConversationMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    /** Newest first; reverse for display. */
    List<ConversationMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable page);

    /** Newest first among messages older than {@code beforeId}. */
    List<ConversationMessage> findByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId,
                                                                            Pageable page);

    long countByConversationId(Long conversationId);
}
