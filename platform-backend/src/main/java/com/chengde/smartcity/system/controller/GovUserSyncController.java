package com.chengde.smartcity.system.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysGovSyncTarget;
import com.chengde.smartcity.system.service.GovUserSyncService;
import com.chengde.smartcity.system.service.SecurityConfigService;
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
@RequestMapping("/api/v1/system/gov-sync")
public class GovUserSyncController {

    private final GovUserSyncService govUserSyncService;
    private final SecurityConfigService securityConfigService;

    public GovUserSyncController(GovUserSyncService govUserSyncService,
                                 SecurityConfigService securityConfigService) {
        this.govUserSyncService = govUserSyncService;
        this.securityConfigService = securityConfigService;
    }

    @GetMapping("/auth-methods")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config') or hasAuthority('system:uum:view') or hasAuthority('hub:analytics:support:sys:cfg:appearance')")
    public ApiResponse<Map<String, Object>> authMethods() {
        return ApiResponse.ok(govUserSyncService.authMethodsView(securityConfigService));
    }

    @PutMapping("/auth-methods")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config') or hasAuthority('system:uum:view')")
    public ApiResponse<Void> saveAuthMethods(@RequestBody Map<String, Object> body) {
        govUserSyncService.saveAuthMethods(securityConfigService, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/targets")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config') or hasAuthority('system:uum:view') or hasAuthority('system:uum:integration') or hasAuthority('hub:analytics:support:sys:cfg:appearance')")
    public ApiResponse<List<SysGovSyncTarget>> targets() {
        return ApiResponse.ok(govUserSyncService.listTargets());
    }

    @PostMapping("/targets")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config') or hasAuthority('system:uum:integration')")
    public ApiResponse<Long> createTarget(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(govUserSyncService.saveTarget(principal, body, null));
    }

    @PutMapping("/targets/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config') or hasAuthority('system:uum:integration')")
    public ApiResponse<Void> updateTarget(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        govUserSyncService.saveTarget(principal, body, id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/targets/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config') or hasAuthority('system:uum:integration')")
    public ApiResponse<Void> deleteTarget(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id) {
        govUserSyncService.deleteTarget(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config') or hasAuthority('system:uum:view') or hasAuthority('system:uum:integration') or hasAuthority('hub:analytics:support:sys:cfg:appearance')")
    public ApiResponse<List<Map<String, Object>>> jobs(@RequestParam(required = false) Long targetId) {
        return ApiResponse.ok(govUserSyncService.listJobs(targetId));
    }

    @PostMapping("/run")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config') or hasAuthority('system:uum:integration')")
    public ApiResponse<Map<String, Object>> run(@AuthenticationPrincipal UserPrincipal principal,
                                                @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(govUserSyncService.runSync(principal, body));
    }
}
