package com.dmu.dshorts.domain.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH body: only non-null fields change. */
public record UpdateMeRequest(
        @Pattern(regexp = "^[a-z0-9._]{3,30}$", message = "아이디는 소문자·숫자·점·밑줄 3~30자여야 합니다.")
        String username,
        @Size(min = 1, max = 50, message = "표시 이름은 1~50자여야 합니다.")
        String displayName,
        @Size(max = 500, message = "소개는 500자 이하여야 합니다.")
        String bio) {
}
