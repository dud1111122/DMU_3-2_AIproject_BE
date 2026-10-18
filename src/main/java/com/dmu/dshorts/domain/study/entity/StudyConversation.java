package com.dmu.dshorts.domain.study.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="study_conversations")
public class StudyConversation {
    @Id private String id=UUID.randomUUID().toString();
    private String userId;private String personaId;private String postId;private Instant createdAt=Instant.now();
    protected StudyConversation() {}
    public StudyConversation(String userId,String personaId,String postId){this.userId=userId;this.personaId=personaId;this.postId=postId;}
    public String getId(){return id;}public String getUserId(){return userId;}public String getPersonaId(){return personaId;}public String getPostId(){return postId;}public Instant getCreatedAt(){return createdAt;}
}
