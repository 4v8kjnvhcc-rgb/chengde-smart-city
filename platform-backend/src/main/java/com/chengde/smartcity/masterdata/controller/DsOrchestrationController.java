package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.service.DsOrchestrationService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DolphinScheduler 真实编排入口：创建/启动汇聚、加工流程实例，查询状态、停止、失败重跑。
 */
@RestController
@RequestMapping("/api/v1/governance/ds")
public class DsOrchestrationController {

    private final DsOrchestrationService service;

    public DsOrchestrationController(DsOrchestrationService service) {
        this.service = service;
    }

    @PostMapping("/orchestrate/collect")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> orchestrateCollect(@AuthenticationPrincipal UserPrincipal principal,
                                                               @RequestBody Map<String, Object> body) {
        Long tableId = body.get("tableId") == null ? null : Long.valueOf(String.valueOf(body.get("tableId")));
        return ApiResponse.ok(service.orchestrateCollect(principal, tableId));
    }

    @PostMapping("/orchestrate/fusion")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> orchestrateFusion(@AuthenticationPrincipal UserPrincipal principal,
                                                              @RequestBody Map<String, Object> body) {
        Long tableId = body.get("tableId") == null ? null : Long.valueOf(String.valueOf(body.get("tableId")));
        String targetTable = body.get("targetTable") == null ? "dws_target" : String.valueOf(body.get("targetTable"));
        return ApiResponse.ok(service.orchestrateFusion(principal, tableId, targetTable));
    }

    @GetMapping("/instance/status")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> status(@RequestParam Long projectCode, @RequestParam Long instanceId) {
        return ApiResponse.ok(service.status(projectCode, instanceId));
    }

    @PostMapping("/instance/stop")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> stop(@AuthenticationPrincipal UserPrincipal principal,
                                                 @RequestBody Map<String, Object> body) {
        Long projectCode = Long.valueOf(String.valueOf(body.get("projectCode")));
        Long instanceId = Long.valueOf(String.valueOf(body.get("instanceId")));
        return ApiResponse.ok(service.stop(principal, projectCode, instanceId));
    }

    @PostMapping("/instance/retry")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> retry(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestBody Map<String, Object> body) {
        Long projectCode = Long.valueOf(String.valueOf(body.get("projectCode")));
        Long instanceId = Long.valueOf(String.valueOf(body.get("instanceId")));
        return ApiResponse.ok(service.retry(principal, projectCode, instanceId));
    }
}
