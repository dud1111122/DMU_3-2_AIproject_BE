package com.dmu.dshorts.domain.admin.service;

import com.dmu.dshorts.domain.admin.dto.AdminTopicDtos.AdminTopicResponse;
import com.dmu.dshorts.domain.admin.dto.AdminTopicDtos.CreateTopicRequest;
import com.dmu.dshorts.domain.admin.dto.AdminTopicDtos.UpdateTopicRequest;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.global.error.ApiException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminTopicService {

    private final TopicRepository topicRepository;

    public AdminTopicService(TopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }

    /** All topics including deleted ones, ordered by sortOrder, id. */
    @Transactional(readOnly = true)
    public List<AdminTopicResponse> list() {
        return topicRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Topic::getSortOrder).thenComparing(Topic::getId))
                .map(AdminTopicResponse::from)
                .toList();
    }

    public AdminTopicResponse create(CreateTopicRequest request) {
        String name = AdminText.requireText(request.name(), "name", 50);
        ensureNameFree(name, null);
        Topic topic = new Topic(name);
        topic.setDescription(AdminText.optionalText(request.description(), "description", 300, ""));
        topic.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return AdminTopicResponse.from(topicRepository.saveAndFlush(topic));
    }

    public AdminTopicResponse update(Long id, UpdateTopicRequest request) {
        Topic topic = topicRepository.findById(id).orElseThrow(ApiException::notFound);
        if (topic.isDeleted()) {
            throw ApiException.conflict("삭제된 주제는 수정할 수 없습니다.");
        }
        if (request.name() != null) {
            String name = AdminText.requireText(request.name(), "name", 50);
            ensureNameFree(name, topic.getId());
            topic.setName(name);
        }
        if (request.description() != null) {
            topic.setDescription(AdminText.optionalText(request.description(), "description", 300, ""));
        }
        if (request.sortOrder() != null) {
            topic.setSortOrder(request.sortOrder());
        }
        return AdminTopicResponse.from(topicRepository.saveAndFlush(topic));
    }

    /** Logical delete; persona/post links stay. Idempotent. */
    public void delete(Long id) {
        Topic topic = topicRepository.findById(id).orElseThrow(ApiException::notFound);
        if (!topic.isDeleted()) {
            topic.setDeletedAt(Instant.now());
        }
    }

    private void ensureNameFree(String name, Long selfId) {
        topicRepository.findByName(name)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw ApiException.conflict("이미 사용 중인 주제 이름입니다.");
                });
    }
}
