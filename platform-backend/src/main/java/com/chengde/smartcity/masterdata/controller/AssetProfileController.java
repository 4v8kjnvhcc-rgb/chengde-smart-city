package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.service.AssetProfileService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资产 360：以 entry_code 为枢纽聚合元数据、血缘、质量、目录、订阅授权。
 */
@RestController
@RequestMapping("/api/v1/governance/asset")
public class AssetProfileController {

    private final AssetProfileService service;

    public AssetProfileController(AssetProfileService service) {
        this.service = service;
    }

    @GetMapping("/360")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> asset360(@RequestParam String entryCode) {
        return ApiResponse.ok(service.asset360(entryCode));
    }
}
