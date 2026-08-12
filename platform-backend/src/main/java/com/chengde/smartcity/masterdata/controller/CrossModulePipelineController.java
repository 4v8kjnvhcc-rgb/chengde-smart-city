package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.service.CrossModulePipelineMonitorService;
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

@RestController
@RequestMapping("/api/v1/governance/cross-pipelines")
public class CrossModulePipelineController {

    private final CrossModulePipelineService service;
    private final CrossModulePipelineMonitorService monitorService;

    public CrossModulePipelineController(CrossModulePipelineService service,
                                         CrossModulePipelineMonitorService monitorService) {
        this.service = service;
        this.monitorService = monitorService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String publishStatus,
            @RequestParam(required = false) String scheduleStatus) {
        return ApiResponse.ok(service.list(keyword, publishStatus, scheduleStatus));
    }

    @GetMapping("/monitor/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> monitorOverview() {
        return ApiResponse.ok(monitorService.todayOverview());
    }

    @GetMapping("/monitor/instances")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> monitorInstances(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String stateType,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(monitorService.listInstances(keyword, stateType, priority, pageNo, pageSize));
    }

    @GetMapping("/monitor/pipelines")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> monitorPipelines() {
        return ApiResponse.ok(monitorService.listPipelinesForPriority());
    }

    @PutMapping("/monitor/pipelines/{id}/priority")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> setPriority(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        monitorService.setPriority(principal, id, body == null ? null : String.valueOf(body.get("priority")));
        return ApiResponse.ok(null);
    }

    @GetMapping("/monitor/instances/{instanceId}/logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> instanceLogs(
            @PathVariable Long instanceId,
            @RequestParam(required = false) Long projectCode,
            @RequestParam(defaultValue = "PROCESS") String logType) {
        return ApiResponse.ok(monitorService.instanceLogs(projectCode, instanceId, logType));
    }

    @PostMapping("/monitor/instances/{instanceId}/control")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> controlInstance(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long instanceId,
            @RequestBody Map<String, Object> body) {
        Long projectCode = body == null || body.get("projectCode") == null
                ? null : Long.valueOf(String.valueOf(body.get("projectCode")));
        String action = body == null ? null : String.valueOf(body.get("action"));
        return ApiResponse.ok(monitorService.control(principal, projectCode, instanceId, action));
    }

    @GetMapping("/monitor/alert/channel")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> alertChannel() {
        return ApiResponse.ok(monitorService.getAlertChannel());
    }

    @PutMapping("/monitor/alert/channel")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> saveAlertChannel(@AuthenticationPrincipal UserPrincipal principal,
                                              @RequestBody Map<String, Object> body) {
        monitorService.saveAlertChannel(principal, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/monitor/alert/notify")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> notifyAlert(@AuthenticationPrincipal UserPrincipal principal,
                                                        @RequestBody Map<String, Object> body) {
        Long pipelineId = body == null || body.get("pipelineId") == null
                ? null : Long.valueOf(String.valueOf(body.get("pipelineId")));
        Long instanceId = body == null || body.get("instanceId") == null
                ? null : Long.valueOf(String.valueOf(body.get("instanceId")));
        String state = body == null || body.get("state") == null ? null : String.valueOf(body.get("state"));
        return ApiResponse.ok(monitorService.notifyInstance(principal, pipelineId, instanceId, state));
    }

    @GetMapping("/monitor/alert/logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> alertLogs(
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) Long pipelineId) {
        return ApiResponse.ok(monitorService.listAlertLogs(instanceId, pipelineId));
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
