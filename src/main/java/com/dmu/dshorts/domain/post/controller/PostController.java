package com.dmu.dshorts.domain.post.controller;

import com.dmu.dshorts.domain.persona.service.PersonaService;
import com.dmu.dshorts.domain.post.dto.PostCard;
import com.dmu.dshorts.domain.post.dto.PostDetail;
import com.dmu.dshorts.domain.post.service.PostService;
import com.dmu.dshorts.domain.post.service.QueryParams;
import com.dmu.dshorts.domain.topic.dto.TopicResponse;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.service.TopicService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.Cursors;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code GET /feed}, {@code GET /search}, {@code GET /posts/{id}}. */
@RestController
@RequestMapping("/api/v1")
public class PostController {

    private final PostService postService;
    private final PersonaService personaService;
    private final TopicService topicService;

    public PostController(PostService postService, PersonaService personaService, TopicService topicService) {
        this.postService = postService;
        this.personaService = personaService;
        this.topicService = topicService;
    }

    @GetMapping("/feed")
    public ApiResponse<PageResponse<PostCard>> feed(@RequestParam(required = false) String topicId,
                                                    @RequestParam(defaultValue = "false") boolean following,
                                                    @RequestParam(required = false) String cursor,
                                                    @RequestParam(required = false) Integer limit,
                                                    @CurrentUser AuthUser user) {
        return ApiResponse.of(postService.feed(QueryParams.optionalId(topicId, "topicId"), following, cursor,
                limit, user));
    }

    /**
     * Simple partial-match search, id desc. {@code type=post} (default) → {@link PostCard} items,
     * {@code persona} → {@code PersonaListItem}, {@code topic} → {@link TopicResponse}.
     */
    @GetMapping("/search")
    public ApiResponse<PageResponse<?>> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer limit,
                                               @CurrentUser AuthUser user) {
        String text = QueryParams.requiredText(q, "q");
        String kind = type == null || type.isBlank() ? "post" : type.strip();
        PageResponse<?> page = switch (kind) {
            case "post" -> postService.search(text, cursor, limit, user);
            case "persona" -> personaService.search(text, cursor, limit);
            case "topic" -> searchTopics(text, cursor, limit);
            default -> throw ApiException.validation("type", "type은 post, persona, topic 중 하나여야 합니다.");
        };
        return ApiResponse.of(page);
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<PostDetail> detail(@PathVariable Long id, @CurrentUser AuthUser user) {
        return ApiResponse.of(postService.detail(id, user));
    }

    private PageResponse<TopicResponse> searchTopics(String text, String cursor, Integer limitParam) {
        int limit = Cursors.limit(limitParam);
        List<Topic> rows = topicService.search(text, Cursors.decodeId(cursor), limit + 1);
        return PageResponse.slice(rows, limit, topic -> Cursors.encodeId(topic.getId()), TopicResponse::from);
    }
}
