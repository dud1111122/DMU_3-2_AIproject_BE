package com.dmu.dshorts.domain.topic.dto;

import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.global.common.Ids;

/** Topic reference embedded in post/persona DTOs. */
public record TopicSummary(String id, String name) {

    public static TopicSummary from(Topic topic) {
        return new TopicSummary(Ids.str(topic.getId()), topic.getName());
    }
}
