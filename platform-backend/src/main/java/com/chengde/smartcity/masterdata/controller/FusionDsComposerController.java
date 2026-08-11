package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.service.FusionDsComposerService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
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
 * 数据融合处理 · 工作流调度1：选已有任务并发布到 DolphinScheduler。
 */
@RestController
@RequestMapping("/api/v1/governance/fusion/ds-composer")
public class FusionDsComposerController {

    private final FusionDsComposerService service;

    public FusionDsComposerController(FusionDsComposerService service) {
        this.service = service;
    }

    @GetMapping("/meta")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> meta() {
        return ApiResponse.ok(service.meta());
    }

    @GetMapping("/task-options")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> taskOptions(@RequestParam String domain) {
        return ApiResponse.ok(service.taskOptions(domain));
    }

    @GetMapping("/preview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> preview(@RequestParam String domain,
                                                    @RequestParam Long taskId) {
        return ApiResponse.ok(service.preview(domain, taskId));
    }

    @PostMapping("/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> publish(@AuthenticationPrincipal UserPrincipal principal,
                                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.publish(principal, body));
    }

    @PostMapping("/stop")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> stop(@AuthenticationPrincipal UserPrincipal principal,
                                                 @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.stop(principal, body));
    }
}
