package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.IngClsAssetMark;
import com.chengde.smartcity.exchange.entity.IngClsAuditLog;
import com.chengde.smartcity.exchange.entity.IngClsCategory;
import com.chengde.smartcity.exchange.entity.IngClsHitLog;
import com.chengde.smartcity.exchange.entity.IngClsLevel;
import com.chengde.smartcity.exchange.entity.IngClsScopeRule;
import com.chengde.smartcity.exchange.service.ClassifyGradeService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据分级分类 API。路径：/api/v1/exchange/ingestion/classify-grade
 */
@RestController
@RequestMapping("/api/v1/exchange/ingestion/classify-grade")
public class ClassifyGradeController {

    private final ClassifyGradeService service;

    public ClassifyGradeController(ClassifyGradeService service) {
        this.service = service;
    }

    @GetMapping("/levels")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngClsLevel>> levels() {
        return ApiResponse.ok(service.listLevels());
    }

    @PostMapping("/levels")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveLevel(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveLevel(body));
    }

    @DeleteMapping("/levels/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> deleteLevel(@PathVariable Long id) {
        service.deleteLevel(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngClsCategory>> categories(@RequestParam(required = false) String dimType) {
        return ApiResponse.ok(service.listCategories(dimType));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveCategory(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveCategory(body));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/marks")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngClsAssetMark>> marks(
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String levelCode,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listMarks(assetType, levelCode, keyword));
    }

    @PostMapping("/marks")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveMark(@AuthenticationPrincipal UserPrincipal principal,
                                      @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveMark(principal, body));
    }

    @DeleteMapping("/marks/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> deleteMark(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id) {
        service.deleteMark(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/marks/batch")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Map<String, Object>> batchMark(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.batchMark(principal, body));
    }

    @PostMapping("/suggest-level")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> suggestLevel(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.suggestLevel(body));
    }

    @GetMapping("/scope-rules")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngClsScopeRule>> scopeRules(@RequestParam(required = false) String actionType) {
        return ApiResponse.ok(service.listScopeRules(actionType));
    }

    @PostMapping("/scope-rules")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveScopeRule(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveScopeRule(body));
    }

    @DeleteMapping("/scope-rules/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> deleteScopeRule(@PathVariable Long id) {
        service.deleteScopeRule(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/evaluate")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> evaluate(@AuthenticationPrincipal UserPrincipal principal,
                                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.evaluate(principal, body));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngClsAuditLog>> auditLogs(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.listAudit(limit));
    }

    @GetMapping("/hit-logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngClsHitLog>> hitLogs(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.listHits(limit));
    }

    @GetMapping("/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/candidates")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> candidates(
            @RequestParam(defaultValue = "TABLE") String assetType,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.candidateAssets(assetType, keyword));
    }
}
