package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.UnsCleanRule;
import com.chengde.smartcity.masterdata.entity.UnsDocCategory;
import com.chengde.smartcity.masterdata.entity.UnsDocPipeline;
import com.chengde.smartcity.masterdata.entity.UnsExternalPlatform;
import com.chengde.smartcity.masterdata.service.UnstructuredCleanService;
import com.chengde.smartcity.masterdata.service.UnstructuredPlatformService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
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
@RequestMapping("/api/v1/unstructured/platform")
public class UnstructuredPlatformController {

    private final UnstructuredPlatformService service;
    private final UnstructuredCleanService cleanService;

    public UnstructuredPlatformController(UnstructuredPlatformService service,
                                          UnstructuredCleanService cleanService) {
        this.service = service;
        this.cleanService = cleanService;
    }

    @GetMapping("/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<UnsDocCategory>> categories() {
        return ApiResponse.ok(service.listCategories());
    }

    @PostMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createCategory(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createCategory(principal, body));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateCategory(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        service.updateCategory(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteCategory(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        service.deleteCategory(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/external-platforms")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<UnsExternalPlatform>> externalPlatforms(
            @RequestParam(required = false) String platformName) {
        return ApiResponse.ok(service.listExternalPlatforms(platformName));
    }

    @PostMapping("/external-platforms")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createExternalPlatform(@AuthenticationPrincipal UserPrincipal principal,
                                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createExternalPlatform(principal, body));
    }

    @PutMapping("/external-platforms/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateExternalPlatform(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
        service.updateExternalPlatform(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/external-platforms/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteExternalPlatform(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id) {
        service.deleteExternalPlatform(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/external-platforms/{id}/sync")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> syncExternalPlatform(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(service.syncExternalPlatform(principal, id));
    }

    @GetMapping("/documents")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> documents(@RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String publishStatus,
                                                            @RequestParam(required = false) String categoryCode,
                                                            @RequestParam(required = false) String sourceType) {
        return ApiResponse.ok(service.listDocuments(keyword, publishStatus, categoryCode, sourceType));
    }

    @PostMapping("/documents")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> register(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.registerDocument(principal, body));
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> upload(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestParam MultipartFile file,
                                    @RequestParam(required = false) String title,
                                    @RequestParam String categoryCode,
                                    @RequestParam(required = false) String description,
                                    @RequestParam(required = false) String tagJson,
                                    @RequestParam(required = false) String sourceSystem) {
        return ApiResponse.ok(service.uploadDocument(
                principal, file, title, categoryCode, description, tagJson, sourceSystem));
    }

    @GetMapping("/documents/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> document(@PathVariable Long id) {
        return ApiResponse.ok(service.documentDetail(id));
    }

    @PutMapping("/documents/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateDocument(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        service.updateDocument(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteDocument(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        service.deleteDocument(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/documents/{id}/content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> content(@PathVariable Long id,
                                          @RequestParam(defaultValue = "false") boolean download) {
        Map<String, Object> detail = service.documentDetail(id);
        byte[] bytes = service.documentContent(id);
        String contentType = String.valueOf(detail.getOrDefault("contentType", "application/octet-stream"));
        String fileName = String.valueOf(detail.getOrDefault("originalFileName", detail.get("title")));
        ContentDisposition disposition = (download
                ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(bytes.length)
                .body(bytes);
    }

    @PostMapping("/documents/{id}/publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> publish(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.publishDocument(principal, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/documents/batch-publish")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> batchPublish(@AuthenticationPrincipal UserPrincipal principal,
                                                         @RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("ids");
        List<Long> ids = new java.util.ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Number n) {
                    ids.add(n.longValue());
                } else if (o != null) {
                    try {
                        ids.add(Long.parseLong(String.valueOf(o)));
                    } catch (Exception ignored) {
                        /* skip */
                    }
                }
            }
        }
        return ApiResponse.ok(service.batchPublishDocuments(principal, ids));
    }

    @PostMapping("/documents/{id}/offline")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> offline(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.offlineDocument(principal, id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/documents/{id}/metadata")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateMetadata(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        service.updateMetadata(principal, id, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/documents/{id}/extract-features")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> extractFeatures(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable Long id) {
        return ApiResponse.ok(service.extractFeatures(principal, id));
    }

    @PostMapping("/documents/batch-extract-features")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> batchExtractFeatures(@AuthenticationPrincipal UserPrincipal principal,
                                                              @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.batchExtractFeatures(principal, parseIdList(body)));
    }

    @PostMapping("/documents/{id}/understand")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> understand(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable Long id) {
        return ApiResponse.ok(service.understandContent(principal, id));
    }

    @PostMapping("/documents/batch-understand")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> batchUnderstand(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.batchUnderstandContent(principal, parseIdList(body)));
    }

    @GetMapping("/documents/{id}/similar")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> similar(@PathVariable Long id,
                                                          @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.findSimilar(id, limit));
    }

    @PostMapping("/documents/{id}/similar-link/{targetId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> linkSimilar(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable Long id,
                                                        @PathVariable Long targetId) {
        return ApiResponse.ok(service.linkSimilar(principal, id, targetId));
    }

    @DeleteMapping("/documents/{id}/similar-link")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> unlinkSimilar(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(service.unlinkSimilar(principal, id));
    }

    @GetMapping("/metadata/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> metadataOverview() {
        return ApiResponse.ok(service.metadataOverview());
    }

    @PostMapping("/documents/{id}/index")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> index(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(service.indexDocument(principal, id));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> search(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) String categoryCode,
                                                   @RequestParam(required = false) String mediaHint,
                                                   @RequestParam(required = false) String createdFrom,
                                                   @RequestParam(required = false) String createdTo,
                                                   @RequestParam(required = false) String updatedFrom,
                                                   @RequestParam(required = false) String updatedTo,
                                                   @RequestParam(required = false) Long minSize,
                                                   @RequestParam(required = false) Long maxSize,
                                                   @RequestParam(required = false) String tag,
                                                   @RequestParam(required = false) String sortBy,
                                                   @RequestParam(required = false) String sortDir) {
        return ApiResponse.ok(service.searchDocuments(
                q, categoryCode, mediaHint, createdFrom, createdTo, updatedFrom, updatedTo,
                minSize, maxSize, tag, sortBy, sortDir));
    }

    @PostMapping("/documents/{id}/pipeline/{type}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> pipeline(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable Long id,
                                                     @PathVariable String type) {
        return ApiResponse.ok(service.runPipeline(principal, id, type));
    }

    @GetMapping("/pipelines")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<UnsDocPipeline>> pipelines(@RequestParam(required = false) Long docId,
                                                       @RequestParam(required = false) String pipelineType) {
        return ApiResponse.ok(service.listPipelines(docId, pipelineType));
    }

    /** 任务管理列表（与 pipelines 同数据，前端统一称「任务」） */
    @GetMapping("/tasks")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<UnsDocPipeline>> tasks(@RequestParam(required = false) Long docId,
                                                   @RequestParam(required = false) String pipelineType) {
        return ApiResponse.ok(service.listPipelines(docId, pipelineType));
    }

    @GetMapping("/tag-defs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<com.chengde.smartcity.masterdata.entity.UnsTagDef>> tagDefs() {
        return ApiResponse.ok(service.listTagDefs());
    }

    @PostMapping("/tag-defs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createTagDef(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createTagDef(principal, body));
    }

    @PutMapping("/tag-defs/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateTagDef(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        service.updateTagDef(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/tag-defs/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteTagDef(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id) {
        service.deleteTagDef(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/link-rules")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<com.chengde.smartcity.masterdata.entity.UnsLinkRule>> linkRules() {
        return ApiResponse.ok(service.listLinkRules());
    }

    @PostMapping("/link-rules")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createLinkRule(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createLinkRule(principal, body));
    }

    @PutMapping("/link-rules/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateLinkRule(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        service.updateLinkRule(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/link-rules/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteLinkRule(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        service.deleteLinkRule(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/clean/overview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> cleanOverview() {
        return ApiResponse.ok(cleanService.cleanOverview());
    }

    @GetMapping("/clean/rules")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<UnsCleanRule>> cleanRules() {
        return ApiResponse.ok(cleanService.listRules());
    }

    @PostMapping("/clean/rules")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> createCleanRule(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(cleanService.createRule(principal, body));
    }

    @PutMapping("/clean/rules/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateCleanRule(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        cleanService.updateRule(principal, id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/clean/rules/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteCleanRule(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id) {
        cleanService.deleteRule(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/clean/issues")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> cleanIssues(@RequestParam(required = false) String issueStatus,
                                                              @RequestParam(required = false) String errorLevel,
                                                              @RequestParam(required = false) Long docId) {
        return ApiResponse.ok(cleanService.listIssues(issueStatus, errorLevel, docId));
    }

    @PostMapping("/clean/issues/{id}/handle")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> handleCleanIssue(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        cleanService.handleIssue(principal, id, body);
        return ApiResponse.ok(null);
    }

    private List<Long> parseIdList(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("ids");
        List<Long> ids = new java.util.ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Number n) {
                    ids.add(n.longValue());
                } else if (o != null) {
                    try {
                        ids.add(Long.parseLong(String.valueOf(o)));
                    } catch (Exception ignored) {
                        /* skip */
                    }
                }
            }
        }
        return ids;
    }
}
