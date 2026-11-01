package com.dmu.dshorts.domain.activity.dto;

import com.dmu.dshorts.domain.post.dto.PostCard;

import java.time.Instant;

/** Item of {@code GET /users/me/saved-posts}: when it was saved plus the same card as the feed. */
public record SavedPostItem(Instant savedAt, PostCard post) {
}
