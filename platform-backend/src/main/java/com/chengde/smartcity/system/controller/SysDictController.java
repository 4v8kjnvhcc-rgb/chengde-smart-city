package com.chengde.smartcity.system.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.SysDictItemRequest;
import com.chengde.smartcity.system.dto.SysDictRequest;
import com.chengde.smartcity.system.entity.SysDict;
import com.chengde.smartcity.system.entity.SysDictItem;
import com.chengde.smartcity.system.service.SysDictService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/dicts")
public class SysDictController {

    private final SysDictService dictService;

    public SysDictController(SysDictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:dict:list') or hasAuthority('hub:analytics:support:sys:dict') or hasAuthority('system:uum:view')")
    public ApiResponse<List<SysDict>> list() {
        return ApiResponse.ok(dictService.listDicts());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:dict:list') or hasAuthority('hub:analytics:support:sys:dict') or hasAuthority('system:uum:view')")
    public ApiResponse<SysDict> get(@PathVariable Long id) {
        return ApiResponse.ok(dictService.getDict(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:dict:add') or hasAuthority('hub:analytics:support:sys:dict')")
    public ApiResponse<Long> create(@AuthenticationPrincipal UserPrincipal principal,
                                    @Valid @RequestBody SysDictRequest request) {
        return ApiResponse.ok(dictService.createDict(principal, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:dict:edit') or hasAuthority('hub:analytics:support:sys:dict')")
    public ApiResponse<Void> update(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id,
                                    @Valid @RequestBody SysDictRequest request) {
        dictService.updateDict(principal, id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:dict:delete') or hasAuthority('hub:analytics:support:sys:dict')")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id) {
        dictService.deleteDict(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:dict:list') or hasAuthority('hub:analytics:support:sys:dict') or hasAuthority('system:uum:view')")
    public ApiResponse<List<SysDictItem>> items(@PathVariable Long id) {
        return ApiResponse.ok(dictService.listItems(id));
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:dict:add') or hasAuthority('system:dict:edit') or hasAuthority('hub:analytics:support:sys:dict')")
    public ApiResponse<Long> createItem(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id,
                                        @Valid @RequestBody SysDictItemRequest request) {
        return ApiResponse.ok(dictService.createItem(principal, id, request));
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:dict:edit') or hasAuthority('hub:analytics:support:sys:dict')")
    public ApiResponse<Void> updateItem(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long itemId,
                                        @Valid @RequestBody SysDictItemRequest request) {
        dictService.updateItem(principal, itemId, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/items/{itemId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:dict:delete') or hasAuthority('hub:analytics:support:sys:dict')")
    public ApiResponse<Void> deleteItem(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long itemId) {
        dictService.deleteItem(principal, itemId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/code/{dictCode}/items")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<SysDictItem>> itemsByCode(@PathVariable String dictCode) {
        return ApiResponse.ok(dictService.listItemsByCode(dictCode));
    }
}
