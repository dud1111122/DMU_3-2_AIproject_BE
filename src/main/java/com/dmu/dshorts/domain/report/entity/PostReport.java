package com.dmu.dshorts.domain.report.entity;

import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="post_reports")
public class PostReport {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String userId;private String postId;private String reason;private String status="OPEN";private Instant createdAt=Instant.now();
    protected PostReport(){}
    public PostReport(String userId,String postId,String reason){this.userId=userId;this.postId=postId;this.reason=reason;}
    public Long getId(){return id;}public String getUserId(){return userId;}public String getPostId(){return postId;}public String getReason(){return reason;}public String getStatus(){return status;}public Instant getCreatedAt(){return createdAt;}
    public void resolve(){status="RESOLVED";}
}
