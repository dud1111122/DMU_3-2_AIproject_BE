package com.dmu.dshorts.domain.post.service;

import com.dmu.dshorts.domain.post.dto.PostCard;
import com.dmu.dshorts.domain.post.dto.PostDetail;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.repository.LikePatterns;
import com.dmu.dshorts.domain.post.repository.PostQueryRepository;
import com.dmu.dshorts.global.common.Cursors;
import com.dmu.dshorts.global.common.Cursors.TimeIdCursor;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.security.SecurityUtils;
import com.dmu.dshorts.global.visibility.VisibilityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Public post reads: feed, persona posts, post search and detail. */
@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostQueryRepository postQueryRepository;
    private final PostCardAssembler assembler;
    private final VisibilityService visibilityService;

    public PostService(PostQueryRepository postQueryRepository, PostCardAssembler assembler,
                       VisibilityService visibilityService) {
        this.postQueryRepository = postQueryRepository;
        this.assembler = assembler;
        this.visibilityService = visibilityService;
    }

    /**
     * Newest-first feed. {@code following=true} requires login (401 for guests) and keeps only posts of followed
     * personas; {@code topicId} keeps only posts linked to that non-deleted topic.
     */
    public PageResponse<PostCard> feed(Long topicId, boolean following, String cursor, Integer limitParam,
                                       AuthUser viewer) {
        Long followerUserId = following ? SecurityUtils.requireUser(viewer).id() : null;
        return page(new PostQueryRepository.Filter(topicId, followerUserId, null), cursor, limitParam, viewer);
    }

    /** Newest-first visible posts of a visible persona (404 otherwise). */
    public PageResponse<PostCard> personaPosts(Long personaId, String cursor, Integer limitParam, AuthUser viewer) {
        visibilityService.getVisiblePersona(personaId);
        return page(new PostQueryRepository.Filter(null, null, personaId), cursor, limitParam, viewer);
    }

    /** Visible posts whose title or card body contains {@code q}, id desc. */
    public PageResponse<PostCard> search(String q, String cursor, Integer limitParam, AuthUser viewer) {
        int limit = Cursors.limit(limitParam);
        Long beforeId = Cursors.decodeId(cursor);
        List<Post> rows = postQueryRepository.searchVisible(LikePatterns.contains(q), beforeId, limit + 1);
        List<Post> kept = rows.size() > limit ? rows.subList(0, limit) : rows;
        List<PostCard> cards = assembler.toCards(kept, viewer);
        String next = rows.size() > limit ? Cursors.encodeId(kept.get(kept.size() - 1).getId()) : null;
        return PageResponse.of(cards, next);
    }

    /** Post detail or 404 when the post is not publicly visible. */
    public PostDetail detail(Long postId, AuthUser viewer) {
        Post post = visibilityService.getVisiblePost(postId);
        return assembler.toDetail(post, viewer);
    }

    private PageResponse<PostCard> page(PostQueryRepository.Filter filter, String cursor, Integer limitParam,
                                        AuthUser viewer) {
        int limit = Cursors.limit(limitParam);
        TimeIdCursor after = Cursors.decodeTimeId(cursor);
        List<Post> rows = postQueryRepository.findVisible(filter, after, limit + 1);
        List<Post> kept = rows.size() > limit ? rows.subList(0, limit) : rows;
        List<PostCard> cards = assembler.toCards(kept, viewer);
        String next = null;
        if (rows.size() > limit) {
            Post last = kept.get(kept.size() - 1);
            next = Cursors.encode(last.getPublishedAt(), last.getId());
        }
        return PageResponse.of(cards, next);
    }
}
