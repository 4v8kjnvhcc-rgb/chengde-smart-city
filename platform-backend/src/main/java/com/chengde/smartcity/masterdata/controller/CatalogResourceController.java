package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovCatalogApproval;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovCatalogResourceVersion;
import com.chengde.smartcity.masterdata.service.CatalogResourceService;
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
 * 资源目录管理（CRUD + 审批流）。
 * 路径使用 resources-mgmt，避免与 {@link GovernancePlatformController}
 * 的 {@code /catalog/resources} 演示接口冲突。
 */
@RestController
@RequestMapping("/api/v1/governance/catalog/resources-mgmt")
public class CatalogResourceController {

    private final CatalogResourceService service;

    public CatalogResourceController(CatalogResourceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovCatalogResource>> list(@RequestParam(required = false) Long categoryId,
                                                      @RequestParam(required = false) String resourceType,
                                                      @RequestParam(required = false) String publishStatus,
                                                      @RequestParam(required = false) String approvalStatus,
                                                      @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(categoryId, resourceType, publishStatus, approvalStatus, keyword));
    }

    @GetMapping("/approvals")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> approvals(@RequestParam(required = false) Long resourceId,
                                                            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listApprovals(resourceId, status));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovCatalogResourceVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(service.listVersions(id));
    }

    @GetMapping("/{id}/versions/diff")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> versionDiff(@PathVariable Long id,
                                                        @RequestParam Integer leftNo,
                                                        @RequestParam Integer rightNo) {
        return ApiResponse.ok(service.diffVersions(id, leftNo, rightNo));
    }

    @PostMapping("/import")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> importResources(@AuthenticationPrincipal UserPrincipal principal,
                                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.importResources(principal, body));
    }

    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Object> exportResources(@RequestParam(required = false) Long categoryId,
                                               @RequestParam(defaultValue = "json") String format) {
        return ApiResponse.ok(service.exportResources(categoryId, format));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovCatalogResource> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/{id}/approvals")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> resourceApprovals(@PathVariable Long id) {
        return ApiResponse.ok(service.listApprovals(id, null));
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

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id) {
        service.delete(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/batch-delete")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> batchDelete(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, Object> body) {
        Object raw = body.get("ids");
        if (!(raw instanceof List<?> list)) {
            return ApiResponse.fail(400, "ids 须为数组");
        }
        List<Long> ids = list.stream().map(v -> Long.valueOf(String.valueOf(v))).toList();
        service.batchDelete(principal, ids);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovCatalogApproval> submit(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.submit(principal, id, body == null ? Map.of() : body));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovCatalogResource> publish(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(service.publish(principal, id));
    }

    @PostMapping("/{id}/offline")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovCatalogResource> offline(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(service.offline(principal, id));
    }

    @PostMapping("/approvals/{approvalId}/approve")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovCatalogApproval> approve(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long approvalId,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.approve(principal, approvalId, body == null ? Map.of() : body));
    }

    @PostMapping("/approvals/{approvalId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovCatalogApproval> reject(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long approvalId,
                                                  @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.reject(principal, approvalId, body == null ? Map.of() : body));
    }

    @PostMapping("/approvals/{approvalId}/withdraw")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovCatalogApproval> withdraw(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long approvalId) {
        return ApiResponse.ok(service.withdraw(principal, approvalId));
    }
}
