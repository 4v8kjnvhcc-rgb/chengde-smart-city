package com.chengde.smartcity.system.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.ClusterAccountRequest;
import com.chengde.smartcity.system.entity.SysClusterAccount;
import com.chengde.smartcity.system.service.ClusterAccountService;
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
@RequestMapping("/api/v1/system/cluster-accounts")
public class ClusterAccountController {

    private final ClusterAccountService clusterAccountService;

    public ClusterAccountController(ClusterAccountService clusterAccountService) {
        this.clusterAccountService = clusterAccountService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:analytics:support:users:cluster')")
    public ApiResponse<List<SysClusterAccount>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(clusterAccountService.list(keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:analytics:support:users:cluster')")
    public ApiResponse<SysClusterAccount> get(@PathVariable Long id) {
        return ApiResponse.ok(clusterAccountService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:analytics:support:users:cluster')")
    public ApiResponse<Long> create(@AuthenticationPrincipal UserPrincipal principal,
                                    @Valid @RequestBody ClusterAccountRequest request) {
        return ApiResponse.ok(clusterAccountService.create(principal, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:analytics:support:users:cluster')")
    public ApiResponse<Void> update(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id,
                                    @Valid @RequestBody ClusterAccountRequest request) {
        clusterAccountService.update(principal, id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('hub:analytics:support:users:cluster')")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id) {
        clusterAccountService.delete(principal, id);
        return ApiResponse.ok(null);
    }
}
