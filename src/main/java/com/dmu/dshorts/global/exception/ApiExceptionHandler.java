package com.dmu.dshorts.global.exception;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(Map.of("error", Map.of(
            "code", status.name(), "message", ex.getReason() == null ? "요청을 처리하지 못했습니다." : ex.getReason(),
            "timestamp", Instant.now().toString())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        var fields = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage() == null ? "잘못된 값입니다." : e.getDefaultMessage()))
            .toList();
        return ResponseEntity.badRequest().body(Map.of("error", Map.of(
            "code", "VALIDATION_FAILED", "message", "입력값을 확인해주세요.", "fields", fields,
            "timestamp", Instant.now().toString())));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError().body(Map.of("error", Map.of(
            "code", "INTERNAL_ERROR", "message", "서버에서 요청을 처리하지 못했습니다.",
            "timestamp", Instant.now().toString())));
    }
}
