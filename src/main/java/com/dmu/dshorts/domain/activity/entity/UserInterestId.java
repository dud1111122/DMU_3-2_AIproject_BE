package com.dmu.dshorts.domain.activity.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link UserInterest}. */
public class UserInterestId implements Serializable {

    private Long userId;
    private Long topicId;

    protected UserInterestId() {
    }

    public UserInterestId(Long userId, Long topicId) {
        this.userId = userId;
        this.topicId = topicId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTopicId() {
        return topicId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserInterestId other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && Objects.equals(topicId, other.topicId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, topicId);
    }
}
