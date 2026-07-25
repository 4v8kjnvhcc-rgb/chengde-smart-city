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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnstructuredPlatformService {

    private static final Set<String> PIPE_TYPES = Set.of("CLEAN", "TAG", "LINK");

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
        c.setMediaType(str(body.get("mediaType"), "DOCUMENT").toUpperCase(Locale.ROOT));
        c.setStatus("ACTIVE");
        categoryMapper.insert(c);
        return c.getId();
    }

    @Transactional
    public void updateCategory(UserPrincipal operator, Long id, Map<String, Object> body) {
        UnsDocCategory c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BusinessException(404, "分类不存在");
        }
        if (body.containsKey("categoryName")) {
            c.setCategoryName(required(body.get("categoryName"), "categoryName").toString());
        }
        if (body.containsKey("mediaType")) {
            c.setMediaType(str(body.get("mediaType"), c.getMediaType()).toUpperCase(Locale.ROOT));
        }
        if (body.containsKey("status")) {
            c.setStatus(str(body.get("status"), c.getStatus()));
        }
        categoryMapper.updateById(c);
    }

    @Transactional
    public void deleteCategory(UserPrincipal operator, Long id) {
        UnsDocCategory c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BusinessException(404, "分类不存在");
        }
        long used = documentMapper.selectCount(new LambdaQueryWrapper<UnsDocument>()
                .eq(UnsDocument::getCategoryCode, c.getCategoryCode()));
        if (used > 0) {
            throw new BusinessException(400, "分类下仍有文档，不可删除");
        }
        categoryMapper.deleteById(id);
    }

    public List<UnsDocument> listDocuments(String keyword, String publishStatus, String categoryCode) {
        LambdaQueryWrapper<UnsDocument> q = new LambdaQueryWrapper<UnsDocument>().orderByDesc(UnsDocument::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.like(UnsDocument::getTitle, keyword);
        }
        if (publishStatus != null && !publishStatus.isBlank()) {
            q.eq(UnsDocument::getPublishStatus, publishStatus);
        }
        if (categoryCode != null && !categoryCode.isBlank()) {
            q.eq(UnsDocument::getCategoryCode, categoryCode.trim());
        }
        return documentMapper.selectList(q);
    }

    @Transactional
    public Long registerDocument(UserPrincipal operator, Map<String, Object> body) {
        String categoryCode = str(body.get("categoryCode"), null);
        if (categoryCode == null || categoryCode.isBlank()) {
            throw new BusinessException(400, "须选择已登记的文件分类");
        }
        UnsDocCategory cat = categoryMapper.selectOne(new LambdaQueryWrapper<UnsDocCategory>()
                .eq(UnsDocCategory::getCategoryCode, categoryCode)
                .last("limit 1"));
        if (cat == null) {
            throw new BusinessException(400, "分类不存在，请先在「数据分类管理」创建：" + categoryCode);
        }
        Long id = demoService.registerDocument(operator, body);
        UnsDocument doc = documentMapper.selectById(id);
        if (doc != null) {
            doc.setCategoryCode(categoryCode);
            doc.setPublishStatus("DRAFT");
            doc.setProcessStatus(str(body.get("processStatus"), "PENDING"));
            documentMapper.updateById(doc);
        }
        return id;
    }

    @Transactional
    public void publishDocument(UserPrincipal operator, Long id) {
        UnsDocument doc = getDoc(id);
        if ("PUBLISHED".equalsIgnoreCase(doc.getPublishStatus())) {
            throw new BusinessException(400, "文档已发布");
        }
        if (doc.getCategoryCode() == null || doc.getCategoryCode().isBlank()) {
            throw new BusinessException(400, "发布前须绑定文件分类");
        }
        doc.setPublishStatus("PUBLISHED");
        documentMapper.updateById(doc);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_PUBLISH", "uns_document", String.valueOf(id), doc.getTitle());
    }

    @Transactional
    public void offlineDocument(UserPrincipal operator, Long id) {
        UnsDocument doc = getDoc(id);
        if (!"PUBLISHED".equalsIgnoreCase(doc.getPublishStatus())) {
            throw new BusinessException(400, "仅已发布文档可下线");
        }
        doc.setPublishStatus("OFFLINE");
        documentMapper.updateById(doc);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_OFFLINE", "uns_document", String.valueOf(id), doc.getTitle());
    }

    @Transactional
    public void updateMetadata(UserPrincipal operator, Long id, Map<String, Object> body) {
        UnsDocument doc = getDoc(id);
        if (body.containsKey("tagJson")) {
            doc.setTagJson(str(body.get("tagJson"), "[]"));
        }
        if (body.containsKey("title")) {
            doc.setTitle(required(body.get("title"), "title").toString());
        }
        documentMapper.updateById(doc);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_META_UPDATE", "uns_document", String.valueOf(id), doc.getTitle());
    }

    public Map<String, Object> searchDocuments(String q, String categoryCode, String mediaHint) {
        List<Map<String, Object>> hits = new ArrayList<>();
        boolean es = storageClient.isElasticsearchHealthy();
        String source = "database";
        if (es && q != null && !q.isBlank()) {
            List<Map<String, Object>> esHits = storageClient.searchDocuments(q, 50);
            if (!esHits.isEmpty()) {
                source = "elasticsearch";
                for (Map<String, Object> eh : esHits) {
                    Long id = null;
                    try {
                        id = Long.valueOf(String.valueOf(eh.get("id")));
                    } catch (Exception ignored) {
                        continue;
                    }
                    UnsDocument d = documentMapper.selectById(id);
                    if (d == null) continue;
                    if (categoryCode != null && !categoryCode.isBlank()
                            && !categoryCode.equals(d.getCategoryCode())) {
                        continue;
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", d.getId());
                    row.put("title", d.getTitle());
                    row.put("categoryCode", d.getCategoryCode());
                    row.put("indexStatus", d.getIndexStatus());
                    row.put("publishStatus", d.getPublishStatus());
                    row.put("tagJson", d.getTagJson());
                    row.put("storageKey", d.getStorageKey());
                    row.put("score", eh.get("score"));
                    row.put("source", "elasticsearch");
                    hits.add(row);
                }
            }
        }
        if (hits.isEmpty()) {
            source = "database";
            for (UnsDocument d : listDocuments(q, null, categoryCode)) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", d.getId());
                row.put("title", d.getTitle());
                row.put("categoryCode", d.getCategoryCode());
                row.put("indexStatus", d.getIndexStatus());
                row.put("publishStatus", d.getPublishStatus());
                row.put("tagJson", d.getTagJson());
                row.put("storageKey", d.getStorageKey());
                row.put("source", "database");
                hits.add(row);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", q == null ? "" : q);
        out.put("mediaHint", mediaHint == null ? "" : mediaHint);
        out.put("hits", hits);
        out.put("source", source);
        out.put("esHealthy", es);
        return out;
    }

    @Transactional
    public Map<String, Object> indexDocument(UserPrincipal operator, Long id) {
        return demoService.indexDocument(operator, id);
    }

    @Transactional
    public Map<String, Object> runPipeline(UserPrincipal operator, Long docId, String pipelineType) {
        UnsDocument doc = getDoc(docId);
        String type = pipelineType == null ? "" : pipelineType.trim().toUpperCase(Locale.ROOT);
        if (!PIPE_TYPES.contains(type)) {
            throw new BusinessException(400, "处理类型须为 CLEAN / TAG / LINK");
        }
        UnsDocPipeline p = new UnsDocPipeline();
        p.setDocId(docId);
        p.setPipelineType(type);
        // 控制面台账处理：明确 LEDGER，不冒充外部清洗/NLP 引擎
        p.setStatus("LEDGER");
        String msg = switch (type) {
            case "CLEAN" -> {
                String title = doc.getTitle() == null ? "" : doc.getTitle().trim();
                doc.setTitle(title);
                long dup = documentMapper.selectCount(new LambdaQueryWrapper<UnsDocument>()
                        .eq(UnsDocument::getTitle, title)
                        .ne(UnsDocument::getId, docId));
                doc.setProcessStatus("CLEANED");
                yield dup > 0
                        ? "台账清洗完成：标题已规整；检测到同名文档 " + dup + " 条（未接外部清洗引擎）"
                        : "台账清洗完成：标题已规整（未接外部清洗引擎）";
            }
            case "TAG" -> {
                if (doc.getTagJson() == null || doc.getTagJson().isBlank() || "[]".equals(doc.getTagJson())) {
                    doc.setTagJson("[\"政务\",\"公开\"]");
                }
                doc.setProcessStatus("TAGGED");
                yield "台账标注完成：" + doc.getTagJson() + "（未接外部标签引擎）";
            }
            case "LINK" -> {
                doc.setLinkedDocId(docId);
                doc.setProcessStatus("LINKED");
                yield "台账关联完成：已回填文档自身关联键（未接外部关联引擎）";
            }
            default -> throw new BusinessException(400, "未知处理类型");
        };
        p.setResultMessage(msg);
        pipelineMapper.insert(p);
        documentMapper.updateById(doc);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_PIPELINE", "uns_doc_pipeline", String.valueOf(p.getId()), type);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pipelineId", p.getId());
        out.put("pipelineType", type);
        out.put("status", "LEDGER");
        out.put("engineMode", "LEDGER");
        out.put("message", msg);
        return out;
    }

    public List<UnsDocPipeline> listPipelines(Long docId, String pipelineType) {
        LambdaQueryWrapper<UnsDocPipeline> q = new LambdaQueryWrapper<UnsDocPipeline>().orderByDesc(UnsDocPipeline::getId);
        if (docId != null) {
            q.eq(UnsDocPipeline::getDocId, docId);
        }
        if (pipelineType != null && !pipelineType.isBlank()) {
            q.eq(UnsDocPipeline::getPipelineType, pipelineType.trim().toUpperCase(Locale.ROOT));
        }
        return pipelineMapper.selectList(q);
    }

    public Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", categoryMapper.selectCount(null));
        out.put("documents", documentMapper.selectCount(null));
        out.put("published", documentMapper.selectCount(new LambdaQueryWrapper<UnsDocument>()
                .eq(UnsDocument::getPublishStatus, "PUBLISHED")));
        out.put("indexed", documentMapper.selectCount(new LambdaQueryWrapper<UnsDocument>()
                .eq(UnsDocument::getIndexStatus, "INDEXED")));
        out.put("pipelines", pipelineMapper.selectCount(null));
        out.put("seaweedHealthy", storageClient.isSeaweedHealthy());
        out.put("esHealthy", storageClient.isElasticsearchHealthy());
        return out;
    }

    private UnsDocument getDoc(Long id) {
        UnsDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        return doc;
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v).trim();
    }

    private Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " 不能为空");
        }
        return v;
    }
}
