package com.dmu.dshorts.domain.persona.controller;

import com.dmu.dshorts.domain.persona.dto.PersonaResponse;
import com.dmu.dshorts.domain.post.dto.PostSummaryResponse;
import com.dmu.dshorts.domain.post.service.CatalogService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.common.UserIdentity;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/personas")
public class PersonaController {
    private final CatalogService catalog; private final UserIdentity identity;
    public PersonaController(CatalogService catalog,UserIdentity identity){this.catalog=catalog;this.identity=identity;}
    @GetMapping @Operation(summary="페르소나 목록 및 검색")
    public ApiResponse<PageResponse<PersonaResponse>> list(@RequestParam(required=false) String q,@RequestParam(required=false) String topicId,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int limit,HttpServletRequest request){return ApiResponse.of(catalog.personas(q,topicId,cursor,limit,identity.current(request)));}
    @GetMapping("/{id}") @Operation(summary="페르소나 프로필")
    public ApiResponse<PersonaResponse> get(@PathVariable String id,HttpServletRequest request){return ApiResponse.of(catalog.persona(id,identity.current(request)));}
    @GetMapping("/{id}/posts") @Operation(summary="페르소나 게시물 목록")
    public ApiResponse<PageResponse<PostSummaryResponse>> posts(@PathVariable String id,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int limit,HttpServletRequest request){
        return ApiResponse.of(catalog.personaPostPage(id,cursor,limit,identity.current(request)));
    }
}
