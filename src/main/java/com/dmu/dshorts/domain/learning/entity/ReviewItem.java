package com.dmu.dshorts.domain.learning.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="review_items",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","post_id","key_point_order"}))
public class ReviewItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private String userId;
    @Column(name="post_id",nullable=false) private String postId;
    @Column(name="key_point_order",nullable=false) private Integer keyPointOrder;
    @Column(nullable=false) private String reason;
    @Column(nullable=false) private String status="OPEN";
    private Instant createdAt=Instant.now();
    protected ReviewItem() {}
    public ReviewItem(String userId,String postId,Integer keyPointOrder,String reason){this.userId=userId;this.postId=postId;this.keyPointOrder=keyPointOrder;this.reason=reason;}
    public Long getId(){return id;} public String getUserId(){return userId;} public String getPostId(){return postId;} public Integer getKeyPointOrder(){return keyPointOrder;} public String getReason(){return reason;} public String getStatus(){return status;} public Instant getCreatedAt(){return createdAt;}
    public void complete(){status="DONE";}
}
