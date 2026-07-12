package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.BizCatalogObjection;
import com.chengde.smartcity.exchange.entity.BizDataDemand;
import com.chengde.smartcity.exchange.entity.BizDemandSupplyTask;
import com.chengde.smartcity.exchange.entity.BizDemandTemplate;
import com.chengde.smartcity.exchange.entity.BizSupplyManifest;
import com.chengde.smartcity.exchange.service.SupplyDemandService;
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
@RequestMapping("/api/v1/exchange/supply")
public class SupplyDemandController {

    private final SupplyDemandService service;

    public SupplyDemandController(SupplyDemandService service) {
        this.service = service;
    }

    @GetMapping("/templates")
    public ApiResponse<List<BizDemandTemplate>> templates() {
        return ApiResponse.ok(service.listTemplates());
    }

    @GetMapping("/demands")
    public ApiResponse<List<BizDataDemand>> demands(@RequestParam(required = false) String stage,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listDemands(stage, status));
    }

    @PostMapping("/demands")
    public ApiResponse<Long> createDemand(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createDemand(principal, body));
    }

    @PostMapping("/demands/{id}/withdraw")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.withdrawDemand(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/analyze")
    public ApiResponse<Map<String, Object>> analyze(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable Long id) {
        return ApiResponse.ok(service.analyzeDemand(principal, id));
    }

    @PostMapping("/demands/{id}/dispatch")
    public ApiResponse<Void> dispatch(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, Object> body) {
        service.dispatchDemand(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/return")
    public ApiResponse<Void> returnDemand(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        service.returnDemand(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/demands/{id}/confirm")
    public ApiResponse<Map<String, Object>> confirm(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.confirmDemand(principal, id, body));
    }

    @PostMapping("/demands/{id}/reject")
    public ApiResponse<Void> reject(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        service.rejectDemand(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/supply-tasks")
    public ApiResponse<List<BizDemandSupplyTask>> supplyTasks(@RequestParam(required = false) Long demandId) {
        return ApiResponse.ok(service.listSupplyTasks(demandId));
    }

    @GetMapping("/supply-view/{demandId}")
    public ApiResponse<Map<String, Object>> supplyView(@PathVariable Long demandId) {
        return ApiResponse.ok(service.supplyView(demandId));
    }

    @GetMapping("/catalog-manifest")
    public ApiResponse<List<BizCatalogItem>> catalogManifest() {
        return ApiResponse.ok(service.catalogManifest());
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

    @PostMapping("/catalog/{id}/offline")
    public ApiResponse<Void> offlineCatalog(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body) {
        service.offlineCatalog(principal, id, body == null ? Map.of() : body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/catalog-manifest/export")
    public ApiResponse<Map<String, Object>> exportCatalogManifest() {
        return ApiResponse.ok(service.exportCatalogManifest());
    }

    @GetMapping("/objections")
    public ApiResponse<List<BizCatalogObjection>> objections(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listObjections(status));
    }

    @PostMapping("/objections")
    public ApiResponse<Long> createObjection(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createObjection(principal, body));
    }

    @PostMapping("/objections/{id}/process")
    public ApiResponse<Void> processObjection(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        service.processObjection(principal, id, body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/manifests")
    public ApiResponse<List<BizSupplyManifest>> manifests(@RequestParam(required = false) String manifestType) {
        return ApiResponse.ok(service.listManifests(manifestType));
    }

    @PostMapping("/manifests/{id}/export")
    public ApiResponse<Map<String, Object>> exportManifest(@PathVariable Long id) {
        return ApiResponse.ok(service.exportManifest(id));
    }
}
