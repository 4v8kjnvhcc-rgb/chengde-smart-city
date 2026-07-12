package com.chengde.smartcity.analysis.controller;

import com.chengde.smartcity.analysis.entity.AnaAnalysisModel;
import com.chengde.smartcity.analysis.entity.AnaBiDashboard;
import com.chengde.smartcity.analysis.entity.AnaDsWorkflow;
import com.chengde.smartcity.analysis.entity.AnaModelSample;
import com.chengde.smartcity.analysis.service.AnalysisDemoService;
import com.chengde.smartcity.common.api.ApiResponse;
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
@RequestMapping("/api/v1/analytics")
public class AnalysisDemoController {

    private final AnalysisDemoService service;

    public AnalysisDemoController(AnalysisDemoService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(service.domainSummary());
    }

    @GetMapping("/models")
    public ApiResponse<List<AnaAnalysisModel>> models(@RequestParam(required = false) String domain) {
        return ApiResponse.ok(service.listModels(domain));
    }

    @GetMapping("/models/{id}")
    public ApiResponse<AnaAnalysisModel> model(@PathVariable Long id) {
        return ApiResponse.ok(service.getModel(id));
    }

    @GetMapping("/models/{id}/samples")
    public ApiResponse<List<AnaModelSample>> samples(@PathVariable Long id) {
        return ApiResponse.ok(service.samples(id));
    }

    @GetMapping("/dashboards")
    public ApiResponse<List<AnaBiDashboard>> dashboards() {
        return ApiResponse.ok(service.listDashboards());
    }

    @GetMapping("/workflows")
    public ApiResponse<List<AnaDsWorkflow>> workflows() {
        return ApiResponse.ok(service.listWorkflows());
    }

    @PostMapping("/workflows/{id}/run")
    public ApiResponse<Map<String, Object>> runWorkflow(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable Long id) {
        return ApiResponse.ok(service.runWorkflow(principal, id));
    }

    @PostMapping("/embed-token")
    public ApiResponse<Map<String, Object>> embedToken(@AuthenticationPrincipal UserPrincipal principal,
                                                       @RequestBody Map<String, String> body) {
        String targetType = body.getOrDefault("targetType", "model");
        String targetId = body.getOrDefault("targetId", "demo");
        return ApiResponse.ok(service.issueEmbedToken(principal, targetType, targetId));
    }

    @GetMapping("/embed-token/validate")
    public ApiResponse<Map<String, Object>> validate(@RequestParam String token) {
        return ApiResponse.ok(service.validateEmbedToken(token));
    }
}
