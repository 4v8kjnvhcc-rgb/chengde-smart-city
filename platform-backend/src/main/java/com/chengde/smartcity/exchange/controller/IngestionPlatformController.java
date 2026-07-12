package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDict;
import com.chengde.smartcity.exchange.entity.IngGovernPolicy;
import com.chengde.smartcity.exchange.entity.IngGuideStep;
import com.chengde.smartcity.exchange.entity.IngHealthMetric;
import com.chengde.smartcity.exchange.entity.IngIngestChannel;
import com.chengde.smartcity.exchange.entity.IngPipelineJob;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.entity.IngResourceRegistry;
import com.chengde.smartcity.exchange.entity.IngStatsMetric;
import com.chengde.smartcity.exchange.entity.IngUploadRecord;
import com.chengde.smartcity.exchange.service.IngestionPlatformService;
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
@RequestMapping("/api/v1/exchange/ingestion")
public class IngestionPlatformController {

    private final IngestionPlatformService service;

    public IngestionPlatformController(IngestionPlatformService service) {
        this.service = service;
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

    @GetMapping("/dicts")
    public ApiResponse<List<IngDict>> dicts() {
        return ApiResponse.ok(service.listDicts());
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
}
