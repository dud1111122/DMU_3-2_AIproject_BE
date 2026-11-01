package com.dmu.dshorts.domain.topic.service;

import com.dmu.dshorts.domain.post.repository.LikePatterns;
import com.dmu.dshorts.domain.topic.dto.TopicResponse;
import com.dmu.dshorts.domain.topic.dto.TopicSummary;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class TopicService {

    private final TopicRepository topicRepository;

    public TopicService(TopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }

    /** All non-deleted topics, sortOrder, id. */
    public List<TopicResponse> listPublic() {
        return topicRepository.findByDeletedAtIsNullOrderBySortOrderAscIdAsc().stream()
                .map(TopicResponse::from)
                .toList();
    }

    /** Non-deleted topics whose name contains {@code q}, id desc, {@code maxResults} rows after {@code beforeId}. */
    public List<Topic> search(String q, Long beforeId, int maxResults) {
        String pattern = LikePatterns.contains(q);
        PageRequest page = PageRequest.ofSize(maxResults);
        return beforeId == null
                ? topicRepository.searchPublic(pattern, page)
                : topicRepository.searchPublicBefore(pattern, beforeId, page);
    }

    /** Public (non-deleted) topics per post ID in one query; posts without topics are absent from the map. */
    public Map<Long, List<TopicSummary>> publicTopicsOfPosts(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return group(topicRepository.findPublicTopicsOfPosts(postIds));
    }

    /** Public (non-deleted) topics per persona ID in one query. */
    public Map<Long, List<TopicSummary>> publicTopicsOfPersonas(Collection<Long> personaIds) {
        if (personaIds.isEmpty()) {
            return Map.of();
        }
        return group(topicRepository.findPublicTopicsOfPersonas(personaIds));
    }

    private static Map<Long, List<TopicSummary>> group(List<Object[]> rows) {
        Map<Long, List<TopicSummary>> result = new HashMap<>();
        for (Object[] row : rows) {
            Long ownerId = (Long) row[0];
            Topic topic = (Topic) row[1];
            result.computeIfAbsent(ownerId, key -> new ArrayList<>()).add(TopicSummary.from(topic));
        }
        return result;
    }
}
