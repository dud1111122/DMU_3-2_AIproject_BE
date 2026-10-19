package com.dmu.dshorts.domain.activity.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link PostLike}. */
public class PostLikeId implements Serializable {

    private Long userId;
    private Long postId;

    protected PostLikeId() {
    }

    public PostLikeId(Long userId, Long postId) {
        this.userId = userId;
        this.postId = postId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getPostId() {
        return postId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PostLikeId other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && Objects.equals(postId, other.postId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, postId);
    }
}
