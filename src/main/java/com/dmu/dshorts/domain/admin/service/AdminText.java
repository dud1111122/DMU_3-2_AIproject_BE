package com.dmu.dshorts.domain.admin.service;

import com.dmu.dshorts.global.error.ApiException;

/** Small trimming/length helpers for admin input. Errors are 400 VALIDATION_FAILED on the given field. */
final class AdminText {

    private AdminText() {
    }

    /** Trimmed, non-blank, at most {@code max} characters ({@code max <= 0}: unlimited). */
    static String requireText(String raw, String field, int max) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw ApiException.validation(field, "필수 값입니다.");
        }
        checkLength(value, field, max);
        return value;
    }

    /** Trimmed; blank → {@code blankValue}. */
    static String optionalText(String raw, String field, int max, String blankValue) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return blankValue;
        }
        checkLength(value, field, max);
        return value;
    }

    private static void checkLength(String value, String field, int max) {
        if (max > 0 && value.codePointCount(0, value.length()) > max) {
            throw ApiException.validation(field, max + "자 이하여야 합니다.");
        }
    }
}
