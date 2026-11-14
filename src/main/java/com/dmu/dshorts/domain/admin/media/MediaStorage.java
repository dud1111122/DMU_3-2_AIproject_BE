package com.dmu.dshorts.domain.admin.media;

import com.dmu.dshorts.domain.admin.media.ImageInspector.ImageInfo;
import com.dmu.dshorts.global.error.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Server-managed image files. Files live under the media directory ({@code MEDIA_DIR}, default {@code ./media});
 * the DB stores only the relative path (e.g. {@code uploads/2026/11/<uuid>.webp}) and clients get
 * {@code "/media/" + path} as URL. Clients can never choose a file name: uploads get a server-generated path,
 * and path references in admin PATCH requests must point at an existing image under the media directory.
 */
@Component
public class MediaStorage {

    public static final String URL_PREFIX = "/media/";

    /** Relative path: segments of [A-Za-z0-9_.-], no leading slash, no backslashes. */
    private static final Pattern RELATIVE_PATH = Pattern.compile("^[A-Za-z0-9_-][A-Za-z0-9_./-]{0,250}$");

    private final Path root;
    private final long maxBytes;

    public MediaStorage(@Value("${app.media.dir:${MEDIA_DIR:./media}}") String dir,
                        @Value("${app.media.max-image-bytes:5242880}") long maxBytes) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public Path root() {
        return root;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public static String urlOf(String path) {
        return path == null ? null : URL_PREFIX + path;
    }

    /** Uploaded and stored image. */
    public record StoredImage(String path, String mime, Integer width, Integer height, long size) {
    }

    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("file", "이미지 파일이 필요합니다.");
        }
        if (file.getSize() > maxBytes) {
            throw ApiException.validation("file", "이미지는 최대 " + (maxBytes / (1024 * 1024)) + "MB까지 업로드할 수 있습니다.");
        }
        byte[] data;
        try (InputStream in = file.getInputStream()) {
            data = in.readAllBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("업로드 파일을 읽을 수 없습니다.");
        }
        ImageInfo info = ImageInspector.inspect(data)
                .orElseThrow(() -> ApiException.validation("file", "PNG, JPEG, WebP 이미지만 업로드할 수 있습니다."));
        String declared = file.getContentType();
        if (declared != null && !declared.isBlank() && !isAllowedMime(declared)) {
            throw ApiException.validation("file", "PNG, JPEG, WebP 이미지만 업로드할 수 있습니다.");
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String relative = "uploads/%d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(),
                UUID.randomUUID(), info.type().extension());
        Path target = root.resolve(relative).normalize();
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temp, data);
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store uploaded image", ex);
        }
        return new StoredImage(relative, info.type().mime(), info.width(), info.height(), data.length);
    }

    /**
     * Validates a path reference from an admin request and returns the actual image metadata.
     * Anything that is not a supported image file under the media directory → 400 VALIDATION_FAILED.
     */
    /**
     * Saves a server-generated image (for example a card background) at a fixed relative path under the media
     * directory, replacing any earlier file at that path. The path must stay inside the media directory.
     */
    public StoredImage saveGenerated(String relativePath, byte[] bytes, String mime, int width, int height) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("generated image path escapes the media directory");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException ex) {
            throw new UncheckedIOException("could not write generated image", ex);
        }
        return new StoredImage(relativePath, mime, width, height, bytes.length);
    }

    public ImageInfo requireExistingImage(String path, String field) {
        if (path == null || !RELATIVE_PATH.matcher(path).matches() || path.contains("..") || path.contains("//")) {
            throw ApiException.validation(field, "업로드 API가 반환한 이미지 경로만 사용할 수 있습니다.");
        }
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw ApiException.validation(field, "존재하지 않는 이미지 경로입니다.");
        }
        try {
            byte[] head = readHead(file);
            return ImageInspector.inspect(head)
                    .orElseThrow(() -> ApiException.validation(field, "지원하지 않는 이미지 형식입니다."));
        } catch (IOException ex) {
            throw ApiException.validation(field, "이미지 파일을 읽을 수 없습니다.");
        }
    }

    private static byte[] readHead(Path file) throws IOException {
        // Enough for PNG/WebP headers and typical JPEG SOF positions (EXIF/ICC segments can be large).
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(256 * 1024);
        }
    }

    private static boolean isAllowedMime(String contentType) {
        String mime = contentType.toLowerCase().split(";")[0].trim();
        return mime.equals("image/png") || mime.equals("image/jpeg") || mime.equals("image/jpg")
                || mime.equals("image/webp");
    }
}
