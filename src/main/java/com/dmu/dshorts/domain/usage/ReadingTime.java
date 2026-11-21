package com.dmu.dshorts.domain.usage;

import com.dmu.dshorts.domain.post.dto.SectionResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReadingTime {
    private final int charsPerMinute;
    public ReadingTime(@Value("${app.usage.reading-chars-per-minute:500}") int charsPerMinute) {
        if (charsPerMinute < 1) throw new IllegalArgumentException("READING_CHARS_PER_MINUTE must be positive");
        this.charsPerMinute = charsPerMinute;
    }
    public int seconds(List<SectionResponse> sections) {
        long chars = sections.stream().mapToLong(s -> s.body().codePointCount(0, s.body().length())).sum();
        return Math.toIntExact((chars * 60 + charsPerMinute - 1) / charsPerMinute);
    }
}
