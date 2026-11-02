package com.dmu.dshorts.domain.post.dto;

import com.dmu.dshorts.domain.post.entity.PostSection;
import com.dmu.dshorts.global.common.Ids;

/** Card of a post, ordered by {@code order} (1..10). */
public record SectionResponse(String id, int order, String title, String body, String citationType,
                              String quoteAttribution, SectionImage image) {

    public static SectionResponse from(PostSection section) {
        return new SectionResponse(Ids.str(section.getId()), section.getSectionOrder(), section.getTitle(),
                section.getBody(), section.getCitationType().name(), section.getQuoteAttribution(),
                SectionImage.from(section));
    }
}
