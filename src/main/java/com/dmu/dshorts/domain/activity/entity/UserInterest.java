package com.dmu.dshorts.domain.activity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Table {@code user_interests}. Member interest topic.
 */
@Entity
@Table(name = "user_interests")
@IdClass(UserInterestId.class)
public class UserInterest {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    protected UserInterest() {
    }

    public UserInterest(Long userId, Long topicId) {
        this.userId = userId;
        this.topicId = topicId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTopicId() {
        return topicId;
    }
}
