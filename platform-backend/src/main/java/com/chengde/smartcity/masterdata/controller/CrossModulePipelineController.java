package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.service.CrossModulePipelineService;
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
 * 跨模块流水线：归集/治理/质量/融合步骤可增删、可调序。
 */
@RestController
@RequestMapping("/api/v1/governance/cross-pipelines")
public class CrossModulePipelineController {

    private final CrossModulePipelineService service;

    public CrossModulePipelineController(CrossModulePipelineService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String publishStatus,
            @RequestParam(required = false) String scheduleStatus) {
        return ApiResponse.ok(service.list(keyword, publishStatus, scheduleStatus));
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

    @PostMapping("/purge-ds-orphans")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> purgeDsOrphans(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.purgeDsOrphans(principal));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> publish(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id) {
        return ApiResponse.ok(service.publish(principal, id));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> start(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long id) {
        return ApiResponse.ok(service.start(principal, id));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> stop(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable Long id) {
        return ApiResponse.ok(service.stop(principal, id));
    }

    @PostMapping("/{id}/run-once")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> runOnce(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id) {
        return ApiResponse.ok(service.runOnce(principal, id));
    }
}
