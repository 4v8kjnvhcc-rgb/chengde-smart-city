package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovGovernanceNodeLog;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTaskRun;
import com.chengde.smartcity.masterdata.service.GovernanceTaskService;
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
 * 治理 ETL 任务编排（画布）。路径用 gov-tasks，避免与质量 tasks 冲突。
 */
@RestController
@RequestMapping("/api/v1/governance/gov-tasks")
public class GovernanceTaskController {

    private final GovernanceTaskService service;

    public GovernanceTaskController(GovernanceTaskService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/runs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovGovernanceTaskRun>> runs(@RequestParam(required = false) Long taskId) {
        return ApiResponse.ok(service.listRuns(taskId));
    }

    @GetMapping("/runs/{runId}/node-logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovGovernanceNodeLog>> nodeLogs(@PathVariable Long runId) {
        return ApiResponse.ok(service.listNodeLogs(runId));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> create(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(principal, body));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
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

    @PostMapping("/batch-delete")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> batchDelete(@AuthenticationPrincipal UserPrincipal principal,
                                                        @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = body.get("ids") instanceof List<?> list ? (List<Object>) list : List.of();
        List<Long> ids = raw.stream()
                .map(v -> Long.valueOf(String.valueOf(v)))
                .toList();
        int n = service.batchDelete(principal, ids);
        return ApiResponse.ok(Map.of("deleted", n));
    }

    @GetMapping("/{id}/graph")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getGraph(@PathVariable Long id) {
        return ApiResponse.ok(service.getGraph(id));
    }

    @PutMapping("/{id}/graph")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> saveGraph(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        service.saveGraph(principal, id, body);
        return ApiResponse.ok(null);
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

    @PostMapping("/{id}/rename")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> rename(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.rename(principal, id, body));
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> run(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long id,
                                                @RequestBody(required = false) Map<String, Object> body) {
        Map<String, String> vars = null;
        if (body != null && body.get("variables") instanceof Map<?, ?> m) {
            vars = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                vars.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            }
        }
        return ApiResponse.ok(service.run(principal, id, vars));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> stop(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable Long id) {
        return ApiResponse.ok(service.stop(principal, id));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> publish(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.publish(principal, id, body));
    }

    @PutMapping("/{id}/schedule")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> schedule(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable Long id,
                                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.updateSchedule(principal, id, body));
    }
}
