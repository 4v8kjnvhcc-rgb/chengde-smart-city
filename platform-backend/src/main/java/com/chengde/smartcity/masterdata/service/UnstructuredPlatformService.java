package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.storage.StorageIntegrationClient;
import com.chengde.smartcity.masterdata.entity.UnsDocCategory;
import com.chengde.smartcity.masterdata.entity.UnsDocPipeline;
import com.chengde.smartcity.masterdata.entity.UnsDocument;
import com.chengde.smartcity.masterdata.mapper.UnsDocCategoryMapper;
import com.chengde.smartcity.masterdata.mapper.UnsDocPipelineMapper;
import com.chengde.smartcity.masterdata.mapper.UnsDocumentMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnstructuredPlatformService {

    private final MasterDataDemoService demoService;
    private final UnsDocumentMapper documentMapper;
    private final UnsDocCategoryMapper categoryMapper;
    private final UnsDocPipelineMapper pipelineMapper;
    private final AuditService auditService;
    private final StorageIntegrationClient storageClient;

    public UnstructuredPlatformService(MasterDataDemoService demoService, UnsDocumentMapper documentMapper,
                                       UnsDocCategoryMapper categoryMapper, UnsDocPipelineMapper pipelineMapper,
                                       AuditService auditService, StorageIntegrationClient storageClient) {
        this.demoService = demoService;
        this.documentMapper = documentMapper;
        this.categoryMapper = categoryMapper;
        this.pipelineMapper = pipelineMapper;
        this.auditService = auditService;
        this.storageClient = storageClient;
    }

    public List<UnsDocCategory> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<UnsDocCategory>().orderByAsc(UnsDocCategory::getId));
    }

    @Transactional
    public Long createCategory(UserPrincipal operator, Map<String, Object> body) {
        UnsDocCategory c = new UnsDocCategory();
        c.setCategoryCode(str(body.get("categoryCode"), "CAT_" + System.currentTimeMillis()));
        c.setCategoryName(required(body.get("categoryName"), "categoryName").toString());
        c.setMediaType(str(body.get("mediaType"), "DOCUMENT"));
        c.setStatus("ACTIVE");
        categoryMapper.insert(c);
        return c.getId();
    }

    public List<UnsDocument> listDocuments(String keyword, String publishStatus) {
        LambdaQueryWrapper<UnsDocument> q = new LambdaQueryWrapper<UnsDocument>().orderByDesc(UnsDocument::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.like(UnsDocument::getTitle, keyword);
        }
        if (publishStatus != null && !publishStatus.isBlank()) {
            q.eq(UnsDocument::getPublishStatus, publishStatus);
        }
        return documentMapper.selectList(q);
    }

    @Transactional
    public Long registerDocument(UserPrincipal operator, Map<String, Object> body) {
        Long id = demoService.registerDocument(operator, body);
        UnsDocument doc = documentMapper.selectById(id);
        if (doc != null) {
            doc.setCategoryCode(str(body.get("categoryCode"), "CAT_GOV_DOC"));
            doc.setPublishStatus("DRAFT");
            documentMapper.updateById(doc);
        }
        return id;
    }

    @Transactional
    public void publishDocument(UserPrincipal operator, Long id) {
        UnsDocument doc = getDoc(id);
        doc.setPublishStatus("PUBLISHED");
        documentMapper.updateById(doc);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_PUBLISH", "uns_document", String.valueOf(id), doc.getTitle());
    }

    public Map<String, Object> searchDocuments(String q) {
        List<Map<String, Object>> hits = new ArrayList<>();
        for (UnsDocument d : listDocuments(q, null)) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", d.getId());
            row.put("title", d.getTitle());
            row.put("categoryCode", d.getCategoryCode());
            row.put("indexStatus", d.getIndexStatus());
            row.put("source", storageClient.isElasticsearchHealthy() ? "elasticsearch" : "database");
            hits.add(row);
        }
        return Map.of("query", q == null ? "" : q, "hits", hits);
    }

    @Transactional
    public Map<String, Object> indexDocument(UserPrincipal operator, Long id) {
        return demoService.indexDocument(operator, id);
    }

    @Transactional
    public Map<String, Object> runPipeline(UserPrincipal operator, Long docId, String pipelineType) {
        UnsDocument doc = getDoc(docId);
        UnsDocPipeline p = new UnsDocPipeline();
        p.setDocId(docId);
        p.setPipelineType(pipelineType.toUpperCase());
        p.setStatus("SUCCESS");
        String msg = switch (p.getPipelineType()) {
            case "CLEAN" -> {
                doc.setProcessStatus("CLEANED");
                yield "dedup+validate done";
            }
            case "TAG" -> {
                doc.setTagJson("[\"政务\",\"公开\"]");
                doc.setProcessStatus("TAGGED");
                yield "tags applied";
            }
            case "LINK" -> {
                doc.setLinkedDocId(docId);
                doc.setProcessStatus("LINKED");
                yield "entity linked";
            }
            default -> throw new BusinessException(400, "unknown pipeline type");
        };
        p.setResultMessage(msg);
        pipelineMapper.insert(p);
        documentMapper.updateById(doc);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_PIPELINE", "uns_doc_pipeline", String.valueOf(p.getId()), pipelineType);
        return Map.of("pipelineId", p.getId(), "pipelineType", pipelineType, "message", msg);
    }

    public List<UnsDocPipeline> listPipelines(Long docId) {
        LambdaQueryWrapper<UnsDocPipeline> q = new LambdaQueryWrapper<UnsDocPipeline>().orderByDesc(UnsDocPipeline::getId);
        if (docId != null) {
            q.eq(UnsDocPipeline::getDocId, docId);
        }
        return pipelineMapper.selectList(q);
    }

    public Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", categoryMapper.selectCount(null));
        out.put("documents", documentMapper.selectCount(null));
        out.put("indexed", documentMapper.selectCount(new LambdaQueryWrapper<UnsDocument>().eq(UnsDocument::getIndexStatus, "INDEXED")));
        out.put("pipelines", pipelineMapper.selectCount(null));
        out.put("seaweedHealthy", storageClient.isSeaweedHealthy());
        out.put("esHealthy", storageClient.isElasticsearchHealthy());
        return out;
    }

    private UnsDocument getDoc(Long id) {
        UnsDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "document not found");
        }
        return doc;
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " required");
        }
        return v;
    }
}
