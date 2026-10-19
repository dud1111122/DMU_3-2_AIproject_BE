package com.dmu.dshorts.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Error body: {@code {"error": {"code", "message", "fields": [], "details"?}}}.
 * {@code details} is omitted unless the thrower attached extra data (e.g. 429 remainingToday/resetAt).
 */
public record ErrorResponse(Body error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String code, String message, List<FieldErrorDetail> fields, Map<String, Object> details) {
    }

    public static ErrorResponse of(String code, String message, List<FieldErrorDetail> fields, Map<String, Object> details) {
        return new ErrorResponse(new Body(code, message, fields == null ? List.of() : fields, details));
    }

    public static ErrorResponse of(ErrorCode code) {
        return of(code.name(), code.defaultMessage(), List.of(), null);
    }

    public static ErrorResponse of(ErrorCode code, String message) {
        return of(code.name(), message, List.of(), null);
    }
}
