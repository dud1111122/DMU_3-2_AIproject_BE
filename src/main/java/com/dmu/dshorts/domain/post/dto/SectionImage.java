package com.dmu.dshorts.domain.post.dto;

import com.dmu.dshorts.domain.post.entity.PostSection;

/** Card image; the section's {@code image} is null when the card has no image. */
public record SectionImage(String url, String altText, boolean aiGenerated, String credit,
                           Integer width, Integer height) {

    public static SectionImage from(PostSection section) {
        String url = MediaUrls.of(section.getImagePath());
        if (url == null) {
            return null;
        }
        return new SectionImage(url, section.getImageAlt(), section.isImageAiGenerated(), section.getImageCredit(),
                section.getImageWidth(), section.getImageHeight());
    }
}
