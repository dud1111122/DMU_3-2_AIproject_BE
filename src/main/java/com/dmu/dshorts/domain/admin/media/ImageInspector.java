package com.dmu.dshorts.domain.admin.media;

import java.util.Optional;

/**
 * Detects the real image type from the file bytes (magic numbers, not the client's Content-Type) and reads
 * the pixel size for PNG, JPEG and WebP without decoding the image.
 */
public final class ImageInspector {

    public enum ImageType {
        PNG("image/png", "png"),
        JPEG("image/jpeg", "jpg"),
        WEBP("image/webp", "webp");

        private final String mime;
        private final String extension;

        ImageType(String mime, String extension) {
            this.mime = mime;
            this.extension = extension;
        }

        public String mime() {
            return mime;
        }

        public String extension() {
            return extension;
        }
    }

    /** Detected type plus size; width/height are {@code null} when the header could not be parsed. */
    public record ImageInfo(ImageType type, Integer width, Integer height) {
    }

    private ImageInspector() {
    }

    public static Optional<ImageInfo> inspect(byte[] data) {
        if (data == null || data.length < 12) {
            return Optional.empty();
        }
        if (isPng(data)) {
            return Optional.of(png(data));
        }
        if (isJpeg(data)) {
            return Optional.of(jpeg(data));
        }
        if (isWebp(data)) {
            return Optional.of(webp(data));
        }
        return Optional.empty();
    }

    private static boolean isPng(byte[] d) {
        return u8(d, 0) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && u8(d, 4) == 0x0D && u8(d, 5) == 0x0A && u8(d, 6) == 0x1A && u8(d, 7) == 0x0A;
    }

    private static boolean isJpeg(byte[] d) {
        return u8(d, 0) == 0xFF && u8(d, 1) == 0xD8 && u8(d, 2) == 0xFF;
    }

    private static boolean isWebp(byte[] d) {
        return d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
    }

    private static ImageInfo png(byte[] d) {
        // 8-byte signature, then the IHDR chunk: length(4) "IHDR"(4) width(4) height(4)
        if (d.length >= 24 && d[12] == 'I' && d[13] == 'H' && d[14] == 'D' && d[15] == 'R') {
            return sized(ImageType.PNG, be32(d, 16), be32(d, 20));
        }
        return new ImageInfo(ImageType.PNG, null, null);
    }

    private static ImageInfo jpeg(byte[] d) {
        int i = 2;
        while (i + 9 < d.length) {
            if (u8(d, i) != 0xFF) {
                i++;
                continue;
            }
            int marker = u8(d, i + 1);
            if (marker == 0xFF) {
                i++;
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }
            int length = be16(d, i + 2);
            boolean sof = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (sof) {
                return sized(ImageType.JPEG, be16(d, i + 7), be16(d, i + 5));
            }
            if (length < 2) {
                break;
            }
            i += 2 + length;
        }
        return new ImageInfo(ImageType.JPEG, null, null);
    }

    private static ImageInfo webp(byte[] d) {
        if (d.length >= 30) {
            String chunk = new String(d, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
            switch (chunk) {
                case "VP8 " -> {
                    return sized(ImageType.WEBP, le16(d, 26) & 0x3FFF, le16(d, 28) & 0x3FFF);
                }
                case "VP8L" -> {
                    long bits = u8(d, 21) | (u8(d, 22) << 8) | (u8(d, 23) << 16) | ((long) u8(d, 24) << 24);
                    return sized(ImageType.WEBP, (int) (bits & 0x3FFF) + 1, (int) ((bits >> 14) & 0x3FFF) + 1);
                }
                case "VP8X" -> {
                    return sized(ImageType.WEBP, le24(d, 24) + 1, le24(d, 27) + 1);
                }
                default -> {
                    // unknown chunk; type is still WebP
                }
            }
        }
        return new ImageInfo(ImageType.WEBP, null, null);
    }

    private static ImageInfo sized(ImageType type, long width, long height) {
        if (width <= 0 || height <= 0 || width > Integer.MAX_VALUE || height > Integer.MAX_VALUE) {
            return new ImageInfo(type, null, null);
        }
        return new ImageInfo(type, (int) width, (int) height);
    }

    private static int u8(byte[] d, int i) {
        return d[i] & 0xFF;
    }

    private static int be16(byte[] d, int i) {
        return (u8(d, i) << 8) | u8(d, i + 1);
    }

    private static long be32(byte[] d, int i) {
        return ((long) u8(d, i) << 24) | (u8(d, i + 1) << 16) | (u8(d, i + 2) << 8) | u8(d, i + 3);
    }

    private static int le16(byte[] d, int i) {
        return u8(d, i) | (u8(d, i + 1) << 8);
    }

    private static int le24(byte[] d, int i) {
        return u8(d, i) | (u8(d, i + 1) << 8) | (u8(d, i + 2) << 16);
    }
}
