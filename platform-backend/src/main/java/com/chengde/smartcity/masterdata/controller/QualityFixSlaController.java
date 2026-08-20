package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovQualityFixSla;
import com.chengde.smartcity.masterdata.service.QualityFixSlaService;
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
@RequestMapping("/api/v1/governance/quality/fix-sla")
public class QualityFixSlaController {

    private final QualityFixSlaService service;

    public QualityFixSlaController(QualityFixSlaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovQualityFixSla>> list(@RequestParam(required = false) String severity,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(severity, status));
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
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
