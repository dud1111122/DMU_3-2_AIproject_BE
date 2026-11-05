package com.dmu.dshorts.domain.admin.media;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves uploaded images publicly at {@code GET /media/**} from the media directory. Paths are UUID based and
 * never overwritten, so they can be cached. ({@code SecurityConfig} permits everything outside
 * {@code /api/v1/admin/**}, so no security change is needed.)
 */
@Configuration
public class MediaWebConfig implements WebMvcConfigurer {

    private final MediaStorage mediaStorage;

    public MediaWebConfig(MediaStorage mediaStorage) {
        this.mediaStorage = mediaStorage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = mediaStorage.root().toUri().toString();
        registry.addResourceHandler(MediaStorage.URL_PREFIX + "**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
    }
}
