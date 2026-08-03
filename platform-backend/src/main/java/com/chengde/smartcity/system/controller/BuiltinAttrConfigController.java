package com.chengde.smartcity.system.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.service.BuiltinAttrConfigService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/builtin-attr-config")
public class BuiltinAttrConfigController {

    private final BuiltinAttrConfigService service;

    public BuiltinAttrConfigController(BuiltinAttrConfigService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Boolean>> get() {
        return ApiResponse.ok(service.getControl());
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> save(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestBody Map<String, Object> body) {
        service.save(principal, body);
        return ApiResponse.ok(null);
    }
}
