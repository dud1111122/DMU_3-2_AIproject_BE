package com.dmu.dshorts.domain.usage;

import com.dmu.dshorts.domain.debate.service.DebateClock;
import com.dmu.dshorts.domain.post.dto.SectionResponse;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.entity.PostSection;
import com.dmu.dshorts.domain.post.repository.PostSectionRepository;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.visibility.VisibilityService;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists only daily aggregates. Submitted post IDs are never stored. */
@Service
@Transactional
public class UsageService {

    private final JdbcTemplate jdbc;
    private final DebateClock clock;
    private final VisibilityService visibility;
    private final PostSectionRepository sections;
    private final ReadingTime readingTime;
    private final ReadingBookService books;

    public UsageService(JdbcTemplate jdbc, DebateClock clock, VisibilityService visibility,
                        PostSectionRepository sections, ReadingTime readingTime, ReadingBookService books) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.visibility = visibility;
        this.sections = sections;
        this.readingTime = readingTime;
        this.books = books;
    }

    @Transactional(readOnly = true)
    public Stats today(Long userId) {
        return stats(userId, date());
    }

    public Stats pledge(Long userId, int count) {
        LocalDate day = date();
        lock(userId, day);
        int changed = jdbc.update("""
                UPDATE daily_usage SET pledged_count=?, pledged_at=?
                WHERE user_id=? AND usage_date=? AND pledged_count IS NULL
                """, count, Timestamp.from(clock.now()), userId, Date.valueOf(day));
        if (changed == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PLEDGE_ALREADY_SET", "오늘의 약속은 이미 정했어요.");
        }
        return stats(userId, day);
    }

    public BundleResult bundle(AuthUser viewer, List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 10 || ids.contains(null)
                || new HashSet<>(ids).size() != ids.size()) {
            throw ApiException.badRequest("게시물 ID를 중복 없이 1~10개 보내 주세요.");
        }
        if (visibility.visiblePostIds(ids).size() != ids.size()) {
            throw ApiException.badRequest("공개 게시물만 집계할 수 있어요.");
        }
        List<Post> posts = ids.stream().map(visibility::getVisiblePost).toList();
        var byPost = sections.findByPostIdInOrderByPostIdAscSectionOrderAsc(ids).stream()
                .collect(Collectors.groupingBy(PostSection::getPostId,
                        Collectors.mapping(SectionResponse::from, Collectors.toList())));
        // Match the individual rounded estimates exposed by feed/detail DTOs.
        int seconds = byPost.values().stream().mapToInt(readingTime::seconds).sum();
        Bundle bundle = new Bundle(ids.size(), seconds);
        var book = books.forPersonas(posts.stream().map(post -> post.getPersona().getId()).toList());
        if (viewer == null) {
            return new BundleResult(bundle, null, null, book);
        }

        LocalDate day = date();
        lock(viewer.id(), day);
        boolean first = !jdbc.queryForObject("""
                SELECT tip_shown FROM daily_usage WHERE user_id=? AND usage_date=?
                """, Boolean.class, viewer.id(), Date.valueOf(day));
        jdbc.update("""
                UPDATE daily_usage SET viewed_count=viewed_count+?,
                    estimated_seconds=estimated_seconds+?, tip_shown=true
                WHERE user_id=? AND usage_date=?
                """, ids.size(), seconds, viewer.id(), Date.valueOf(day));
        Stats stats = stats(viewer.id(), day);
        boolean exceeded = stats.pledgedCount() != null && stats.today().count() > stats.pledgedCount();
        return new BundleResult(bundle, stats, new Pledge(stats.pledgedCount(), exceeded), first ? book : null);
    }

    public Stats stop(Long userId) {
        LocalDate day = date();
        lock(userId, day);
        jdbc.update("""
                UPDATE daily_usage SET stop_count=stop_count+1 WHERE user_id=? AND usage_date=?
                """, userId, Date.valueOf(day));
        return stats(userId, day);
    }

    private LocalDate date() {
        return LocalDate.ofInstant(clock.now(), DebateClock.KST);
    }

    /** The day row serializes increments, pledge creation and first-tip claiming, including first insert. */
    private void lock(Long userId, LocalDate day) {
        jdbc.update("""
                INSERT INTO daily_usage(user_id, usage_date) VALUES (?,?) ON CONFLICT DO NOTHING
                """, userId, Date.valueOf(day));
        jdbc.queryForList("""
                SELECT user_id FROM daily_usage WHERE user_id=? AND usage_date=? FOR UPDATE
                """, userId, Date.valueOf(day));
    }

    private Stats stats(Long userId, LocalDate day) {
        var rows = jdbc.queryForList("""
                SELECT pledged_count FROM daily_usage WHERE user_id=? AND usage_date=?
                """, userId, Date.valueOf(day));
        Integer pledge = rows.isEmpty() ? null : (Integer) rows.get(0).get("pledged_count");
        LocalDate week = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long stops = jdbc.queryForObject("""
                SELECT COALESCE(SUM(stop_count),0) FROM daily_usage
                WHERE user_id=? AND usage_date BETWEEN ? AND ?
                """, Long.class, userId, Date.valueOf(week), Date.valueOf(day));
        return new Stats(day.toString(), pledge, pledge == null, period(userId, day, day),
                period(userId, week, day), period(userId, day.withDayOfMonth(1), day), stops);
    }

    private Period period(Long userId, LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(viewed_count),0), COALESCE(SUM(estimated_seconds),0)
                FROM daily_usage WHERE user_id=? AND usage_date BETWEEN ? AND ?
                """, (rs, row) -> new Period(rs.getLong(1), wholeMinutes(rs.getLong(2))),
                userId, Date.valueOf(from), Date.valueOf(to));
    }

    /** Estimated minutes shown to the user: the summed seconds rounded to the nearest minute; any time above zero is at least 1. */
    static long wholeMinutes(long seconds) {
        return seconds <= 0 ? 0 : Math.max(1, Math.round(seconds / 60.0));
    }

    public record Period(long count, long minutes) {}
    public record Stats(String date, Integer pledgedCount, boolean needsPledge, Period today,
                        Period week, Period month, long stopsThisWeek) {}
    public record Bundle(int count, int estimatedSeconds) {}
    public record Pledge(Integer count, boolean exceeded) {}
    public record BundleResult(Bundle bundle, Stats stats, Pledge pledge, ReadingBookService.Book tip) {}
}
