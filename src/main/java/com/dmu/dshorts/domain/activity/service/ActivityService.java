package com.dmu.dshorts.domain.activity.service;

import com.dmu.dshorts.domain.activity.dto.SavedPostItem;
import com.dmu.dshorts.domain.activity.entity.SavedPost;
import com.dmu.dshorts.domain.activity.repository.FollowRepository;
import com.dmu.dshorts.domain.activity.repository.PostLikeRepository;
import com.dmu.dshorts.domain.activity.repository.SavedPostRepository;
import com.dmu.dshorts.domain.activity.repository.UserInterestRepository;
import com.dmu.dshorts.domain.persona.dto.PersonaListItem;
import com.dmu.dshorts.domain.persona.service.PersonaService;
import com.dmu.dshorts.domain.post.dto.PostCard;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.post.service.PostCardAssembler;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.global.common.Cursors;
import com.dmu.dshorts.global.common.Cursors.TimeIdCursor;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.visibility.VisibilityService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Member activity: interests, follows, likes, saves. PUT/DELETE are idempotent (insert-if-absent / delete own row).
 * PUT requires a visible target (404 otherwise); DELETE never checks visibility so hidden targets can be cleaned up.
 */
@Service
@Transactional
public class ActivityService {

    private final UserInterestRepository interestRepository;
    private final FollowRepository followRepository;
    private final PostLikeRepository likeRepository;
    private final SavedPostRepository savedPostRepository;
    private final TopicRepository topicRepository;
    private final PostRepository postRepository;
    private final VisibilityService visibilityService;
    private final PersonaService personaService;
    private final PostCardAssembler assembler;

    public ActivityService(UserInterestRepository interestRepository, FollowRepository followRepository,
                           PostLikeRepository likeRepository, SavedPostRepository savedPostRepository,
                           TopicRepository topicRepository, PostRepository postRepository,
                           VisibilityService visibilityService, PersonaService personaService,
                           PostCardAssembler assembler) {
        this.interestRepository = interestRepository;
        this.followRepository = followRepository;
        this.likeRepository = likeRepository;
        this.savedPostRepository = savedPostRepository;
        this.topicRepository = topicRepository;
        this.postRepository = postRepository;
        this.visibilityService = visibilityService;
        this.personaService = personaService;
        this.assembler = assembler;
    }

    public void addInterest(AuthUser user, Long topicId) {
        topicRepository.findByIdAndDeletedAtIsNull(topicId).orElseThrow(ApiException::notFound);
        interestRepository.insertIfAbsent(user.id(), topicId);
    }

    public void removeInterest(AuthUser user, Long topicId) {
        interestRepository.deleteOne(user.id(), topicId);
    }

    public void follow(AuthUser user, Long personaId) {
        visibilityService.getVisiblePersona(personaId);
        followRepository.insertIfAbsent(user.id(), personaId);
    }

    public void unfollow(AuthUser user, Long personaId) {
        followRepository.deleteOne(user.id(), personaId);
    }

    public void like(AuthUser user, Long postId) {
        visibilityService.requireVisiblePost(postId);
        likeRepository.insertIfAbsent(user.id(), postId);
    }

    public void unlike(AuthUser user, Long postId) {
        likeRepository.deleteOne(user.id(), postId);
    }

    public void save(AuthUser user, Long postId) {
        visibilityService.requireVisiblePost(postId);
        savedPostRepository.insertIfAbsent(user.id(), postId);
    }

    public void unsave(AuthUser user, Long postId) {
        savedPostRepository.deleteOne(user.id(), postId);
    }

    /** Visible followed personas, id asc. */
    @Transactional(readOnly = true)
    public PageResponse<PersonaListItem> follows(AuthUser user, String cursor, Integer limit) {
        return personaService.followedBy(user.id(), cursor, limit);
    }

    /** Saved posts that are still visible, newest save first, cursor {@code (createdAt, postId)}. */
    @Transactional(readOnly = true)
    public PageResponse<SavedPostItem> savedPosts(AuthUser user, String cursor, Integer limitParam) {
        int limit = Cursors.limit(limitParam);
        TimeIdCursor after = Cursors.decodeTimeId(cursor);
        PageRequest page = PageRequest.ofSize(limit + 1);
        List<SavedPost> rows = after == null
                ? savedPostRepository.findVisiblePage(user.id(), page)
                : savedPostRepository.findVisiblePageAfter(user.id(), after.time(), after.id(), page);
        boolean hasMore = rows.size() > limit;
        List<SavedPost> kept = hasMore ? rows.subList(0, limit) : rows;

        Map<Long, Post> postsById = postRepository.findVisibleWithPersonaByIdIn(
                        kept.stream().map(SavedPost::getPostId).toList()).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        List<Post> orderedPosts = new ArrayList<>();
        List<SavedPost> orderedRows = new ArrayList<>();
        for (SavedPost row : kept) {
            Post post = postsById.get(row.getPostId());
            if (post != null) {
                orderedPosts.add(post);
                orderedRows.add(row);
            }
        }
        List<PostCard> cards = assembler.toCards(orderedPosts, user);
        List<SavedPostItem> items = new ArrayList<>(cards.size());
        for (int i = 0; i < cards.size(); i++) {
            items.add(new SavedPostItem(orderedRows.get(i).getCreatedAt(), cards.get(i)));
        }
        String next = null;
        if (hasMore) {
            SavedPost last = kept.get(kept.size() - 1);
            next = Cursors.encode(last.getCreatedAt(), last.getPostId());
        }
        return PageResponse.of(items, next);
    }
}
