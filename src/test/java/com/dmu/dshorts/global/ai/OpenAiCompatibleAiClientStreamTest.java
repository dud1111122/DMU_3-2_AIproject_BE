package com.dmu.dshorts.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Parsing of the streamed {@code /chat/completions} response, against a local fake provider. */
class OpenAiCompatibleAiClientStreamTest {

    private final AtomicReference<String> responseBody = new AtomicReference<>("");
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(responseStatus.get(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopProvider() {
        server.stop(0);
    }

    @Test
    void sendsFragmentsInOrderSkipsNoiseAndStopsAtDone() {
        responseBody.set("""
                data: {"choices":[{"delta":{"role":"assistant"}}]}

                : keep-alive

                data: {"choices":[{"delta":{"content":"안녕"}}]}

                data: {"choices":[{"delta":{"content":"하세요"}}]}

                data: {"choices":[{"delta":{}}]}

                data: [DONE]

                data: {"choices":[{"delta":{"content":"버려짐"}}]}
                """);
        List<String> fragments = new ArrayList<>();

        String text = client().stream(List.of(AiMessage.user("hi")), fragments::add);

        assertThat(fragments).containsExactly("안녕", "하세요");
        assertThat(text).isEqualTo("안녕하세요");
    }

    @Test
    void httpErrorIsAiFailed() {
        responseStatus.set(500);
        responseBody.set("{\"error\":\"down\"}");

        assertThatThrownBy(() -> client().stream(List.of(AiMessage.user("hi")), fragment -> { }))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.AI_FAILED.name()));
    }

    @Test
    void providerErrorChunkIsAiFailed() {
        responseBody.set("data: {\"error\":{\"message\":\"quota\"}}\n\n");

        assertThatThrownBy(() -> client().stream(List.of(AiMessage.user("hi")), fragment -> { }))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.AI_FAILED.name()));
    }

    @Test
    void emptyStreamIsAiFailed() {
        responseBody.set("data: [DONE]\n\n");

        assertThatThrownBy(() -> client().stream(List.of(AiMessage.user("hi")), fragment -> { }))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.AI_FAILED.name()));
    }

    @Test
    void notConfiguredIsAiNotConfigured() {
        OpenAiCompatibleAiClient unconfigured = new OpenAiCompatibleAiClient(RestClient.builder(), new ObjectMapper(),
                "openai-compatible", "", "test-model", "", baseUrl, Duration.ofSeconds(5), Duration.ofSeconds(5));

        assertThatThrownBy(() -> unconfigured.stream(List.of(AiMessage.user("hi")), fragment -> { }))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo(ErrorCode.AI_NOT_CONFIGURED.name()));
    }

    private OpenAiCompatibleAiClient client() {
        return new OpenAiCompatibleAiClient(RestClient.builder(), new ObjectMapper(), "openai-compatible",
                "test-key", "test-model", "", baseUrl, Duration.ofSeconds(5), Duration.ofSeconds(5));
    }
}
