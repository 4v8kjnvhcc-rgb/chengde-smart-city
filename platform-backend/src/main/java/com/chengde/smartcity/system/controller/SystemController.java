package com.chengde.smartcity.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.MenuTreeNode;
import com.chengde.smartcity.system.dto.RoleMenuAssignRequest;
import com.chengde.smartcity.system.dto.UserCreateRequest;
import com.chengde.smartcity.system.dto.UserUpdateRequest;
import com.chengde.smartcity.system.entity.AuditLog;
import com.chengde.smartcity.system.entity.SysMenu;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.entity.SysRole;
import com.chengde.smartcity.system.entity.SysUser;
import com.chengde.smartcity.system.mapper.AuditLogMapper;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import com.chengde.smartcity.system.service.MenuService;
import com.chengde.smartcity.system.service.RoleService;
import com.chengde.smartcity.system.service.SecurityConfigService;
import com.chengde.smartcity.system.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final MenuService menuService;
    private final UserService userService;
    private final RoleService roleService;
    private final SysOrgMapper orgMapper;
    private final AuditLogMapper auditLogMapper;
    private final SecurityConfigService securityConfigService;

    public SystemController(MenuService menuService, UserService userService, RoleService roleService,
                              SysOrgMapper orgMapper, AuditLogMapper auditLogMapper,
                              SecurityConfigService securityConfigService) {
        this.menuService = menuService;
        this.userService = userService;
        this.roleService = roleService;
        this.orgMapper = orgMapper;
        this.auditLogMapper = auditLogMapper;
        this.securityConfigService = securityConfigService;
    }

    @GetMapping("/menus/me")
    public ApiResponse<List<MenuTreeNode>> myMenus(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(menuService.treeForUser(principal.getUserId()));
    }

    @GetMapping("/menus")
    @PreAuthorize("hasAuthority('system:menu:list')")
    public ApiResponse<List<SysMenu>> allMenus() {
        return ApiResponse.ok(menuService.listAll());
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('system:user:list')")
    public ApiResponse<Page<SysUser>> users(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(userService.page(principal, page, size, keyword));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('system:user:add')")
    public ApiResponse<Long> createUser(@AuthenticationPrincipal UserPrincipal principal,
                                        @Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(userService.create(principal, request));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('system:user:edit')")
    public ApiResponse<Void> updateUser(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id,
                                        @RequestBody UserUpdateRequest request) {
        userService.update(principal, id, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/roles")
  public ApiResponse<List<SysRole>> roles() {
        return ApiResponse.ok(roleService.list());
    }

    @PutMapping("/roles/{id}/menus")
    public ApiResponse<Void> assignRoleMenus(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id,
                                             @Valid @RequestBody RoleMenuAssignRequest request) {
        roleService.assignMenus(principal, id, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/roles/{id}/menus")
    public ApiResponse<List<Long>> roleMenus(@PathVariable Long id) {
        return ApiResponse.ok(roleService.menuIdsOfRole(id));
    }

    @GetMapping("/orgs")
    @PreAuthorize("hasAuthority('system:org:list')")
    public ApiResponse<List<SysOrg>> orgs() {
        return ApiResponse.ok(orgMapper.selectList(null));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('system:audit:list')")
    public ApiResponse<Page<AuditLog>> auditLogs(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(auditLogMapper.selectPage(new Page<>(page, size), null));
    }

    @GetMapping("/security-config")
    @PreAuthorize("hasAuthority('system:security:config')")
    public ApiResponse<Map<String, String>> securityConfig() {
        return ApiResponse.ok(securityConfigService.getAll());
    }

    @PutMapping("/security-config")
    @PreAuthorize("hasAuthority('system:security:config')")
    public ApiResponse<Void> updateSecurityConfig(@RequestBody Map<String, String> body) {
        securityConfigService.update(body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/permissions/me")
    public ApiResponse<List<String>> myPermissions(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(List.copyOf(principal.getPermissions()));
    }
}
