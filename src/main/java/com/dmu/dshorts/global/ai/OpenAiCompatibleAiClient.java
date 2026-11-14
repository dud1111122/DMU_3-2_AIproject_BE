package com.dmu.dshorts.global.ai;

import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link AiClient} for any OpenAI-compatible {@code POST /chat/completions} API.
 * Configured by AI_PROVIDER (openai | openai-compatible), AI_API_KEY, AI_MODEL, optional AI_BASE_URL.
 */
@Component
public class OpenAiCompatibleAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiClient.class);
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("openai", "openai-compatible");
    private static final double TEMPERATURE = 0.5;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String provider;
    private final String apiKey;
    private final String model;
    private final String imageModel;

    public OpenAiCompatibleAiClient(RestClient.Builder builder,
                                    ObjectMapper objectMapper,
                                    @Value("${app.ai.provider:}") String provider,
                                    @Value("${app.ai.api-key:}") String apiKey,
                                    @Value("${app.ai.model:}") String model,
                                    @Value("${app.ai.image-model:}") String imageModel,
                                    @Value("${app.ai.base-url:https://api.openai.com/v1}") String baseUrl,
                                    @Value("${app.ai.connect-timeout:10s}") Duration connectTimeout,
                                    @Value("${app.ai.read-timeout:60s}") Duration readTimeout) {
        this.objectMapper = objectMapper;
        this.provider = provider == null ? "" : provider.trim().toLowerCase();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.imageModel = imageModel == null ? "" : imageModel.trim();

        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        if (!this.provider.isEmpty() && !SUPPORTED_PROVIDERS.contains(this.provider)) {
            log.warn("AI_PROVIDER '{}' is not supported (use openai or openai-compatible); AI is disabled", provider);
        }
    }

    @Override
    public boolean configured() {
        return SUPPORTED_PROVIDERS.contains(provider) && !apiKey.isEmpty() && !model.isEmpty();
    }

    @Override
    public String modelName() {
        return configured() ? model : null;
    }

    @Override
    public String complete(List<AiMessage> messages, boolean jsonMode) {
        if (!configured()) {
            throw new ApiException(ErrorCode.AI_NOT_CONFIGURED);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages.stream()
                .map(message -> Map.of("role", message.role().wireName(), "content", message.content()))
                .toList());
        body.put("temperature", TEMPERATURE);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            log.warn("AI provider returned HTTP {}", ex.getStatusCode().value());
            throw new ApiException(ErrorCode.AI_FAILED);
        } catch (RestClientException ex) {
            log.warn("AI provider request failed: {}", ex.getClass().getSimpleName());
            throw new ApiException(ErrorCode.AI_FAILED);
        }

        JsonNode content = response == null ? null : response.at("/choices/0/message/content");
        if (content == null || content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
            log.warn("AI provider returned an empty completion");
            throw new ApiException(ErrorCode.AI_FAILED);
        }
        return content.asText();
    }

    @Override
    public boolean imageConfigured() {
        return configured() && !imageModel.isEmpty();
    }

    @Override
    public GeneratedImage generateImage(String prompt, String size) {
        if (!imageConfigured()) {
            throw new ApiException(ErrorCode.AI_NOT_CONFIGURED);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", imageModel);
        body.put("prompt", prompt);
        body.put("size", size);
        body.put("quality", "medium");
        body.put("output_format", "jpeg");
        body.put("n", 1);

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/images/generations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            log.warn("AI image provider returned HTTP {}", ex.getStatusCode().value());
            throw new ApiException(ErrorCode.AI_FAILED);
        } catch (RestClientException ex) {
            log.warn("AI image request failed: {}", ex.getClass().getSimpleName());
            throw new ApiException(ErrorCode.AI_FAILED);
        }

        JsonNode encoded = response == null ? null : response.at("/data/0/b64_json");
        if (encoded == null || encoded.isMissingNode() || encoded.isNull() || encoded.asText().isBlank()) {
            log.warn("AI image provider returned no image");
            throw new ApiException(ErrorCode.AI_FAILED);
        }
        try {
            return new GeneratedImage(java.util.Base64.getDecoder().decode(encoded.asText()), "image/jpeg");
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.AI_FAILED);
        }
    }

    @Override
    public JsonNode completeJson(List<AiMessage> messages) {
        String text = complete(messages, true);
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node == null || !node.isObject()) {
                throw new ApiException(ErrorCode.AI_FAILED, "AI 응답 형식이 올바르지 않습니다.");
            }
            return node;
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.AI_FAILED, "AI 응답 형식이 올바르지 않습니다.");
        }
    }
}
