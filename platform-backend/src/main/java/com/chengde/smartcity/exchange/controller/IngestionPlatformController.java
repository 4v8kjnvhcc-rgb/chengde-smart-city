package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.IngAssetTag;
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
import com.chengde.smartcity.exchange.service.CollectUploadService;
import com.chengde.smartcity.exchange.service.IngestAssetGovernService;
import com.chengde.smartcity.exchange.service.IngestCatalogService;
import com.chengde.smartcity.exchange.service.IngestionPlatformService;
import com.chengde.smartcity.exchange.service.PipelineDesignService;
import com.chengde.smartcity.exchange.service.RegisterService;
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
    private final CollectUploadService collectUploadService;
    private final PipelineDesignService pipelineDesignService;
    private final IngestCatalogService catalogService;
    private final IngestAssetGovernService assetGovernService;

    public IngestionPlatformController(IngestionPlatformService service, RegisterService registerService,
                                       CollectUploadService collectUploadService,
                                       PipelineDesignService pipelineDesignService,
                                       IngestCatalogService catalogService,
                                       IngestAssetGovernService assetGovernService) {
        this.service = service;
        this.registerService = registerService;
        this.collectUploadService = collectUploadService;
        this.pipelineDesignService = pipelineDesignService;
        this.catalogService = catalogService;
        this.assetGovernService = assetGovernService;
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
    public ApiResponse<Map<String, Object>> registerOverview() {
        return ApiResponse.ok(service.registerOverview());
    }

    @GetMapping("/projects")
    public ApiResponse<List<IngProject>> projects() {
        return ApiResponse.ok(service.listProjects());
    }

    @PostMapping("/projects")
    public ApiResponse<Long> createProject(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createProject(principal, body));
    }

    @DeleteMapping("/projects/{id}")
    @PreAuthorize("hasAuthority('exchange:project:delete')")
    public ApiResponse<Void> deleteProject(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long id) {
        service.deleteProject(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/data-sources")
    public ApiResponse<List<IngDataSource>> dataSources(@RequestParam(required = false) Long projectId) {
        return ApiResponse.ok(service.listDataSources(projectId));
    }

    @PostMapping("/data-sources")
    public ApiResponse<Long> createDataSource(@AuthenticationPrincipal UserPrincipal principal,
                                              @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createDataSource(principal, body));
    }

    @PostMapping("/data-sources/{id}/test")
    public ApiResponse<Map<String, Object>> testDataSource(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(service.testDataSource(principal, id));
    }

    @PutMapping("/data-sources/{id}")
    public ApiResponse<Void> updateDataSource(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        service.updateDataSource(principal, id, body);
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
    public ApiResponse<List<IngResourceRegistry>> registries() {
        return ApiResponse.ok(service.listRegistries());
    }

    @PostMapping("/registries")
    public ApiResponse<Long> createRegistry(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createRegistry(principal, body));
    }

    @PostMapping("/registries/{id}/approve")
    public ApiResponse<Void> approveRegistry(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        service.approveRegistry(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/policies")
    public ApiResponse<List<IngGovernPolicy>> policies(@RequestParam(required = false) String policyType) {
        return ApiResponse.ok(service.listPolicies(policyType));
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
    public ApiResponse<Map<String, Object>> assetReport() {
        return ApiResponse.ok(registerService.assetReport());
    }

    @GetMapping("/register/lineage")
    public ApiResponse<Map<String, Object>> lineage(@RequestParam(required = false) String projectScope) {
        return ApiResponse.ok(registerService.lineageGraph(projectScope));
    }

    @GetMapping("/register/lineage/drill")
    public ApiResponse<Map<String, Object>> lineageDrill(@RequestParam String nodeId) {
        return ApiResponse.ok(registerService.lineageDrill(nodeId));
    }

    @GetMapping("/register/lineage/fields")
    public ApiResponse<List<com.chengde.smartcity.exchange.entity.IngColumnLineage>> fieldLineage(
            @RequestParam String tableNode) {
        return ApiResponse.ok(registerService.fieldLineage(tableNode));
    }

    @GetMapping("/register/tables")
    public ApiResponse<List<IngDataTable>> tables(@RequestParam(required = false) Long sourceId) {
        return ApiResponse.ok(registerService.listTables(sourceId));
    }

    @PostMapping("/register/tables")
    public ApiResponse<Long> createTable(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(registerService.createTable(principal, body));
    }

    @GetMapping("/register/tables/{id}/columns")
    public ApiResponse<List<IngDataColumn>> columns(@PathVariable Long id) {
        return ApiResponse.ok(registerService.listColumns(id));
    }

    @PostMapping("/register/tables/{id}/columns")
    public ApiResponse<Long> createColumn(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(registerService.createColumn(principal, id, body));
    }

    @PutMapping("/register/columns/{id}")
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
    public ApiResponse<List<IngAssetTag>> tags() {
        return ApiResponse.ok(registerService.listTags());
    }

    @PostMapping("/register/tags")
    public ApiResponse<Long> createTag(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(registerService.createTag(principal, body));
    }

    @PutMapping("/register/tags/{id}")
    public ApiResponse<Void> updateTag(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        registerService.updateTag(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/register/tags/match")
    public ApiResponse<Map<String, Object>> matchTags(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(registerService.matchTags(principal));
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
    public ApiResponse<Long> createTemplate(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(collectUploadService.createTemplate(principal, body));
    }

    @PostMapping("/collect/uploads/file")
    public ApiResponse<Long> uploadFile(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestParam("file") MultipartFile file,
                                        @RequestParam(required = false) String templateCode) {
        return ApiResponse.ok(collectUploadService.uploadFile(principal, file, templateCode));
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
    public ApiResponse<List<IngCategoryNode>> categories() {
        return ApiResponse.ok(catalogService.listCategories());
    }

    @PostMapping("/collect/categories")
    public ApiResponse<Long> createCategory(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(catalogService.createCategory(principal, body));
    }

    @GetMapping("/collect/backup-jobs")
    public ApiResponse<List<Map<String, Object>>> backupJobs() {
        return ApiResponse.ok(assetGovernService.listBackupJobs());
    }

    @GetMapping("/collect/archive-jobs")
    public ApiResponse<List<Map<String, Object>>> archiveJobs() {
        return ApiResponse.ok(assetGovernService.listArchiveJobs());
    }
}
