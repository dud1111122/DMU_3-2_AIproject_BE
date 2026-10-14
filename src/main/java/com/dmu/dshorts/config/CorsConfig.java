package com.dmu.dshorts.config;
import org.springframework.context.annotation.Configuration; import org.springframework.web.servlet.config.annotation.*;
@Configuration public class CorsConfig implements WebMvcConfigurer { @Override public void addCorsMappings(CorsRegistry r){ r.addMapping("/**").allowedOrigins("http://localhost:5173").allowedMethods("GET","POST","PATCH","OPTIONS").allowedHeaders("*"); } }
