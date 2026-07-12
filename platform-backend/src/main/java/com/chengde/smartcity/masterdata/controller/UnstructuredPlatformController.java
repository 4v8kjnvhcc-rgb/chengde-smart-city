package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.UnsDocCategory;
import com.chengde.smartcity.masterdata.entity.UnsDocPipeline;
import com.chengde.smartcity.masterdata.entity.UnsDocument;
import com.chengde.smartcity.masterdata.service.UnstructuredPlatformService;
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
@RequestMapping("/api/v1/unstructured/platform")
public class UnstructuredPlatformController {

    private final UnstructuredPlatformService service;

    public UnstructuredPlatformController(UnstructuredPlatformService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/categories")
    public ApiResponse<List<UnsDocCategory>> categories() {
        return ApiResponse.ok(service.listCategories());
    }

    @PostMapping("/categories")
    public ApiResponse<Long> createCategory(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createCategory(principal, body));
    }

    @GetMapping("/documents")
    public ApiResponse<List<UnsDocument>> documents(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String publishStatus) {
        return ApiResponse.ok(service.listDocuments(keyword, publishStatus));
    }

    @PostMapping("/documents")
    public ApiResponse<Long> register(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.registerDocument(principal, body));
    }

    @PostMapping("/documents/{id}/publish")
    public ApiResponse<Void> publish(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.publishDocument(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/documents/{id}/index")
    public ApiResponse<Map<String, Object>> index(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(service.indexDocument(principal, id));
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(@RequestParam(required = false) String q) {
        return ApiResponse.ok(service.searchDocuments(q));
    }

    @PostMapping("/documents/{id}/pipeline/{type}")
    public ApiResponse<Map<String, Object>> pipeline(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable Long id,
                                                     @PathVariable String type) {
        return ApiResponse.ok(service.runPipeline(principal, id, type));
    }

    @GetMapping("/pipelines")
    public ApiResponse<List<UnsDocPipeline>> pipelines(@RequestParam(required = false) Long docId) {
        return ApiResponse.ok(service.listPipelines(docId));
    }
}
