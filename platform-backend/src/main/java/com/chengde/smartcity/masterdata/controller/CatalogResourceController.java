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
    public ApiResponse<List<GovCatalogResource>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestParam(required = false) Long categoryId,
                                                      @RequestParam(required = false) String resourceType,
                                                      @RequestParam(required = false) String publishStatus,
                                                      @RequestParam(required = false) String approvalStatus,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String sourcePathType,
                                                      @RequestParam(required = false) String providerOrg,
                                                      @RequestParam(required = false) Boolean unboundOnly,
                                                      @RequestParam(required = false) String catalogOrigin,
                                                      @RequestParam(required = false) String shareType,
                                                      @RequestParam(required = false) String resourceFormat,
                                                      @RequestParam(required = false) Boolean excludeApprovalDraft,
                                                      @RequestParam(required = false) Boolean forPortal) {
        return ApiResponse.ok(service.list(categoryId, resourceType, publishStatus, approvalStatus, keyword,
                sourcePathType, providerOrg, unboundOnly, catalogOrigin, shareType, excludeApprovalDraft, resourceFormat,
                principal, Boolean.TRUE.equals(forPortal)));
    }

    @GetMapping("/eligible-metadata")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> eligibleMetadata(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listEligibleMetadata(keyword));
    }

    @GetMapping("/bind-sources")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> bindSources(
            @RequestParam(required = false) String categoryKey,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "ING") String sourceKind) {
        return ApiResponse.ok(service.listBindSources(categoryKey, categoryId, keyword, sourceKind));
    }

    @GetMapping("/bind-sources/{sourceId}/tables")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> bindTables(@PathVariable Long sourceId,
                                                              @RequestParam(required = false, defaultValue = "ING") String sourceKind) {
        return ApiResponse.ok(service.listBindTables(sourceId, sourceKind));
    }

    @GetMapping("/bind-sources/{sourceId}/table-columns")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> bindTableColumns(@PathVariable Long sourceId,
                                                             @RequestParam String tableName,
                                                             @RequestParam(required = false, defaultValue = "ING") String sourceKind) {
        return ApiResponse.ok(service.describeBindTable(sourceId, tableName, sourceKind));
    }

    @GetMapping("/approvals")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> approvals(@AuthenticationPrincipal UserPrincipal principal,
                                                            @RequestParam(required = false) Long resourceId,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String catalogOrigin,
                                                            @RequestParam(required = false) String scope) {
        return ApiResponse.ok(service.listApprovals(resourceId, status, catalogOrigin, scope, principal));
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
    public ApiResponse<GovCatalogResource> get(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long id) {
        return ApiResponse.ok(service.get(principal, id));
    }

    @GetMapping("/{id}/approvals")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> resourceApprovals(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable Long id) {
        return ApiResponse.ok(service.listApprovals(id, null, null, null, principal));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> create(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(principal, body));
    }

    /** 批量新增：从已登记库表/元数据抽取核心元数据生成资源目录 */
    @PostMapping("/batch-from-metadata")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> batchFromMetadata(@AuthenticationPrincipal UserPrincipal principal,
                                                              @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.batchCreateFromMetadata(principal, body));
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

    @PostMapping("/bind-category")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> bindCategory(@AuthenticationPrincipal UserPrincipal principal,
                                                         @RequestBody Map<String, Object> body) {
        Long categoryId = body.get("categoryId") == null ? null : Long.valueOf(String.valueOf(body.get("categoryId")));
        Object raw = body.get("resourceIds");
        if (!(raw instanceof List<?> list)) {
            return ApiResponse.fail(400, "resourceIds 须为数组");
        }
        List<Long> ids = list.stream().map(v -> Long.valueOf(String.valueOf(v))).toList();
        return ApiResponse.ok(service.bindCategory(principal, categoryId, ids));
    }

    @PostMapping("/unbind-category")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> unbindCategory(@AuthenticationPrincipal UserPrincipal principal,
                                                           @RequestBody Map<String, Object> body) {
        Object raw = body.get("resourceIds");
        if (!(raw instanceof List<?> list)) {
            return ApiResponse.fail(400, "resourceIds 须为数组");
        }
        List<Long> ids = list.stream().map(v -> Long.valueOf(String.valueOf(v))).toList();
        return ApiResponse.ok(service.unbindCategory(principal, ids));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovCatalogApproval> submit(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.submit(principal, id, body == null ? Map.of() : body));
    }

    /** 编目「提交」：草稿进入注册发布可见；不发起审批 */
    @PostMapping("/{id}/submit-register")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovCatalogResource> submitToRegister(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable Long id) {
        return ApiResponse.ok(service.submitToRegister(principal, id));
    }

    /** 编目「撤回」：待发布退回草稿 */
    @PostMapping("/{id}/withdraw-register")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovCatalogResource> withdrawFromRegister(@AuthenticationPrincipal UserPrincipal principal,
                                                                @PathVariable Long id) {
        return ApiResponse.ok(service.withdrawFromRegister(principal, id));
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

    /** 平台/系统管理员：已发布目录即时下线，回到编目管理 */
    @PostMapping("/{id}/admin-offline")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovCatalogResource> adminOffline(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable Long id,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.adminForceOffline(principal, id, body == null ? Map.of() : body));
    }

    /** 平台/系统管理员：即时删除目录 */
    @PostMapping("/{id}/admin-delete")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> adminDelete(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable Long id,
                                         @RequestBody(required = false) Map<String, Object> body) {
        service.adminForceDelete(principal, id, body == null ? Map.of() : body);
        return ApiResponse.ok(null);
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

    @PostMapping("/approvals/batch-approve")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> batchApprove(@AuthenticationPrincipal UserPrincipal principal,
                                                         @RequestBody Map<String, Object> body) {
        Object raw = body.get("ids");
        if (!(raw instanceof List<?> list)) {
            return ApiResponse.fail(400, "ids 须为数组");
        }
        List<Long> ids = list.stream().map(v -> Long.valueOf(String.valueOf(v))).toList();
        return ApiResponse.ok(service.batchApprove(principal, ids, body));
    }

    @PostMapping("/approvals/batch-reject")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> batchReject(@AuthenticationPrincipal UserPrincipal principal,
                                                        @RequestBody Map<String, Object> body) {
        Object raw = body.get("ids");
        if (!(raw instanceof List<?> list)) {
            return ApiResponse.fail(400, "ids 须为数组");
        }
        List<Long> ids = list.stream().map(v -> Long.valueOf(String.valueOf(v))).toList();
        return ApiResponse.ok(service.batchReject(principal, ids, body));
    }
}
