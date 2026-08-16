package com.chengde.smartcity.analysis.controller;

import com.chengde.smartcity.analysis.entity.AnaIndicator;
import com.chengde.smartcity.analysis.entity.AnaModelSample;
import com.chengde.smartcity.analysis.entity.IndArea;
import com.chengde.smartcity.analysis.entity.IndField;
import com.chengde.smartcity.analysis.entity.IndGroup;
import com.chengde.smartcity.analysis.entity.IndJob;
import com.chengde.smartcity.analysis.entity.AnaPopBatchLedger;
import com.chengde.smartcity.analysis.entity.AnaPopServiceContract;
import com.chengde.smartcity.analysis.entity.AnaPopVerifyLedger;
import com.chengde.smartcity.analysis.entity.AnaZoneBinding;
import com.chengde.smartcity.analysis.service.AnalyticsDomainService;
import com.chengde.smartcity.analysis.service.IndicatorTaskService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics/domain")
public class AnalyticsDomainController {

    private final AnalyticsDomainService service;
    private final IndicatorTaskService indicatorTaskService;

    public AnalyticsDomainController(AnalyticsDomainService service, IndicatorTaskService indicatorTaskService) {
        this.service = service;
        this.indicatorTaskService = indicatorTaskService;
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

    @GetMapping("/{domain}/indicator-domains")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IndArea>> indicatorDomains(
            @PathVariable String domain,
            @RequestParam(required = false) String domainName,
            @RequestParam(required = false) String domainDbName) {
        return ApiResponse.ok(service.listIndicatorDomains(domain, domainName, domainDbName));
    }

    @PostMapping("/{domain}/indicator-domains")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<String> createIndicatorDomain(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable String domain,
                                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createIndicatorDomain(principal, domain, body));
    }

    @PutMapping("/indicator-domains/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateIndicatorDomain(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable String id,
                                                   @RequestBody Map<String, Object> body) {
        service.updateIndicatorDomain(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/indicator-domains/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteIndicatorDomain(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable String id) {
        service.deleteIndicatorDomain(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/indicator-domains/{id}/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> publishIndicatorDomain(@AuthenticationPrincipal UserPrincipal principal,
                                                                   @PathVariable String id,
                                                                   @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.publishIndicatorDomain(principal, id, body));
    }

    @GetMapping("/{domain}/indicator-groups")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IndGroup>> indicatorGroups(
            @PathVariable String domain,
            @RequestParam(required = false) String indicatorDomainId,
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String targetTable,
            @RequestParam(required = false) String groupCategory) {
        return ApiResponse.ok(service.listIndicatorGroups(domain, indicatorDomainId, groupName, targetTable, groupCategory));
    }

    @GetMapping("/indicator-groups/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<IndGroup> indicatorGroup(@PathVariable String id) {
        return ApiResponse.ok(service.getIndicatorGroup(id));
    }

    @PostMapping("/{domain}/indicator-groups")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<String> createIndicatorGroup(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable String domain,
                                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createIndicatorGroup(principal, domain, body));
    }

    @PutMapping("/indicator-groups/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateIndicatorGroup(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable String id,
                                                  @RequestBody Map<String, Object> body) {
        service.updateIndicatorGroup(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/indicator-groups/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteIndicatorGroup(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable String id) {
        service.deleteIndicatorGroup(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/indicator-groups/{id}/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> publishIndicatorGroup(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable String id,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        service.publishIndicatorGroup(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/indicator-groups/{id}/sql")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> groupSql(@PathVariable String id) {
        return ApiResponse.ok(service.latestGroupSql(id));
    }

    @GetMapping("/indicator-groups/{id}/indicators")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IndField>> groupIndicators(@PathVariable String id) {
        return ApiResponse.ok(service.listIndicatorsByGroup(id));
    }

    @GetMapping("/{domain}/indicator-datasource-catalog")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> indicatorDatasourceCatalog(
            @PathVariable String domain,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listIndicatorDatasourceCatalog(domain, category, keyword));
    }

    @GetMapping("/{domain}/indicator-tasks")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IndJob>> indicatorTasks(
            @PathVariable String domain,
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String scheduleStatus,
            @RequestParam(required = false) String execStatus,
            @RequestParam(required = false) String calcResult) {
        return ApiResponse.ok(indicatorTaskService.list(domain, taskName, scheduleStatus, execStatus, calcResult));
    }

    @PostMapping("/indicator-tasks/batch")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> indicatorTasksBatch(@AuthenticationPrincipal UserPrincipal principal,
                                                                @RequestBody Map<String, Object> body) {
        List<String> ids = new java.util.ArrayList<>();
        Object raw = body.get("ids");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    ids.add(String.valueOf(o));
                }
            }
        }
        return ApiResponse.ok(indicatorTaskService.batch(principal, String.valueOf(body.get("action")), ids));
    }

    @PostMapping("/indicator-tasks/{id}/execute")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> executeIndicatorTask(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable String id) {
        return ApiResponse.ok(indicatorTaskService.execute(principal, id));
    }

    @PostMapping("/indicator-tasks/{id}/start")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> startIndicatorTask(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable String id) {
        return ApiResponse.ok(indicatorTaskService.start(principal, id));
    }

    @PostMapping("/indicator-tasks/{id}/stop")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> stopIndicatorTask(@AuthenticationPrincipal UserPrincipal principal,
                                                              @PathVariable String id) {
        return ApiResponse.ok(indicatorTaskService.stop(principal, id));
    }

    @PostMapping("/indicator-tasks/{id}/offline")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> offlineIndicatorTask(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable String id) {
        return ApiResponse.ok(indicatorTaskService.offline(principal, id));
    }

    @GetMapping("/indicator-tasks/{id}/log")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> indicatorTaskLog(@PathVariable String id) {
        return ApiResponse.ok(indicatorTaskService.logDetail(id));
    }

    @GetMapping("/indicator-tasks/{id}/indicators")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IndField>> indicatorTaskIndicators(@PathVariable String id) {
        return ApiResponse.ok(indicatorTaskService.indicatorsOfTask(id));
    }

    @PostMapping("/indicator-tasks/{id}/ds-trigger")
    public ApiResponse<Map<String, Object>> indicatorTaskDsTrigger(
            @PathVariable String id,
            @RequestHeader(value = "X-Ds-Callback-Token", required = false) String token,
            @RequestBody(required = false) Map<String, Object> body) {
        Long dsInstanceId = null;
        if (body != null && body.get("dsInstanceId") != null) {
            Object v = body.get("dsInstanceId");
            if (v instanceof Number n) dsInstanceId = n.longValue();
            else {
                try { dsInstanceId = Long.valueOf(String.valueOf(v)); } catch (Exception ignored) { /* keep null */ }
            }
        }
        return ApiResponse.ok(indicatorTaskService.runFromDsCallback(id, token, dsInstanceId));
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
    public ApiResponse<String> createIndicatorSql(@AuthenticationPrincipal UserPrincipal principal,
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
                                             @PathVariable String id,
                                             @RequestBody Map<String, Object> body) {
        service.updateIndicator(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/indicators/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteIndicator(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String id) {
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
