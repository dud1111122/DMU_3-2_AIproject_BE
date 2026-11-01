package com.dmu.dshorts.domain.topic.dto;

import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.global.common.Ids;

/** Item of {@code GET /topics} and of topic search. */
public record TopicResponse(String id, String name, String description, int sortOrder) {

    public static TopicResponse from(Topic topic) {
        return new TopicResponse(Ids.str(topic.getId()), topic.getName(), topic.getDescription(), topic.getSortOrder());
    }
}
