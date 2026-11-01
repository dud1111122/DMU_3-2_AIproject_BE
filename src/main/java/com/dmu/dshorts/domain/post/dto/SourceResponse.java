package com.dmu.dshorts.domain.post.dto;

import com.dmu.dshorts.domain.post.entity.PostSource;
import com.dmu.dshorts.global.common.Ids;

import java.time.LocalDate;

/** Bibliography entry. {@code verified=false} means an AI-suggested, unchecked source. */
public record SourceResponse(String id, int order, String title, String author, String publisher, String url,
                             String publicationLabel, boolean verified, LocalDate checkedAt, String note) {

    public static SourceResponse from(PostSource source) {
        return new SourceResponse(Ids.str(source.getId()), source.getSourceOrder(), source.getTitle(),
                source.getAuthor(), source.getPublisher(), source.getUrl(), source.getPublicationLabel(),
                source.isVerified(), source.getCheckedAt(), source.getNote());
    }
}
