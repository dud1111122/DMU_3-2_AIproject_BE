package com.dmu.dshorts.domain.post.service;

import com.dmu.dshorts.global.common.Ids;
import com.dmu.dshorts.global.error.ApiException;

/** Shared parsing of read-side query parameters. */
public final class QueryParams {

    public static final int MAX_QUERY_LENGTH = 100;

    private QueryParams() {
    }

    /** Optional ID query parameter: blank → null, invalid → 400 VALIDATION_FAILED. */
    public static Long optionalId(String raw, String field) {
        return raw == null || raw.isBlank() ? null : Ids.parseBodyId(raw, field);
    }

    /** Optional search text: trimmed, blank → null, longer than 100 chars → 400. */
    public static String optionalText(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.strip();
        if (text.length() > MAX_QUERY_LENGTH) {
            throw ApiException.validation(field, "검색어는 " + MAX_QUERY_LENGTH + "자 이하여야 합니다.");
        }
        return text;
    }

    /** Required search text: like {@link #optionalText} but blank → 400. */
    public static String requiredText(String raw, String field) {
        String text = optionalText(raw, field);
        if (text == null) {
            throw ApiException.validation(field, "검색어를 입력하세요.");
        }
        return text;
    }
}
