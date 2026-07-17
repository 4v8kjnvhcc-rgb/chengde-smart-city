package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovQualityReport;
import com.chengde.smartcity.masterdata.service.QualityReportService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 质量报告管理（与 platform generate 接口并存，使用 reports-mgmt 前缀）。
 */
@RestController
@RequestMapping("/api/v1/governance/quality/reports-mgmt")
public class QualityReportController {

    private final QualityReportService service;

    public QualityReportController(QualityReportService service) {
        this.service = service;
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
