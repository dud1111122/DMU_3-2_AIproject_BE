package com.dmu.dshorts.domain.report.controller;

import com.dmu.dshorts.domain.report.dto.ReportDtos.CreateReportRequest;
import com.dmu.dshorts.domain.report.dto.ReportDtos.ReportResponse;
import com.dmu.dshorts.domain.report.service.ReportService;
import com.dmu.dshorts.domain.report.service.ReportService.CreateResult;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Member report on a visible post: first report 201, the user's existing report 200. */
    @PostMapping("/api/v1/posts/{postId}/reports")
    public ResponseEntity<ApiResponse<ReportResponse>> create(@PathVariable Long postId,
                                                              @RequestBody CreateReportRequest request,
                                                              @CurrentUser(required = true) AuthUser user) {
        CreateResult result = reportService.create(user.id(), postId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.of(result.report()));
    }
}
