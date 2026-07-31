package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.IngMaskAuditLog;
import com.chengde.smartcity.exchange.entity.IngMaskBinding;
import com.chengde.smartcity.exchange.entity.IngMaskCryptoRef;
import com.chengde.smartcity.exchange.entity.IngMaskPolicy;
import com.chengde.smartcity.exchange.entity.IngMaskRule;
import com.chengde.smartcity.exchange.service.MaskPolicyService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exchange/ingestion/mask-policy")
public class MaskPolicyController {

    private final MaskPolicyService service;

    public MaskPolicyController(MaskPolicyService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/rules")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngMaskRule>> rules(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listRules(keyword));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveRule(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveRule(body));
    }

    @PostMapping("/rules/{id}/clone")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> cloneRule(@PathVariable Long id) {
        return ApiResponse.ok(service.cloneRule(id));
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        service.deleteRule(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/rules/{id}/preview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> previewRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.previewRule(id, body.get("sample") == null ? "" : String.valueOf(body.get("sample"))));
    }

    @GetMapping("/policies")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngMaskPolicy>> policies(
            @RequestParam(required = false) String sceneCode,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listPolicies(sceneCode, status));
    }

    @PostMapping("/policies")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> savePolicy(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.savePolicy(principal, body));
    }

    @PostMapping("/policies/{id}/publish")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> publish(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.publishPolicy(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/policies/{id}/rollback")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> rollback(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(service.rollbackPolicy(principal, id));
    }

    @GetMapping("/bindings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngMaskBinding>> bindings() {
        return ApiResponse.ok(service.listBindings());
    }

    @PostMapping("/bindings")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveBinding(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveBinding(body));
    }

    @DeleteMapping("/bindings/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> deleteBinding(@PathVariable Long id) {
        service.deleteBinding(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/crypto-refs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngMaskCryptoRef>> cryptoRefs() {
        return ApiResponse.ok(service.listCryptoRefs());
    }

    @PostMapping("/crypto-refs")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveCrypto(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveCryptoRef(body));
    }

    @PostMapping("/preview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> preview(@AuthenticationPrincipal UserPrincipal principal,
                                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.apply(principal, body, true));
    }

    @PostMapping("/apply")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> apply(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.apply(principal, body, false));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngMaskAuditLog>> audit(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.listAudit(limit));
    }
}
