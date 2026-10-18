package com.dmu.dshorts.domain.study.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="conversation_messages")
public class ConversationMessage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String conversationId;private String role;
    @Column(columnDefinition="TEXT") private String content;
    private Instant createdAt=Instant.now();
    protected ConversationMessage() {}
    public ConversationMessage(String conversationId,String role,String content){this.conversationId=conversationId;this.role=role;this.content=content;}
    public Long getId(){return id;}public String getConversationId(){return conversationId;}public String getRole(){return role;}public String getContent(){return content;}public Instant getCreatedAt(){return createdAt;}
}
