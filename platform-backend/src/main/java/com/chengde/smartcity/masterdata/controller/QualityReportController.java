package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovQualityCodeImpact;
import com.chengde.smartcity.masterdata.entity.GovQualityKnowledge;
import com.chengde.smartcity.masterdata.entity.GovQualityReport;
import com.chengde.smartcity.masterdata.service.QualityProblemAnalysisService;
import com.chengde.smartcity.masterdata.service.QualityReportService;
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

/**
 * 质量报告管理（与 platform generate 接口并存，使用 reports-mgmt 前缀）。
 * 含问题快速定位、编码映射影响分析、知识沉淀。
 */
@RestController
@RequestMapping("/api/v1/governance/quality/reports-mgmt")
public class QualityReportController {

    private final QualityReportService service;
    private final QualityProblemAnalysisService analysisService;

    public QualityReportController(QualityReportService service,
                                   QualityProblemAnalysisService analysisService) {
        this.service = service;
        this.analysisService = analysisService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovQualityReport>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/trend")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> trend(@RequestParam(required = false) Integer days) {
        return ApiResponse.ok(service.trend(days));
    }

    @GetMapping("/analysis/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> analysisOverview() {
        return ApiResponse.ok(analysisService.overview());
    }

    @GetMapping("/analysis/locate")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> locate(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String targetTable,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String severity) {
        return ApiResponse.ok(analysisService.locate(keyword, targetTable, issueType, severity));
    }

    @GetMapping("/analysis/code-impacts")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovQualityCodeImpact>> codeImpacts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String impactLevel,
            @RequestParam(required = false) String mappingStatus) {
        return ApiResponse.ok(analysisService.listCodeImpacts(keyword, impactLevel, mappingStatus));
    }

    @GetMapping("/analysis/code-impacts/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> codeImpactDetail(@PathVariable Long id) {
        return ApiResponse.ok(analysisService.codeImpactDetail(id));
    }

    @GetMapping("/analysis/knowledge")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovQualityKnowledge>> knowledge(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(analysisService.listKnowledge(keyword, issueType, category));
    }

    @PostMapping("/analysis/knowledge")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovQualityKnowledge> createKnowledge(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(analysisService.saveKnowledge(principal, body));
    }

    @PutMapping("/analysis/knowledge/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovQualityKnowledge> updateKnowledge(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return ApiResponse.ok(analysisService.saveKnowledge(principal, body));
    }

    @PostMapping("/analysis/knowledge/{id}/hit")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovQualityKnowledge> hitKnowledge(@PathVariable Long id) {
        return ApiResponse.ok(analysisService.hitKnowledge(id));
    }

    @DeleteMapping("/analysis/knowledge/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteKnowledge(@PathVariable Long id) {
        analysisService.deleteKnowledge(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @GetMapping("/{id}/drill")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> drill(@PathVariable Long id) {
        return ApiResponse.ok(service.drill(id));
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> export(@PathVariable Long id) {
        return ApiResponse.ok(service.exportJson(id));
    }
}
