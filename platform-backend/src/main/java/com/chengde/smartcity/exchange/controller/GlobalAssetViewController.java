package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.service.GlobalAssetViewService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exchange/ingestion/global-asset-view")
public class GlobalAssetViewController {

    private final GlobalAssetViewService service;

    public GlobalAssetViewController(GlobalAssetViewService service) {
        this.service = service;
    }

    @GetMapping("/panorama")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> panorama() {
        return ApiResponse.ok(service.panorama());
    }

    @GetMapping("/top")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> top(@RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.topAssets(limit));
    }

    @GetMapping("/trends")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> trends(@RequestParam(required = false) Integer days) {
        return ApiResponse.ok(service.trends(days));
    }

    @GetMapping("/lineage")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> lineage(@RequestParam(required = false) Long projectId) {
        return ApiResponse.ok(service.lineageSnapshot(projectId));
    }

    @GetMapping("/facets")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> facets() {
        return ApiResponse.ok(service.facets());
    }

    @GetMapping("/assets/{assetType}/{assetId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String assetType, @PathVariable Long assetId) {
        return ApiResponse.ok(service.assetDetail(assetType, assetId));
    }
}
