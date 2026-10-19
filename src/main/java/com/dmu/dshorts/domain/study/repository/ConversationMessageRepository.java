package com.dmu.dshorts.domain.study.repository;

import com.dmu.dshorts.domain.study.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
}
