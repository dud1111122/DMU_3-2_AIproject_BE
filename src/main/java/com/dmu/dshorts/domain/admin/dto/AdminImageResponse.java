package com.dmu.dshorts.domain.admin.dto;

import com.dmu.dshorts.domain.admin.media.MediaStorage;
import com.dmu.dshorts.domain.admin.media.MediaStorage.StoredImage;

/** Upload result. {@code path} is what admin PATCH requests reference; {@code url} = {@code "/media/" + path}. */
public record AdminImageResponse(String path, String url, String mime, Integer width, Integer height, long size) {

    public static AdminImageResponse from(StoredImage image) {
        return new AdminImageResponse(image.path(), MediaStorage.urlOf(image.path()), image.mime(),
                image.width(), image.height(), image.size());
    }
}
