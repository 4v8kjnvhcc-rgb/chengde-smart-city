package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.service.QualityAlertService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/governance/quality/alerts")
public class QualityAlertController {

    private final QualityAlertService service;

    public QualityAlertController(QualityAlertService service) {
        this.service = service;
    }

    @GetMapping("/channel")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> channel() {
        return ApiResponse.ok(service.getChannel());
    }

    @PutMapping("/channel")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> saveChannel(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, Object> body) {
        service.saveChannel(principal, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> logs(
            @RequestParam(required = false) Long runId,
            @RequestParam(required = false) Long schemeId) {
        return ApiResponse.ok(service.listAlertLogs(runId, schemeId));
    }

    @GetMapping("/tickets")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> tickets(@RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.listOpenTickets(limit == null ? 50 : limit));
    }

    @PostMapping("/notify-run")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> notifyRun(@RequestBody Map<String, Object> body) {
        Long schemeId = body.get("schemeId") == null ? null : Long.valueOf(String.valueOf(body.get("schemeId")));
        Long taskId = body.get("taskId") == null ? null : Long.valueOf(String.valueOf(body.get("taskId")));
        Long runId = body.get("runId") == null ? null : Long.valueOf(String.valueOf(body.get("runId")));
        String taskName = body.get("taskName") == null ? null : String.valueOf(body.get("taskName"));
        if (runId == null) {
            return ApiResponse.fail(400, "runId 不能为空");
        }
        return ApiResponse.ok(service.notifyAfterRun(schemeId, taskId, runId, taskName));
    }
}
