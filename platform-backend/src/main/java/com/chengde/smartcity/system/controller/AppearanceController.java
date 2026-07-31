package com.chengde.smartcity.system.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.service.AppearanceService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/system/appearance")
public class AppearanceController {

    private final AppearanceService appearanceService;

    public AppearanceController(AppearanceService appearanceService) {
        this.appearanceService = appearanceService;
    }

    @GetMapping("/public")
    public ApiResponse<Map<String, Object>> publicConfig() {
        return ApiResponse.ok(appearanceService.toPublicMap(appearanceService.requireRow()));
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config') or hasAuthority('system:uum:view')")
    public ApiResponse<Map<String, Object>> get() {
        return ApiResponse.ok(appearanceService.toAdminMap(appearanceService.requireRow()));
    }

    @PutMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config')")
    public ApiResponse<Void> save(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestBody Map<String, Object> body) {
        appearanceService.save(principal, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config')")
    public ApiResponse<Map<String, Object>> upload(@AuthenticationPrincipal UserPrincipal principal,
                                                   @RequestParam String kind,
                                                   @RequestParam("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(appearanceService.uploadAsset(principal, kind, file));
    }

    @PostMapping("/theme/upload")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config')")
    public ApiResponse<Map<String, Object>> uploadTheme(@AuthenticationPrincipal UserPrincipal principal,
                                                        @RequestParam String name,
                                                        @RequestParam(required = false) String primaryColor,
                                                        @RequestParam(required = false) String sidebarBg,
                                                        @RequestParam(value = "file", required = false) MultipartFile file)
            throws Exception {
        return ApiResponse.ok(appearanceService.uploadCustomTheme(principal, name, primaryColor, sidebarBg, file));
    }

    @GetMapping("/theme/builtin/{id}/download")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config')")
    public ResponseEntity<byte[]> downloadBuiltin(@PathVariable String id) {
        byte[] body = appearanceService.downloadBuiltinTheme(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @DeleteMapping("/theme/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('system:security:config')")
    public ApiResponse<Void> deleteTheme(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable String id) {
        appearanceService.deleteCustomTheme(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/files/**")
    public ResponseEntity<Resource> file(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String marker = "/appearance/files/";
        int idx = uri.indexOf(marker);
        String relative = idx >= 0 ? uri.substring(idx + marker.length()) : "";
        Resource resource = appearanceService.resolveFile(relative);
        String name = relative.contains("/") ? relative.substring(relative.lastIndexOf('/') + 1) : relative;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                .body(resource);
    }
}
