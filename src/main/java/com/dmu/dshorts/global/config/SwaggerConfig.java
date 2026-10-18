package com.dmu.dshorts.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    OpenAPI deShortsOpenApi() {
        return new OpenAPI().info(new Info().title("De-Shorts API").version("v1")
            .description("공통 지식 콘텐츠, 페르소나, 활동 및 학습 API"));
    }
}
