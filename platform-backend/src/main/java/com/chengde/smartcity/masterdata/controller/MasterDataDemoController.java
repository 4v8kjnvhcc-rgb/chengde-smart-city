package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovOmConnector;
import com.chengde.smartcity.masterdata.entity.GovQualityRule;
import com.chengde.smartcity.masterdata.entity.GovQualityTask;
import com.chengde.smartcity.masterdata.entity.RcBackupJob;
import com.chengde.smartcity.masterdata.entity.RcThemeLibrary;
import com.chengde.smartcity.masterdata.entity.UnsDocument;
import com.chengde.smartcity.masterdata.service.MasterDataDemoService;
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
@RequestMapping("/api/v1")
public class MasterDataDemoController {

    private final MasterDataDemoService service;

    public MasterDataDemoController(MasterDataDemoService service) {
        this.service = service;
    }

    @GetMapping("/governance/connectors")
    public ApiResponse<List<GovOmConnector>> connectors() {
        return ApiResponse.ok(service.listConnectors());
    }

    @PostMapping("/governance/connectors")
    public ApiResponse<Long> createConnector(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createConnector(principal, body));
    }

    @PostMapping("/governance/connectors/{id}/sync")
    public ApiResponse<Map<String, Object>> syncConnector(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(service.syncConnector(principal, id));
    }

    @GetMapping("/governance/quality/rules")
    public ApiResponse<List<GovQualityRule>> rules() {
        return ApiResponse.ok(service.listRules());
    }

    @PostMapping("/governance/quality/rules")
    public ApiResponse<Long> createRule(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createRule(principal, body));
    }

    @GetMapping("/governance/quality/tasks")
    public ApiResponse<List<GovQualityTask>> qualityTasks() {
        return ApiResponse.ok(service.listQualityTasks());
    }

    @PostMapping("/governance/quality/tasks")
    public ApiResponse<Long> createQualityTask(@AuthenticationPrincipal UserPrincipal principal,
                                               @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createQualityTask(principal, body));
    }

    @PostMapping("/governance/quality/tasks/{id}/run")
    public ApiResponse<Map<String, Object>> runQualityTask(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable Long id) {
        return ApiResponse.ok(service.runQualityTask(principal, id));
    }

    @GetMapping("/unstructured/documents")
    public ApiResponse<List<UnsDocument>> documents(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listDocuments(keyword));
    }

    @PostMapping("/unstructured/documents")
    public ApiResponse<Long> registerDocument(@AuthenticationPrincipal UserPrincipal principal,
                                              @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.registerDocument(principal, body));
    }

    @PostMapping("/unstructured/documents/{id}/index")
    public ApiResponse<Map<String, Object>> indexDocument(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(service.indexDocument(principal, id));
    }

    @GetMapping("/resource-center/themes")
    public ApiResponse<List<RcThemeLibrary>> themes() {
        return ApiResponse.ok(service.listThemes());
    }

    @PostMapping("/resource-center/themes")
    public ApiResponse<Long> createTheme(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createTheme(principal, body));
    }

    @GetMapping("/resource-center/backups")
    public ApiResponse<List<RcBackupJob>> backups() {
        return ApiResponse.ok(service.listBackupJobs());
    }

    @PostMapping("/resource-center/backups")
    public ApiResponse<Long> createBackup(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createBackupJob(principal, body));
    }

    @PostMapping("/resource-center/backups/{id}/run")
    public ApiResponse<Map<String, Object>> runBackup(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable Long id) {
        return ApiResponse.ok(service.runBackup(principal, id));
    }
}
