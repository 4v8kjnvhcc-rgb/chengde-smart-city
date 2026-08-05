package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovMetaChangeNotice;
import com.chengde.smartcity.masterdata.entity.GovMetaCollectTask;
import com.chengde.smartcity.masterdata.entity.GovMetaModel;
import com.chengde.smartcity.masterdata.entity.GovMetaSubscription;
import com.chengde.smartcity.masterdata.entity.GovMetaVersion;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.service.MetadataSubsystemService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/governance/platform/metadata")
public class MetadataSubsystemController {

    private final MetadataSubsystemService service;

    public MetadataSubsystemController(MetadataSubsystemService service) {
        this.service = service;
    }

    @GetMapping("/models")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovMetaModel>> models(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listModels(status));
    }

    @PostMapping("/models")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createModel(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createModel(principal, body));
    }

    @PutMapping("/models/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateModel(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        service.updateModel(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/models/{id}/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> publishModel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.publishModel(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/models/{id}/offline")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> offlineModel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.offlineModel(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/models/compare")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> compareModels(@RequestParam Long leftId, @RequestParam Long rightId) {
        return ApiResponse.ok(service.compareModels(leftId, rightId));
    }

    @GetMapping("/models/export")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> exportModels() {
        return ApiResponse.ok(service.exportModels());
    }

    @PostMapping("/models/import")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Integer> importModels(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.importModels(principal, body));
    }

    @GetMapping("/models/{id}/bindings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> modelBindings(@PathVariable Long id) {
        return ApiResponse.ok(service.listModelBindings(id));
    }

    @PostMapping("/models/from-entry")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createModelFromEntry(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createModelFromEntry(principal, body));
    }

    @PostMapping("/models/{id}/recheck")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> recheckModel(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable Long id) {
        return ApiResponse.ok(service.recheckModelConformity(principal, id));
    }

    @GetMapping("/collect/data-sources")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> collectDataSources() {
        return ApiResponse.ok(service.listCollectDataSources());
    }

    @GetMapping("/collect/data-sources/{id}/tables")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> collectDataSourceTables(@PathVariable Long id) {
        return ApiResponse.ok(service.listCollectDataSourceTables(id));
    }
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovMetaCollectTask>> collectTasks(@RequestParam(required = false) String status,
                                                              @RequestParam(required = false) String sourceType,
                                                              @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listTasks(status, sourceType, keyword));
    }

    @PostMapping("/collect/tasks")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createCollectTask(@AuthenticationPrincipal UserPrincipal principal,
                                               @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createTask(principal, body));
    }

    @PutMapping("/collect/tasks/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateCollectTask(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long id,
                                               @RequestBody Map<String, Object> body) {
        service.updateTask(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/collect/tasks/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteCollectTask(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long id) {
        service.deleteTask(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/collect/tasks/{id}/run")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> runCollectTask(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable Long id) {
        return ApiResponse.ok(service.runTask(principal, id));
    }

    @PostMapping("/collect/runs/{runId}/stop")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> stopCollectRun(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable Long runId) {
        return ApiResponse.ok(service.stopRun(principal, runId));
    }

    @PostMapping("/collect/tasks/{id}/stop")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> stopCollectTask(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable Long id) {
        return ApiResponse.ok(service.stopTaskRunning(principal, id));
    }

    @GetMapping("/collect/runs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> collectRuns(@RequestParam(required = false) Long taskId,
                                                              @RequestParam(required = false) String status,
                                                              @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listRunsEnriched(taskId, status, keyword));
    }

    @GetMapping("/collect/runs/{runId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> collectRunDetail(@PathVariable Long runId) {
        return ApiResponse.ok(service.runDetail(runId));
    }

    @GetMapping("/collect/runs/{runId}/results")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovMetadataRegistry>> collectRunResults(@PathVariable Long runId) {
        return ApiResponse.ok(service.listRunResults(runId));
    }

    @GetMapping("/collect/monitor")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> collectMonitor(@RequestParam(required = false) String sourceKeyword,
                                                           @RequestParam(required = false) String taskKeyword,
                                                           @RequestParam(required = false) Long sourceId,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String runStatus) {
        return ApiResponse.ok(service.monitorOverview(sourceKeyword, taskKeyword, sourceId, status, runStatus));
    }

    @PostMapping("/maintain")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> maintain(@AuthenticationPrincipal UserPrincipal principal,
                                      @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.maintainEntry(principal, body));
    }

    @GetMapping("/maintain/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> maintainOverview(@RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) String entryType,
                                                             @RequestParam(required = false) Boolean needRepublishOnly) {
        return ApiResponse.ok(service.maintainOverview(keyword, entryType, needRepublishOnly));
    }

    @GetMapping("/maintain/auto-preview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> autoPreview(@RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.autoMatchPreview(limit));
    }

    @PostMapping("/maintain/auto-run")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> autoRun(@AuthenticationPrincipal UserPrincipal principal,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.autoMaintainBatch(principal, body == null ? Map.of() : body));
    }

    @PostMapping("/maintain/entries/{id}/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> publishEntry(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable Long id) {
        return ApiResponse.ok(service.publishEntry(principal, id));
    }

    @GetMapping("/maintain/entries/{id}/compare-published")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> comparePublished(@PathVariable Long id) {
        return ApiResponse.ok(service.compareWithPublished(id));
    }

    @PostMapping("/notices/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> readNotice(@PathVariable Long id) {
        service.markNoticeRead(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/maintain/suggest-standards")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> suggestStandards() {
        return ApiResponse.ok(service.suggestStandards());
    }

    @PostMapping("/maintain/promote-standard")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> promoteStandard(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.promoteSuggestedStandard(principal, body));
    }

    @GetMapping("/versions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovMetaVersion>> versions(@RequestParam String targetType,
                                                      @RequestParam Long targetId) {
        return ApiResponse.ok(service.listVersions(targetType, targetId));
    }

    @GetMapping("/versions/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> versionOverview(@RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String targetType,
                                                            @RequestParam(required = false) String publishStatus) {
        return ApiResponse.ok(service.versionOverview(keyword, targetType, publishStatus));
    }

    @GetMapping("/versions/{id}/detail")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> versionDetail(@PathVariable Long id) {
        return ApiResponse.ok(service.versionDetail(id));
    }

    @PostMapping("/versions/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> publishVersion(@AuthenticationPrincipal UserPrincipal principal,
                                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.publishVersionTarget(principal, body));
    }

    @PostMapping("/versions/offline")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> offlineVersion(@AuthenticationPrincipal UserPrincipal principal,
                                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.offlineVersionTarget(principal, body));
    }

    @GetMapping("/versions/compare")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> compareVersions(@RequestParam Long leftId, @RequestParam Long rightId) {
        return ApiResponse.ok(service.compareVersions(leftId, rightId));
    }

    @PostMapping("/versions/{id}/rollback")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> rollbackVersion(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id) {
        return ApiResponse.ok(service.rollbackVersion(principal, id));
    }

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> catalog() {
        return ApiResponse.ok(service.catalogViews());
    }

    @GetMapping("/catalog/search")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovMetadataRegistry>> catalogSearch(@AuthenticationPrincipal UserPrincipal principal,
                                                                @RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) String type,
                                                                @RequestParam(required = false) String tag) {
        return ApiResponse.ok(service.searchCatalog(keyword, type, tag, principal));
    }

    @GetMapping("/catalog/browse")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> catalogBrowse(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String tag,
                                                          @RequestParam(required = false) String catalogKind) {
        return ApiResponse.ok(service.catalogBrowse(keyword, tag, catalogKind, principal));
    }

    @GetMapping("/catalog/entries/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> catalogEntryDetail(@PathVariable Long id) {
        return ApiResponse.ok(service.catalogEntryDetail(id));
    }

    @GetMapping("/catalog/inventory")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> catalogInventory(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.catalogInventory(principal));
    }

    @PostMapping("/catalog/{id}/offline")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> offlineCatalogEntry(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable Long id) {
        service.offlineEntry(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/analyze")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> analyze(@RequestParam(required = false) String relationType,
                                                    @RequestParam(required = false) String focusCode) {
        return ApiResponse.ok(service.analyzeGraph(relationType, focusCode));
    }

    @GetMapping("/analyze/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> analyzeOverview() {
        return ApiResponse.ok(service.analyzeOverview());
    }

    @GetMapping("/analyze/tables")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> analyzeTables(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listAnalyzeTables(keyword));
    }

    @GetMapping("/analyze/lineage")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> analyzeLineage(@RequestParam String entryCode,
                                                           @RequestParam(required = false) String level) {
        return ApiResponse.ok(service.analyzeLineage(entryCode, level));
    }

    @GetMapping("/analyze/impact")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> analyzeImpact(@RequestParam String fromCode,
                                                          @RequestParam(required = false) Integer maxDepth) {
        return ApiResponse.ok(service.analyzeImpactRecursive(fromCode, maxDepth));
    }

    @GetMapping("/analyze/offline-assess")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> analyzeOfflineAssess(@RequestParam String entryCode) {
        return ApiResponse.ok(service.analyzeOfflineAssess(entryCode));
    }

    @GetMapping("/analyze/tasks")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> analyzeTasks(@RequestParam String entryCode) {
        return ApiResponse.ok(service.analyzeTasks(entryCode));
    }

    @GetMapping("/relations")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> listRelations(@RequestParam(required = false) String relationType,
                                                                @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listRelations(relationType, keyword));
    }

    @PostMapping("/relations")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createRelation(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createRelation(principal, body));
    }

    @PutMapping("/relations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateRelation(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        service.updateRelation(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/relations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteRelation(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        service.deleteRelation(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/relations/parse-fk")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Integer> parseForeignKeys(@AuthenticationPrincipal UserPrincipal principal,
                                                 @RequestParam Long connectorId) {
        return ApiResponse.ok(service.parseForeignKeys(principal, connectorId));
    }

    @PostMapping("/relations/parse-lineage")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> parseLineage(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.parseLineageAuto(principal));
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> subscribe(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.subscribe(principal, body));
    }

    @GetMapping("/subscriptions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovMetaSubscription>> subscriptions(@AuthenticationPrincipal UserPrincipal principal,
                                                                @RequestParam(required = false) String targetType) {
        return ApiResponse.ok(service.listSubscriptions(principal, targetType));
    }

    @GetMapping("/notices")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovMetaChangeNotice>> notices(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listNotices(status));
    }
}
