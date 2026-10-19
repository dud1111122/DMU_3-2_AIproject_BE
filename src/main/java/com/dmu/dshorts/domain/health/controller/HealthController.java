package com.dmu.dshorts.domain.health.controller;

import com.dmu.dshorts.global.ai.AiClient;
import com.dmu.dshorts.global.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final AiClient aiClient;

    public HealthController(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    /** {@code {"data": {"status": "ok", "ai": "configured" | "not-configured"}}}; no key or internal detail. */
    @GetMapping
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.of(new HealthResponse("ok", aiClient.configured() ? "configured" : "not-configured"));
    }

    public record HealthResponse(String status, String ai) {
    }
}
