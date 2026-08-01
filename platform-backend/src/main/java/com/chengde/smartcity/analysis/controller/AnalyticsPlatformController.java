package com.chengde.smartcity.analysis.controller;

import com.chengde.smartcity.analysis.entity.AnaBiWidget;
import com.chengde.smartcity.analysis.service.AnalyticsPlatformService;
import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics/platform")
public class AnalyticsPlatformController {

    private final AnalyticsPlatformService service;

    public AnalyticsPlatformController(AnalyticsPlatformService service) {
        this.service = service;
    }

    @GetMapping("/support/overview")
    public ApiResponse<Map<String, Object>> supportOverview() {
        return ApiResponse.ok(service.supportOverview());
    }

    @PostMapping("/apps")
    public ApiResponse<Long> createApp(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createApp(principal, body));
    }

    @PostMapping("/services")
    public ApiResponse<Long> createService(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createService(principal, body));
    }

    @PostMapping("/integrations/{id}/test")
    public ApiResponse<Map<String, Object>> testIntegration(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable Long id) {
        return ApiResponse.ok(service.testIntegration(principal, id));
    }

    @GetMapping("/bi/overview")
    public ApiResponse<Map<String, Object>> biOverview() {
        return ApiResponse.ok(service.biOverview());
    }

    @GetMapping("/bi/widgets/{mCode}")
    public ApiResponse<AnaBiWidget> widget(@PathVariable String mCode) {
        return ApiResponse.ok(service.getWidgetByMCode(mCode));
    }

    @PostMapping("/bi/widgets/{mCode}/embed-token")
    public ApiResponse<Map<String, Object>> widgetEmbed(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable String mCode) {
        return ApiResponse.ok(service.issueWidgetEmbed(principal, mCode));
    }
}
