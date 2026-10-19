package com.dmu.dshorts.global.config;

import com.dmu.dshorts.global.security.CurrentUser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    static {
        // @CurrentUser parameters come from the session, not from the request.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    @Bean
    OpenAPI deShortsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("De-Shorts API")
                .version("v1")
                .description("세션 쿠키 인증 + CSRF(X-XSRF-TOKEN). 공통 지식 콘텐츠, 페르소나, 활동 및 학습 API"));
    }
}
