package com.dmu.dshorts.domain.report.controller;

import com.dmu.dshorts.domain.report.dto.ReportDtos.AdminReportResponse;
import com.dmu.dshorts.domain.report.dto.ReportDtos.UpdateReportRequest;
import com.dmu.dshorts.domain.report.service.ReportService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Newest first by {@code (createdAt,id)}; {@code status} optional (all statuses when omitted). */
    @GetMapping
    public ApiResponse<PageResponse<AdminReportResponse>> list(@RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String cursor,
                                                               @RequestParam(required = false) Integer limit) {
        return ApiResponse.of(reportService.list(status, cursor, limit));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AdminReportResponse> update(@PathVariable Long id, @RequestBody UpdateReportRequest request) {
        return ApiResponse.of(reportService.resolve(id, request.status()));
    }
}
