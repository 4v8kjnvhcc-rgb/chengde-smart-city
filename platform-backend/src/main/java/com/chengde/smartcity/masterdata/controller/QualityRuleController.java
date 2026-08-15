package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovQualityRuleConfig;
import com.chengde.smartcity.masterdata.service.QualityRuleService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 质量规则配置管理。
 * 与 {@link MasterDataDemoController} 的 GET/POST {@code /api/v1/governance/quality/rules} 并存；
 * 因 GET / 语义冲突（现有仅返回规则列表），本 Controller 使用 rule-mgmt 前缀。
 */
@RestController
@RequestMapping("/api/v1/governance/quality/rule-mgmt")
public class QualityRuleController {

    private final QualityRuleService service;

    public QualityRuleController(QualityRuleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.listWithConfig());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> create(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(principal, body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> update(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        service.update(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/{id}/config")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovQualityRuleConfig> saveConfig(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable Long id,
                                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveConfig(principal, id, body));
    }

    @GetMapping("/{id}/config")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovQualityRuleConfig> getConfig(@PathVariable Long id) {
        return ApiResponse.ok(service.getConfig(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id) {
        service.delete(principal, id);
        return ApiResponse.ok(null);
    }

    /** 对齐标准规则目录（补齐 11 类、清理临时规则、重排排序） */
    @PostMapping("/align-standard")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> alignStandard(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.alignStandardCatalog(principal));
    }
}
