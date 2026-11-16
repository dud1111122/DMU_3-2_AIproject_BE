package com.dmu.dshorts.global.ai;

import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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

    /** Idle-timeout timers for streams; a stream silent for longer than the read timeout is closed. */
    private static final ScheduledExecutorService IDLE_TIMER = Executors.newSingleThreadScheduledExecutor(
            (ThreadFactory) runnable -> {
                Thread thread = new Thread(runnable, "ai-stream-idle");
                thread.setDaemon(true);
                return thread;
            });

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final java.net.http.HttpClient httpClient;
    private final String baseUrl;
    private final Duration readTimeout;
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

        this.httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        this.baseUrl = stripTrailingSlashes(baseUrl);
        this.readTimeout = readTimeout;
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
        Map<String, Object> body = chatBody(messages, jsonMode);

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

    /**
     * Same request as {@link #complete} plus {@code "stream": true}. Reads the server-sent lines of
     * {@code /chat/completions}, passes each {@code choices[0].delta.content} fragment to {@code onDelta}
     * and stops at {@code data: [DONE]}. Connect timeout and read timeout apply as in {@link #complete}: the read
     * timeout covers the response headers, and an idle stream (no line for that long) is closed.
     */
    @Override
    public String stream(List<AiMessage> messages, Consumer<String> onDelta) {
        if (!configured()) {
            throw new ApiException(ErrorCode.AI_NOT_CONFIGURED);
        }
        Map<String, Object> body = chatBody(messages, false);
        body.put("stream", true);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.AI_FAILED);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(readTimeout)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException ex) {
            log.warn("AI stream request failed: {}", ex.getClass().getSimpleName());
            throw new ApiException(ErrorCode.AI_FAILED);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.AI_FAILED);
        }
        if (response.statusCode() != 200) {
            log.warn("AI provider returned HTTP {} for a stream", response.statusCode());
            closeQuietly(response.body());
            throw new ApiException(ErrorCode.AI_FAILED);
        }

        StringBuilder text = new StringBuilder();
        try (InputStream in = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             IdleGuard guard = new IdleGuard(readTimeout, in)) {
            String line;
            while ((line = reader.readLine()) != null) {
                guard.touch();
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring("data:".length()).strip();
                if (data.equals("[DONE]")) {
                    break;
                }
                String fragment = streamedDelta(data);
                if (fragment != null && !fragment.isEmpty()) {
                    text.append(fragment);
                    onDelta.accept(fragment);
                }
            }
        } catch (IOException ex) {
            log.warn("AI stream interrupted: {}", ex.getClass().getSimpleName());
            throw new ApiException(ErrorCode.AI_FAILED);
        }
        if (text.isEmpty()) {
            log.warn("AI provider streamed an empty completion");
            throw new ApiException(ErrorCode.AI_FAILED);
        }
        return text.toString();
    }

    private Map<String, Object> chatBody(List<AiMessage> messages, boolean jsonMode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages.stream()
                .map(message -> Map.of("role", message.role().wireName(), "content", message.content()))
                .toList());
        body.put("temperature", TEMPERATURE);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    /** Text fragment of one streamed chunk, or null when the chunk carries none. Provider error → AI_FAILED. */
    private String streamedDelta(String data) {
        JsonNode chunk;
        try {
            chunk = objectMapper.readTree(data);
        } catch (JsonProcessingException ex) {
            log.warn("AI stream chunk is not valid JSON");
            throw new ApiException(ErrorCode.AI_FAILED);
        }
        if (chunk == null || chunk.has("error")) {
            log.warn("AI provider reported an error in the stream");
            throw new ApiException(ErrorCode.AI_FAILED);
        }
        JsonNode content = chunk.at("/choices/0/delta/content");
        return content.isTextual() ? content.asText() : null;
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException | RuntimeException ignored) {
            // nothing more to do
        }
    }

    private static String stripTrailingSlashes(String url) {
        String value = url == null ? "" : url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * Closes the stream when no line arrived for {@code idle}. Makes sure a stalled provider cannot hold a
     * debate lock forever. {@link #touch} is called for every line.
     */
    private static final class IdleGuard implements Closeable {

        private final Duration idle;
        private final Closeable stream;
        private ScheduledFuture<?> pending;

        IdleGuard(Duration idle, Closeable stream) {
            this.idle = idle;
            this.stream = stream;
            touch();
        }

        synchronized void touch() {
            if (pending != null) {
                pending.cancel(false);
            }
            pending = IDLE_TIMER.schedule(this::fire, idle.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void fire() {
            log.warn("AI stream idle for {} ms; closing", idle.toMillis());
            closeQuietly(stream);
        }

        @Override
        public synchronized void close() {
            if (pending != null) {
                pending.cancel(false);
            }
        }
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
