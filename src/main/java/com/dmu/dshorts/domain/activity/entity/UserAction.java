package com.dmu.dshorts.domain.activity.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="user_actions", uniqueConstraints=@UniqueConstraint(columnNames={"user_id","action_type","target_id"}))
public class UserAction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private String userId;
    @Column(name="action_type",nullable=false) private String actionType;
    @Column(name="target_id",nullable=false) private String targetId;
    private Instant createdAt=Instant.now();
    protected UserAction() {}
    public UserAction(String userId,String actionType,String targetId){this.userId=userId;this.actionType=actionType;this.targetId=targetId;}
    public Long getId(){return id;} public String getUserId(){return userId;} public String getActionType(){return actionType;} public String getTargetId(){return targetId;} public Instant getCreatedAt(){return createdAt;}
}
