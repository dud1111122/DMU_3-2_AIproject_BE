package com.dmu.dshorts.domain.post.dto;

import com.dmu.dshorts.domain.post.entity.KeyPoint;
import com.dmu.dshorts.global.common.Ids;

/** Learning point; {@code sectionId} is the related card or null. */
public record KeyPointResponse(String id, int order, String text, String sectionId) {

    public static KeyPointResponse from(KeyPoint point) {
        return new KeyPointResponse(Ids.str(point.getId()), point.getPointOrder(), point.getText(),
                Ids.str(point.getSectionId()));
    }
}
