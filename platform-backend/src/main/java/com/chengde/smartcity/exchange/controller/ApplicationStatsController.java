package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.IngStatsMetric;
import com.chengde.smartcity.exchange.service.IngestionPlatformService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exchange/application")
public class ApplicationStatsController {

    private final IngestionPlatformService ingestionPlatformService;

    public ApplicationStatsController(IngestionPlatformService ingestionPlatformService) {
        this.ingestionPlatformService = ingestionPlatformService;
    }

    @GetMapping("/stats/base")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngStatsMetric>> baseStats() {
        return ApiResponse.ok(ingestionPlatformService.baseStats());
    }

    @GetMapping("/stats/domain")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngStatsMetric>> domainStats() {
        return ApiResponse.ok(ingestionPlatformService.domainStats());
    }
}
