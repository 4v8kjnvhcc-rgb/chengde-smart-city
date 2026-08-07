package com.chengde.smartcity.system.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.service.AccessControlService;
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
@RequestMapping("/api/v1/system/access")
public class AccessControlController {

    private final AccessControlService accessControlService;

    public AccessControlController(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    @GetMapping("/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> overview(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(accessControlService.overview(principal));
    }

    @GetMapping("/my-effective")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> myEffective(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(accessControlService.myEffective(principal));
    }

    @GetMapping("/project-grants")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> projectGrants(@AuthenticationPrincipal UserPrincipal principal,
                                                                @RequestParam(required = false) Long projectId,
                                                                @RequestParam(required = false) Long granteeUserId) {
        return ApiResponse.ok(accessControlService.listProjectGrants(principal, projectId, granteeUserId));
    }

    @GetMapping("/users-for-project-grant")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> usersForProjectGrant(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.ok(accessControlService.listUsersForProjectGrant(principal, projectId));
    }

    @PostMapping("/project-grants")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createProjectGrant(@AuthenticationPrincipal UserPrincipal principal,
                                                @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(accessControlService.createProjectGrant(principal, body));
    }

    @DeleteMapping("/project-grants/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteProjectGrant(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long id) {
        accessControlService.deleteProjectGrant(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/data-grants")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> dataGrants(@AuthenticationPrincipal UserPrincipal principal,
                                                             @RequestParam(required = false) String scopeType,
                                                             @RequestParam(required = false) Long scopeId) {
        return ApiResponse.ok(accessControlService.listDataGrants(principal, scopeType, scopeId));
    }

    @PostMapping("/data-grants")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createDataGrant(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(accessControlService.createDataGrant(principal, body));
    }

    @DeleteMapping("/data-grants/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteDataGrant(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id) {
        accessControlService.deleteDataGrant(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/cross-dept/requests")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> crossDeptList(@AuthenticationPrincipal UserPrincipal principal,
                                                                @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(accessControlService.listCrossDept(principal, status));
    }

    @PostMapping("/cross-dept/requests")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> applyCrossDept(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(accessControlService.applyCrossDept(principal, body));
    }

    @PostMapping("/cross-dept/requests/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> approve(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable Long id,
                                     @RequestBody(required = false) Map<String, Object> body) {
        String comment = body == null ? null : (body.get("comment") == null ? null : String.valueOf(body.get("comment")));
        accessControlService.approveCrossDept(principal, id, true, comment);
        return ApiResponse.ok(null);
    }

    @PostMapping("/cross-dept/requests/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> reject(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestBody(required = false) Map<String, Object> body) {
        String comment = body == null ? null : (body.get("comment") == null ? null : String.valueOf(body.get("comment")));
        accessControlService.approveCrossDept(principal, id, false, comment);
        return ApiResponse.ok(null);
    }
}
