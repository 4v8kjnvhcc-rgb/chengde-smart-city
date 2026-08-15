package com.chengde.smartcity.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengde.smartcity.auth.TransportCryptoService;
import com.chengde.smartcity.auth.dto.EncryptedTransportRequest;
import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.MenuTreeNode;
import com.chengde.smartcity.system.dto.OrgCreateRequest;
import com.chengde.smartcity.system.dto.OrgUpdateRequest;
import com.chengde.smartcity.system.dto.RegisterMenuDeleteRequest;
import com.chengde.smartcity.system.dto.RegisterMenuUpsertRequest;
import com.chengde.smartcity.system.dto.RoleCreateRequest;
import com.chengde.smartcity.system.dto.RoleMenuAssignRequest;
import com.chengde.smartcity.system.dto.RoleUpdateRequest;
import com.chengde.smartcity.system.dto.UserCreateRequest;
import com.chengde.smartcity.system.dto.UserListItem;
import com.chengde.smartcity.system.dto.UserUpdateRequest;
import com.chengde.smartcity.system.entity.AuditLog;
import com.chengde.smartcity.system.entity.SysMenu;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.entity.SysRole;
import com.chengde.smartcity.system.entity.SysUser;
import com.chengde.smartcity.system.mapper.AuditLogMapper;
import com.chengde.smartcity.system.service.MenuService;
import com.chengde.smartcity.system.service.OrgService;
import com.chengde.smartcity.system.service.RoleService;
import com.chengde.smartcity.system.service.SecurityConfigService;
import com.chengde.smartcity.system.service.UserService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/system")
public class SystemController {

    private final MenuService menuService;
    private final UserService userService;
    private final RoleService roleService;
    private final OrgService orgService;
    private final AuditLogMapper auditLogMapper;
    private final SecurityConfigService securityConfigService;
    private final TransportCryptoService transportCryptoService;

    public SystemController(MenuService menuService, UserService userService, RoleService roleService,
                            OrgService orgService, AuditLogMapper auditLogMapper,
                            SecurityConfigService securityConfigService,
                            TransportCryptoService transportCryptoService) {
        this.menuService = menuService;
        this.userService = userService;
        this.roleService = roleService;
        this.orgService = orgService;
        this.auditLogMapper = auditLogMapper;
        this.securityConfigService = securityConfigService;
        this.transportCryptoService = transportCryptoService;
    }

    @GetMapping("/menus/me")
    public ApiResponse<List<MenuTreeNode>> myMenus(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(menuService.treeForUser(principal));
    }

    @GetMapping("/menus")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:menu:list') or hasAuthority('system:role:list') or hasAuthority('system:org:list')")
    public ApiResponse<List<SysMenu>> allMenus(
            @RequestParam(required = false, defaultValue = "false") boolean manage) {
        return ApiResponse.ok(manage ? menuService.listForManage() : menuService.listAll());
    }

    @PostMapping("/menus")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:menu:list')")
    public ApiResponse<Long> createMenu(@Valid @RequestBody RegisterMenuUpsertRequest request) {
        return ApiResponse.ok(menuService.createMenu(request));
    }

