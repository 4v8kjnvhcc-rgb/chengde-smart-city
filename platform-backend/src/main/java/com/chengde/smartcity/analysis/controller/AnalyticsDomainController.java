package com.chengde.smartcity.analysis.controller;

import com.chengde.smartcity.analysis.entity.AnaIndicator;
import com.chengde.smartcity.analysis.entity.AnaModelSample;
import com.chengde.smartcity.analysis.entity.AnaPopBatchLedger;
import com.chengde.smartcity.analysis.entity.AnaPopServiceContract;
import com.chengde.smartcity.analysis.entity.AnaPopVerifyLedger;
import com.chengde.smartcity.analysis.entity.AnaZoneBinding;
import com.chengde.smartcity.analysis.service.AnalyticsDomainService;
import com.chengde.smartcity.common.api.ApiResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics/domain")
public class AnalyticsDomainController {

    private final AnalyticsDomainService service;

    public AnalyticsDomainController(AnalyticsDomainService service) {
        this.service = service;
    }

    @GetMapping("/{domain}/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> overview(@PathVariable String domain) {
        return ApiResponse.ok(service.domainOverview(domain));
    }

    @GetMapping("/modules/{mCode}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> module(@PathVariable String mCode) {
        return ApiResponse.ok(service.moduleDetail(mCode));
    }

    @PostMapping("/modules/{mCode}/run")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> run(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable String mCode) {
        return ApiResponse.ok(service.runDataOps(principal, mCode));
    }

    @PostMapping("/modules/{mCode}/embed-token")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> embed(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable String mCode) {
        return ApiResponse.ok(service.issueModuleEmbed(principal, mCode));
    }

    @GetMapping("/{domain}/zones/{zone}/bindings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<AnaZoneBinding>> bindings(@PathVariable String domain, @PathVariable String zone) {
        return ApiResponse.ok(service.listBindings(domain, zone));
    }

    @GetMapping("/{domain}/zones/{zone}/candidates")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> candidates(@PathVariable String domain, @PathVariable String zone) {
        return ApiResponse.ok(service.zoneCandidates(domain, zone));
    }

    @PostMapping("/{domain}/zones/{zone}/bindings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> bind(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable String domain,
                                  @PathVariable String zone,
                                  @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.bindAsset(principal, domain, zone, body));
    }

    @DeleteMapping("/bindings/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> unbind(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.unbindAsset(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{domain}/indicators")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<AnaIndicator>> indicators(@PathVariable String domain) {
        return ApiResponse.ok(service.listIndicators(domain));
    }

    @GetMapping("/{domain}/indicators/datasources")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, String>>> indicatorDatasources(@PathVariable String domain) {
        return ApiResponse.ok(service.listIndicatorDatasources(domain));
    }

    @PostMapping("/{domain}/indicators/sql/parse")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> parseIndicatorSql(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.parseIndicatorSql(body));
    }

    @PostMapping("/{domain}/indicators/sql/preview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> previewIndicatorSql(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.previewIndicatorSql(body));
    }

    @PostMapping("/{domain}/indicators/sql")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createIndicatorSql(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable String domain,
                                                @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createIndicatorSql(principal, domain, body));
    }

    @PostMapping("/{domain}/indicators")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createIndicator(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable String domain,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createIndicator(principal, domain, body));
    }

    @PutMapping("/indicators/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateIndicator(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        service.updateIndicator(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/indicators/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteIndicator(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.deleteIndicator(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{domain}/models")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> models(@PathVariable String domain) {
        return ApiResponse.ok(service.listModels(domain));
    }

    @PutMapping("/models/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateModel(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        service.updateModelDesign(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/models/{id}/samples")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<AnaModelSample>> modelSamples(@PathVariable Long id) {
        return ApiResponse.ok(service.listModelSamples(id));
    }

    @PostMapping("/models/{id}/embed-token")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> modelEmbed(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable Long id) {
        return ApiResponse.ok(service.issueModelEmbedById(principal, id));
    }

    @GetMapping("/{domain}/verify-ledger")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<AnaPopVerifyLedger>> verifyLedger(@PathVariable String domain,
                                                              @org.springframework.web.bind.annotation.RequestParam(required = false) String mCode) {
        return ApiResponse.ok(service.listPopVerifyLedger(domain, mCode));
    }

    @PostMapping("/{domain}/verify-ledger")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createVerifyLedger(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable String domain,
                                                @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createPopVerifyLedger(principal, domain, body));
    }

    @PutMapping("/verify-ledger/{id}/feedback")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateVerifyFeedback(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        service.updatePopVerifyFeedback(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{domain}/services")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<AnaPopServiceContract>> popServices(@PathVariable String domain) {
        return ApiResponse.ok(service.listPopServiceContracts(domain));
    }

    @PostMapping("/{domain}/services/{serviceCode}/invoke")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> invokePopService(@AuthenticationPrincipal UserPrincipal principal,
                                                             @PathVariable String domain,
                                                             @PathVariable String serviceCode,
                                                             @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.invokePopService(principal, domain, serviceCode, body));
    }

    @GetMapping("/{domain}/batch-ledger")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<AnaPopBatchLedger>> batchLedger(@PathVariable String domain) {
        return ApiResponse.ok(service.listPopBatchLedger(domain));
    }

    @PostMapping("/{domain}/batch-ledger")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createBatchLedger(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable String domain,
                                               @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createPopBatchLedger(principal, domain, body));
    }

    @PutMapping("/batch-ledger/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateBatchStatus(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long id,
                                               @RequestBody Map<String, Object> body) {
        service.updatePopBatchStatus(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{domain}/storage-summary")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> storageSummary(@PathVariable String domain) {
        return ApiResponse.ok(service.populationStorageSummary(domain));
    }
}
