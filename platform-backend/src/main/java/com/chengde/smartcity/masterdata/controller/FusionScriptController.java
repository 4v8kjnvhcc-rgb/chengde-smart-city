package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovFusionScriptVersion;
import com.chengde.smartcity.masterdata.service.FusionScriptService;
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

@RestController
@RequestMapping("/api/v1/governance/fusion/scripts")
public class FusionScriptController {

    private final FusionScriptService service;

    public FusionScriptController(FusionScriptService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/runs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> runs(@RequestParam(required = false) Long scriptId) {
        return ApiResponse.ok(service.listRuns(scriptId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
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

    @PostMapping("/{id}/execute")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> execute(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id) {
        return ApiResponse.ok(service.execute(principal, id));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> publish(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.publish(principal, id, body == null ? Map.of() : body));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovFusionScriptVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(service.listVersions(id));
    }

    @PostMapping("/{id}/rollback/{versionNo}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> rollback(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable Long id,
                                                     @PathVariable Integer versionNo) {
        return ApiResponse.ok(service.rollback(principal, id, versionNo));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> lock(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable Long id) {
        return ApiResponse.ok(service.lock(principal, id));
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> unlock(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(service.unlock(principal, id));
    }

    @PutMapping("/{id}/env")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> setEnv(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        String env = body == null || body.get("envScope") == null ? null : String.valueOf(body.get("envScope"));
        return ApiResponse.ok(service.setEnv(principal, id, env));
    }

    @PostMapping("/{id}/deploy-prod")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> deployProd(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable Long id,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.deployToProduction(principal, id, body));
    }

    @PostMapping("/{id}/ds-trigger")
    public ApiResponse<Map<String, Object>> dsTrigger(@PathVariable Long id,
                                                      @org.springframework.web.bind.annotation.RequestHeader(
                                                              value = "X-Ds-Callback-Token", required = false) String token) {
        return ApiResponse.ok(service.executeFromDsCallback(id, token));
    }
}
