package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.RcBackupArtifact;
import com.chengde.smartcity.masterdata.entity.RcBaseLibrary;
import com.chengde.smartcity.masterdata.entity.RcMonitorMetric;
import com.chengde.smartcity.masterdata.entity.RcPartitionOp;
import com.chengde.smartcity.masterdata.entity.RcPolicyRunLog;
import com.chengde.smartcity.masterdata.entity.RcStoragePolicy;
import com.chengde.smartcity.masterdata.service.BackupDsScheduleService;
import com.chengde.smartcity.masterdata.service.ResourceCenterPlatformService;
import com.chengde.smartcity.masterdata.service.ResourceCenterStatsService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resource-center/platform")
public class ResourceCenterPlatformController {

    private final ResourceCenterPlatformService service;
    private final ResourceCenterStatsService statsService;
    private final BackupDsScheduleService backupDsScheduleService;

    public ResourceCenterPlatformController(ResourceCenterPlatformService service,
                                            ResourceCenterStatsService statsService,
                                            BackupDsScheduleService backupDsScheduleService) {
        this.service = service;
        this.statsService = statsService;
        this.backupDsScheduleService = backupDsScheduleService;
    }

    @GetMapping("/libraries/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> libraryOverview() {
        return ApiResponse.ok(service.libraryOverview());
    }

    @GetMapping("/asset/inventory")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> assetInventory() {
        return ApiResponse.ok(service.assetInventory());
    }

    @GetMapping("/asset/modules")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> assetModules() {
        return ApiResponse.ok(service.listAssetModules());
    }

