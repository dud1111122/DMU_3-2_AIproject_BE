package com.dmu.dshorts.global.error;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * The single exception type for expected API errors. {@link GlobalExceptionHandler} renders it as
 * {@code {"error": {"code", "message", "fields", "details"?}}} with the given HTTP status.
 *
 * <pre>
 * throw ApiException.notFound();
 * throw new ApiException(ErrorCode.CONFLICT, "이미 사용 중인 아이디입니다.");
 * throw ApiException.validation("topic", "논제는 1~200자여야 합니다.");
 * throw new ApiException(ErrorCode.DAILY_LIMIT_EXCEEDED).withDetails(Map.of("remainingToday", 0, "resetAt", resetAt));
 * </pre>
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldErrorDetail> fields;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of(), null);
    }

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode.status(), errorCode.name(), message, List.of(), null);
    }

    private ApiException(HttpStatus status, String code, String message,
                         List<FieldErrorDetail> fields, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = List.copyOf(fields);
        this.details = details == null ? null : Map.copyOf(details);
    }

    /** Returns a copy carrying extra data rendered under {@code error.details}. */
    public ApiException withDetails(Map<String, Object> details) {
        return new ApiException(status, code, getMessage(), fields, details);
    }

    /** Returns a copy carrying per-field validation errors rendered under {@code error.fields}. */
    public ApiException withFields(List<FieldErrorDetail> fields) {
        return new ApiException(status, code, getMessage(), fields, details);
    }

    public static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND);
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(ErrorCode.BAD_REQUEST, message);
    }

    /** 400 VALIDATION_FAILED with a single field error. */
    public static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED)
                .withFields(List.of(new FieldErrorDetail(field, message)));
    }

    public static ApiException unauthorized() {
        return new ApiException(ErrorCode.UNAUTHORIZED);
    }

    public static ApiException forbidden() {
        return new ApiException(ErrorCode.FORBIDDEN);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<FieldErrorDetail> getFields() {
        return fields;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
