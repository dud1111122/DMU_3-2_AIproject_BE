package com.dmu.dshorts.global;

import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints exercising GlobalExceptionHandler (lives in test sources, never shipped). */
@RestController
@RequestMapping("/api/v1/test-probe")
class ErrorProbeController {

    record Body(@NotBlank String name) {
    }

    @GetMapping("/items/{id}")
    ApiResponse<Map<String, Long>> item(@PathVariable Long id) {
        return ApiResponse.of(Map.of("id", id));
    }

    @GetMapping("/required")
    ApiResponse<String> required(@RequestParam String q) {
        return ApiResponse.of(q);
    }

    @PostMapping("/body")
    ApiResponse<String> body(@Valid @RequestBody Body body) {
        return ApiResponse.of(body.name());
    }

    @GetMapping("/limit")
    void limit() {
        throw new ApiException(ErrorCode.DAILY_LIMIT_EXCEEDED).withDetails(Map.of("remainingToday", 0));
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("unexpected");
    }
}
