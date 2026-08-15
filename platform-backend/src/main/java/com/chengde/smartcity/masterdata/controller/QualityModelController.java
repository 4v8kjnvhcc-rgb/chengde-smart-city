package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.service.QualityModelService;
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
 * 质量模型管理 + 模型上的质量规则配置（与校验规则类型 rule-mgmt 区分）。
 */
@RestController
@RequestMapping("/api/v1/governance/quality/models")
public class QualityModelController {

    private final QualityModelService service;

    public QualityModelController(QualityModelService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.listModels());
    }

    @GetMapping("/tree")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> tree() {
        return ApiResponse.ok(service.tree());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        return ApiResponse.ok(service.getModel(id));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> create(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createModel(principal, body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> update(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        service.updateModel(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id) {
        service.deleteModel(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/rules")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> listRules(@PathVariable Long id,
                                                            @RequestParam(required = false) Long modelTableId,
                                                            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listRules(id, modelTableId, keyword));
    }

    @PostMapping("/{id}/rules")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createRule(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id,
                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createRule(principal, id, body));
    }

    @PutMapping("/{id}/rules/{ruleId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateRule(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id,
                                        @PathVariable Long ruleId,
                                        @RequestBody Map<String, Object> body) {
        service.updateRule(principal, id, ruleId, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/rules/{ruleId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteRule(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id,
                                        @PathVariable Long ruleId) {
        service.deleteRule(principal, id, ruleId);
        return ApiResponse.ok(null);
    }

    /** 一键清除某字段下绑定的全部模型规则 */
    @PostMapping("/{id}/rules/clear-by-field")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> clearRulesByField(@AuthenticationPrincipal UserPrincipal principal,
                                                              @PathVariable Long id,
                                                              @RequestBody Map<String, Object> body) {
        Long modelTableId = body.get("modelTableId") == null ? null : Long.valueOf(String.valueOf(body.get("modelTableId")));
        String fieldName = body.get("fieldName") == null ? null : String.valueOf(body.get("fieldName"));
        return ApiResponse.ok(service.clearRulesByField(principal, id, modelTableId, fieldName));
    }
}
