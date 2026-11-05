package com.dmu.dshorts.domain.admin.controller;

import com.dmu.dshorts.domain.admin.dto.AdminImageResponse;
import com.dmu.dshorts.domain.admin.media.MediaStorage;
import com.dmu.dshorts.global.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** {@code POST /api/v1/admin/images} (multipart part {@code file}): PNG/JPEG/WebP upload with a server-generated path. */
@RestController
@RequestMapping("/api/v1/admin/images")
public class AdminImageController {

    private final MediaStorage mediaStorage;

    public AdminImageController(MediaStorage mediaStorage) {
        this.mediaStorage = mediaStorage;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminImageResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.of(AdminImageResponse.from(mediaStorage.store(file)));
    }
}
