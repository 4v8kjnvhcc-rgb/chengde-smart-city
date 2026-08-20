package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovFusionField;
import com.chengde.smartcity.masterdata.entity.GovFusionLogicEntity;
import com.chengde.smartcity.masterdata.entity.GovFusionPhysical;
import com.chengde.smartcity.masterdata.entity.GovFusionRelation;
import com.chengde.smartcity.masterdata.service.FusionModelService;
import com.chengde.smartcity.security.UserPrincipal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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

@RestController
@RequestMapping("/api/v1/governance/fusion/models")
public class FusionModelController {

    private final FusionModelService service;

    public FusionModelController(FusionModelService service) {
        this.service = service;
    }

    @GetMapping("/domains")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> listDomains() {
        return ApiResponse.ok(service.listDomains());
    }

    @GetMapping("/domains/{id}/tree")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> domainTree(@PathVariable Long id) {
        return ApiResponse.ok(service.getDomainTree(id));
    }

    /** 导出当前主题域模型报告（Excel：概要/逻辑模型图/实体/属性/物理映射） */
    @GetMapping("/domains/{id}/report")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportModelReport(@PathVariable Long id) {
        byte[] bytes = service.exportModelReport(id);
        String fileName = service.modelReportFileName(id);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    /** 可视化建模画布布局 */
    @GetMapping("/domains/{id}/canvas")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getCanvas(@PathVariable Long id) {
        return ApiResponse.ok(service.getDomainCanvas(id));
    }

    @PutMapping("/domains/{id}/canvas")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> saveCanvas(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id,
                                        @RequestBody Map<String, Object> body) {
        service.saveDomainCanvas(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/domains")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createDomain(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createDomain(principal, body));
    }

    @PutMapping("/domains/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateDomain(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        service.updateDomain(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/domains/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteDomain(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id) {
        service.deleteDomain(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/entities")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovFusionLogicEntity>> listEntities(@RequestParam Long domainId) {
        return ApiResponse.ok(service.listEntities(domainId));
    }

    @PostMapping("/entities")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createEntity(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createEntity(principal, body));
    }

    @PutMapping("/entities/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateEntity(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        service.updateEntity(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/entities/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteEntity(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id) {
        service.deleteEntity(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/fields")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovFusionField>> listFields(@RequestParam Long entityId) {
        return ApiResponse.ok(service.listFields(entityId));
    }

    @PostMapping("/fields")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createField(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createField(principal, body));
    }

    @PutMapping("/fields/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateField(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        service.updateField(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/fields/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteField(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable Long id) {
        service.deleteField(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/relations")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovFusionRelation>> listRelations(@RequestParam Long domainId) {
        return ApiResponse.ok(service.listRelations(domainId));
    }

    @PostMapping("/relations")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createRelation(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createRelation(principal, body));
    }

    @DeleteMapping("/relations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteRelation(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        service.deleteRelation(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/physical")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovFusionPhysical>> listPhysical(@RequestParam Long entityId) {
        return ApiResponse.ok(service.listPhysical(entityId));
    }

    @PostMapping("/physical")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createPhysical(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createPhysical(principal, body));
    }

    @PutMapping("/physical/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updatePhysical(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        service.updatePhysical(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/physical/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deletePhysical(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        service.deletePhysical(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/physical/{id}/preview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> previewPhysical(@PathVariable Long id) {
        return ApiResponse.ok(service.previewPhysical(id));
    }

    @PostMapping("/entities/{id}/import-fields")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> importFields(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable Long id,
                                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.importFieldsFromTable(principal, id, body));
    }
}
