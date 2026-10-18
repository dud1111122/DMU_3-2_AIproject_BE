package com.dmu.dshorts.domain.study.entity;

import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="study_assessments")
public class StudyAssessment {
    @Id private String conversationId;
    @Column(columnDefinition="TEXT") private String resultJson;
    private Instant createdAt=Instant.now();
    protected StudyAssessment() {}
    public StudyAssessment(String id,String resultJson){this.conversationId=id;this.resultJson=resultJson;}
    public String getConversationId(){return conversationId;}public String getResultJson(){return resultJson;}public Instant getCreatedAt(){return createdAt;}
}
