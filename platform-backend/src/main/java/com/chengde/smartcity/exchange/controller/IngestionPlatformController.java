package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.IngAssetTag;
import com.chengde.smartcity.exchange.entity.IngAssetTagBinding;
import com.chengde.smartcity.exchange.entity.IngBizSystem;
import com.chengde.smartcity.exchange.entity.IngCategoryNode;
import com.chengde.smartcity.exchange.entity.IngDataColumn;
import com.chengde.smartcity.exchange.entity.IngDataDefinition;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngDict;
import com.chengde.smartcity.exchange.entity.IngDictItem;
import com.chengde.smartcity.exchange.entity.IngGovernPolicy;
import com.chengde.smartcity.exchange.entity.IngGuideStep;
import com.chengde.smartcity.exchange.entity.IngHealthMetric;
import com.chengde.smartcity.exchange.entity.IngIngestChannel;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.entity.IngPipelineJob;
import com.chengde.smartcity.exchange.entity.IngProbeReport;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.entity.IngReconcileLog;
import com.chengde.smartcity.exchange.entity.IngResourceRegistry;
import com.chengde.smartcity.exchange.entity.IngStatsMetric;
import com.chengde.smartcity.exchange.entity.IngUploadRecord;
import com.chengde.smartcity.exchange.entity.IngUploadTemplate;
import com.chengde.smartcity.exchange.service.AssetReportService;
import com.chengde.smartcity.exchange.service.CollectUploadService;
import com.chengde.smartcity.exchange.service.ExcelManualUploadService;
import com.chengde.smartcity.exchange.service.IngestAssetGovernService;
import com.chengde.smartcity.exchange.service.IngestCatalogService;
import com.chengde.smartcity.exchange.service.IngestionPlatformService;
import com.chengde.smartcity.exchange.service.KettleCollectService;
import com.chengde.smartcity.exchange.service.LineageService;
import com.chengde.smartcity.exchange.service.PipelineDesignService;
import com.chengde.smartcity.exchange.service.RegisterService;
import com.chengde.smartcity.exchange.service.RegisterWorkflowService;
import com.chengde.smartcity.exchange.service.DictColumnLinkService;
import com.chengde.smartcity.exchange.service.TableIngestEngine;
import com.chengde.smartcity.exchange.entity.IngDictColumnLink;
import com.chengde.smartcity.exchange.entity.IngRegisterAuditLog;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/exchange/ingestion")
public class IngestionPlatformController {

    private final IngestionPlatformService service;
    private final RegisterService registerService;
    private final AssetReportService assetReportService;
    private final LineageService lineageService;
    private final CollectUploadService collectUploadService;
    private final ExcelManualUploadService excelManualUploadService;
    private final PipelineDesignService pipelineDesignService;
    private final IngestCatalogService catalogService;
    private final IngestAssetGovernService assetGovernService;
    private final KettleCollectService kettleCollectService;
    private final TableIngestEngine tableIngestEngine;
    private final RegisterWorkflowService registerWorkflowService;
    private final DictColumnLinkService dictColumnLinkService;

    public IngestionPlatformController(IngestionPlatformService service, RegisterService registerService,
                                       AssetReportService assetReportService,
                                       LineageService lineageService,
                                       CollectUploadService collectUploadService,
                                       ExcelManualUploadService excelManualUploadService,
                                       PipelineDesignService pipelineDesignService,
                                       IngestCatalogService catalogService,
                                       IngestAssetGovernService assetGovernService,
                                       KettleCollectService kettleCollectService,
                                       TableIngestEngine tableIngestEngine,
                                       RegisterWorkflowService registerWorkflowService,
                                       DictColumnLinkService dictColumnLinkService) {
        this.service = service;
        this.registerService = registerService;
        this.assetReportService = assetReportService;
        this.lineageService = lineageService;
        this.collectUploadService = collectUploadService;
        this.excelManualUploadService = excelManualUploadService;
        this.pipelineDesignService = pipelineDesignService;
        this.catalogService = catalogService;
        this.assetGovernService = assetGovernService;
        this.kettleCollectService = kettleCollectService;
        this.tableIngestEngine = tableIngestEngine;
        this.registerWorkflowService = registerWorkflowService;
        this.dictColumnLinkService = dictColumnLinkService;
    }

