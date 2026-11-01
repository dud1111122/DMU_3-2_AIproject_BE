package com.dmu.dshorts.domain.post.service;

import com.dmu.dshorts.domain.activity.repository.FollowRepository;
import com.dmu.dshorts.domain.activity.repository.PostLikeRepository;
import com.dmu.dshorts.domain.activity.repository.SavedPostRepository;
import com.dmu.dshorts.domain.persona.dto.PersonaSummary;
import com.dmu.dshorts.domain.post.dto.KeyPointResponse;
import com.dmu.dshorts.domain.post.dto.PostCard;
import com.dmu.dshorts.domain.post.dto.PostDetail;
import com.dmu.dshorts.domain.post.dto.SectionResponse;
import com.dmu.dshorts.domain.post.dto.SourceResponse;
import com.dmu.dshorts.domain.post.dto.UserState;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.entity.PostSection;
import com.dmu.dshorts.domain.post.repository.KeyPointRepository;
import com.dmu.dshorts.domain.post.repository.PostSectionRepository;
import com.dmu.dshorts.domain.post.repository.PostSourceRepository;
import com.dmu.dshorts.domain.topic.dto.TopicSummary;
import com.dmu.dshorts.domain.topic.service.TopicService;
import com.dmu.dshorts.global.common.Ids;
import com.dmu.dshorts.global.security.AuthUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns visible posts (persona already fetched) into {@link PostCard}/{@link PostDetail} with a fixed number of
 * queries per page: sections, topics, like counts and the viewer's liked/saved/followed sets are batch-loaded.
 */
@Component
@Transactional(readOnly = true)
public class PostCardAssembler {

    private final TopicService topicService;
    private final PostSectionRepository sectionRepository;
    private final KeyPointRepository keyPointRepository;
    private final PostSourceRepository sourceRepository;
    private final PostLikeRepository likeRepository;
    private final SavedPostRepository savedPostRepository;
    private final FollowRepository followRepository;

    public PostCardAssembler(TopicService topicService, PostSectionRepository sectionRepository,
                             KeyPointRepository keyPointRepository, PostSourceRepository sourceRepository,
                             PostLikeRepository likeRepository, SavedPostRepository savedPostRepository,
                             FollowRepository followRepository) {
        this.topicService = topicService;
        this.sectionRepository = sectionRepository;
        this.keyPointRepository = keyPointRepository;
        this.sourceRepository = sourceRepository;
        this.likeRepository = likeRepository;
        this.savedPostRepository = savedPostRepository;
        this.followRepository = followRepository;
    }

    /** Cards in the order of {@code posts}. {@code viewer} is null for guests. */
    public List<PostCard> toCards(List<Post> posts, AuthUser viewer) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Context context = load(posts, viewer);
        return posts.stream().map(post -> toCard(post, context)).toList();
    }

    public PostCard toCard(Post post, AuthUser viewer) {
        return toCards(List.of(post), viewer).get(0);
    }

    public PostDetail toDetail(Post post, AuthUser viewer) {
        Context context = load(List.of(post), viewer);
        PostCard card = toCard(post, context);
        List<KeyPointResponse> keyPoints = keyPointRepository.findByPostIdOrderByPointOrderAsc(post.getId()).stream()
                .map(KeyPointResponse::from)
                .toList();
        List<SourceResponse> sources = sourceRepository.findByPostIdOrderBySourceOrderAsc(post.getId()).stream()
                .map(SourceResponse::from)
                .toList();
        return new PostDetail(card.id(), card.persona(), card.title(), card.summary(), card.caption(),
                card.groundingMode(), card.publishedAt(), card.topics(), card.sections(), keyPoints, sources,
                card.likeCount(), card.userState());
    }

    private PostCard toCard(Post post, Context context) {
        Long postId = post.getId();
        Long personaId = post.getPersona().getId();
        UserState userState = context.viewer == null
                ? UserState.GUEST
                : new UserState(true, context.liked.contains(postId), context.saved.contains(postId),
                context.followed.contains(personaId));
        return new PostCard(
                Ids.str(postId),
                PersonaSummary.from(post.getPersona()),
                post.getTitle(),
                post.getSummary(),
                post.getCaption(),
                post.getGroundingMode().name(),
                post.getPublishedAt(),
                context.topics.getOrDefault(postId, List.of()),
                context.sections.getOrDefault(postId, List.of()),
                context.likeCounts.getOrDefault(postId, 0L),
                userState);
    }

    private Context load(List<Post> posts, AuthUser viewer) {
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Set<Long> personaIds = new HashSet<>();
        posts.forEach(post -> personaIds.add(post.getPersona().getId()));

        Map<Long, List<SectionResponse>> sections = new HashMap<>();
        for (PostSection section : sectionRepository.findByPostIdInOrderByPostIdAscSectionOrderAsc(postIds)) {
            sections.computeIfAbsent(section.getPostId(), key -> new ArrayList<>()).add(SectionResponse.from(section));
        }
        Map<Long, List<TopicSummary>> topics = topicService.publicTopicsOfPosts(postIds);
        Map<Long, Long> likeCounts = new HashMap<>();
        for (Object[] row : likeRepository.countByPostIds(postIds)) {
            likeCounts.put((Long) row[0], ((Number) row[1]).longValue());
        }

        Set<Long> liked = Set.of();
        Set<Long> saved = Set.of();
        Set<Long> followed = Set.of();
        if (viewer != null) {
            liked = new HashSet<>(likeRepository.findLikedPostIds(viewer.id(), postIds));
            saved = new HashSet<>(savedPostRepository.findSavedPostIds(viewer.id(), postIds));
            followed = new HashSet<>(followRepository.findFollowedPersonaIds(viewer.id(), personaIds));
        }
        return new Context(viewer, sections, topics, likeCounts, liked, saved, followed);
    }

    private record Context(AuthUser viewer,
                           Map<Long, List<SectionResponse>> sections,
                           Map<Long, List<TopicSummary>> topics,
                           Map<Long, Long> likeCounts,
                           Set<Long> liked,
                           Set<Long> saved,
                           Set<Long> followed) {
    }
}
