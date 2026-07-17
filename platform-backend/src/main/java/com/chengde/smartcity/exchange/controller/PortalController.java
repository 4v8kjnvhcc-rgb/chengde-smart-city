package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.BizPortalSituation;
import com.chengde.smartcity.exchange.service.PortalService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exchange/portal")
public class PortalController {

    private final PortalService service;

    public PortalController(PortalService service) {
        this.service = service;
    }

    @GetMapping("/home")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> home(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.home(keyword));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String themeCode,
            @RequestParam(required = false) String providerOrg,
            @RequestParam(required = false) String catalogKind,
            @RequestParam(required = false) String shareMode) {
        return ApiResponse.ok(service.search(q, themeCode, providerOrg, catalogKind, shareMode));
    }

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> catalog(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String themeCode,
            @RequestParam(required = false) String providerOrg,
            @RequestParam(required = false) String catalogKind,
            @RequestParam(required = false) String shareMode) {
        return ApiResponse.ok(service.catalogBrowse(keyword, themeCode, providerOrg, catalogKind, shareMode));
    }

    @GetMapping("/subscriptions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> subscriptions(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listSubscriptions(status));
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createSubscription(@AuthenticationPrincipal UserPrincipal principal,
                                                @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createSubscription(principal, body));
    }

    @PostMapping("/subscriptions/{id}/review")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> reviewSubscription(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable Long id,
                                                               @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.reviewSubscription(principal, id, body));
    }

    @GetMapping("/situations")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<BizPortalSituation>> situations() {
        return ApiResponse.ok(service.listSituations());
    }

    @PostMapping("/search/sync")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> syncIndex(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.syncSearchIndex(principal));
    }
}
