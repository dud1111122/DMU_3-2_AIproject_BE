package com.dmu.dshorts.domain.debate.service;

import com.dmu.dshorts.domain.debate.dto.DebateDtos.CreateDebateRequest;
import com.dmu.dshorts.global.common.Ids;
import com.dmu.dshorts.global.error.ApiException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validated create input: persona ids in request order, trimmed topic and its reuse key.
 */
record DebateInput(Long firstPersonaId, Long secondPersonaId, String topic, String topicKey) {

    static final int TOPIC_MAX = 200;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    Long lowId() {
        return Math.min(firstPersonaId, secondPersonaId);
    }

    Long highId() {
        return Math.max(firstPersonaId, secondPersonaId);
    }

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
        if (length < 1 || length > TOPIC_MAX) {
            throw ApiException.validation("topic", "논제는 1~200자여야 합니다.");
        }
        return new DebateInput(ids.get(0), ids.get(1), topic, topicKey(topic));
    }

    /** trim, collapse whitespace runs to one space, lowercase ASCII letters only. */
    static String topicKey(String topic) {
        String collapsed = WHITESPACE.matcher(topic.strip()).replaceAll(" ");
        StringBuilder key = new StringBuilder(collapsed.length());
        for (int i = 0; i < collapsed.length(); i++) {
            char c = collapsed.charAt(i);
            key.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return key.toString();
    }
}
