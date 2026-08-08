package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovMetaSourceCategory;
import com.chengde.smartcity.masterdata.service.MetaSourceCategoryService;
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
@RequestMapping("/api/v1/governance/platform/metadata/source-categories")
public class MetaSourceCategoryController {

    private final MetaSourceCategoryService service;

    public MetaSourceCategoryController(MetaSourceCategoryService service) {
        this.service = service;
    }

    @GetMapping("/tree")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> tree(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listTree(keyword));
    }

    @GetMapping("/tag-options")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> tagOptions() {
        return ApiResponse.ok(service.listTagOptions());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovMetaSourceCategory>> children(@RequestParam Long parentId,
                                                             @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listChildren(parentId, keyword));
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
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.delete(principal, id);
        return ApiResponse.ok(null);
    }
}
