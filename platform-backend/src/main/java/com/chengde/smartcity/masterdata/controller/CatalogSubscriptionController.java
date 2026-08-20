package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.service.CatalogSubscribeDistributeService;
import com.chengde.smartcity.masterdata.service.CatalogSubscriptionService;
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
 * 资源目录订阅申请 / 审批 / 分发（B4、B5）+ 变更通知与多目标分发。
 */
@RestController
@RequestMapping("/api/v1/governance/catalog/subscriptions")
public class CatalogSubscriptionController {

    private final CatalogSubscriptionService service;
    private final CatalogSubscribeDistributeService distributeService;

    public CatalogSubscriptionController(CatalogSubscriptionService service,
                                         CatalogSubscribeDistributeService distributeService) {
        this.service = service;
        this.distributeService = distributeService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                       @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listMine(principal, status));
    }

    @GetMapping("/pending")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> pending(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.listPending(principal));
    }

    @GetMapping("/reviewed")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> reviewed(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.listReviewed(principal));
    }

    @GetMapping("/notices")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> notices(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestParam(required = false) String status) {
        return ApiResponse.ok(distributeService.listNotices(principal, status));
    }

    @PostMapping("/notices/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> markNoticeRead(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable Long id) {
        return ApiResponse.ok(distributeService.markNoticeRead(principal, id));
    }

    @PostMapping("/notices/{id}/ack")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> ackNotice(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable Long id) {
        return ApiResponse.ok(distributeService.ackNotice(principal, id));
    }

    @GetMapping("/distribute-targets")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> listTargets(@AuthenticationPrincipal UserPrincipal principal,
                                                              @RequestParam(required = false) Long subscriptionId) {
        return ApiResponse.ok(distributeService.listTargets(principal, subscriptionId));
    }

    @PostMapping("/distribute-targets")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> saveTarget(@AuthenticationPrincipal UserPrincipal principal,
                                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(distributeService.saveTarget(principal, body));
    }

    @PutMapping("/distribute-targets/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> updateTarget(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable Long id,
                                                         @RequestBody Map<String, Object> body) {
        Map<String, Object> payload = body == null ? new java.util.HashMap<>() : new java.util.HashMap<>(body);
        payload.put("id", id);
        return ApiResponse.ok(distributeService.saveTarget(principal, payload));
    }

    @DeleteMapping("/distribute-targets/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteTarget(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id) {
        distributeService.deleteTarget(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/distribute-logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> distributeLogs(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @RequestParam(required = false) Long subscriptionId,
                                                                 @RequestParam(required = false) Long resourceId) {
        return ApiResponse.ok(distributeService.listDistributeLogs(principal, subscriptionId, resourceId));
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

    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> approve(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.approve(principal, id, body == null ? Map.of() : body));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> reject(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.reject(principal, id, body == null ? Map.of() : body));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> cancel(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(service.cancel(principal, id));
    }

    @PostMapping("/{id}/distribute")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> distribute(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable Long id) {
        return ApiResponse.ok(service.distribute(principal, id));
    }

    @PostMapping("/{id}/distribute-now")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> distributeNow(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long id,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        Long targetId = null;
        if (body != null && body.get("targetId") != null) {
            Object v = body.get("targetId");
            if (v instanceof Number n) {
                targetId = n.longValue();
            } else {
                try {
                    targetId = Long.parseLong(String.valueOf(v));
                } catch (Exception ignored) {
                    targetId = null;
                }
            }
        }
        return ApiResponse.ok(distributeService.distributeNow(principal, id, targetId));
    }

    @GetMapping("/{id}/distribute-result")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> distributeResult(@PathVariable Long id) {
        return ApiResponse.ok(service.distributeResult(id));
    }

    @GetMapping("/{id}/authorization")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> authorization(@PathVariable Long id) {
        return ApiResponse.ok(service.authorization(id));
    }

    @GetMapping("/{id}/test-api")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> testApi(@PathVariable Long id) {
        return ApiResponse.ok(service.testApi(id));
    }
}
