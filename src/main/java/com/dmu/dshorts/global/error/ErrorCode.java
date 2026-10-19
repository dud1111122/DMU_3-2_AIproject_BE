package com.dmu.dshorts.global.error;

import org.springframework.http.HttpStatus;

/**
 * Error codes of the API contract. Each code has a fixed HTTP status and a default Korean message.
 * Throw with {@code throw new ApiException(ErrorCode.NOT_FOUND)} or the static factories on {@link ApiException}.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "CSRF 토큰이 없거나 올바르지 않습니다. 토큰을 다시 받아 요청해주세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    CONFLICT(HttpStatus.CONFLICT, "현재 상태와 충돌하는 요청입니다."),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "이미 처리 중인 요청이 있습니다. 잠시 후 다시 시도해주세요."),
    DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘 사용할 수 있는 횟수를 모두 사용했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 요청을 처리하지 못했습니다."),
    AI_FAILED(HttpStatus.BAD_GATEWAY, "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요."),
    AI_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "AI가 설정되지 않았습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
