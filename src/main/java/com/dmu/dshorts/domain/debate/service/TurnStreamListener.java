package com.dmu.dshorts.domain.debate.service;

import com.dmu.dshorts.domain.debate.dto.DebateDtos.TurnResponse;
import com.dmu.dshorts.global.error.ApiException;

/**
 * Receives the events of one AI turn generation. Called from a worker thread. Order:
 * {@code delta}* then either {@code turn} (saved) or {@code error} (nothing saved, except that a
 * 12th-turn summary failure is reported after the saved {@code turn}), then {@code complete} exactly once.
 */
public interface TurnStreamListener {

    void delta(String text);

    void turn(TurnResponse turn);

    void error(ApiException error);

    void complete();
}
