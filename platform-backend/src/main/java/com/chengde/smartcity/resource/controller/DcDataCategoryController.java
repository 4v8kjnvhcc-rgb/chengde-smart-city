package com.chengde.smartcity.resource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.resource.entity.DcDataCategory;
import com.chengde.smartcity.resource.service.DcDataCategoryService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据分类目录。契约路径：/api/resource/data-category；
 * 本仓统一网关前缀为 /api/v1，故实际映射为 /api/v1/resource/data-category。
 */
@RestController
@RequestMapping("/api/v1/resource/data-category")
public class DcDataCategoryController {

    private final DcDataCategoryService service;

    public DcDataCategoryController(DcDataCategoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('resource:data-category:manage') or hasAuthority('system:uum:view')")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<DcDataCategory> p = service.page(keyword, page, size);
        return ApiResponse.ok(service.toPageResult(p));
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('resource:data-category:manage')")
    public ApiResponse<String> save(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.save(body));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('resource:data-category:manage')")
    public ApiResponse<Void> delete(@PathVariable String uuid) {
        service.delete(uuid);
        return ApiResponse.ok(null);
    }
}
