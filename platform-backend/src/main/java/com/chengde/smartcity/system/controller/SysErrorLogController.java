package com.chengde.smartcity.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.SysErrorLogReportRequest;
import com.chengde.smartcity.system.entity.SysErrorLog;
import com.chengde.smartcity.system.service.SysErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/error-logs")
public class SysErrorLogController {

    private final SysErrorLogService sysErrorLogService;

    public SysErrorLogController(SysErrorLogService sysErrorLogService) {
        this.sysErrorLogService = sysErrorLogService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:error-log:list') or hasAuthority('system:audit:list')")
    public ApiResponse<Page<SysErrorLog>> page(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String source,
                                               @RequestParam(required = false) String moduleCode,
                                               @RequestParam(required = false) String level,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {
        return ApiResponse.ok(sysErrorLogService.page(page, size, source, moduleCode, level, keyword, from, to));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:error-log:list') or hasAuthority('system:audit:list')")
    public ApiResponse<SysErrorLog> detail(@PathVariable Long id) {
        return ApiResponse.ok(sysErrorLogService.getById(id));
    }

    /** 前端/客户端上报；登录用户即可（避免未授权角色无法上报） */
    @PostMapping("/report")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> report(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestBody SysErrorLogReportRequest body,
                                    HttpServletRequest request) {
        return ApiResponse.ok(sysErrorLogService.report(body, principal, request));
    }
}
