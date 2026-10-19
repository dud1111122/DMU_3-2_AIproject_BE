package com.dmu.dshorts.global.common;

import com.dmu.dshorts.global.error.ApiException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * IDs are {@code bigint} in the DB but JSON strings in the API.
 *
 * <ul>
 *   <li>DTO fields holding IDs are {@code String}; fill them with {@link #str(Long)}.</li>
 *   <li>Path variables: just declare {@code @PathVariable Long id}. A non-numeric value becomes
 *       404 NOT_FOUND via the global handler. {@link #parsePathId(String)} does the same by hand.</li>
 *   <li>Body / query IDs: {@link #parseBodyId(String, String)} etc. Invalid → 400 VALIDATION_FAILED
 *       with the given field name.</li>
 * </ul>
 */
public final class Ids {

    private Ids() {
    }

    /** Long → JSON string ID ({@code null} stays {@code null}). */
    public static String str(Long id) {
        return id == null ? null : id.toString();
    }

    public static List<String> strs(Collection<Long> ids) {
        return ids.stream().map(Ids::str).toList();
    }

    /** Parses a path ID. Anything that is not a positive long → 404 NOT_FOUND. */
    public static Long parsePathId(String raw) {
        Long id = tryParse(raw);
        if (id == null) {
            throw ApiException.notFound();
        }
        return id;
    }

    /** Parses an ID from a request body/query. Missing or not a positive long → 400 VALIDATION_FAILED. */
    public static Long parseBodyId(String raw, String field) {
        Long id = tryParse(raw);
        if (id == null) {
            throw ApiException.validation(field, "올바른 ID가 아닙니다.");
        }
        return id;
    }

    /** Parses a list of body IDs; field errors are reported as {@code field[index]}. */
    public static List<Long> parseBodyIds(Collection<String> raws, String field) {
        if (raws == null) {
            throw ApiException.validation(field, "필수 값입니다.");
        }
        List<Long> result = new ArrayList<>(raws.size());
        int index = 0;
        for (String raw : raws) {
            result.add(parseBodyId(raw, field + "[" + index + "]"));
            index++;
        }
        return result;
    }

    /** Parses a comma separated query value such as {@code topicIds=1,2}. Blank → empty list. */
    public static List<Long> parseCsvIds(String csv, String field) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String part : csv.split(",")) {
            if (!part.isBlank()) {
                result.add(parseBodyId(part.trim(), field));
            }
        }
        return result;
    }

    private static Long tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
