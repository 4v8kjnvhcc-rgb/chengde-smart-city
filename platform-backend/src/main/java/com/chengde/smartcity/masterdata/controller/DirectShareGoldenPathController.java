package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.service.DirectShareGoldenPathService;
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
 * 直通共享黄金路径：支持按 tableId 选择任意已汇聚表。
 */
@RestController
@RequestMapping("/api/v1/governance/direct-share")
public class DirectShareGoldenPathController {

    private final DirectShareGoldenPathService service;

    public DirectShareGoldenPathController(DirectShareGoldenPathService service) {
        this.service = service;
    }

    @GetMapping("/eligible-tables")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> eligibleTables() {
        return ApiResponse.ok(service.eligibleTables());
    }

    @GetMapping("/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> overview(@AuthenticationPrincipal UserPrincipal principal,
                                                     @RequestParam(required = false) Long tableId) {
        return ApiResponse.ok(service.overview(principal, tableId));
    }

    @GetMapping("/sample")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> sample(@RequestParam(required = false) Long tableId) {
        return ApiResponse.ok(service.sample(tableId));
    }

    @PostMapping("/metadata/collect")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> collectMetadata(@AuthenticationPrincipal UserPrincipal principal,
                                                            @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.collectMetadata(principal, tableId(body)));
    }

    @PostMapping("/quality/run")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> runQuality(@AuthenticationPrincipal UserPrincipal principal,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body == null ? Map.of() : body;
        return ApiResponse.ok(service.runQuality(principal, tableId(req), req));
    }

    @PostMapping("/catalog/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> publishCatalog(@AuthenticationPrincipal UserPrincipal principal,
                                                           @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.publishCatalog(principal, tableId(body)));
    }

    @PostMapping("/subscription/authorize")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> subscribeAndAuthorize(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body == null ? Map.of() : body;
        return ApiResponse.ok(service.subscribeAndAuthorize(principal, tableId(req), req));
    }

    private static Long tableId(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object v = body.get("tableId");
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }
}

