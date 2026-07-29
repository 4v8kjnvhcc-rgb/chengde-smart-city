package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.IngAssetCatalogReg;
import com.chengde.smartcity.exchange.service.AssetCatalogRegService;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/exchange/ingestion/asset-catalog-regs")
public class AssetCatalogRegController {

    private final AssetCatalogRegService service;

    public AssetCatalogRegController(AssetCatalogRegService service) {
        this.service = service;
    }

    @GetMapping("/defaults")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> defaults(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.defaults(principal));
    }

    @GetMapping("/org-options")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> orgOptions() {
        return ApiResponse.ok(service.orgOptions());
    }

    @GetMapping("/contacts")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> contacts(@RequestParam Long orgId) {
        return ApiResponse.ok(service.contactsByOrg(orgId));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<IngAssetCatalogReg>> list(
            @RequestParam(required = false) String assetName,
            @RequestParam(required = false) String orgName,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(assetName, orgName, projectName, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<IngAssetCatalogReg> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> create(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(principal, body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> update(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        service.update(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id) {
        service.delete(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/report")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> report(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id) {
        service.report(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> reject(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestBody(required = false) Map<String, Object> body) {
        service.reject(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> archive(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable Long id) {
        service.archive(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> upload(@AuthenticationPrincipal UserPrincipal principal,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(defaultValue = "quality") String kind) {
        return ApiResponse.ok(service.upload(principal, file, kind));
    }
}
