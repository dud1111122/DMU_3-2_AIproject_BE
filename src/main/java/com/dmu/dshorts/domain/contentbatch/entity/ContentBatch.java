package com.dmu.dshorts.domain.contentbatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="content_batches")
public class ContentBatch {
    @Id private String id=UUID.randomUUID().toString();
    @Column(nullable=false) private String userId;
    private String personaId;
    @Column(columnDefinition="TEXT") private String topicIds="[]";
    private String status="PENDING";
    private String postId;
    @Column(columnDefinition="TEXT") private String errorMessage;
    private Instant createdAt=Instant.now();
    private Instant updatedAt=Instant.now();
    protected ContentBatch() {}
    public ContentBatch(String userId,String personaId,String topicIds){this.userId=userId;this.personaId=personaId;this.topicIds=topicIds;}
    public String getId(){return id;} public String getUserId(){return userId;} public String getPersonaId(){return personaId;} public String getTopicIds(){return topicIds;} public String getStatus(){return status;} public String getPostId(){return postId;} public String getErrorMessage(){return errorMessage;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public void generating(){status="GENERATING";updatedAt=Instant.now();}
    public void published(String postId){this.postId=postId;this.status="PUBLISHED";this.updatedAt=Instant.now();}
    public void failed(String message){this.status="FAILED";this.errorMessage=message;this.updatedAt=Instant.now();}
}
