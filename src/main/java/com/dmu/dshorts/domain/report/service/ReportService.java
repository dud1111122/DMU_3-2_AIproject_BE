package com.dmu.dshorts.domain.report.service;

import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.report.dto.ReportDtos.AdminReportResponse;
import com.dmu.dshorts.domain.report.dto.ReportDtos.CreateReportRequest;
import com.dmu.dshorts.domain.report.dto.ReportDtos.ReportResponse;
import com.dmu.dshorts.domain.report.entity.PostReport;
import com.dmu.dshorts.domain.report.entity.ReportReason;
import com.dmu.dshorts.domain.report.entity.ReportStatus;
import com.dmu.dshorts.domain.report.repository.PostReportRepository;
import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.domain.user.repository.UserRepository;
import com.dmu.dshorts.global.common.Cursors;
import com.dmu.dshorts.global.common.Cursors.TimeIdCursor;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.visibility.VisibilityService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReportService {

    private static final int DETAIL_MAX = 500;

    private final PostReportRepository reportRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final VisibilityService visibilityService;

    public ReportService(PostReportRepository reportRepository, PostRepository postRepository,
                         UserRepository userRepository, VisibilityService visibilityService) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.visibilityService = visibilityService;
    }

    /** Result of a report submission; {@code created=false} means the user's existing report was returned. */
    public record CreateResult(ReportResponse report, boolean created) {
    }

    /** One report per (user, post): the first call creates it, later calls return the existing one unchanged. */
    public CreateResult create(Long userId, Long postId, CreateReportRequest request) {
        visibilityService.requireVisiblePost(postId);
        ReportReason reason = reason(request == null ? null : request.reasonCode());
        String detail = detail(request.detail());
        var existing = reportRepository.findByUserIdAndPostId(userId, postId);
        if (existing.isPresent()) {
            return new CreateResult(ReportResponse.from(existing.get()), false);
        }
        PostReport report = new PostReport(userId, postId, reason);
        report.setDetail(detail);
        return new CreateResult(ReportResponse.from(reportRepository.saveAndFlush(report)), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminReportResponse> list(String statusParam, String cursorParam, Integer limitParam) {
        ReportStatus status = null;
        if (statusParam != null && !statusParam.isBlank()) {
            try {
                status = ReportStatus.valueOf(statusParam.trim());
            } catch (IllegalArgumentException ex) {
                throw ApiException.validation("status", "OPEN, RESOLVED, DISMISSED 중 하나여야 합니다.");
            }
        }
        int limit = Cursors.limit(limitParam);
        TimeIdCursor cursor = Cursors.decodeTimeId(cursorParam);
        Pageable page = PageRequest.ofSize(limit + 1);
        List<PostReport> rows;
        if (status == null) {
            rows = cursor == null ? reportRepository.findLatest(page)
                    : reportRepository.findLatestAfter(cursor.time(), cursor.id(), page);
        } else {
            rows = cursor == null ? reportRepository.findLatestByStatus(status, page)
                    : reportRepository.findLatestByStatusAfter(status, cursor.time(), cursor.id(), page);
        }
        Map<Long, Post> posts = postRepository.findAllById(rows.stream().map(PostReport::getPostId).distinct().toList())
                .stream().collect(Collectors.toMap(Post::getId, Function.identity()));
        Map<Long, User> users = userRepository.findAllById(rows.stream().map(PostReport::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.slice(rows, limit, r -> Cursors.encode(r.getCreatedAt(), r.getId()),
                r -> AdminReportResponse.of(r, posts.get(r.getPostId()), users.get(r.getUserId())));
    }

    /** RESOLVED or DISMISSED, sets resolvedAt. Hiding the post is a separate visibility call. */
    public AdminReportResponse resolve(Long reportId, String statusParam) {
        ReportStatus status;
        try {
            status = statusParam == null ? null : ReportStatus.valueOf(statusParam.trim());
        } catch (IllegalArgumentException ex) {
            status = null;
        }
        if (status == null || status == ReportStatus.OPEN) {
            throw ApiException.validation("status", "RESOLVED 또는 DISMISSED여야 합니다.");
        }
        PostReport report = reportRepository.findById(reportId).orElseThrow(ApiException::notFound);
        if (report.getStatus() != status) {
            report.setStatus(status);
            report.setResolvedAt(Instant.now());
            reportRepository.flush();
        }
        return AdminReportResponse.of(report, postRepository.findById(report.getPostId()).orElse(null),
                userRepository.findById(report.getUserId()).orElse(null));
    }

    private static ReportReason reason(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                return ReportReason.valueOf(raw.trim());
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        throw ApiException.validation("reasonCode", "INACCURATE, FAKE_QUOTE, HARMFUL, INAPPROPRIATE, OTHER 중 하나여야 합니다.");
    }

    private static String detail(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.codePointCount(0, value.length()) > DETAIL_MAX) {
            throw ApiException.validation("detail", "상세 내용은 500자 이하여야 합니다.");
        }
        return value;
    }
}
