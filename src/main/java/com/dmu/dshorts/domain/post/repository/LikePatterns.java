package com.dmu.dshorts.domain.post.repository;

import java.util.Locale;

/**
 * Builds {@code LIKE} patterns for simple partial-match search. Queries must use {@code lower(column) like :pattern escape '!'}.
 */
public final class LikePatterns {

    public static final char ESCAPE = '!';

    private LikePatterns() {
    }

    /** {@code "50%"} → {@code "%50!%%"} (lower-cased, wildcards escaped). */
    public static String contains(String text) {
        StringBuilder sb = new StringBuilder("%");
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '%' || c == '_' || c == ESCAPE) {
                sb.append(ESCAPE);
            }
            sb.append(c);
        }
        return sb.append('%').toString();
    }
}
