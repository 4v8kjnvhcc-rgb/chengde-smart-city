package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.BizCollectTask;
import com.chengde.smartcity.exchange.entity.BizDataAsset;
import com.chengde.smartcity.exchange.entity.BizDataDemand;
import com.chengde.smartcity.exchange.entity.BizEsbFlow;
import com.chengde.smartcity.exchange.entity.BizKettleJob;
import com.chengde.smartcity.exchange.service.ExchangeDemoService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exchange")
public class ExchangeDemoController {

    private final ExchangeDemoService service;

    public ExchangeDemoController(ExchangeDemoService service) {
        this.service = service;
    }

    @GetMapping("/assets")
    public ApiResponse<List<BizDataAsset>> assets() {
        return ApiResponse.ok(service.listAssets());
    }

    @PostMapping("/assets")
    public ApiResponse<Long> createAsset(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createAsset(principal, body));
    }

    @GetMapping("/collect-tasks")
    public ApiResponse<List<BizCollectTask>> tasks() {
        return ApiResponse.ok(service.listTasks());
    }

    @PostMapping("/collect-tasks")
    public ApiResponse<Long> createTask(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createTask(principal, body));
    }

    @PostMapping("/collect-tasks/{id}/run")
    public ApiResponse<Void> runTask(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.runTask(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/catalog")
    public ApiResponse<List<BizCatalogItem>> catalog() {
        return ApiResponse.ok(service.listCatalog());
    }

    @PostMapping("/catalog")
    public ApiResponse<Long> createCatalog(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createCatalog(principal, body));
    }

    @PostMapping("/catalog/{id}/publish")
    public ApiResponse<Void> publishCatalog(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.publishCatalog(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/shared-portal")
    public ApiResponse<List<BizCatalogItem>> sharedPortal() {
        return ApiResponse.ok(service.sharedPortal());
    }

    @GetMapping("/demands")
    public ApiResponse<List<BizDataDemand>> demands() {
        return ApiResponse.ok(service.listDemands());
    }

    @PostMapping("/demands")
    public ApiResponse<Long> createDemand(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createDemand(principal, body));
    }

    @PostMapping("/demands/{id}/confirm")
    public ApiResponse<Void> confirmDemand(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long id,
                                           @RequestBody(required = false) Map<String, Object> body) {
        service.confirmDemand(principal, id, body == null ? Map.of() : body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/esb/flows")
    public ApiResponse<List<BizEsbFlow>> esbFlows() {
        return ApiResponse.ok(service.listEsbFlows());
    }

    @PostMapping("/esb/flows/{id}/invoke")
    public ApiResponse<Map<String, Object>> invokeEsb(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable Long id) {
        return ApiResponse.ok(service.invokeEsb(principal, id));
    }

    @GetMapping("/kettle/jobs")
    public ApiResponse<List<BizKettleJob>> kettleJobs() {
        return ApiResponse.ok(service.listKettleJobs());
    }

    @PostMapping("/kettle/jobs/{id}/run")
    public ApiResponse<Map<String, Object>> runKettle(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable Long id) {
        return ApiResponse.ok(service.runKettle(principal, id));
    }
}
