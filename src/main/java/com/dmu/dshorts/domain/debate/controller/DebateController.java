package com.dmu.dshorts.domain.debate.controller;

import com.dmu.dshorts.domain.debate.dto.DebateDtos.CreateDebateRequest;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.DebateListResponse;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.DebateResponse;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.DeltaEvent;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.ErrorEvent;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.MessageRequest;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.TurnResponse;
import com.dmu.dshorts.domain.debate.service.DebateConversationService;
import com.dmu.dshorts.domain.debate.service.DebateService;
import com.dmu.dshorts.domain.debate.service.TurnStreamListener;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.security.CurrentUser;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Members only. All endpoints need a session (guests get 401). */
@RestController
@RequestMapping("/api/v1")
public class DebateController {

    /** Upper bound for one AI turn stream (including the 12th-turn summary). */
    private static final long TURN_STREAM_TIMEOUT_MS = 5L * 60 * 1000;

    private final DebateService debateService;
    private final DebateConversationService conversationService;

    public DebateController(DebateService debateService, DebateConversationService conversationService) {
        this.debateService = debateService;
        this.conversationService = conversationService;
    }

    /** 201 with the new debate (no turns yet). */
    @PostMapping("/debates")
    public ResponseEntity<ApiResponse<DebateResponse>> create(@CurrentUser(required = true) AuthUser user,
                                                              @RequestBody CreateDebateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(debateService.create(user.id(), request)));
    }

    @GetMapping("/debates/{id}")
    public ApiResponse<DebateResponse> get(@CurrentUser(required = true) AuthUser user, @PathVariable Long id) {
        return ApiResponse.of(debateService.get(user.id(), id));
    }

    @GetMapping("/users/me/debates")
    public ApiResponse<DebateListResponse> list(@CurrentUser(required = true) AuthUser user,
                                                @RequestParam(required = false) String cursor,
                                                @RequestParam(required = false) Integer limit) {
        return ApiResponse.of(debateService.list(user.id(), cursor, limit));
    }

    /** 201 with the stored user turn. */
    @PostMapping("/debates/{id}/messages")
    public ResponseEntity<ApiResponse<TurnResponse>> postMessage(@CurrentUser(required = true) AuthUser user,
                                                                 @PathVariable Long id,
                                                                 @RequestBody MessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(conversationService.postMessage(user.id(), id, request)));
    }

    /**
     * Generates the next AI turn and streams it as {@code text/event-stream}: {@code delta} events, then
     * {@code turn} (saved) or {@code error} (nothing saved), then the stream completes. Errors before the
     * stream starts are the normal JSON error response.
     */
    @PostMapping("/debates/{id}/turns")
    public SseEmitter nextTurn(@CurrentUser(required = true) AuthUser user, @PathVariable Long id) {
        SseEmitter emitter = new SseEmitter(TURN_STREAM_TIMEOUT_MS);
        conversationService.streamNextTurn(user.id(), id, new SseTurnStream(emitter));
        return emitter;
    }

    /** 200 with the debate ENDED and its summary. */
    @PostMapping("/debates/{id}/end")
    public ApiResponse<DebateResponse> end(@CurrentUser(required = true) AuthUser user, @PathVariable Long id) {
        return ApiResponse.of(conversationService.end(user.id(), id));
    }

    /** Writes turn events to one SSE emitter. Sends after a client disconnect are dropped. */
    private static final class SseTurnStream implements TurnStreamListener {

        private final SseEmitter emitter;
        private volatile boolean open = true;

        SseTurnStream(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void delta(String text) {
            send("delta", new DeltaEvent(text));
        }

        @Override
        public void turn(TurnResponse turn) {
            send("turn", turn);
        }

        @Override
        public void error(ApiException error) {
            send("error", new ErrorEvent(error.getCode(), error.getMessage()));
        }

        @Override
        public void complete() {
            open = false;
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // already completed or timed out
            }
        }

        private void send(String name, Object data) {
            if (!open) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                open = false;
            }
        }
    }
}
