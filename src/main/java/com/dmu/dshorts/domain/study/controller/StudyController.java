package com.dmu.dshorts.domain.study.controller;

import com.dmu.dshorts.domain.study.dto.StudyMessageRequest;
import com.dmu.dshorts.domain.study.service.StudyService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.UserIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class StudyController {
    private final StudyService study;private final UserIdentity identity;
    public StudyController(StudyService study,UserIdentity identity){this.study=study;this.identity=identity;}
    @PostMapping("/posts/{postId}/study-conversations") public ApiResponse<Map<String,Object>> create(@PathVariable String postId,HttpServletRequest req){return ApiResponse.of(study.create(identity.current(req),postId));}
    @GetMapping("/study-conversations/{id}") public ApiResponse<Map<String,Object>> get(@PathVariable String id,HttpServletRequest req){return ApiResponse.of(study.get(id,identity.current(req)));}
    @PostMapping("/study-conversations/{id}/messages") public ApiResponse<Map<String,Object>> send(@PathVariable String id,@Valid @RequestBody StudyMessageRequest body,HttpServletRequest req){return ApiResponse.of(study.send(id,identity.current(req),body));}
    @GetMapping("/study-conversations/{id}/assessment") public ApiResponse<Map<String,Object>> assess(@PathVariable String id,HttpServletRequest req){return ApiResponse.of(study.assess(id,identity.current(req)));}
}