    @PutMapping("/menus/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:menu:list')")
    public ApiResponse<Void> updateMenu(@PathVariable Long id,
                                        @Valid @RequestBody RegisterMenuUpsertRequest request) {
        menuService.updateMenu(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/menus")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:menu:list')")
    public ApiResponse<Void> deleteMenus(@Valid @RequestBody RegisterMenuDeleteRequest request) {
        menuService.deleteMenus(request.getIds());
        return ApiResponse.ok(null);
    }

    @PutMapping("/menus/{id}/move")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:menu:list')")
    public ApiResponse<Void> moveMenu(@PathVariable Long id, @RequestParam int direction) {
        menuService.moveSort(id, direction);
        return ApiResponse.ok(null);
    }

    @GetMapping("/menus/register-scope")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<SysMenu>> registerScopeMenus() {
        return ApiResponse.ok(menuService.listRegisterScope());
    }

    @PostMapping("/menus/register-scope")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Long> createRegisterMenu(@Valid @RequestBody RegisterMenuUpsertRequest request) {
        return ApiResponse.ok(menuService.createRegisterMenu(request));
    }

    @PutMapping("/menus/register-scope/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> updateRegisterMenu(@PathVariable Long id,
                                                @Valid @RequestBody RegisterMenuUpsertRequest request) {
        menuService.updateRegisterMenu(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/menus/register-scope")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> deleteRegisterMenus(@Valid @RequestBody RegisterMenuDeleteRequest request) {
        menuService.deleteRegisterMenus(request.getIds());
        return ApiResponse.ok(null);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:user:list')")
    public ApiResponse<Page<UserListItem>> users(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgId) {
        return ApiResponse.ok(userService.pageWithRoles(principal, page, size, keyword, orgId));
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:user:list')")
    public ApiResponse<Long> createUser(@AuthenticationPrincipal UserPrincipal principal,
                                        @Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(userService.create(principal, request));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:user:list')")
    public ApiResponse<Void> updateUser(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id,
                                        @RequestBody UserUpdateRequest request) {
        userService.update(principal, id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:user:list')")
    public ApiResponse<Void> disableUser(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable Long id) {
        userService.disable(principal, id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/users/{id}/hard")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:user:list')")
    public ApiResponse<Void> deleteUser(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id) {
        userService.delete(principal, id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/users/{id}/password")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:user:list')")
    public ApiResponse<Void> resetPassword(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long id,
                                           @Valid @RequestBody EncryptedTransportRequest request) {
        String pwd = transportCryptoService.decryptPassword(request);
        userService.resetPassword(principal, id, pwd);
        return ApiResponse.ok(null);
    }

    @GetMapping("/users/{id}/roles")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:user:list')")
    public ApiResponse<List<Long>> userRoles(@PathVariable Long id) {
        return ApiResponse.ok(userService.roleIdsOfUser(id));
    }

    @GetMapping("/roles")
    public ApiResponse<List<SysRole>> roles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean includeDisabled) {
        return ApiResponse.ok(roleService.list(keyword, includeDisabled));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:role:list')")
    public ApiResponse<Long> createRole(@AuthenticationPrincipal UserPrincipal principal,
                                        @Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.ok(roleService.create(principal, request));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:role:list')")
    public ApiResponse<Void> updateRole(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id,
                                        @RequestBody RoleUpdateRequest request) {
        roleService.update(principal, id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:role:list')")
    public ApiResponse<Void> deleteRole(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id) {
        roleService.delete(principal, id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/roles/{id}/menus")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:role:list') or hasAuthority('system:org:list')")
    public ApiResponse<Void> assignRoleMenus(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id,
                                             @Valid @RequestBody RoleMenuAssignRequest request) {
        roleService.assignMenus(principal, id, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/roles/{id}/menus")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:role:list') or hasAuthority('system:org:list')")
    public ApiResponse<List<Long>> roleMenus(@PathVariable Long id) {
        return ApiResponse.ok(roleService.menuIdsOfRole(id));
    }

    /**
     * 机构列表（只读）。需求申请「数据提供单位」、编目提供方等下拉均依赖本接口；
     * 部门管理员通常无 system:org:list（机构管理权），故查询对已登录用户开放；增删改仍受写权限约束。
     */
    @GetMapping("/orgs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<SysOrg>> orgs() {
        return ApiResponse.ok(orgService.list());
    }

    @PostMapping("/orgs")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:org:list')")
    public ApiResponse<Long> createOrg(@AuthenticationPrincipal UserPrincipal principal,
                                       @Valid @RequestBody OrgCreateRequest request) {
        return ApiResponse.ok(orgService.create(principal, request));
    }

    @PutMapping("/orgs/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:org:list')")
    public ApiResponse<Void> updateOrg(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long id,
                                       @RequestBody OrgUpdateRequest request) {
        orgService.update(principal, id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/orgs/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:org:list')")
    public ApiResponse<Void> deleteOrg(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long id) {
        orgService.delete(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('system:audit:list')")
    public ApiResponse<Page<AuditLog>> auditLogs(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) String username,
                                                 @RequestParam(required = false) String action) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditLog> q =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditLog>()
                        .orderByDesc(AuditLog::getId);
        if (username != null && !username.isBlank()) {
            q.like(AuditLog::getUsername, username.trim());
        }
        if (action != null && !action.isBlank()) {
            q.eq(AuditLog::getAction, action.trim());
        }
        return ApiResponse.ok(auditLogMapper.selectPage(new Page<>(page, size), q));
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
