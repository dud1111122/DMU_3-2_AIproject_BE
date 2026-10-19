package com.dmu.dshorts.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "아이디를 입력해주세요.") @Size(max = 30) String username,
        @NotBlank(message = "비밀번호를 입력해주세요.") @Size(max = 200) String password) {
}
