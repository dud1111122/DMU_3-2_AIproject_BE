package com.dmu.dshorts.domain.post.dto;

/** Public URL of a server-managed media file: files are served under {@code /media/**}. */
public final class MediaUrls {

    public static final String PREFIX = "/media/";

    private MediaUrls() {
    }

    /** {@code "cards/31/1.webp"} → {@code "/media/cards/31/1.webp"}; null/blank path → null. */
    public static String of(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String trimmed = path.strip();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return PREFIX + trimmed;
    }
}
