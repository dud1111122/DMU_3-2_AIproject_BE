package com.dmu.dshorts.domain.report.controller;

import com.dmu.dshorts.domain.report.service.ReportService;
import com.dmu.dshorts.global.common.AdminAccess;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.UserIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class ReportController {
    private final ReportService reports;private final UserIdentity identity;private final AdminAccess admin;
    public ReportController(ReportService reports,UserIdentity identity,AdminAccess admin){this.reports=reports;this.identity=identity;this.admin=admin;}
    public record ReportRequest(@NotBlank String reason){}
    @PostMapping("/posts/{postId}/reports") public ApiResponse<Map<String,Object>> report(@PathVariable String postId,@Valid @RequestBody ReportRequest body,HttpServletRequest req){return ApiResponse.of(reports.report(identity.current(req),postId,body.reason()));}
    @GetMapping("/admin/reports") public ApiResponse<List<Map<String,Object>>> list(@RequestParam(required=false) String status,HttpServletRequest req){admin.require(req);return ApiResponse.of(reports.list(status));}
    @PostMapping("/admin/posts/{postId}/hide") public ApiResponse<Map<String,Object>> hide(@PathVariable String postId,HttpServletRequest req){admin.require(req);return ApiResponse.of(reports.hide(postId));}
}
