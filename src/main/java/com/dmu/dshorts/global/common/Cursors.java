package com.dmu.dshorts.global.common;

import com.dmu.dshorts.global.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Opaque keyset cursors and the shared {@code limit} rule (default 20, max 50).
 *
 * <p>A cursor is base64url text that the client passes back unchanged. Two shapes exist:
 * <ul>
 *   <li>time + id, e.g. feed {@code (publishedAt, id)}, saved posts {@code (createdAt, postId)},
 *       debates {@code (completedAt, id)}: {@link #encode(Instant, long)} / {@link #decodeTimeId(String)}</li>
 *   <li>id only, e.g. personas by id: {@link #encodeId(long)} / {@link #decodeId(String)}</li>
 * </ul>
 * A malformed cursor → 400 VALIDATION_FAILED (field {@code cursor}). {@code null}/blank cursor → {@code null}
 * (first page).
 *
 * <p>Typical "newest first" keyset condition for the next page:
 * {@code where (p.publishedAt < :time or (p.publishedAt = :time and p.id < :id)) order by p.publishedAt desc, p.id desc}
 * fetched with {@code PageRequest.ofSize(limit + 1)} and turned into a page by {@link PageResponse#slice}.
 */
public final class Cursors {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    private static final String TIME_PREFIX = "t1|";
    private static final String ID_PREFIX = "i1|";

    private Cursors() {
    }

    /** Decoded composite cursor. */
    public record TimeIdCursor(Instant time, long id) {
    }

    /**
     * Resolves the {@code limit} query parameter: {@code null} → 20, above 50 → 50, below 1 → 400.
     */
    public static int limit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        if (requested < 1) {
            throw ApiException.validation("limit", "limit은 1 이상이어야 합니다.");
        }
        return Math.min(requested, MAX_LIMIT);
    }

    public static String encode(Instant time, long id) {
        return base64(TIME_PREFIX + time.toString() + "|" + id);
    }

    public static TimeIdCursor decodeTimeId(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String text = unbase64(cursor);
        if (!text.startsWith(TIME_PREFIX)) {
            throw invalid();
        }
        String[] parts = text.substring(TIME_PREFIX.length()).split("\\|");
        if (parts.length != 2) {
            throw invalid();
        }
        try {
            return new TimeIdCursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (DateTimeParseException | NumberFormatException ex) {
            throw invalid();
        }
    }

    public static String encodeId(long id) {
        return base64(ID_PREFIX + id);
    }

    public static Long decodeId(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String text = unbase64(cursor);
        if (!text.startsWith(ID_PREFIX)) {
            throw invalid();
        }
        try {
            return Long.parseLong(text.substring(ID_PREFIX.length()));
        } catch (NumberFormatException ex) {
            throw invalid();
        }
    }

    private static String base64(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String unbase64(String cursor) {
        try {
            return new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return ApiException.validation("cursor", "올바르지 않은 커서입니다.");
    }
}
