package com.dmu.dshorts.domain.usage;

import com.dmu.dshorts.global.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReadingBookService {

    private static final RowMapper<Book> ROW = (rs, row) -> new Book(
            rs.getString("id"), rs.getString("persona_id"), rs.getString("title"), rs.getString("author"),
            rs.getInt("reading_minutes"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant());

    private final JdbcTemplate jdbc;

    public ReadingBookService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Book> list() {
        return jdbc.query("SELECT * FROM reading_books WHERE deleted_at IS NULL ORDER BY id", ROW);
    }

    @Transactional(readOnly = true)
    public Book forPersonas(List<Long> ids) {
        List<Book> all = jdbc.query("""
                SELECT b.* FROM reading_books b LEFT JOIN personas p ON p.id=b.persona_id
                WHERE b.deleted_at IS NULL
                    AND (b.persona_id IS NULL OR (p.active=true AND p.deleted_at IS NULL))
                ORDER BY b.id
                """, ROW);
        return all.stream()
                .filter(book -> book.personaId() != null && ids.contains(Long.valueOf(book.personaId())))
                .findFirst()
                .orElseGet(() -> all.stream().filter(book -> book.personaId() == null).findFirst().orElse(null));
    }

    public Book save(Long id, BookInput input) {
        if (input.title() == null || input.title().isBlank() || input.title().trim().length() > 200
                || input.author() == null || input.author().isBlank() || input.author().trim().length() > 100
                || input.readingMinutes() == null || input.readingMinutes() < 1) {
            throw ApiException.badRequest("제목·저자·양수 정독 시간을 입력해 주세요.");
        }
        // Admin metadata edits remain possible after a linked persona is hidden.
        // Such books are excluded from public tips by forPersonas().
        if (input.personaId() != null && !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM personas WHERE id=?)", Boolean.class, input.personaId()))) {
            throw ApiException.badRequest("페르소나 ID를 확인해 주세요.");
        }
        if (id == null) {
            return jdbc.queryForObject("""
                    INSERT INTO reading_books(persona_id,title,author,reading_minutes)
                    VALUES (?,?,?,?) RETURNING *
                    """, ROW, input.personaId(), input.title().trim(), input.author().trim(), input.readingMinutes());
        }
        var rows = jdbc.query("""
                UPDATE reading_books SET persona_id=?, title=?, author=?, reading_minutes=?
                WHERE id=? AND deleted_at IS NULL RETURNING *
                """, ROW, input.personaId(), input.title().trim(), input.author().trim(), input.readingMinutes(), id);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        return rows.get(0);
    }

    public Book patch(Long id, JsonNode input) {
        List<Book> rows = jdbc.query("SELECT * FROM reading_books WHERE id=? AND deleted_at IS NULL", ROW, id);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        if (!input.isObject()) {
            throw ApiException.badRequest("책 수정 항목을 확인해 주세요.");
        }
        Book current = rows.get(0);
        Long personaId = current.personaId() == null ? null : Long.valueOf(current.personaId());
        if (input.has("personaId")) {
            var value = input.get("personaId");
            if (value.isNull()) {
                personaId = null;
            } else {
                try {
                    personaId = Long.valueOf(value.asText());
                } catch (NumberFormatException error) {
                    throw ApiException.badRequest("페르소나 ID를 확인해 주세요.");
                }
            }
        }
        String title = input.has("title") ? text(input.get("title")) : current.title();
        String author = input.has("author") ? text(input.get("author")) : current.author();
        Integer minutes = current.readingMinutes();
        if (input.has("readingMinutes")) {
            var value = input.get("readingMinutes");
            if (!value.isIntegralNumber() || !value.canConvertToInt()) {
                throw ApiException.badRequest("정독 시간은 양의 정수로 입력해 주세요.");
            }
            minutes = value.intValue();
        }
        return save(id, new BookInput(personaId, title, author, minutes));
    }

    public void delete(Long id) {
        int changed = jdbc.update("UPDATE reading_books SET deleted_at=COALESCE(deleted_at,now()) WHERE id=?", id);
        if (changed == 0) {
            throw ApiException.notFound();
        }
    }

    private static String text(JsonNode value) {
        return value.isTextual() ? value.textValue() : null;
    }

    public record BookInput(Long personaId, String title, String author, Integer readingMinutes) {}
    public record Book(String id, String personaId, String title, String author, int readingMinutes,
                       Instant createdAt, Instant deletedAt) {}
}
