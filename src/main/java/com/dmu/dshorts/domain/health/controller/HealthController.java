package com.dmu.dshorts.domain.health.controller;

import com.dmu.dshorts.domain.generation.service.AiClient;
import com.dmu.dshorts.global.common.ApiResponse;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/health")
public class HealthController {
    private final DataSource dataSource;private final AiClient ai;
    public HealthController(DataSource dataSource,AiClient ai){this.dataSource=dataSource;this.ai=ai;}
    @GetMapping public ApiResponse<Map<String,String>> health() throws SQLException {
        String product;
        try(var connection=dataSource.getConnection()){product=connection.getMetaData().getDatabaseProductName().toLowerCase();}
        return ApiResponse.of(Map.of("status","ok","database",product.contains("h2")?"h2":"postgresql","ai",ai.configured()?"configured":"not-configured"));
    }
}
