package com.dmu.dshorts.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dmu.dshorts.global.common.Cursors;
import com.dmu.dshorts.global.common.Ids;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.error.ApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CommonHelpersTest {

    @Test
    void timeIdCursorRoundTrips() {
        Instant time = Instant.parse("2026-11-17T01:02:03.123456Z");
        String cursor = Cursors.encode(time, 31L);

        Cursors.TimeIdCursor decoded = Cursors.decodeTimeId(cursor);

        assertThat(decoded.time()).isEqualTo(time);
        assertThat(decoded.id()).isEqualTo(31L);
        assertThat(Cursors.decodeTimeId(null)).isNull();
        assertThat(Cursors.decodeId(Cursors.encodeId(7L))).isEqualTo(7L);
    }

    @Test
    void malformedCursorIsBadRequest() {
        assertThatThrownBy(() -> Cursors.decodeTimeId("not-a-cursor"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> Cursors.decodeId(Cursors.encode(Instant.now(), 1L)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void limitDefaultsAndClamps() {
        assertThat(Cursors.limit(null)).isEqualTo(20);
        assertThat(Cursors.limit(5)).isEqualTo(5);
        assertThat(Cursors.limit(500)).isEqualTo(50);
        assertThatThrownBy(() -> Cursors.limit(0)).isInstanceOf(ApiException.class);
    }

    @Test
    void idsParseAndFormat() {
        assertThat(Ids.str(12L)).isEqualTo("12");
        assertThat(Ids.str(null)).isNull();
        assertThat(Ids.parseBodyId("12", "postId")).isEqualTo(12L);
        assertThat(Ids.parseCsvIds("1, 2,,3", "topicIds")).containsExactly(1L, 2L, 3L);
        assertThatThrownBy(() -> Ids.parsePathId("x"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> Ids.parseBodyId("-1", "postId"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getFields().get(0).field()).isEqualTo("postId");
                });
    }

    @Test
    void pageSliceBuildsNextCursorFromLastKeptRow() {
        PageResponse<String> page = PageResponse.slice(List.of(3L, 2L, 1L), 2,
                id -> Cursors.encodeId(id), String::valueOf);

        assertThat(page.items()).containsExactly("3", "2");
        assertThat(Cursors.decodeId(page.nextCursor())).isEqualTo(2L);

        PageResponse<String> last = PageResponse.slice(List.of(1L), 2, id -> Cursors.encodeId(id), String::valueOf);
        assertThat(last.nextCursor()).isNull();
    }
}
