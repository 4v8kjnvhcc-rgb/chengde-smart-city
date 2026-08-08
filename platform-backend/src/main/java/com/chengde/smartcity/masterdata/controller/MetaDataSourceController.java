package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.service.MetaDataSourceService;
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
@RequestMapping("/api/v1/governance/platform/metadata/data-sources")
public class MetaDataSourceController {

    private final MetaDataSourceService service;

    public MetaDataSourceController(MetaDataSourceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Long categoryId,
                                                       @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(categoryId, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.getDetail(id));
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

    @PutMapping("/{id}/move")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> move(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable Long id,
                                  @RequestBody Map<String, Object> body) {
        Object categoryId = body.get("categoryId");
        if (categoryId == null) {
            throw new BusinessException(400, "目标分类不能为空");
        }
        service.move(principal, id, Long.valueOf(String.valueOf(categoryId)));
        return ApiResponse.ok(null);
    }

    @PostMapping("/test-connection")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> testConnection(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.testConnection(body));
    }

    @GetMapping("/collect/candidates")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> collectCandidates(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.listCollectCandidates(principal));
    }

    @PostMapping("/collect")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> collect(@AuthenticationPrincipal UserPrincipal principal,
                                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.collectFromIngestion(principal, body));
    }
}