    @GetMapping("/stats/base")
    public ApiResponse<List<IngStatsMetric>> baseStats() {
        return ApiResponse.ok(service.baseStats());
    }

    @GetMapping("/stats/domain")
    public ApiResponse<List<IngStatsMetric>> domainStats() {
        return ApiResponse.ok(service.domainStats());
    }

    @GetMapping("/guides")
    public ApiResponse<List<IngGuideStep>> guides() {
        return ApiResponse.ok(service.guideSteps());
    }

    @GetMapping("/register/overview")
    public ApiResponse<Map<String, Object>> registerOverview(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.registerOverview(principal));
    }

    @GetMapping("/projects")
    public ApiResponse<List<IngProject>> projects(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.listProjects(principal));
    }

    @GetMapping("/cluster-accounts/options")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> clusterAccountOptions() {
        return ApiResponse.ok(service.listClusterAccountOptions());
    }

    @PostMapping("/projects")
    public ApiResponse<Long> createProject(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createProject(principal, body));
    }

    @PutMapping("/projects/{id}")
    public ApiResponse<Void> updateProject(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        service.updateProject(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/projects/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteProject(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long id) {
        service.deleteProject(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/systems")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngBizSystem>> systems(@AuthenticationPrincipal UserPrincipal principal,
                                                   @RequestParam Long projectId) {
        return ApiResponse.ok(service.listBizSystems(principal, projectId));
    }

    @PostMapping("/systems")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createSystem(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createBizSystem(principal, body));
    }

    @PutMapping("/systems/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateSystem(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        service.updateBizSystem(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/systems/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteSystem(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id) {
        service.deleteBizSystem(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/data-sources")
    public ApiResponse<List<IngDataSource>> dataSources(@AuthenticationPrincipal UserPrincipal principal,
                                                        @RequestParam(required = false) Long projectId,
                                                        @RequestParam(required = false) Long systemId) {
        return ApiResponse.ok(service.listDataSources(principal, projectId, systemId));
    }

    @PostMapping("/data-sources")
    public ApiResponse<Long> createDataSource(@AuthenticationPrincipal UserPrincipal principal,
                                              @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createDataSource(principal, body));
    }

    @PostMapping("/data-sources/{id}/test")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> testDataSource(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(service.testDataSource(principal, id));
    }

    @PostMapping("/data-sources/{id}/probe")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> probeDataSource(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable Long id) {
        return ApiResponse.ok(service.probeDataSource(principal, id));
    }

    @PostMapping("/data-sources/{id}/register-tables")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> registerTables(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable Long id,
                                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.registerTables(principal, id, body));
    }

    @PutMapping("/data-sources/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateDataSource(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        service.updateDataSource(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/data-sources/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteDataSource(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable Long id) {
        service.deleteDataSource(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/dicts")
    public ApiResponse<List<IngDict>> dicts(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(registerService.listDicts(keyword));
    }

    @PostMapping("/dicts")
    public ApiResponse<Long> createDict(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createDict(principal, body));
    }

    @GetMapping("/uploads")
    public ApiResponse<List<IngUploadRecord>> uploads() {
        return ApiResponse.ok(service.listUploads());
    }

    @PostMapping("/uploads")
    public ApiResponse<Long> upload(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.uploadData(principal, body));
    }

    @GetMapping("/channels")
    public ApiResponse<List<IngIngestChannel>> channels(@RequestParam(required = false) String channelType) {
        return ApiResponse.ok(service.listChannels(channelType));
    }

    @PostMapping("/channels")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createChannel(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createChannel(principal, body));
    }

    @PostMapping("/channels/{id}/run")
    public ApiResponse<Map<String, Object>> runChannel(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable Long id) {
        return ApiResponse.ok(service.runChannel(principal, id));
    }

    @PutMapping("/channels/{id}")
    public ApiResponse<Void> updateChannel(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        service.updateChannel(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/pipeline-jobs")
    public ApiResponse<List<IngPipelineJob>> pipelineJobs(@RequestParam(required = false) String jobType) {
        return ApiResponse.ok(service.listPipelineJobs(jobType));
    }

    @PostMapping("/pipeline-jobs/run")
    public ApiResponse<Long> runPipeline(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.runPipeline(principal, body));
    }

    @GetMapping("/reconcile/{action}")
    public ApiResponse<Map<String, Object>> reconcile(@PathVariable String action) {
        return ApiResponse.ok(service.reconcileApi(action));
    }

    @GetMapping("/registries")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngResourceRegistry>> registries(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String shareType,
            @RequestParam(required = false) Boolean unboundOnly,
            @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(catalogService.listRegistries(
                principal, keyword, approvalStatus, shareType, unboundOnly, categoryId));
    }

    @GetMapping("/registries/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<IngResourceRegistry> registryDetail(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable Long id) {
        return ApiResponse.ok(catalogService.getRegistry(principal, id));
    }

    @PostMapping("/registries")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:resources') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Long> createRegistry(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(catalogService.createRegistry(principal, body));
    }

    @PostMapping("/registries/batch")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:resources') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<List<Long>> batchCreateRegistry(@AuthenticationPrincipal UserPrincipal principal,
                                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(catalogService.batchCreateFromTables(principal, body));
    }

    @PostMapping("/registries/import")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:resources') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Map<String, Object>> importRegistries(@AuthenticationPrincipal UserPrincipal principal,
                                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(catalogService.importRegistries(principal, body));
    }

    @PutMapping("/registries/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:resources') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Void> updateRegistry(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        catalogService.updateRegistry(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/registries/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:resources') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Void> deleteRegistry(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        catalogService.deleteRegistry(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/registries/{id}/approve")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:approvals') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Void> approveRegistry(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        catalogService.approveRegistryLegacy(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/registries/submit-publish")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:publish') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Long> submitPublish(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        List<Long> registryIds = ids == null ? List.of() : ids.stream().map(Number::longValue).toList();
        return ApiResponse.ok(catalogService.submitPublish(principal, registryIds,
                body.get("comment") == null ? null : String.valueOf(body.get("comment"))));
    }

    @PostMapping("/registries/submit-offline")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:publish') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Long> submitOffline(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        List<Long> registryIds = ids == null ? List.of() : ids.stream().map(Number::longValue).toList();
        return ApiResponse.ok(catalogService.submitOffline(principal, registryIds,
                body.get("comment") == null ? null : String.valueOf(body.get("comment"))));
    }

    @PostMapping("/collect/categories/{categoryId}/bind")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:publish') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Void> bindResources(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long categoryId,
                                           @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        List<Long> registryIds = ids == null ? List.of() : ids.stream().map(Number::longValue).toList();
        catalogService.bindResources(principal, categoryId, registryIds);
        return ApiResponse.ok(null);
    }

    @PostMapping("/collect/categories/unbind")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:publish') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Void> unbindResources(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        List<Long> registryIds = ids == null ? List.of() : ids.stream().map(Number::longValue).toList();
        catalogService.unbindResources(principal, registryIds);
        return ApiResponse.ok(null);
    }

    @GetMapping("/collect/categories/{categoryId}/bound")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngResourceRegistry>> boundResources(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable Long categoryId) {
        return ApiResponse.ok(catalogService.listBound(principal, categoryId));
    }

    @GetMapping("/collect/approvals")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:approvals') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<List<Map<String, Object>>> catalogApprovals(@RequestParam(required = false) String status) {
        return ApiResponse.ok(catalogService.listApprovals(status));
    }

    @PostMapping("/collect/approvals/{id}/approve")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:approvals') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Void> approveCatalog(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body) {
        String comment = body == null || body.get("comment") == null ? "同意" : String.valueOf(body.get("comment"));
        catalogService.approve(principal, id, comment);
        return ApiResponse.ok(null);
    }

    @PostMapping("/collect/approvals/{id}/reject")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:approvals') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Void> rejectCatalog(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        catalogService.reject(principal, id, body.get("comment") == null ? null : String.valueOf(body.get("comment")));
        return ApiResponse.ok(null);
    }

    @PostMapping("/collect/approvals/batch-approve")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:approvals') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Map<String, Object>> batchApproveCatalog(@AuthenticationPrincipal UserPrincipal principal,
                                                                @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        List<Long> approvalIds = ids == null ? List.of() : ids.stream().map(Number::longValue).toList();
        String comment = body.get("comment") == null ? "批量同意" : String.valueOf(body.get("comment"));
        return ApiResponse.ok(catalogService.batchApprove(principal, approvalIds, comment));
    }

    @PostMapping("/collect/approvals/batch-reject")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:approvals') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Map<String, Object>> batchRejectCatalog(@AuthenticationPrincipal UserPrincipal principal,
                                                               @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        List<Long> approvalIds = ids == null ? List.of() : ids.stream().map(Number::longValue).toList();
        return ApiResponse.ok(catalogService.batchReject(principal, approvalIds,
                body.get("comment") == null ? null : String.valueOf(body.get("comment"))));
    }

    @GetMapping("/policies")
    public ApiResponse<List<IngGovernPolicy>> policies(@RequestParam(required = false) String policyType) {
        return ApiResponse.ok(service.listPolicies(policyType));
    }

    @PostMapping("/policies")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> savePolicy(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.savePolicy(body));
    }

    @DeleteMapping("/policies/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> deletePolicy(@PathVariable Long id) {
        service.deletePolicy(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/global-view")
    public ApiResponse<Map<String, Object>> globalView() {
        return ApiResponse.ok(service.globalAssetView());
    }

    @GetMapping("/health")
    public ApiResponse<List<IngHealthMetric>> health() {
        return ApiResponse.ok(service.healthMetrics());
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(@RequestParam(required = false) String q) {
        return ApiResponse.ok(service.searchMetadata(q));
    }

    @PostMapping("/policies/{id}/lifecycle")
    public ApiResponse<Map<String, Object>> lifecycle(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable Long id) {
        return ApiResponse.ok(service.runLifecycleDemo(principal, id));
    }

    @GetMapping("/register/asset-report")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> assetReport(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(assetReportService.dashboard(principal));
    }

    @GetMapping("/register/asset-report/projects/{projectId}/tables")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> assetReportProjectTables(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long projectId) {
        return ApiResponse.ok(assetReportService.projectTables(principal, projectId));
    }

    @GetMapping("/register/asset-report/tables/{id}/detail")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> assetReportTableDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(assetReportService.tableDetail(principal, id));
    }

    @GetMapping("/register/asset-report/scripts/{id}/detail")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> assetReportScriptDetail(@PathVariable Long id) {
        return ApiResponse.ok(assetReportService.scriptDetail(id));
    }

    @GetMapping("/register/asset-report/workflows/{id}/detail")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> assetReportWorkflowDetail(@PathVariable Long id) {
        return ApiResponse.ok(assetReportService.workflowDetail(id));
    }

    @GetMapping("/register/asset-report/workflows/runs/{runId}/monitor")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> assetReportWorkflowRunMonitor(@PathVariable Long runId) {
        return ApiResponse.ok(assetReportService.workflowRunMonitor(runId));
    }

    @GetMapping("/register/lineage")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> lineage(@AuthenticationPrincipal UserPrincipal principal,
                                                    @RequestParam(required = false) Long projectId,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Long categoryTagId,
                                                    @RequestParam(required = false) String projectScope) {
        // projectScope 兼容旧参数：若未传 projectId 则忽略
        return ApiResponse.ok(lineageService.panorama(principal, projectId, keyword, categoryTagId));
    }

    @GetMapping("/register/lineage/drill")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> lineageDrill(@AuthenticationPrincipal UserPrincipal principal,
                                                         @RequestParam String nodeId) {
        return ApiResponse.ok(lineageService.drill(principal, nodeId));
    }

    @GetMapping("/register/lineage/fields")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> fieldLineage(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestParam String tableNode) {
        return ApiResponse.ok(lineageService.fieldLineage(principal, tableNode));
    }

    @GetMapping("/register/lineage/table-meta")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> lineageTableMeta(@AuthenticationPrincipal UserPrincipal principal,
                                                             @RequestParam String tableNode) {
        return ApiResponse.ok(lineageService.tableMeta(principal, tableNode));
    }

    @GetMapping("/register/tables")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngDataTable>> tables(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestParam(required = false) Long sourceId) {
        return ApiResponse.ok(registerService.listTables(principal, sourceId));
    }

    @PostMapping("/register/tables")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createTable(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(registerService.createTable(principal, body));
    }

    @PostMapping("/register/tables/{id}/finalize-forward")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> finalizeForwardTable(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable Long id) {
        return ApiResponse.ok(registerService.finalizeForwardTable(principal, id));
    }

    @PostMapping("/register/tables/{id}/collect")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> collectTable(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable Long id) {
        return ApiResponse.ok(kettleCollectService.collectTable(principal, id));
    }

    @GetMapping("/register/tables/{id}/columns")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngDataColumn>> columns(@PathVariable Long id) {
        return ApiResponse.ok(registerService.listColumns(id));
    }

    @PostMapping("/register/tables/{id}/columns")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createColumn(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(registerService.createColumn(principal, id, body));
    }

    @PutMapping("/register/columns/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateColumn(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        registerService.updateColumn(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/register/metadata/template")
    public ApiResponse<String> metadataTemplate() {
        return ApiResponse.ok(registerService.metadataTemplateCsv());
    }

    @PostMapping("/register/metadata/import")
    public ApiResponse<Map<String, Object>> importMetadata(@AuthenticationPrincipal UserPrincipal principal,
                                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(registerService.importMetadata(principal, body));
    }

    @GetMapping("/register/tags")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> tags(@RequestParam(required = false, defaultValue = "false") boolean tree) {
        return ApiResponse.ok(registerService.listTags(tree));
    }

    @PostMapping("/register/tags")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:tag:list') or hasAuthority('system:tag:edit') or hasAuthority('system:tag:query')")
    public ApiResponse<Long> createTag(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(registerService.createTag(principal, body));
    }

    @PutMapping("/register/tags/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:tag:list') or hasAuthority('system:tag:edit') or hasAuthority('system:tag:query')")
    public ApiResponse<Void> updateTag(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        registerService.updateTag(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/register/tags/match")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> matchTags(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(registerService.matchTags(principal));
    }

    @GetMapping("/register/tags/{id}/suggest-rule")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> suggestTagRule(@PathVariable Long id) {
        return ApiResponse.ok(registerService.suggestRuleFromBindings(id));
    }

    @PostMapping("/register/tags/{id}/apply-suggested-rule")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> applySuggestedTagRule(@AuthenticationPrincipal UserPrincipal principal,
                                                                  @PathVariable Long id,
                                                                  @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(registerService.applySuggestedRule(principal, id, body == null ? Map.of() : body));
    }

    @GetMapping("/register/tag-bindings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngAssetTagBinding>> tagBindings(@RequestParam String assetType,
                                                             @RequestParam Long assetId) {
        return ApiResponse.ok(registerService.listTagBindings(assetType, assetId));
    }

    @GetMapping("/register/tag-bindings/by-tag")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngAssetTagBinding>> tagBindingsByTag(@RequestParam Long tagId) {
        return ApiResponse.ok(registerService.listTagBindingsByTag(tagId));
    }

    @GetMapping("/register/tables/{id}/tag-match-context")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> tagMatchContext(@PathVariable Long id) {
        return ApiResponse.ok(registerService.tagMatchContext(id));
    }

    @PostMapping("/register/tag-bindings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> bindTag(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(registerService.bindTag(principal, body));
    }

    @DeleteMapping("/register/tag-bindings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> unbindTag(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestBody Map<String, Object> body) {
        registerService.unbindTag(principal, body);
        return ApiResponse.ok(null);
    }

    @PutMapping("/dicts/{id}")
    public ApiResponse<Void> updateDict(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id,
                                        @RequestBody Map<String, Object> body) {
        registerService.updateDict(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/dicts")
    public ApiResponse<Void> deleteDicts(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> raw = (List<Number>) body.get("ids");
        List<Long> ids = raw == null ? List.of() : raw.stream().map(Number::longValue).toList();
        registerService.deleteDicts(principal, ids);
        return ApiResponse.ok(null);
    }

    @GetMapping("/dicts/{id}/items")
    public ApiResponse<List<IngDictItem>> dictItems(@PathVariable Long id) {
        return ApiResponse.ok(registerService.listDictItems(id));
    }

    @PostMapping("/dicts/{id}/items")
    public ApiResponse<Long> createDictItem(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(registerService.createDictItem(principal, id, body));
    }

    @PutMapping("/dicts/items/{itemId}")
    public ApiResponse<Void> updateDictItem(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long itemId,
                                            @RequestBody Map<String, Object> body) {
        registerService.updateDictItem(principal, itemId, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/dicts/items/{itemId}")
    public ApiResponse<Void> deleteDictItem(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long itemId) {
        registerService.deleteDictItem(principal, itemId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/dicts/template")
    public ApiResponse<String> dictTemplate() {
        return ApiResponse.ok(registerService.dictTemplateCsv());
    }

    @PostMapping("/dicts/import")
    public ApiResponse<Map<String, Object>> importDict(@AuthenticationPrincipal UserPrincipal principal,
                                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(registerService.importDictCsv(principal, String.valueOf(body.get("csvText"))));
    }

    @PostMapping("/dicts/export")
    public ApiResponse<String> exportDict(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> raw = (List<Number>) body.get("ids");
        List<Long> ids = raw == null ? List.of() : raw.stream().map(Number::longValue).toList();
        return ApiResponse.ok(registerService.exportDictCsv(ids));
    }

    @GetMapping("/collect/templates")
    public ApiResponse<List<IngUploadTemplate>> templates() {
        return ApiResponse.ok(collectUploadService.listTemplates());
    }

    @PostMapping("/collect/templates")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createTemplate(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        if (body != null && body.get("bindings") != null) {
            return ApiResponse.ok(excelManualUploadService.saveTemplate(principal, body));
        }
        return ApiResponse.ok(collectUploadService.createTemplate(principal, body));
    }

    @GetMapping("/collect/templates/{templateCode}/bindings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> templateBindings(@PathVariable String templateCode) {
        return ApiResponse.ok(excelManualUploadService.describeTemplate(templateCode));
    }

    @DeleteMapping("/collect/templates/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteTemplate(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        collectUploadService.deleteTemplate(principal, id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/collect/templates/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateTemplateStatus(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        Object st = body == null ? null : body.get("status");
        collectUploadService.updateTemplateStatus(principal, id, st == null ? "" : String.valueOf(st));
        return ApiResponse.ok(null);
    }

    @PostMapping("/collect/uploads/file")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> uploadFile(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestParam("file") MultipartFile file,
                                        @RequestParam(required = false) String templateCode) {
        return ApiResponse.ok(collectUploadService.uploadFile(principal, file, templateCode));
    }

    /** 选文件：解析 sheet 列表（不落 ODS） */
    @PostMapping("/collect/uploads/inspect")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> inspectUpload(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(excelManualUploadService.inspect(principal, file));
    }

    /** 指定表头行读取列名（建模板用） */
    @PostMapping("/collect/uploads/preview-header")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> previewHeader(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(excelManualUploadService.previewHeader(principal, body));
    }

    /** 按模板预览：校验字段一致后返回数据行 */
    @PostMapping("/collect/uploads/preview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> previewUpload(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(excelManualUploadService.preview(principal, body));
    }

    /** 确认写入 smart_city_ods（同一文件可多次 commit 不同 sheet） */
    @PostMapping("/collect/uploads/commit")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> commitUpload(@AuthenticationPrincipal UserPrincipal principal,
                                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(excelManualUploadService.commitToOds(principal, body));
    }

    /** 结束上传会话（释放临时文件） */
    @PostMapping("/collect/uploads/finish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> finishUpload(@AuthenticationPrincipal UserPrincipal principal,
                                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(excelManualUploadService.finishSession(principal, body));
    }

    @GetMapping("/collect/tasks")
    public ApiResponse<List<IngIngestTask>> tasks(@RequestParam(required = false) Long channelId) {
        return ApiResponse.ok(collectUploadService.listTasks(channelId));
    }

    @PostMapping("/collect/tasks")
    public ApiResponse<Long> createTask(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(collectUploadService.createTask(principal, body));
    }

    @GetMapping("/collect/jobs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngIngestTask>> jobs(@RequestParam(required = false) String accessMode) {
        return ApiResponse.ok(collectUploadService.listJobs(accessMode));
    }

    @GetMapping("/collect/jobs/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<IngIngestTask> job(@PathVariable Long id) {
        return ApiResponse.ok(collectUploadService.getJob(id));
    }

    @PostMapping("/collect/jobs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createJob(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(collectUploadService.createTask(principal, body));
    }

    @PutMapping("/collect/jobs/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateJob(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        collectUploadService.updateJob(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/collect/jobs/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteJob(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long id) {
        collectUploadService.deleteJob(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/collect/jobs/{id}/run")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> runJob(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(tableIngestEngine.runJob(principal, id));
    }

    @PostMapping("/collect/jobs/{id}/reset")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> resetJob(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable Long id) {
        collectUploadService.resetStuckJob(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/collect/jobs/preview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> previewJob(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(tableIngestEngine.preview(body));
    }

    @GetMapping("/collect/jobs/mapping-suggest")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> mappingSuggest(@RequestParam Long tableId,
                                                                 @RequestParam(required = false, defaultValue = "NAME") String mode) {
        return ApiResponse.ok(tableIngestEngine.suggestMapping(tableId, mode));
    }

    @GetMapping("/collect/probe-reports")
    public ApiResponse<List<IngProbeReport>> probeReports() {
        return ApiResponse.ok(pipelineDesignService.listProbeReports());
    }

    @GetMapping("/collect/definitions")
    public ApiResponse<List<IngDataDefinition>> definitions() {
        return ApiResponse.ok(pipelineDesignService.listDefinitions());
    }

    @PostMapping("/collect/definitions")
    public ApiResponse<Long> saveDefinition(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(pipelineDesignService.saveDefinition(principal, body));
    }

    @GetMapping("/collect/reconcile-logs")
    public ApiResponse<List<IngReconcileLog>> reconcileLogs() {
        return ApiResponse.ok(pipelineDesignService.listReconcileLogs());
    }

    @GetMapping("/collect/categories")
    public ApiResponse<List<IngCategoryNode>> categories(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(catalogService.listCategories(keyword));
    }

    @PostMapping("/collect/categories")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:classify') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Long> createCategory(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(catalogService.createCategory(principal, body));
    }

    @PutMapping("/collect/categories/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:classify') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Void> updateCategory(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        catalogService.updateCategory(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/collect/categories/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:catalog:classify') or hasAuthority('hub:ingestion:collect:catalog')")
    public ApiResponse<Void> deleteCategory(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        catalogService.deleteCategory(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/collect/backup-jobs")
    public ApiResponse<List<Map<String, Object>>> backupJobs() {
        return ApiResponse.ok(assetGovernService.listBackupJobs());
    }

    @GetMapping("/collect/archive-jobs")
    public ApiResponse<List<Map<String, Object>>> archiveJobs() {
        return ApiResponse.ok(assetGovernService.listArchiveJobs());
    }

    // ── 登记审核流（sjzc）────────────────────────────────────
    @PostMapping("/register/workflow/submit")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> registerSubmit(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        var t = RegisterWorkflowService.parseTarget(body);
        registerWorkflowService.submit(principal, t.getKey(), t.getValue());
        return ApiResponse.ok(null);
    }

    @PostMapping("/register/workflow/approve")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> registerApprove(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestBody Map<String, Object> body) {
        var t = RegisterWorkflowService.parseTarget(body);
        String comment = body.get("comment") == null ? null : String.valueOf(body.get("comment"));
        registerWorkflowService.approve(principal, t.getKey(), t.getValue(), comment);
        return ApiResponse.ok(null);
    }

    @PostMapping("/register/workflow/reject")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> registerReject(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        var t = RegisterWorkflowService.parseTarget(body);
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        registerWorkflowService.reject(principal, t.getKey(), t.getValue(), reason);
        return ApiResponse.ok(null);
    }

    @GetMapping("/register/workflow/logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngRegisterAuditLog>> registerAuditLogs(@RequestParam String objectType,
                                                                    @RequestParam Long objectId) {
        return ApiResponse.ok(registerWorkflowService.listAuditLogs(objectType, objectId));
    }

    @GetMapping("/dicts/{id}/column-links")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngDictColumnLink>> dictColumnLinks(@PathVariable Long id) {
        return ApiResponse.ok(dictColumnLinkService.listByDict(id));
    }

    @PostMapping("/dicts/{id}/column-links")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> bindDictColumn(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(dictColumnLinkService.bind(principal, id, body));
    }

    @DeleteMapping("/dicts/column-links/{linkId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> unbindDictColumn(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable Long linkId) {
        dictColumnLinkService.unbind(principal, linkId);
        return ApiResponse.ok(null);
    }
}
