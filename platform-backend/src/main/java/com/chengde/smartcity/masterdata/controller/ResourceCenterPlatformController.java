package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.RcAssetCatalogEntry;
import com.chengde.smartcity.masterdata.entity.RcBackupArtifact;
import com.chengde.smartcity.masterdata.entity.RcBaseLibrary;
import com.chengde.smartcity.masterdata.entity.RcMonitorMetric;
import com.chengde.smartcity.masterdata.entity.RcStoragePolicy;
import com.chengde.smartcity.masterdata.service.ResourceCenterPlatformService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> libraryOverview() {
        return ApiResponse.ok(service.libraryOverview());
    }

    @GetMapping("/libraries")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<RcBaseLibrary>> libraries(@RequestParam(required = false) String libType) {
        return ApiResponse.ok(service.listLibraries(libType));
    }

    @PostMapping("/libraries")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createLibrary(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createLibrary(principal, body));
    }

    @GetMapping("/themes")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> themes(@RequestParam(required = false) String libraryKind) {
        return ApiResponse.ok(service.listThemes(libraryKind));
    }

    @PostMapping("/themes")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createTheme(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createTheme(principal, body));
    }

    @GetMapping("/managed-tables")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> managedTables(@RequestParam(required = false) Long themeId) {
        return ApiResponse.ok(service.listManagedTables(themeId));
    }

    @GetMapping("/managed-tables/candidates")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> candidates() {
        return ApiResponse.ok(service.candidateProduceTables());
    }

    @PostMapping("/managed-tables")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> manageTable(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.manageTable(principal, body));
    }

    @DeleteMapping("/managed-tables/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> unmanage(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.unmanageTable(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/managed-tables/{id}/backup")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> backup(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        Integer days = null;
        if (body != null && body.get("retentionDays") != null) {
            days = Integer.valueOf(String.valueOf(body.get("retentionDays")));
        }
        return ApiResponse.ok(service.runLogicalBackup(principal, id, days));
    }

    @GetMapping("/partition/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> partitionOverview() {
        return ApiResponse.ok(service.partitionOverview());
    }

    @PostMapping("/partitions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createPartition(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createPartition(principal, body));
    }

    @PostMapping("/partitions/{id}/pretest")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> pretestPartition(@AuthenticationPrincipal UserPrincipal principal,
                                                             @PathVariable Long id) {
        return ApiResponse.ok(service.pretestPartition(principal, id));
    }

    @GetMapping("/policies")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<RcStoragePolicy>> policies(@RequestParam(required = false) String actionType) {
        return ApiResponse.ok(service.listPolicies(actionType));
    }

    @PostMapping("/policies")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createPolicy(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createPolicy(principal, body));
    }

    @PostMapping("/policies/{id}/execute")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> executePolicy(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(service.executePolicy(principal, id));
    }

    @GetMapping("/backups/artifacts")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<RcBackupArtifact>> artifacts(@RequestParam(required = false) Long managedTableId) {
        return ApiResponse.ok(service.listArtifacts(managedTableId));
    }

    @GetMapping("/backups/artifacts/{id}/verify")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> verifyArtifact(@PathVariable Long id) {
        return ApiResponse.ok(service.verifyArtifact(id));
    }

    @GetMapping("/catalog/entries")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<RcAssetCatalogEntry>> catalogEntries() {
        return ApiResponse.ok(service.listCatalogEntries());
    }

    @PostMapping("/catalog/entries")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createCatalogEntry(@AuthenticationPrincipal UserPrincipal principal,
                                                @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createCatalogEntry(principal, body));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> search(@RequestParam(required = false) String q) {
        return ApiResponse.ok(service.searchLibraries(q));
    }

    @GetMapping("/managed-tables/{id}/query")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> queryManagedTable(@PathVariable Long id,
                                                              @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.queryManagedTable(id, limit));
    }

    @GetMapping("/statistics")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> statistics() {
        return ApiResponse.ok(service.statistics());
    }

    @GetMapping("/monitor")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<RcMonitorMetric>> monitor() {
        return ApiResponse.ok(service.monitorMetrics());
    }

    @PostMapping("/monitor/refresh")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> refreshMonitor(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.refreshMonitor(principal));
    }
}
