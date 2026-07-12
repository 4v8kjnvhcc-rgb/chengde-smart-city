package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.RcAssetCatalogEntry;
import com.chengde.smartcity.masterdata.entity.RcBaseLibrary;
import com.chengde.smartcity.masterdata.entity.RcMonitorMetric;
import com.chengde.smartcity.masterdata.service.ResourceCenterPlatformService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resource-center/platform")
public class ResourceCenterPlatformController {

    private final ResourceCenterPlatformService service;

    public ResourceCenterPlatformController(ResourceCenterPlatformService service) {
        this.service = service;
    }

    @GetMapping("/libraries/overview")
    public ApiResponse<Map<String, Object>> libraryOverview() {
        return ApiResponse.ok(service.libraryOverview());
    }

    @GetMapping("/libraries")
    public ApiResponse<List<RcBaseLibrary>> libraries(@RequestParam(required = false) String libType) {
        return ApiResponse.ok(service.listLibraries(libType));
    }

    @PostMapping("/libraries")
    public ApiResponse<Long> createLibrary(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createLibrary(principal, body));
    }

    @GetMapping("/partition/overview")
    public ApiResponse<Map<String, Object>> partitionOverview() {
        return ApiResponse.ok(service.partitionOverview());
    }

    @PostMapping("/policies/{id}/execute")
    public ApiResponse<Map<String, Object>> executePolicy(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(service.executePolicy(principal, id));
    }

    @GetMapping("/catalog/entries")
    public ApiResponse<List<RcAssetCatalogEntry>> catalogEntries() {
        return ApiResponse.ok(service.listCatalogEntries());
    }

    @PostMapping("/catalog/entries")
    public ApiResponse<Long> createCatalogEntry(@AuthenticationPrincipal UserPrincipal principal,
                                                @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createCatalogEntry(principal, body));
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(@RequestParam(required = false) String q) {
        return ApiResponse.ok(service.searchLibraries(q));
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> statistics() {
        return ApiResponse.ok(service.statistics());
    }

    @GetMapping("/monitor")
    public ApiResponse<List<RcMonitorMetric>> monitor() {
        return ApiResponse.ok(service.monitorMetrics());
    }
}
