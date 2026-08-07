package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.BizCatalogObjection;
import com.chengde.smartcity.exchange.entity.BizDataDemand;
import com.chengde.smartcity.exchange.entity.BizDemandSupplyTask;
import com.chengde.smartcity.exchange.entity.BizDemandTemplate;
import com.chengde.smartcity.exchange.entity.BizSupplyManifest;
import com.chengde.smartcity.exchange.service.SupplyDemandService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exchange/supply")
public class SupplyDemandController {

    private final SupplyDemandService service;

    public SupplyDemandController(SupplyDemandService service) {
        this.service = service;
    }

    @GetMapping("/templates")
    public ApiResponse<List<BizDemandTemplate>> templates(@RequestParam(required = false, defaultValue = "active") String scope) {
        if ("all".equalsIgnoreCase(scope)) {
            return ApiResponse.ok(service.listTemplates());
        }
        return ApiResponse.ok(service.listActiveTemplates());
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('system:exchange:supply-config')")
    public ApiResponse<Long> createTemplate(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createTemplate(principal, body));
    }

    @PostMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('system:exchange:supply-config')")
    public ApiResponse<Void> updateTemplate(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        service.updateTemplate(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/templates/{id}/delete")
    @PreAuthorize("hasAuthority('system:exchange:supply-config')")
    public ApiResponse<Void> deleteTemplate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.deleteTemplate(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/duties")
    public ApiResponse<List<com.chengde.smartcity.exchange.entity.BizDataDuty>> duties(
            @RequestParam(required = false) Long demandId) {
        return ApiResponse.ok(service.listDuties(demandId));
    }

    @GetMapping("/demands")
    public ApiResponse<List<BizDataDemand>> demands(@RequestParam(required = false) String stage,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listDemands(stage, status));
    }

    @PostMapping("/demands")
    @PreAuthorize("hasAuthority('portal:supply:create') or hasAuthority('portal:supply:approve') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Long> createDemand(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createDemand(principal, body));
    }

    @PostMapping("/demands/{id}/withdraw")
    @PreAuthorize("hasAuthority('portal:supply:create') or hasAuthority('portal:supply:approve') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.withdrawDemand(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/submit")
    @PreAuthorize("hasAuthority('portal:supply:create') or hasAuthority('portal:supply:approve') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> submitExisting(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.submitDemand(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/delete")
    @PreAuthorize("hasAuthority('portal:supply:create') or hasAuthority('portal:supply:approve') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> deleteDemand(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.deleteDemand(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/matters")
    public ApiResponse<List<com.chengde.smartcity.exchange.entity.BizGovMatter>> matters(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String matterType,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listMatters(keyword, matterType, status));
    }

    @PostMapping("/matters")
    @PreAuthorize("hasAuthority('portal:supply:approve') or hasAuthority('system:exchange:supply-config') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Long> createMatter(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createMatter(principal, body));
    }

    @PostMapping("/matters/{id}")
    @PreAuthorize("hasAuthority('portal:supply:approve') or hasAuthority('system:exchange:supply-config') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> updateMatter(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        service.updateMatter(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/matters/{id}/delete")
    @PreAuthorize("hasAuthority('portal:supply:approve') or hasAuthority('system:exchange:supply-config') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> deleteMatter(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.deleteMatter(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/analyze")
    @PreAuthorize("hasAuthority('portal:supply:approve') or hasAuthority('system:exchange:supply-config') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> analyze(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.analyzeDemand(principal, id, body == null ? Map.of() : body));
    }

    @PostMapping("/demands/{id}/dispatch")
    @PreAuthorize("hasAuthority('portal:supply:approve') or hasAuthority('system:exchange:supply-config') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> dispatch(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, Object> body) {
        service.dispatchDemand(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/return")
    @PreAuthorize("hasAuthority('portal:supply:approve') or hasAuthority('system:exchange:supply-config') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> returnDemand(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        service.returnDemand(principal, id, body);
        return ApiResponse.ok(null);
    }

    /** 高匹配：退回需求部门走门户申请 */
    @PostMapping("/demands/{id}/return-portal")
    @PreAuthorize("hasAuthority('portal:supply:approve') or hasAuthority('system:exchange:supply-config') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> returnPortal(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody(required = false) Map<String, Object> body) {
        service.returnToPortalApply(principal, id, body == null ? Map.of() : body);
        return ApiResponse.ok(null);
    }

    /** 管理员同意提供方退回 → 退回需求部门 */
    @PostMapping("/demands/{id}/admin-agree-return")
    @PreAuthorize("hasAuthority('portal:supply:approve') or hasAuthority('system:exchange:supply-config') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> adminAgreeReturn(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable Long id,
                                              @RequestBody(required = false) Map<String, Object> body) {
        service.adminAgreeProviderReturn(principal, id, body == null ? Map.of() : body);
        return ApiResponse.ok(null);
    }

    /** 管理员拒绝提供方退回 → 打回提供部门再确认 */
    @PostMapping("/demands/{id}/admin-refuse-return")
    @PreAuthorize("hasAuthority('portal:supply:approve') or hasAuthority('system:exchange:supply-config') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> adminRefuseReturn(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long id,
                                               @RequestBody(required = false) Map<String, Object> body) {
        service.adminRefuseProviderReturn(principal, id, body == null ? Map.of() : body);
        return ApiResponse.ok(null);
    }

    /** 提供部门：标记目录已挂载门户 */
    @PostMapping("/demands/{id}/mark-mounted")
    @PreAuthorize("hasAuthority('portal:supply:create') or hasAuthority('portal:supply:approve') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> markMounted(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable Long id,
                                         @RequestBody(required = false) Map<String, Object> body) {
        service.markCatalogMounted(principal, id, body == null ? Map.of() : body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/supervise")
    @PreAuthorize("hasAuthority('portal:supply:approve') or hasAuthority('system:exchange:supply-config') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> supervise(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        service.superviseDemand(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/analysis-settings")
    @PreAuthorize("hasAuthority('portal:supply:approve') or hasAuthority('system:exchange:supply-config') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> analysisSettings(@AuthenticationPrincipal UserPrincipal principal,
                                                             @PathVariable Long id,
                                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.applyAnalysisSettings(principal, id, body));
    }

    @GetMapping("/resource-search")
    public ApiResponse<Map<String, Object>> resourceSearch(@RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String resourceType,
                                                           @RequestParam(required = false) String providerOrg) {
        return ApiResponse.ok(service.searchResources(keyword, resourceType, providerOrg));
    }

    @PostMapping("/demands/{id}/confirm")
    @PreAuthorize("hasAuthority('portal:supply:create') or hasAuthority('portal:supply:approve') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> confirm(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.confirmDemand(principal, id, body));
    }

    @PostMapping("/demands/{id}/confirm-return")
    @PreAuthorize("hasAuthority('portal:supply:create') or hasAuthority('portal:supply:approve') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> confirmReturn(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        service.confirmReturnDemand(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/confirm-feedback")
    @PreAuthorize("hasAuthority('portal:supply:create') or hasAuthority('portal:supply:approve') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> confirmFeedback(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        service.confirmFeedback(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/complete")
    @PreAuthorize("hasAuthority('portal:supply:create') or hasAuthority('portal:supply:approve') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> complete(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable Long id,
                                      @RequestBody(required = false) Map<String, Object> body) {
        service.completeDemand(principal, id, body == null ? Map.of() : body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/cancel")
    @PreAuthorize("hasAuthority('portal:supply:create') or hasAuthority('portal:supply:approve') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> cancel(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestBody(required = false) Map<String, Object> body) {
        service.cancelDemand(principal, id, body == null ? Map.of() : body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/update")
    @PreAuthorize("hasAuthority('portal:supply:create') or hasAuthority('portal:supply:approve') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> update(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        service.updateDemand(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/reject")
    @PreAuthorize("hasAuthority('portal:supply:create') or hasAuthority('portal:supply:approve') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> reject(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        service.rejectDemand(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/supervise-settings")
    @PreAuthorize("hasAuthority('portal:supply:approve') or hasAuthority('system:exchange:supply-config') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> getSuperviseSettings() {
        return ApiResponse.ok(service.getSuperviseSettings());
    }

    @PutMapping("/supervise-settings")
    @PreAuthorize("hasAuthority('portal:supply:approve') or hasAuthority('system:exchange:supply-config') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> saveSuperviseSettings(@AuthenticationPrincipal UserPrincipal principal,
                                                                  @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveSuperviseSettings(principal, body));
    }

    @GetMapping("/supply-tasks")
    public ApiResponse<List<BizDemandSupplyTask>> supplyTasks(@RequestParam(required = false) Long demandId) {
        return ApiResponse.ok(service.listSupplyTasks(demandId));
    }

    @GetMapping("/supply-view/{demandId}")
    public ApiResponse<Map<String, Object>> supplyView(@PathVariable Long demandId) {
        return ApiResponse.ok(service.supplyView(demandId));
    }

    @GetMapping("/list-center")
    public ApiResponse<Map<String, Object>> listCenter(@RequestParam(required = false, defaultValue = "dept-catalog") String listType) {
        return ApiResponse.ok(service.listCenter(listType));
    }

    @GetMapping("/catalog-manifest")
    public ApiResponse<List<BizCatalogItem>> catalogManifest(
            @RequestParam(required = false, defaultValue = "all") String scope) {
        if ("published".equalsIgnoreCase(scope)) {
            return ApiResponse.ok(service.publishedCatalogs());
        }
        return ApiResponse.ok(service.catalogManifest());
    }

    @PostMapping("/catalog")
    public ApiResponse<Long> createCatalog(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createCatalog(principal, body));
    }

    @PostMapping("/catalog/{id}/publish")
    public ApiResponse<Void> publishCatalog(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.publishCatalog(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/catalog/{id}/offline")
    public ApiResponse<Void> offlineCatalog(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body) {
        service.offlineCatalog(principal, id, body == null ? Map.of() : body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/catalog-manifest/export")
    public ApiResponse<Map<String, Object>> exportCatalogManifest() {
        return ApiResponse.ok(service.exportCatalogManifest());
    }

    @GetMapping("/objections")
    public ApiResponse<List<BizCatalogObjection>> objections(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listObjections(status));
    }

    @PostMapping("/objections")
    public ApiResponse<Long> createObjection(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createObjection(principal, body));
    }

    @PostMapping("/objections/{id}/process")
    public ApiResponse<Void> processObjection(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        service.processObjection(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/manifests")
    public ApiResponse<List<BizSupplyManifest>> manifests(@RequestParam(required = false) String manifestType) {
        return ApiResponse.ok(service.listManifests(manifestType));
    }

    @PostMapping("/manifests/{id}/export")
    public ApiResponse<Map<String, Object>> exportManifest(@PathVariable Long id) {
        return ApiResponse.ok(service.exportManifest(id));
    }
}
