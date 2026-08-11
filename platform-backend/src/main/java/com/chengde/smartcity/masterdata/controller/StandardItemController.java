package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovNamingStandard;
import com.chengde.smartcity.masterdata.entity.GovStandardCodebook;
import com.chengde.smartcity.masterdata.entity.GovStandardItem;
import com.chengde.smartcity.masterdata.entity.GovStandardItemVersion;
import com.chengde.smartcity.masterdata.entity.GovStandardMapping;
import com.chengde.smartcity.masterdata.service.StandardItemService;
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
@RequestMapping("/api/v1/governance/standards")
public class StandardItemController {

    private final StandardItemService service;

    public StandardItemController(StandardItemService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovStandardItem>> list(@RequestParam(required = false) String itemType,
                                                   @RequestParam(required = false) String publishStatus,
                                                   @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(itemType, publishStatus, keyword));
    }

    // ---- A4 naming（字面路径须先于 /{id}）----

    @GetMapping("/naming")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovNamingStandard>> listNaming(@RequestParam(required = false) String namingType,
                                                           @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listNaming(namingType, status));
    }

    @PostMapping("/naming")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createNaming(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createNaming(principal, body));
    }

    @PutMapping("/naming/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateNaming(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        service.updateNaming(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/naming/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteNaming(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id) {
        service.deleteNaming(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/naming/validate")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> validateNaming(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.validateNaming(body));
    }

    @PostMapping("/naming/generate-task-name")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> generateTaskName(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.generateTaskName(body));
    }

    // ---- A10 mapping ----

    @GetMapping("/mappings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovStandardMapping>> listMappings(@RequestParam(required = false) Long standardItemId,
                                                              @RequestParam(required = false) String mappingStatus) {
        return ApiResponse.ok(service.listMappings(standardItemId, mappingStatus));
    }

    @PostMapping("/mappings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createMapping(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createMapping(principal, body));
    }

    @PutMapping("/mappings/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateMapping(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        service.updateMapping(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/mappings/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteMapping(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long id) {
        service.deleteMapping(principal, id);
        return ApiResponse.ok(null);
    }

    // ---- codebook （非 id 前缀）----

    @PutMapping("/codebook/{codeId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateCodebook(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long codeId,
                                            @RequestBody Map<String, Object> body) {
        service.updateCodebook(principal, codeId, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/codebook/{codeId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteCodebook(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long codeId) {
        service.deleteCodebook(principal, codeId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovStandardItem> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
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
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id) {
        service.delete(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovStandardItem> publish(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long id) {
        return ApiResponse.ok(service.publish(principal, id));
    }

    @PostMapping("/{id}/offline")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovStandardItem> offline(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long id) {
        return ApiResponse.ok(service.offline(principal, id));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovStandardItemVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(service.listVersions(id));
    }

    @GetMapping("/{id}/versions/{versionNo}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<GovStandardItemVersion> version(@PathVariable Long id,
                                                       @PathVariable Integer versionNo) {
        return ApiResponse.ok(service.getVersion(id, versionNo));
    }

    // ---- A3 codebook under item ----

    @GetMapping("/{id}/codebook")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovStandardCodebook>> listCodebook(@PathVariable Long id) {
        return ApiResponse.ok(service.listCodebook(id));
    }

    @PostMapping("/{id}/codebook")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createCodebook(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createCodebook(principal, id, body));
    }

    @PostMapping("/{id}/codebook/import")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> importCodebook(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable Long id,
                                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.importCodebook(principal, id, body));
    }

    @GetMapping("/{id}/codebook/export")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovStandardCodebook>> exportCodebook(@PathVariable Long id) {
        return ApiResponse.ok(service.exportCodebook(id));
    }

    @PostMapping("/{id}/codebook/from-dict")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> fromDict(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable Long id,
                                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.fromDict(principal, id, body));
    }

    @GetMapping("/{id}/mappings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GovStandardMapping>> listItemMappings(@PathVariable Long id) {
        return ApiResponse.ok(service.listMappings(id, null));
    }
}
