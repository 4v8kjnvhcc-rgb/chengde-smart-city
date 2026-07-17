package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovMetaChangeNotice;
import com.chengde.smartcity.masterdata.entity.GovMetaCollectRun;
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

    @GetMapping("/collect/tasks")
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

    @GetMapping("/collect/runs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovMetaCollectRun>> collectRuns(@RequestParam(required = false) Long taskId,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listRuns(taskId, status, keyword));
    }

    @GetMapping("/collect/runs/{runId}/results")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovMetadataRegistry>> collectRunResults(@PathVariable Long runId) {
        return ApiResponse.ok(service.listRunResults(runId));
    }

    @GetMapping("/collect/monitor")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> collectMonitor(@RequestParam(required = false) String sourceKeyword,
                                                           @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.monitorOverview(sourceKeyword, status));
    }

    @PostMapping("/maintain")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> maintain(@AuthenticationPrincipal UserPrincipal principal,
                                      @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.maintainEntry(principal, body));
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

    @GetMapping("/versions/compare")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> compareVersions(@RequestParam Long leftId, @RequestParam Long rightId) {
        return ApiResponse.ok(service.compareVersions(leftId, rightId));
    }

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> catalog() {
        return ApiResponse.ok(service.catalogViews());
    }

    @GetMapping("/catalog/search")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovMetadataRegistry>> catalogSearch(@RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) String type,
                                                                @RequestParam(required = false) String tag) {
        return ApiResponse.ok(service.searchCatalog(keyword, type, tag));
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
    public ApiResponse<Map<String, Object>> analyze(@RequestParam(required = false) String relationType) {
        return ApiResponse.ok(service.analyzeGraph(relationType));
    }

    @GetMapping("/analyze/impact")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> analyzeImpact(@RequestParam String fromCode) {
        return ApiResponse.ok(service.analyzeImpactRecursive(fromCode));
    }

    @PostMapping("/relations")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createRelation(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createRelation(principal, body));
    }

    @PostMapping("/relations/parse-fk")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Integer> parseForeignKeys(@AuthenticationPrincipal UserPrincipal principal,
                                                 @RequestParam Long connectorId) {
        return ApiResponse.ok(service.parseForeignKeys(principal, connectorId));
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
