package com.dmu.dshorts.domain.study.repository;
import com.dmu.dshorts.domain.study.entity.ConversationMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage,Long> {List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);}
