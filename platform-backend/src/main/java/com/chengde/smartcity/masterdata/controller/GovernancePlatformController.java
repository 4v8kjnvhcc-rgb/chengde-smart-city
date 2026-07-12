package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovFusionAsset;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.entity.GovQualityReport;
import com.chengde.smartcity.masterdata.entity.GovStandardItem;
import com.chengde.smartcity.masterdata.service.GovernancePlatformService;
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
@RequestMapping("/api/v1/governance/platform")
public class GovernancePlatformController {

    private final GovernancePlatformService service;

    public GovernancePlatformController(GovernancePlatformService service) {
        this.service = service;
    }

    @GetMapping("/quality/overview")
    public ApiResponse<Map<String, Object>> qualityOverview() {
        return ApiResponse.ok(service.qualityOverview());
    }

    @GetMapping("/standards")
    public ApiResponse<List<GovStandardItem>> standards(@RequestParam(required = false) String itemType) {
        return ApiResponse.ok(service.listStandards(itemType));
    }

    @PostMapping("/standards")
    public ApiResponse<Long> createStandard(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createStandard(principal, body));
    }

    @PostMapping("/quality/reports")
    public ApiResponse<GovQualityReport> generateReport(@AuthenticationPrincipal UserPrincipal principal,
                                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.generateReport(principal, body));
    }

    @GetMapping("/metadata/overview")
    public ApiResponse<Map<String, Object>> metadataOverview() {
        return ApiResponse.ok(service.metadataOverview());
    }

    @GetMapping("/metadata/entries")
    public ApiResponse<List<GovMetadataRegistry>> metadataEntries(@RequestParam(required = false) String entryType) {
        return ApiResponse.ok(service.listMetadata(entryType));
    }

    @PostMapping("/metadata/entries")
    public ApiResponse<Long> createMetadataEntry(@AuthenticationPrincipal UserPrincipal principal,
                                                 @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createMetadataEntry(principal, body));
    }

    @GetMapping("/fusion/assets")
    public ApiResponse<List<GovFusionAsset>> fusionAssets(@RequestParam(required = false) String assetType) {
        return ApiResponse.ok(service.listFusionAssets(assetType));
    }

    @PostMapping("/fusion/assets/{id}/run")
    public ApiResponse<Map<String, Object>> runFusionAsset(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(service.runFusionAsset(principal, id));
    }

    @GetMapping("/catalog/resources")
    public ApiResponse<List<GovCatalogResource>> catalogResources(@RequestParam(required = false) String resourceType) {
        return ApiResponse.ok(service.listCatalogResources(resourceType));
    }

    @PostMapping("/catalog/resources")
    public ApiResponse<Long> createCatalogResource(@AuthenticationPrincipal UserPrincipal principal,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createCatalogResource(principal, body));
    }

    @PostMapping("/catalog/resources/{id}/approve")
    public ApiResponse<Void> approveCatalogResource(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
        service.approveCatalogResource(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/catalog/resources/{id}/subscribe")
    public ApiResponse<Map<String, Object>> subscribeResource(@AuthenticationPrincipal UserPrincipal principal,
                                                             @PathVariable Long id) {
        return ApiResponse.ok(service.subscribeResource(principal, id));
    }

    @PostMapping("/catalog/resources/{id}/distribute")
    public ApiResponse<Map<String, Object>> distributeResource(@AuthenticationPrincipal UserPrincipal principal,
                                                             @PathVariable Long id) {
        return ApiResponse.ok(service.distributeResource(principal, id));
    }
}
