package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.service.QualitySchemeService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 质量方案管理：定时经 DolphinScheduler，执行复用质量稽核引擎。
 */
@RestController
@RequestMapping("/api/v1/governance/quality/schemes")
public class QualitySchemeController {

    private final QualitySchemeService service;

    public QualitySchemeController(QualitySchemeService service) {
        this.service = service;
    }

    @GetMapping("/published")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> published() {
        return ApiResponse.ok(service.listPublished());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String scheduleStatus,
            @RequestParam(required = false) String cycleName) {
        return ApiResponse.ok(service.list(keyword, scheduleStatus, cycleName));
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

    @PostMapping("/{id}/generate")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> generate(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable Long id) {
        return ApiResponse.ok(service.generate(principal, id));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> start(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.start(principal, id, body));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> stop(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable Long id) {
        return ApiResponse.ok(service.stop(principal, id));
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> execute(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.execute(principal, id, body));
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> logs(@PathVariable Long id) {
        return ApiResponse.ok(service.logs(id));
    }

    /** DolphinScheduler Shell 回调（无登录态，令牌鉴权）。 */
    @PostMapping("/{id}/run-callback")
    public ApiResponse<Map<String, Object>> runCallback(
            @PathVariable Long id,
            @RequestHeader(value = "X-Ds-Callback-Token", required = false) String token) {
        return ApiResponse.ok(service.runCallback(id, token));
    }
}
