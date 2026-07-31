package com.chengde.smartcity.system.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.service.MailConfigService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/mail-config")
public class MailConfigController {

    private final MailConfigService mailConfigService;

    public MailConfigController(MailConfigService mailConfigService) {
        this.mailConfigService = mailConfigService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config') or hasAuthority('system:uum:view')")
    public ApiResponse<Map<String, Object>> get() {
        return ApiResponse.ok(mailConfigService.toView());
    }

    @PutMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config')")
    public ApiResponse<Void> save(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestBody Map<String, Object> body) {
        mailConfigService.save(principal, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/test")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config')")
    public ApiResponse<Void> test(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestBody Map<String, Object> body) {
        Object to = body == null ? null : body.get("to");
        mailConfigService.sendTest(principal, to == null ? null : String.valueOf(to));
        return ApiResponse.ok(null);
    }
}
