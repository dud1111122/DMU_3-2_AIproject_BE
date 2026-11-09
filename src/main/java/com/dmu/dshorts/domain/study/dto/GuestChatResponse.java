package com.dmu.dshorts.domain.study.dto;

import java.util.List;

/** Guest chat result. Nothing is stored; the client keeps the conversation in memory. */
public record GuestChatResponse(
        String reply,
        boolean aiGenerated,
        String aiDisclosure,
        List<Assessment> assessments
) {
    public record Assessment(String keyPointId, String level, String feedback) {
    }
}
