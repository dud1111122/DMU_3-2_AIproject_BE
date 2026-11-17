package com.dmu.dshorts.domain.debate.service;

import com.dmu.dshorts.domain.debate.dto.DebateDtos.CreateDebateRequest;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.MessageRequest;
import com.dmu.dshorts.global.common.Ids;
import com.dmu.dshorts.global.error.ApiException;
import java.util.List;

/** Validated create input: persona ids in request order (A, B) and the stripped topic. */
record DebateInput(Long personaAId, Long personaBId, String topic) {

    static DebateInput from(CreateDebateRequest request) {
        if (request == null) {
            throw ApiException.badRequest("요청 본문이 필요합니다.");
        }
        List<String> rawIds = request.personaIds();
        if (rawIds == null || rawIds.size() != 2) {
            throw ApiException.validation("personaIds", "페르소나 두 명을 선택해야 합니다.");
        }
        List<Long> ids = Ids.parseBodyIds(rawIds, "personaIds");
        if (ids.get(0).equals(ids.get(1))) {
            throw ApiException.validation("personaIds", "서로 다른 페르소나 두 명을 선택해야 합니다.");
        }
        String topic = request.topic() == null ? "" : request.topic().strip();
        int length = topic.codePointCount(0, topic.length());
        if (length < 1 || length > DebateRules.TOPIC_MAX_CHARS) {
            throw ApiException.validation("topic", "논제는 1~200자여야 합니다.");
        }
        return new DebateInput(ids.get(0), ids.get(1), topic);
    }

    /** Trimmed user message, 1..300 characters. */
    static String messageContent(MessageRequest request) {
        if (request == null) {
            throw ApiException.badRequest("요청 본문이 필요합니다.");
        }
        String content = request.content() == null ? "" : request.content().strip();
        int length = content.codePointCount(0, content.length());
        if (length < 1 || length > DebateRules.TURN_MAX_CHARS) {
            throw ApiException.validation("content", "메시지는 1~300자여야 합니다.");
        }
        return content;
    }
}
