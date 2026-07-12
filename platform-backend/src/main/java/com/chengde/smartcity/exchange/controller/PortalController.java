package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.BizPortalSituation;
import com.chengde.smartcity.exchange.entity.BizPortalSubscription;
import com.chengde.smartcity.exchange.service.PortalService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
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
    public ApiResponse<Map<String, Object>> home(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.home(keyword));
    }

    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(@RequestParam(required = false) String q) {
        return ApiResponse.ok(service.search(q));
    }

    @GetMapping("/catalog")
    public ApiResponse<List<Map<String, Object>>> catalog(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.catalogBrowse(keyword));
    }

    @GetMapping("/subscriptions")
    public ApiResponse<List<BizPortalSubscription>> subscriptions(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listSubscriptions(status));
    }

    @PostMapping("/subscriptions")
    public ApiResponse<Long> createSubscription(@AuthenticationPrincipal UserPrincipal principal,
                                                @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createSubscription(principal, body));
    }

    @PostMapping("/subscriptions/{id}/review")
    public ApiResponse<Void> reviewSubscription(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        service.reviewSubscription(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/situations")
    public ApiResponse<List<BizPortalSituation>> situations() {
        return ApiResponse.ok(service.listSituations());
    }

    @PostMapping("/search/sync")
    public ApiResponse<Map<String, Object>> syncIndex(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.syncSearchIndex(principal));
    }
}
