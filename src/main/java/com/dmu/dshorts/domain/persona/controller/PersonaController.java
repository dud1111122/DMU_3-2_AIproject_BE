package com.dmu.dshorts.domain.persona.controller;

import com.dmu.dshorts.domain.persona.dto.PersonaDetail;
import com.dmu.dshorts.domain.persona.dto.PersonaListItem;
import com.dmu.dshorts.domain.persona.dto.RecommendedPersona;
import com.dmu.dshorts.domain.persona.service.PersonaService;
import com.dmu.dshorts.domain.post.dto.PostCard;
import com.dmu.dshorts.domain.post.service.PostService;
import com.dmu.dshorts.domain.post.service.QueryParams;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/personas")
public class PersonaController {

    private final PersonaService personaService;
    private final PostService postService;

    public PersonaController(PersonaService personaService, PostService postService) {
        this.personaService = personaService;
        this.postService = postService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PersonaListItem>> list(@RequestParam(required = false) String q,
                                                           @RequestParam(required = false) String topicId,
                                                           @RequestParam(required = false) String cursor,
                                                           @RequestParam(required = false) Integer limit) {
        return ApiResponse.of(personaService.list(QueryParams.optionalText(q, "q"),
                QueryParams.optionalId(topicId, "topicId"), cursor, limit));
    }

    /** Not paginated (max 10): {@code nextCursor} is always null. */
    @GetMapping("/recommendations")
    public ApiResponse<PageResponse<RecommendedPersona>> recommendations(
            @RequestParam(required = false) String topicIds, @CurrentUser AuthUser user) {
        return ApiResponse.of(PageResponse.of(personaService.recommendations(topicIds, user), null));
    }

    @GetMapping("/{id}")
    public ApiResponse<PersonaDetail> detail(@PathVariable Long id, @CurrentUser AuthUser user) {
        return ApiResponse.of(personaService.detail(id, user));
    }

    @GetMapping("/{id}/posts")
    public ApiResponse<PageResponse<PostCard>> posts(@PathVariable Long id,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(required = false) Integer limit,
                                                     @CurrentUser AuthUser user) {
        return ApiResponse.of(postService.personaPosts(id, cursor, limit, user));
    }
}
