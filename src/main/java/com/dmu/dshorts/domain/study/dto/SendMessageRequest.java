package com.dmu.dshorts.domain.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "메시지를 입력해주세요.")
        @Size(max = 2000, message = "메시지는 2000자 이하여야 합니다.")
        String content
) {
}
