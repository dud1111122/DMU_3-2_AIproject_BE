package com.dmu.dshorts.domain.debate.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Time source and KST day window for debates. A dedicated bean (not a global {@link Clock}) so tests can
 * replace it with {@code @MockitoBean DebateClock} to control day boundaries.
 */
@Component
public class DebateClock {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public DebateClock() {
        this(Clock.systemUTC());
    }

    DebateClock(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    /** Start (inclusive) of the KST day containing {@code instant}. */
    public static Instant dayStart(Instant instant) {
        return LocalDate.ofInstant(instant, KST).atStartOfDay(KST).toInstant();
    }

    /** End (exclusive) of the KST day containing {@code instant} = next KST midnight = {@code resetAt}. */
    public static Instant dayEnd(Instant instant) {
        return LocalDate.ofInstant(instant, KST).plusDays(1).atStartOfDay(KST).toInstant();
    }
}
