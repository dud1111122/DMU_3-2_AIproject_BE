package com.dmu.dshorts.global.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Provider-neutral AI text completion. Feature packages (study, debate, generation) build their own prompts
 * and validate the output themselves.
 *
 * <p>Errors (as {@link com.dmu.dshorts.global.error.ApiException}):
 * not configured → 503 AI_NOT_CONFIGURED; network/HTTP/empty response/invalid JSON → 502 AI_FAILED.
 * Calls are blocking (up to the read timeout, default 60s): never call inside an open DB transaction.
 */
public interface AiClient {

    /** True when provider, API key and model are all set. Reported by GET /api/v1/health. */
    boolean configured();

    /** Model identifier to store in {@code ai_model} columns, or null when not configured. */
    String modelName();

    /**
     * Sends the messages and returns the assistant text.
     *
     * @param jsonMode ask the provider for a single JSON object (include the word "JSON" in your prompt)
     */
    String complete(List<AiMessage> messages, boolean jsonMode);

    /** {@link #complete} in JSON mode, parsed. Unparseable output → 502 AI_FAILED. */
    JsonNode completeJson(List<AiMessage> messages);
}
