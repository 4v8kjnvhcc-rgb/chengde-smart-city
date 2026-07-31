package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.IngSearchAuditLog;
import com.chengde.smartcity.exchange.entity.IngSearchGlobalField;
import com.chengde.smartcity.exchange.entity.IngSearchIdentity;
import com.chengde.smartcity.exchange.entity.IngSearchKnowledge;
import com.chengde.smartcity.exchange.entity.IngSearchQueryLog;
import com.chengde.smartcity.exchange.entity.IngSearchSavedQuery;
import com.chengde.smartcity.exchange.entity.IngSearchSyncPolicy;
import com.chengde.smartcity.exchange.service.AssetSearchService;
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
@RequestMapping("/api/v1/exchange/ingestion/asset-search")
public class AssetSearchController {

    private final AssetSearchService service;

    public AssetSearchController(AssetSearchService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/sync-policies")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngSearchSyncPolicy>> syncPolicies() {
        return ApiResponse.ok(service.listSyncPolicies());
    }

    @PostMapping("/sync-policies")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveSync(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveSyncPolicy(body));
    }

    @PostMapping("/sync-policies/{id}/run")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Map<String, Object>> runSync(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id) {
        return ApiResponse.ok(service.runSync(principal, id));
    }

    @GetMapping("/knowledge")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngSearchKnowledge>> knowledge(@RequestParam(required = false) String type) {
        return ApiResponse.ok(service.listKnowledge(type));
    }

    @PostMapping("/knowledge")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveKnowledge(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveKnowledge(principal, body));
    }

    @DeleteMapping("/knowledge/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> deleteKnowledge(@PathVariable Long id) {
        service.deleteKnowledge(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/global-fields")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngSearchGlobalField>> globalFields() {
        return ApiResponse.ok(service.listGlobalFields());
    }

    @PostMapping("/global-fields")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveGlobalField(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveGlobalField(principal, body));
    }

    @PostMapping("/global-fields/{id}/auto-match")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Map<String, Object>> autoMatch(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable Long id) {
        return ApiResponse.ok(service.autoMatchGlobalField(principal, id));
    }

    @GetMapping("/global-bindings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> globalBindings(
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) String confirmStatus) {
        return ApiResponse.ok(service.listGlobalBindings(fieldId, confirmStatus));
    }

    @PostMapping("/global-bindings/{id}/confirm")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Void> confirmBinding(@PathVariable Long id,
                                            @RequestParam(defaultValue = "true") boolean accept) {
        service.confirmGlobalBinding(id, accept);
        return ApiResponse.ok(null);
    }

    @GetMapping("/global-fields/{id}/impact")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> impact(@PathVariable Long id) {
        return ApiResponse.ok(service.previewGlobalImpact(id));
    }

    @GetMapping("/tables/{tableId}/global-fields")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> tableGlobals(@PathVariable Long tableId) {
        return ApiResponse.ok(service.globalFieldsForTable(tableId));
    }

    @PostMapping("/query")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> query(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.search(principal, body));
    }

    @PostMapping("/browse")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> browse(@AuthenticationPrincipal UserPrincipal principal,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.browseTable(principal, body));
    }

    @PostMapping("/download")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> download(@AuthenticationPrincipal UserPrincipal principal,
                                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.downloadRequest(principal, body));
    }

    @PostMapping("/click")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> click(@AuthenticationPrincipal UserPrincipal principal,
                                   @RequestBody Map<String, Object> body) {
        service.click(principal, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/identities")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngSearchIdentity>> identities(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listIdentities(keyword));
    }

    @PostMapping("/identities")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:ingestion:collect:asset')")
    public ApiResponse<Long> saveIdentity(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveIdentity(principal, body));
    }

    @GetMapping("/saved-queries")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngSearchSavedQuery>> savedQueries() {
        return ApiResponse.ok(service.listSavedQueries());
    }

    @PostMapping("/saved-queries")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> saveQuery(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveQuery(principal, body));
    }

    @GetMapping("/query-logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngSearchQueryLog>> queryLogs(@RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.queryLogs(limit));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngSearchAuditLog>> auditLogs(@RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.auditLogs(limit));
    }
}
