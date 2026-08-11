package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskDetail;
import com.chengde.smartcity.masterdata.service.QualityTaskService;
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
 * 质量任务配置与运行管理。
 * 与 {@link MasterDataDemoController} 的 {@code /governance/quality/tasks} 并存，使用 task-mgmt 前缀。
 */
@RestController
@RequestMapping("/api/v1/governance/quality/task-mgmt")
public class QualityTaskController {

    private final QualityTaskService service;

    public QualityTaskController(QualityTaskService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(service.stats());
    }

    @GetMapping("/runs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> runs(@RequestParam(required = false) Long taskId) {
        return ApiResponse.ok(service.listRunViews(taskId));
    }

    @GetMapping("/runs/{runId}/issues")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> issues(@PathVariable Long runId) {
        return ApiResponse.ok(service.listIssues(runId));
    }

    @PostMapping("/runs/{runId}/rerun")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> rerun(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long runId) {
        return ApiResponse.ok(service.rerun(principal, runId));
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

    @GetMapping("/{id}/details")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovQualityTaskDetail>> details(@PathVariable Long id) {
        return ApiResponse.ok(service.listDetails(id));
    }

    @PostMapping("/{id}/details")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovQualityTaskDetail> addDetail(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.addDetail(principal, id, body));
    }

    @DeleteMapping("/{id}/details/{detailId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteDetail(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @PathVariable Long detailId) {
        service.deleteDetail(principal, id, detailId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> run(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long id) {
        return ApiResponse.ok(service.run(principal, id));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> stop(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable Long id) {
        return ApiResponse.ok(service.stop(principal, id));
    }
}
