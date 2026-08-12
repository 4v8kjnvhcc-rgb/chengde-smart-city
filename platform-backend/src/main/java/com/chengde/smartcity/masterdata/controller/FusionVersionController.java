package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.service.FusionVersionService;
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
@RequestMapping("/api/v1/governance/fusion/versions")
public class FusionVersionController {

    private final FusionVersionService service;

    public FusionVersionController(FusionVersionService service) {
        this.service = service;
    }

    @GetMapping("/workflows")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> listWorkflows(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String envScope,
            @RequestParam(required = false) String objectType) {
        return ApiResponse.ok(service.listWorkflows(keyword, envScope, objectType));
    }

    @PostMapping("/workflows/{objectType}/{id}/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> publish(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable String objectType,
                                                    @PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.publish(principal, objectType, id, body == null ? Map.of() : body));
    }

    @GetMapping("/workflows/{objectType}/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> versions(@PathVariable String objectType,
                                                           @PathVariable Long id) {
        return ApiResponse.ok(service.listVersions(objectType, id));
    }

    @PostMapping("/workflows/{objectType}/{id}/rollback/{versionNo}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> rollback(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable String objectType,
                                                     @PathVariable Long id,
                                                     @PathVariable Integer versionNo) {
        return ApiResponse.ok(service.rollback(principal, objectType, id, versionNo));
    }

    @PostMapping("/workflows/{objectType}/{id}/lock")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> lock(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable String objectType,
                                                 @PathVariable Long id) {
        return ApiResponse.ok(service.lock(principal, objectType, id));
    }

    @PostMapping("/workflows/{objectType}/{id}/unlock")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> unlock(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable String objectType,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(service.unlock(principal, objectType, id));
    }

    @PutMapping("/workflows/{objectType}/{id}/env")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> setEnv(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable String objectType,
                                                   @PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        String env = body == null || body.get("envScope") == null ? null : String.valueOf(body.get("envScope"));
        return ApiResponse.ok(service.setEnv(principal, objectType, id, env));
    }

    @PostMapping("/workflows/{objectType}/{id}/deploy-prod")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> deployProd(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable String objectType,
                                                       @PathVariable Long id,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.deployToProduction(principal, objectType, id, body));
    }
}
