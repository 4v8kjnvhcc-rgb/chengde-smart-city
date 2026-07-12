package com.chengde.smartcity.analysis.controller;

import com.chengde.smartcity.analysis.service.AnalyticsDomainService;
import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics/domain")
public class AnalyticsDomainController {

    private final AnalyticsDomainService service;

    public AnalyticsDomainController(AnalyticsDomainService service) {
        this.service = service;
    }

    @GetMapping("/{domain}/overview")
    public ApiResponse<Map<String, Object>> overview(@PathVariable String domain) {
        return ApiResponse.ok(service.domainOverview(domain));
    }

    @GetMapping("/modules/{mCode}")
    public ApiResponse<Map<String, Object>> module(@PathVariable String mCode) {
        return ApiResponse.ok(service.moduleDetail(mCode));
    }

    @PostMapping("/modules/{mCode}/run")
    public ApiResponse<Map<String, Object>> run(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable String mCode) {
        return ApiResponse.ok(service.runDataOps(principal, mCode));
    }

    @PostMapping("/modules/{mCode}/embed-token")
    public ApiResponse<Map<String, Object>> embed(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable String mCode) {
        return ApiResponse.ok(service.issueModuleEmbed(principal, mCode));
    }
}
