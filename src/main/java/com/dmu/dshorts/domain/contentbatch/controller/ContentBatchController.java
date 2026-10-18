package com.dmu.dshorts.domain.contentbatch.controller;

import com.dmu.dshorts.domain.contentbatch.dto.ContentBatchRequest;
import com.dmu.dshorts.domain.contentbatch.service.ContentBatchService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.UserIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/content-batches")
public class ContentBatchController {
    private final ContentBatchService batches;private final UserIdentity identity;
    public ContentBatchController(ContentBatchService batches,UserIdentity identity){this.batches=batches;this.identity=identity;}
    @PostMapping public ApiResponse<Map<String,Object>> create(@Valid @RequestBody ContentBatchRequest body,HttpServletRequest req){return ApiResponse.of(batches.create(identity.current(req),body));}
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> get(@PathVariable String id,HttpServletRequest req){return ApiResponse.of(batches.get(id,identity.current(req)));}
}
