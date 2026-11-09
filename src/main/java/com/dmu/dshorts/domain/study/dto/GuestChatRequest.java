package com.dmu.dshorts.domain.study.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Guest chat turn. {@code history} is the client's in-memory conversation (max 10 most recent messages); it is
 * only ever sent to the AI as plain user/assistant turns, never as system instructions.
 */
public record GuestChatRequest(
        @NotBlank(message = "메시지를 입력해주세요.")
        @Size(max = 2000, message = "메시지는 2000자 이하여야 합니다.")
        String content,

        @Size(max = 10, message = "대화 기록은 최근 10개까지만 보낼 수 있습니다.")
        List<@Valid @NotNull(message = "대화 기록 항목이 비어 있습니다.") HistoryItem> history
) {
    public record HistoryItem(
            @NotNull(message = "역할은 USER 또는 PERSONA여야 합니다.")
            @Pattern(regexp = "USER|PERSONA", message = "역할은 USER 또는 PERSONA여야 합니다.")
            String role,

            @NotBlank(message = "대화 내용이 비어 있습니다.")
            @Size(max = 2000, message = "대화 내용은 2000자 이하여야 합니다.")
            String content
    ) {
    }

    public List<HistoryItem> historyOrEmpty() {
        return history == null ? List.of() : history;
    }
}
