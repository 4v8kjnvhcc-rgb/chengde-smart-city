package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.IngAssetTag;
import com.chengde.smartcity.exchange.entity.IngAssetTagBinding;
import com.chengde.smartcity.exchange.entity.IngTagAuditLog;
import com.chengde.smartcity.exchange.entity.IngTagDim;
import com.chengde.smartcity.exchange.entity.IngTagRule;
import com.chengde.smartcity.exchange.service.TagManageService;
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

@RestController
@RequestMapping("/api/v1/exchange/ingestion/tag-manage")
public class TagManageController {

    private final TagManageService service;

    public TagManageController(TagManageService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/dims")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngTagDim>> dims() {
        return ApiResponse.ok(service.listDims());
    }

    @PostMapping("/dims")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveDim(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveDim(body));
    }

    @GetMapping("/tags")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngAssetTag>> tags(
            @RequestParam(required = false) String dimType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tagSource) {
        return ApiResponse.ok(service.listTags(dimType, keyword, tagSource));
    }

    @PostMapping("/tags")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveTag(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveTag(principal, body));
    }

    @PostMapping("/tags/{id}/disable")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> disableTag(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.disableTag(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/tags/merge")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Map<String, Object>> merge(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestBody Map<String, Object> body) {
        Long keepId = body.get("keepId") == null ? null : Long.valueOf(String.valueOf(body.get("keepId")));
        Long dropId = body.get("dropId") == null ? null : Long.valueOf(String.valueOf(body.get("dropId")));
        return ApiResponse.ok(service.mergeTags(principal, keepId, dropId));
    }

    @GetMapping("/rules")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngTagRule>> rules(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listRules(keyword));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveRule(@AuthenticationPrincipal UserPrincipal principal,
                                      @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveRule(principal, body));
    }

    @PostMapping("/rules/{id}/publish")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> publishRule(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.publishRule(principal, id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        service.deleteRule(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/rules/{id}/dry-run")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> dryRun(@PathVariable Long id,
                                                   @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.dryRunRule(id, limit));
    }

    @PostMapping("/rules/{id}/run")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Map<String, Object>> runRule(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id,
                                                    @RequestParam(required = false, defaultValue = "false") boolean dryRun,
                                                    @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.runRule(principal, id, dryRun, limit));
    }

    @PostMapping("/rules/run-all")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Map<String, Object>> runAll(@AuthenticationPrincipal UserPrincipal principal,
                                                   @RequestParam(required = false) Integer limitPerRule) {
        return ApiResponse.ok(service.runAllRules(principal, limitPerRule));
    }

    @PostMapping("/bindings")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> bind(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.bind(principal, body));
    }

    @PostMapping("/bindings/batch")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Map<String, Object>> batchBind(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.batchBind(principal, body));
    }

    @GetMapping("/bindings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngAssetTagBinding>> bindings(
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String confirmStatus,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.listBindings(tagId, assetType, confirmStatus, limit));
    }

    @DeleteMapping("/bindings/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> unbind(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.unbind(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/pending")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngAssetTagBinding>> pending(@RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.listPending(limit));
    }

    @PostMapping("/pending/{id}/confirm")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> confirm(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable Long id,
                                     @RequestParam(defaultValue = "true") boolean accept) {
        service.confirm(principal, id, accept);
        return ApiResponse.ok(null);
    }

    @PostMapping("/pending/batch-confirm")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Map<String, Object>> batchConfirm(@AuthenticationPrincipal UserPrincipal principal,
                                                         @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = body.get("ids") instanceof List ? (List<Object>) body.get("ids") : List.of();
        List<Long> ids = raw.stream().map(o -> Long.valueOf(String.valueOf(o))).toList();
        boolean accept = body.get("accept") == null || Boolean.parseBoolean(String.valueOf(body.get("accept")));
        return ApiResponse.ok(service.batchConfirm(principal, ids, accept));
    }

    @PostMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = body.get("tagIds") instanceof List ? (List<Object>) body.get("tagIds") : List.of();
        List<Long> tagIds = raw.stream().map(o -> Long.valueOf(String.valueOf(o))).toList();
        String mode = body.get("mode") == null ? "OR" : String.valueOf(body.get("mode"));
        String exclude = body.get("excludeTagIds") == null ? null : String.valueOf(body.get("excludeTagIds"));
        Integer limit = body.get("limit") == null ? null : Integer.valueOf(String.valueOf(body.get("limit")));
        return ApiResponse.ok(service.searchByTags(tagIds, mode, exclude, limit));
    }

    @GetMapping("/navigate")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> navigate(@RequestParam(required = false) String dimType) {
        return ApiResponse.ok(service.navigateByDim(dimType));
    }

    @GetMapping("/coverage")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> coverage() {
        return ApiResponse.ok(service.coverageReport());
    }

    @GetMapping("/related/{tagId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> related(@PathVariable Long tagId,
                                                          @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.relatedTags(tagId, limit));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngTagAuditLog>> audits(@RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.auditLogs(limit));
    }
}
