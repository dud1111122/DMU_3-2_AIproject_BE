package com.dmu.dshorts.domain.learning.dto;

/** {@code PATCH /users/me/review-items/{id}} body: {@code {"status": "DONE" | "OPEN"}}. */
public record ReviewStatusUpdateRequest(String status) {
}
