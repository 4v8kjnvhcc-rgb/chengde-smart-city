package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
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
            @RequestParam(required = false) String baseCode,
            @RequestParam(required = false) String providerOrg,
            @RequestParam(required = false) String catalogKind,
            @RequestParam(required = false) String shareMode,
            @RequestParam(required = false) String shareAttr,
            @RequestParam(required = false) String openAttr,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return ApiResponse.ok(service.catalogBrowse(
                keyword, themeCode, baseCode, providerOrg, catalogKind, shareMode,
                shareAttr, openAttr, resourceType, sortBy, sortDir));
    }

    @GetMapping("/catalog/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> catalogDetail(@PathVariable Long id) {
        return ApiResponse.ok(service.catalogDetail(id));
    }

    @GetMapping("/subscriptions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> subscriptions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scope) {
        return ApiResponse.ok(service.listSubscriptions(principal, status, scope));
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

    @PostMapping("/subscriptions/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> cancelSubscription(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long id) {
        service.cancelSubscription(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/favorites")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> favorites(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.listFavorites(principal));
    }

    @PostMapping("/favorites")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> addFavorite(@AuthenticationPrincipal UserPrincipal principal,
                                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.addFavorite(principal, body));
    }

    @PostMapping("/favorites/{id}/remove")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> removeFavorite(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        service.removeFavorite(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/favorites/remove")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> removeFavoriteByResource(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestBody Map<String, Object> body) {
        Long catalogId = body.get("catalogId") == null ? null
                : Long.valueOf(String.valueOf(body.get("catalogId")));
        Long govResourceId = body.get("govResourceId") == null ? null
                : Long.valueOf(String.valueOf(body.get("govResourceId")));
        service.removeFavoriteByResource(principal, catalogId, govResourceId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/situations")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> situations() {
        return ApiResponse.ok(service.listSituations());
    }

    @PostMapping("/search/sync")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> syncIndex(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.syncSearchIndex(principal));
    }
}
