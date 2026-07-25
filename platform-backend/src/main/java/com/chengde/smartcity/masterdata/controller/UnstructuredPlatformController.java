package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.UnsDocCategory;
import com.chengde.smartcity.masterdata.entity.UnsDocPipeline;
import com.chengde.smartcity.masterdata.entity.UnsDocument;
import com.chengde.smartcity.masterdata.service.UnstructuredPlatformService;
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

@RestController
@RequestMapping("/api/v1/unstructured/platform")
public class UnstructuredPlatformController {

    private final UnstructuredPlatformService service;

    public UnstructuredPlatformController(UnstructuredPlatformService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<UnsDocCategory>> categories() {
        return ApiResponse.ok(service.listCategories());
    }

    @PostMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createCategory(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createCategory(principal, body));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateCategory(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        service.updateCategory(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteCategory(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        service.deleteCategory(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/documents")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<UnsDocument>> documents(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String publishStatus,
                                                    @RequestParam(required = false) String categoryCode) {
        return ApiResponse.ok(service.listDocuments(keyword, publishStatus, categoryCode));
    }

    @PostMapping("/documents")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> register(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.registerDocument(principal, body));
    }

    @PostMapping("/documents/{id}/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> publish(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.publishDocument(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/documents/{id}/offline")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> offline(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.offlineDocument(principal, id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/documents/{id}/metadata")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateMetadata(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        service.updateMetadata(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/documents/{id}/index")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> index(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(service.indexDocument(principal, id));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> search(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) String categoryCode,
                                                   @RequestParam(required = false) String mediaHint) {
        return ApiResponse.ok(service.searchDocuments(q, categoryCode, mediaHint));
    }

    @PostMapping("/documents/{id}/pipeline/{type}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> pipeline(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable Long id,
                                                     @PathVariable String type) {
        return ApiResponse.ok(service.runPipeline(principal, id, type));
    }

    @GetMapping("/pipelines")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<UnsDocPipeline>> pipelines(@RequestParam(required = false) Long docId,
                                                       @RequestParam(required = false) String pipelineType) {
        return ApiResponse.ok(service.listPipelines(docId, pipelineType));
    }
}
