package com.dmu.dshorts.domain.debate.service;

import com.dmu.dshorts.domain.debate.entity.Debate;
import com.dmu.dshorts.domain.debate.entity.DebateStatus;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Limits and shared conflict errors of the debate feature. */
public final class DebateRules {

    /** AI and user turns combined; reaching this ends the debate. Keep in sync with the debate_turns CHECKs. */
    public static final int TURN_LIMIT = 30;
    /** Max characters of one turn (AI or user), after trim. */
    public static final int TURN_MAX_CHARS = 300;
    public static final int TOPIC_MAX_CHARS = 200;

    public static final String CODE_DEBATE_ENDED = "DEBATE_ENDED";
    public static final String CODE_TURN_LIMIT_REACHED = "TURN_LIMIT_REACHED";

    private DebateRules() {
    }

    /** Throws 409 DEBATE_ENDED when the debate is ENDED, 409 TURN_LIMIT_REACHED when it has 12 turns. */
    static void requireActive(Debate debate) {
        if (debate.getStatus() == DebateStatus.ENDED) {
            throw debateEnded();
        }
        if (debate.getTurnCount() >= TURN_LIMIT) {
            throw turnLimitReached();
        }
    }

    static ApiException debateEnded() {
        return new ApiException(HttpStatus.CONFLICT, CODE_DEBATE_ENDED, "이미 종료된 대담입니다.");
    }

    static ApiException turnLimitReached() {
        return new ApiException(HttpStatus.CONFLICT, CODE_TURN_LIMIT_REACHED,
                "대담이 최대 " + TURN_LIMIT + "턴에 도달했습니다.");
    }

    static ApiException requestInProgress() {
        return new ApiException(ErrorCode.REQUEST_IN_PROGRESS);
    }
}
