package com.dmu.dshorts.domain.report.dto;

import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.report.entity.PostReport;
import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.global.common.Ids;
import java.time.Instant;

/** DTOs of {@code POST /posts/{id}/reports} and {@code /admin/reports}. */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** {@code {reasonCode: INACCURATE|FAKE_QUOTE|HARMFUL|INAPPROPRIATE|OTHER, detail?: ≤500}}. */
    public record CreateReportRequest(String reasonCode, String detail) {
    }

    /** {@code {status: RESOLVED|DISMISSED}}. */
    public record UpdateReportRequest(String status) {
    }

    /** The reporter's own view of a report. */
    public record ReportResponse(String id, String postId, String reasonCode, String detail, String status,
                                 Instant createdAt, Instant resolvedAt) {

        public static ReportResponse from(PostReport r) {
            return new ReportResponse(Ids.str(r.getId()), Ids.str(r.getPostId()), r.getReasonCode().name(),
                    r.getDetail(), r.getStatus().name(), r.getCreatedAt(), r.getResolvedAt());
        }
    }

    public record ReportedPost(String id, String title, String status, boolean deleted) {
    }

    public record Reporter(String id, String username) {
    }

    /** Admin list/update view with the reported post's current state and the reporter. */
    public record AdminReportResponse(String id, ReportedPost post, Reporter reporter, String reasonCode,
                                      String detail, String status, Instant createdAt, Instant resolvedAt) {

        public static AdminReportResponse of(PostReport r, Post post, User user) {
            ReportedPost postRef = post == null ? new ReportedPost(Ids.str(r.getPostId()), null, null, false)
                    : new ReportedPost(Ids.str(post.getId()), post.getTitle(), post.getStatus().name(),
                    post.isDeleted());
            Reporter reporter = new Reporter(Ids.str(r.getUserId()), user == null ? null : user.getUsername());
            return new AdminReportResponse(Ids.str(r.getId()), postRef, reporter, r.getReasonCode().name(),
                    r.getDetail(), r.getStatus().name(), r.getCreatedAt(), r.getResolvedAt());
        }
    }
}
