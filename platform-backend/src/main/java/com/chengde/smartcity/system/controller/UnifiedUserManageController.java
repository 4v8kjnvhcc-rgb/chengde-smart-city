package com.chengde.smartcity.system.controller;

import com.chengde.smartcity.analysis.entity.AnaPlatformApp;
import com.chengde.smartcity.analysis.entity.AnaPlatformIntegration;
import com.chengde.smartcity.analysis.entity.AnaPlatformService;
import com.chengde.smartcity.analysis.service.AnalyticsPlatformService;
import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.service.UnifiedUserManageService;
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

@RestController
@RequestMapping("/api/v1/system/uum")
public class UnifiedUserManageController {

    private final UnifiedUserManageService uumService;
    private final AnalyticsPlatformService analyticsPlatformService;

    public UnifiedUserManageController(UnifiedUserManageService uumService,
                                       AnalyticsPlatformService analyticsPlatformService) {
        this.uumService = uumService;
        this.analyticsPlatformService = analyticsPlatformService;
    }

    @GetMapping("/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(uumService.overview());
    }

    @GetMapping("/apps")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<AnaPlatformApp>> apps() {
        return ApiResponse.ok(uumService.apps());
    }

    @PostMapping("/apps")
    @PreAuthorize("hasAuthority('system:uum:apps') or hasAuthority('system:uum:view') or hasAuthority('system:user:list')")
    public ApiResponse<Long> createApp(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(analyticsPlatformService.createApp(principal, body));
    }

    @GetMapping("/app-grants")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> appGrants(@RequestParam(required = false) Long appId) {
        return ApiResponse.ok(uumService.listAppGrants(appId));
    }

    @PostMapping("/app-grants")
    @PreAuthorize("hasAuthority('system:uum:apps') or hasAuthority('system:uum:view') or hasAuthority('system:user:list')")
    public ApiResponse<Long> createAppGrant(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(uumService.createAppGrant(principal, body));
    }

    @DeleteMapping("/app-grants/{id}")
    @PreAuthorize("hasAuthority('system:uum:apps') or hasAuthority('system:uum:view') or hasAuthority('system:user:list')")
    public ApiResponse<Void> deleteAppGrant(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        uumService.deleteAppGrant(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/auth-configs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> authConfigs() {
        return ApiResponse.ok(uumService.authConfigs());
    }

    @PostMapping("/auth-configs")
    @PreAuthorize("hasAuthority('system:uum:view') or hasAuthority('system:user:list') or hasAuthority('system:dict:edit') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Long> createAuthConfig(@AuthenticationPrincipal UserPrincipal principal,
                                              @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(uumService.createAuthConfig(principal, body));
    }

    @PutMapping("/auth-configs/{id}")
    @PreAuthorize("hasAuthority('system:uum:view') or hasAuthority('system:user:list') or hasAuthority('system:dict:edit') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> updateAuthConfig(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        uumService.updateAuthConfig(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/auth-configs/{id}")
    @PreAuthorize("hasAuthority('system:uum:view') or hasAuthority('system:user:list') or hasAuthority('system:dict:edit') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> deleteAuthConfig(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable Long id) {
        uumService.deleteAuthConfig(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/system-configs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> systemConfigs() {
        return ApiResponse.ok(uumService.systemConfigs());
    }

    @PutMapping("/system-configs/{id}")
    @PreAuthorize("hasAuthority('system:uum:view') or hasAuthority('system:user:list') or hasAuthority('system:dict:edit') or hasAuthority('hub:analytics:support:sys:general') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> updateSystemConfig(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long id,
                                                @RequestBody Map<String, Object> body) {
        uumService.updateAuthConfig(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/services")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<AnaPlatformService>> services() {
        return ApiResponse.ok(uumService.services());
    }

    @PostMapping("/services")
    @PreAuthorize("hasAuthority('system:uum:services') or hasAuthority('system:uum:view') or hasAuthority('system:user:list')")
    public ApiResponse<Long> createService(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(analyticsPlatformService.createService(principal, body));
    }

    @GetMapping("/service-stats")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> serviceStats(@RequestParam(required = false) Long serviceId) {
        return ApiResponse.ok(uumService.listServiceStats(serviceId));
    }

    @GetMapping("/service-approvals")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> serviceApprovals(@RequestParam(required = false) String status) {
        return ApiResponse.ok(uumService.listApprovals(status));
    }

    @PostMapping("/service-approvals")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> applyService(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(uumService.applyService(principal, body));
    }

    @PostMapping("/service-approvals/{id}/approve")
    @PreAuthorize("hasAuthority('system:uum:services') or hasAuthority('system:uum:view') or hasAuthority('system:user:list')")
    public ApiResponse<Void> approve(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable Long id,
                                     @RequestBody(required = false) Map<String, Object> body) {
        String comment = body == null || body.get("comment") == null ? null : String.valueOf(body.get("comment"));
        uumService.decideApproval(principal, id, true, comment);
        return ApiResponse.ok(null);
    }

    @PostMapping("/service-approvals/{id}/reject")
    @PreAuthorize("hasAuthority('system:uum:services') or hasAuthority('system:uum:view') or hasAuthority('system:user:list')")
    public ApiResponse<Void> reject(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestBody(required = false) Map<String, Object> body) {
        String comment = body == null || body.get("comment") == null ? null : String.valueOf(body.get("comment"));
        uumService.decideApproval(principal, id, false, comment);
        return ApiResponse.ok(null);
    }

    @GetMapping("/integrations")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<AnaPlatformIntegration>> integrations(
            @RequestParam(required = false) String integrationCode,
            @RequestParam(required = false) String integrationName,
            @RequestParam(required = false) String targetSystem) {
        return ApiResponse.ok(analyticsPlatformService.listIntegrations(integrationCode, integrationName, targetSystem));
    }

    @PostMapping("/integrations")
    @PreAuthorize("hasAuthority('system:uum:integration') or hasAuthority('system:uum:view') or hasAuthority('system:user:list')")
    public ApiResponse<Long> createIntegration(@AuthenticationPrincipal UserPrincipal principal,
                                               @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(analyticsPlatformService.createIntegration(principal, body));
    }

    @PutMapping("/integrations/{id}")
    @PreAuthorize("hasAuthority('system:uum:integration') or hasAuthority('system:uum:view') or hasAuthority('system:user:list')")
    public ApiResponse<Void> updateIntegration(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long id,
                                               @RequestBody Map<String, Object> body) {
        analyticsPlatformService.updateIntegration(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/integrations/{id}")
    @PreAuthorize("hasAuthority('system:uum:integration') or hasAuthority('system:uum:view') or hasAuthority('system:user:list')")
    public ApiResponse<Void> deleteIntegration(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long id) {
        analyticsPlatformService.deleteIntegration(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/integrations/{id}/test")
    @PreAuthorize("hasAuthority('system:uum:integration') or hasAuthority('system:uum:view') or hasAuthority('system:user:list')")
    public ApiResponse<Map<String, Object>> testIntegration(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable Long id) {
        return ApiResponse.ok(analyticsPlatformService.testIntegration(principal, id));
    }
}
