package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.service.KettleExecuteService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/governance/kettle")
public class KettleExecuteController {

    private final KettleExecuteService executeService;

    public KettleExecuteController(KettleExecuteService executeService) {
        this.executeService = executeService;
    }

    @GetMapping("/health")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> healthCheck() {
        return ApiResponse.ok(executeService.healthCheck());
    }

    @PostMapping("/tasks/{taskId}/execute")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> executeTask(@PathVariable Long taskId,
                                                        @RequestBody(required = false) Map<String, String> params) {
        return ApiResponse.ok(executeService.executeTask(taskId, params));
    }

    @PostMapping("/tasks/{taskId}/stop")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> stopTask(@PathVariable Long taskId) {
        return ApiResponse.ok(executeService.stopTask(taskId));
    }

    @GetMapping("/runs/{runId}/status")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getStatus(@PathVariable Long runId) {
        return ApiResponse.ok(executeService.updateExecutionStatus(runId));
    }

    @GetMapping("/runs/{runId}/log")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getLog(@PathVariable Long runId) {
        return ApiResponse.ok(executeService.getExecutionLog(runId));
    }

    @DeleteMapping("/runs/{runId}/cleanup")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> cleanup(@PathVariable Long runId) {
        return ApiResponse.ok(executeService.cleanupExecution(runId));
    }
}
