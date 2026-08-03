package com.chengde.smartcity.system.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.PortalNavNodeRequest;
import com.chengde.smartcity.system.dto.PortalNavNodeTree;
import com.chengde.smartcity.system.entity.PortalNavNode;
import com.chengde.smartcity.system.service.PortalNavService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/portal-nav")
public class PortalNavController {

    private final PortalNavService portalNavService;

    public PortalNavController(PortalNavService portalNavService) {
        this.portalNavService = portalNavService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:portal-nav:list') or hasAuthority('hub:system:uum:portal')")
    public ApiResponse<List<?>> list(@RequestParam(defaultValue = "flat") String format) {
        if ("tree".equalsIgnoreCase(format)) {
            return ApiResponse.ok(portalNavService.listTree());
        }
        return ApiResponse.ok(portalNavService.listFlat());
    }

    @GetMapping("/enabled-tree")
    public ApiResponse<List<PortalNavNodeTree>> enabledTree(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(portalNavService.enabledTreeForUser(principal));
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:portal-nav:list') or hasAuthority('hub:system:uum:portal')")
    public ApiResponse<Long> create(@AuthenticationPrincipal UserPrincipal principal,
                                    @Valid @RequestBody PortalNavNodeRequest request) {
        return ApiResponse.ok(portalNavService.create(principal, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:portal-nav:list') or hasAuthority('hub:system:uum:portal')")
    public ApiResponse<Void> update(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id,
                                    @Valid @RequestBody PortalNavNodeRequest request) {
        portalNavService.update(principal, id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:portal-nav:list') or hasAuthority('hub:system:uum:portal')")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id) {
        portalNavService.delete(principal, id);
        return ApiResponse.ok(null);
    }

    /** 便于前端类型推断的扁平列表别名（与 GET /?format=flat 相同） */
    @GetMapping("/flat")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:portal-nav:list') or hasAuthority('hub:system:uum:portal')")
    public ApiResponse<List<PortalNavNode>> flat() {
        return ApiResponse.ok(portalNavService.listFlat());
    }
}