    @GetMapping("/asset/file-libraries")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> fileLibraries() {
        return ApiResponse.ok(service.fileLibrariesOverview());
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

    @PutMapping("/libraries/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<RcBaseLibrary> updateLibrary(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.updateLibrary(principal, id, body));
    }

    @DeleteMapping("/libraries/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteLibrary(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long id) {
        service.deleteLibrary(principal, id);
        return ApiResponse.ok(null);
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

    @PutMapping("/themes/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> updateTheme(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable Long id,
                                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.updateTheme(principal, id, body));
    }

    @DeleteMapping("/themes/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteTheme(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable Long id) {
        service.deleteTheme(principal, id);
        return ApiResponse.ok(null);
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

    @PutMapping("/managed-tables/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> updateManagedTable(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable Long id,
                                                               @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.updateManagedTable(principal, id, body));
    }

    @DeleteMapping("/managed-tables/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> unmanage(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.unmanageTable(principal, id);
        return ApiResponse.ok(null);
    }

    /**
     * 纳管表逻辑备份（资产区库管理用）。定时策略仍走「数据库存储管理」；
     * 此处提供即时备份台账，满足基础库备份/恢复场景演示。
     */
    @PostMapping("/managed-tables/{id}/backup")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> backup(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        Integer days = 30;
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

    @PutMapping("/partitions/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updatePartition(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        service.updatePartition(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/partitions/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deletePartition(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id) {
        service.deletePartition(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/partitions/{id}/pretest")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> pretestPartition(@AuthenticationPrincipal UserPrincipal principal,
                                                             @PathVariable Long id) {
        return ApiResponse.ok(service.pretestPartition(principal, id));
    }

    @PostMapping("/partitions/{id}/migrate")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> migratePartition(@AuthenticationPrincipal UserPrincipal principal,
                                                             @PathVariable Long id,
                                                             @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.migratePartition(principal, id, body == null ? Map.of() : body));
    }

    @GetMapping("/partitions/ops")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<RcPartitionOp>> partitionOps(@RequestParam(required = false) Long partitionDefId,
                                                         @RequestParam(required = false) Long managedTableId) {
        return ApiResponse.ok(service.listPartitionOps(partitionDefId, managedTableId));
    }

    @PostMapping("/partitions/ops")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> createPartitionOp(@AuthenticationPrincipal UserPrincipal principal,
                                                              @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createPartitionOp(principal, body));
    }

    @GetMapping("/managed-tables/{id}/columns")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> managedTableColumns(@PathVariable Long id) {
        return ApiResponse.ok(service.listManagedTableColumns(id));
    }

    @GetMapping("/managed-tables/{id}/partitions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> livePartitions(@PathVariable Long id) {
        return ApiResponse.ok(service.livePartitions(id));
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

    @GetMapping("/policies/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<RcStoragePolicy> getPolicy(@PathVariable Long id) {
        return ApiResponse.ok(service.getPolicy(id));
    }

    @PutMapping("/policies/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updatePolicy(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        service.updatePolicy(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/policies/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deletePolicy(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id) {
        service.deletePolicy(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/policies/{id}/execute")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> executePolicy(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(service.executePolicy(principal, id));
    }

    @GetMapping("/lifecycle/databases")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, String>>> lifecycleDatabases() {
        return ApiResponse.ok(service.listLifecycleDatabases());
    }

    @GetMapping("/lifecycle/tables")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<String>> lifecycleTables(@RequestParam String database) {
        return ApiResponse.ok(service.listLifecycleTables(database));
    }

    @PostMapping("/policies/{id}/schedule/start")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> startSchedule(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(backupDsScheduleService.startSchedule(principal, id));
    }

    @PostMapping("/policies/{id}/schedule/stop")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> stopSchedule(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable Long id) {
        return ApiResponse.ok(backupDsScheduleService.stopSchedule(principal, id));
    }

    @PostMapping("/policies/{id}/ds-trigger")
    public ApiResponse<Map<String, Object>> dsTriggerPolicy(@PathVariable Long id,
                                                            @RequestHeader(value = "X-Ds-Callback-Token", required = false) String token,
                                                            @RequestBody(required = false) Map<String, Object> body) {
        Long dsInstanceId = null;
        if (body != null && body.get("dsInstanceId") != null) {
            dsInstanceId = Long.valueOf(String.valueOf(body.get("dsInstanceId")));
        }
        return ApiResponse.ok(backupDsScheduleService.runFromDsCallback(id, token, dsInstanceId));
    }

    @PutMapping("/policies/{id}/schedule")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> schedulePolicy(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable Long id,
                                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.updatePolicySchedule(principal, id, body));
    }

    @GetMapping("/policies/runs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<RcPolicyRunLog>> policyRuns(@RequestParam(required = false) Long policyId) {
        return ApiResponse.ok(service.listPolicyRuns(policyId));
    }

    @GetMapping("/backups/artifacts")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<RcBackupArtifact>> artifacts(@RequestParam(required = false) Long managedTableId) {
        return ApiResponse.ok(service.listArtifacts(managedTableId));
    }

    @PostMapping("/lifecycle/sync-artifacts")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> syncLifecycleArtifacts() {
        return ApiResponse.ok(service.syncLifecycleArtifacts());
    }

    @PutMapping("/backups/artifacts/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateArtifact(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        service.updateArtifact(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/backups/artifacts/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteArtifact(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        service.deleteArtifact(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/backups/artifacts/{id}/verify")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> verifyArtifact(@PathVariable Long id) {
        return ApiResponse.ok(service.verifyArtifact(id));
    }

    @PostMapping("/backups/artifacts/{id}/restore")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> restoreArtifact(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable Long id) {
        return ApiResponse.ok(service.restoreArtifact(principal, id));
    }

    @GetMapping("/catalog/subsystems")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> catalogSubsystems() {
        return ApiResponse.ok(service.listCatalogSubsystems());
    }

    @GetMapping("/catalog/entries")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> catalogEntries(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String subsystem,
            @RequestParam(required = false) String publishStatus) {
        return ApiResponse.ok(service.listCatalogEntries(q, visibility, subsystem, publishStatus));
    }

    @PostMapping("/catalog/entries")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createCatalogEntry(@AuthenticationPrincipal UserPrincipal principal,
                                                @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createCatalogEntry(principal, body));
    }

    @PutMapping("/catalog/entries/{id}/encrypt")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateCatalogEncrypt(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        service.updateCatalogEncrypt(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/catalog/entries/{id}/submit-publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> submitCatalogPublish(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long id) {
        service.submitCatalogPublish(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/catalog/entries/{id}/approve-publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> approveCatalogPublish(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long id) {
        service.approveCatalogPublish(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/catalog/entries/{id}/reject-publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> rejectCatalogPublish(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        service.rejectCatalogPublish(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/catalog/entries/{id}/unpublish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> unpublishCatalog(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable Long id) {
        service.unpublishCatalog(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/catalog/entries/{id}/drive-exchange")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> driveCatalogExchange(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable Long id) {
        return ApiResponse.ok(service.driveCatalogExchange(principal, id));
    }

    @PostMapping("/catalog/entries/drive-exchange-batch")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> driveAllPublicCatalogExchange(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.driveAllPublicCatalogExchange(principal));
    }

    @PostMapping("/catalog/entries/sync-from-published")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> syncFromPublished(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.syncFromPublishedGovCatalogs(principal));
    }

    @GetMapping("/catalog/exchange-jobs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> catalogExchangeJobs(
            @RequestParam(required = false) Long entryId) {
        return ApiResponse.ok(service.listCatalogExchangeJobs(entryId));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> search(@RequestParam(required = false) String q) {
        return ApiResponse.ok(service.searchLibraries(q));
    }

    @GetMapping("/search/fulltext")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> searchFullText(@RequestParam String q,
                                                           @RequestParam(required = false) Integer perTableLimit,
                                                           @RequestParam(required = false) Integer maxTables) {
        return ApiResponse.ok(service.searchFullText(q, perTableLimit, maxTables));
    }

    @GetMapping("/search/metadata")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> searchMetadata(@RequestParam(required = false) String q,
                                                           @RequestParam(required = false) String tag,
                                                           @RequestParam(required = false) String domain,
                                                           @RequestParam(required = false) String dataItem) {
        return ApiResponse.ok(service.searchMetadata(q, tag, domain, dataItem));
    }

    @GetMapping("/managed-tables/{id}/query")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> queryManagedTable(@PathVariable Long id,
                                                              @RequestParam(required = false) Integer limit,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) String column) {
        return ApiResponse.ok(service.queryManagedTable(id, limit, keyword, column));
    }

    @GetMapping("/statistics")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> statistics() {
        return ApiResponse.ok(service.statistics());
    }

    /** 数据库统计分析 · 汇总聚合与描述性统计 */
    @GetMapping("/statistics/summary")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> statisticsSummary(@RequestParam(required = false) String libType,
                                                              @RequestParam(required = false) String assetType) {
        return ApiResponse.ok(statsService.summary(libType, assetType));
    }

    /** 数据库统计分析 · 趋势回归、集中度与离群分析 */
    @GetMapping("/statistics/analysis")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> statisticsAnalysis(@RequestParam(required = false) Integer months) {
        return ApiResponse.ok(statsService.analysis(months));
    }

    /** 数据库统计分析 · 决策支持建议 */
    @GetMapping("/statistics/decisions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> statisticsDecisions() {
        return ApiResponse.ok(statsService.decisions());
    }

    @GetMapping("/monitor")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<RcMonitorMetric>> monitor() {
        return ApiResponse.ok(service.monitorMetrics());
    }

    @GetMapping("/monitor/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> monitorOverview() {
        return ApiResponse.ok(service.monitorOverview());
    }

    @PostMapping("/monitor/refresh")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> refreshMonitor(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.refreshMonitor(principal));
    }
}
