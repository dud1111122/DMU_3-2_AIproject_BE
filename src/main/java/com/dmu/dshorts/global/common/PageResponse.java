package com.dmu.dshorts.global.common;

import java.util.List;
import java.util.function.Function;

/**
 * List payload {@code {"items": [...], "nextCursor": "..."|null}}; wrap it as
 * {@code ApiResponse.of(PageResponse...)} to get {@code {"data": {"items", "nextCursor"}}}.
 */
public record PageResponse<T>(List<T> items, String nextCursor) {

    public static <T> PageResponse<T> of(List<T> items, String nextCursor) {
        return new PageResponse<>(List.copyOf(items), nextCursor);
    }

    /**
     * Builds a page from rows fetched with {@code limit + 1} (keyset pagination). If more than {@code limit}
     * rows came back, the extra row is dropped and {@code nextCursor} is built from the last kept row.
     *
     * <pre>
     * int limit = Cursors.limit(limitParam);
     * List&lt;Post&gt; rows = repository.findPage(..., PageRequest.ofSize(limit + 1));
     * return PageResponse.slice(rows, limit, p -&gt; Cursors.encode(p.getPublishedAt(), p.getId()), PostSummary::from);
     * </pre>
     */
    public static <E, T> PageResponse<T> slice(List<E> rows, int limit,
                                               Function<E, String> cursorOf, Function<E, T> mapper) {
        boolean hasMore = rows.size() > limit;
        List<E> kept = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore && !kept.isEmpty() ? cursorOf.apply(kept.get(kept.size() - 1)) : null;
        return new PageResponse<>(kept.stream().map(mapper).toList(), next);
    }
}
