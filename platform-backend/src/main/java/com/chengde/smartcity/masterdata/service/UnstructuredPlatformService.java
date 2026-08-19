package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.storage.StorageIntegrationClient;
import com.chengde.smartcity.masterdata.entity.UnsDocCategory;
import com.chengde.smartcity.masterdata.entity.UnsDocPipeline;
import com.chengde.smartcity.masterdata.entity.UnsDocument;
import com.chengde.smartcity.masterdata.entity.UnsExternalPlatform;
import com.chengde.smartcity.masterdata.mapper.UnsDocCategoryMapper;
import com.chengde.smartcity.masterdata.mapper.UnsDocPipelineMapper;
import com.chengde.smartcity.masterdata.mapper.UnsDocumentMapper;
import com.chengde.smartcity.masterdata.mapper.UnsExternalPlatformMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UnstructuredPlatformService {

    private static final Logger log = LoggerFactory.getLogger(UnstructuredPlatformService.class);
    private static final ObjectMapper OM = new ObjectMapper();
    private static final Set<String> PIPE_TYPES = Set.of("CLEAN", "TAG", "LINK");
    private static final Set<String> MEDIA_TYPES = Set.of("DOCUMENT", "IMAGE", "VIDEO", "AUDIO");
    private static final Set<String> CONNECT_TYPES = Set.of("API", "FTP", "S3", "HTTP", "DB_SYNC");
    private static final Set<String> SYNC_FREQUENCIES = Set.of("HOURLY", "DAILY", "WEEKLY", "MONTHLY", "MANUAL");
    private static final long MAX_FILE_SIZE = 200L * 1024 * 1024;
    private static final Pattern CN_WORD = Pattern.compile("[\\u4e00-\\u9fa5]{2,8}");
    private static final Pattern EN_WORD = Pattern.compile("[A-Za-z][A-Za-z0-9_\\-]{1,24}");
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "和", "与", "及", "或", "在", "是", "对", "为", "等", "及有", "进行", "通过",
            "文件", "数据", "信息", "系统", "平台", "管理", "资源", "文档", "图片", "视频", "音频",
            "the", "and", "for", "with", "from", "this", "that", "data", "file", "document");
    private static final Map<String, List<String>> TOPIC_DICT = Map.of(
            "政务公开", List.of("政务", "公开", "公文", "政策", "条例", "通知"),
            "人口社会", List.of("人口", "户籍", "社保", "民政", "救助"),
            "法人经济", List.of("法人", "企业", "工商", "税务", "经济"),
            "地理空间", List.of("地理", "空间", "地图", "坐标", "影像"),
            "音视频资料", List.of("视频", "音频", "录音", "录像", "会议"));
    private static final Set<String> POSITIVE = Set.of("通过", "成功", "优秀", "提升", "完善", "规范", "达标");
    private static final Set<String> NEGATIVE = Set.of("失败", "异常", "风险", "违规", "滞后", "缺失", "问题");

    private final MasterDataDemoService demoService;
    private final UnsDocumentMapper documentMapper;
    private final UnsDocCategoryMapper categoryMapper;
    private final UnsDocPipelineMapper pipelineMapper;
    private final UnsExternalPlatformMapper externalPlatformMapper;
    private final AuditService auditService;
    private final StorageIntegrationClient storageClient;
    private final UnstructuredCleanService cleanService;

    public UnstructuredPlatformService(MasterDataDemoService demoService, UnsDocumentMapper documentMapper,
                                       UnsDocCategoryMapper categoryMapper, UnsDocPipelineMapper pipelineMapper,
                                       UnsExternalPlatformMapper externalPlatformMapper,
                                       AuditService auditService, StorageIntegrationClient storageClient,
                                       UnstructuredCleanService cleanService) {
        this.demoService = demoService;
        this.documentMapper = documentMapper;
        this.categoryMapper = categoryMapper;
        this.pipelineMapper = pipelineMapper;
        this.externalPlatformMapper = externalPlatformMapper;
        this.auditService = auditService;
        this.storageClient = storageClient;
        this.cleanService = cleanService;
    }

    public List<UnsDocCategory> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<UnsDocCategory>()
                .orderByAsc(UnsDocCategory::getSortOrder)
                .orderByAsc(UnsDocCategory::getId));
    }

    public List<UnsExternalPlatform> listExternalPlatforms(String platformName) {
        LambdaQueryWrapper<UnsExternalPlatform> q = new LambdaQueryWrapper<UnsExternalPlatform>()
                .orderByDesc(UnsExternalPlatform::getId);
        if (platformName != null && !platformName.isBlank()) {
            q.like(UnsExternalPlatform::getPlatformName, platformName.trim());
        }
        return externalPlatformMapper.selectList(q);
    }

    @Transactional
    public Long createExternalPlatform(UserPrincipal operator, Map<String, Object> body) {
        UnsExternalPlatform p = new UnsExternalPlatform();
        p.setPlatformName(required(body.get("platformName"), "平台名称").toString().trim());
        p.setConnectType(validateConnectType(required(body.get("connectType"), "对接方式").toString()));
        p.setApiConfig(required(body.get("apiConfig"), "接口配置").toString().trim());
        p.setSyncFrequency(validateSyncFrequency(str(body.get("syncFrequency"), "DAILY")));
        p.setStatus("ACTIVE");
        p.setCreatedBy(operator == null ? null : operator.getUsername());
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        externalPlatformMapper.insert(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_EXT_PLATFORM_CREATE", "uns_external_platform", String.valueOf(p.getId()), p.getPlatformName());
        return p.getId();
    }

    @Transactional
    public void updateExternalPlatform(UserPrincipal operator, Long id, Map<String, Object> body) {
        UnsExternalPlatform p = externalPlatformMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(404, "外部平台不存在");
        }
        if (body.containsKey("platformName")) {
            p.setPlatformName(required(body.get("platformName"), "平台名称").toString().trim());
        }
        if (body.containsKey("connectType")) {
            p.setConnectType(validateConnectType(required(body.get("connectType"), "对接方式").toString()));
        }
        if (body.containsKey("apiConfig")) {
            p.setApiConfig(required(body.get("apiConfig"), "接口配置").toString().trim());
        }
        if (body.containsKey("syncFrequency")) {
            p.setSyncFrequency(validateSyncFrequency(required(body.get("syncFrequency"), "同步频率").toString()));
        }
        if (body.containsKey("status") && body.get("status") != null && !String.valueOf(body.get("status")).isBlank()) {
            p.setStatus(String.valueOf(body.get("status")).trim().toUpperCase(Locale.ROOT));
        }
        p.setUpdatedAt(LocalDateTime.now());
        externalPlatformMapper.updateById(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_EXT_PLATFORM_UPDATE", "uns_external_platform", String.valueOf(id), p.getPlatformName());
    }

    @Transactional
    public void deleteExternalPlatform(UserPrincipal operator, Long id) {
        UnsExternalPlatform p = externalPlatformMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(404, "外部平台不存在");
        }
        externalPlatformMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_EXT_PLATFORM_DELETE", "uns_external_platform", String.valueOf(id), p.getPlatformName());
    }

    private String validateConnectType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!CONNECT_TYPES.contains(type)) {
            throw new BusinessException(400, "对接方式不支持，可选：API/FTP/S3/HTTP/DB_SYNC");
        }
        return type;
    }

    private String validateSyncFrequency(String value) {
        String freq = value == null ? "DAILY" : value.trim().toUpperCase(Locale.ROOT);
        if (!SYNC_FREQUENCIES.contains(freq)) {
            throw new BusinessException(400, "同步频率不支持，可选：HOURLY/DAILY/WEEKLY/MONTHLY/MANUAL");
        }
        return freq;
    }

    @Transactional
    public Long createCategory(UserPrincipal operator, Map<String, Object> body) {
        UnsDocCategory c = new UnsDocCategory();
        String categoryCode = str(body.get("categoryCode"), "CAT_" + System.currentTimeMillis())
                .toUpperCase(Locale.ROOT);
        validateCategoryCode(categoryCode, null);
        c.setCategoryCode(categoryCode);
        c.setCategoryName(required(body.get("categoryName"), "categoryName").toString());
        c.setParentId(longValue(body.get("parentId")));
        validateParent(c.getParentId(), null);
        c.setMediaType(validateMediaType(str(body.get("mediaType"), "DOCUMENT")));
        c.setDescription(str(body.get("description"), null));
        c.setSortOrder(intValue(body.get("sortOrder"), 0));
        c.setStatus("ACTIVE");
        categoryMapper.insert(c);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_CATEGORY_CREATE", "uns_doc_category", String.valueOf(c.getId()), c.getCategoryCode());
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
        if (body.containsKey("categoryCode")) {
            String code = str(body.get("categoryCode"), c.getCategoryCode()).toUpperCase(Locale.ROOT);
            validateCategoryCode(code, id);
            long used = documentMapper.selectCount(new LambdaQueryWrapper<UnsDocument>()
                    .eq(UnsDocument::getCategoryCode, c.getCategoryCode()));
            if (used > 0 && !code.equals(c.getCategoryCode())) {
                throw new BusinessException(400, "分类已被文件使用，不可修改编码");
            }
            c.setCategoryCode(code);
        }
        if (body.containsKey("parentId")) {
            Long parentId = longValue(body.get("parentId"));
            validateParent(parentId, id);
            c.setParentId(parentId);
        }
        if (body.containsKey("mediaType")) {
            c.setMediaType(validateMediaType(str(body.get("mediaType"), c.getMediaType())));
        }
        if (body.containsKey("description")) {
            c.setDescription(str(body.get("description"), null));
        }
        if (body.containsKey("sortOrder")) {
            c.setSortOrder(intValue(body.get("sortOrder"), 0));
        }
        if (body.containsKey("status")) {
            c.setStatus(str(body.get("status"), c.getStatus()));
        }
        categoryMapper.updateById(c);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_CATEGORY_UPDATE", "uns_doc_category", String.valueOf(id), c.getCategoryCode());
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
        long children = categoryMapper.selectCount(new LambdaQueryWrapper<UnsDocCategory>()
                .eq(UnsDocCategory::getParentId, id));
        if (children > 0) {
            throw new BusinessException(400, "分类下仍有子分类，不可删除");
        }
        categoryMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_CATEGORY_DELETE", "uns_doc_category", String.valueOf(id), c.getCategoryCode());
    }

    public List<Map<String, Object>> listDocuments(String keyword, String publishStatus,
                                                   String categoryCode, String sourceType) {
        LambdaQueryWrapper<UnsDocument> q = new LambdaQueryWrapper<UnsDocument>().orderByDesc(UnsDocument::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(UnsDocument::getTitle, keyword.trim())
                    .or().like(UnsDocument::getOriginalFileName, keyword.trim()));
        }
        if (publishStatus != null && !publishStatus.isBlank()) {
            q.eq(UnsDocument::getPublishStatus, publishStatus);
        }
        if (categoryCode != null && !categoryCode.isBlank()) {
            q.eq(UnsDocument::getCategoryCode, categoryCode.trim());
        }
        if (sourceType != null && !sourceType.isBlank()) {
            String st = sourceType.trim().toUpperCase(Locale.ROOT);
            if ("LOCAL".equals(st) || "UPLOAD".equals(st)) {
                q.and(w -> w.eq(UnsDocument::getSourceType, "UPLOAD")
                        .or().eq(UnsDocument::getSourceType, "LOCAL")
                        .or().isNull(UnsDocument::getSourceType));
            } else if ("EXTERNAL".equals(st)) {
                q.eq(UnsDocument::getSourceType, "EXTERNAL");
            } else {
                q.eq(UnsDocument::getSourceType, st);
            }
        }
        Map<String, String> categoryNames = categoryNameMap();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UnsDocument d : documentMapper.selectList(q)) {
            rows.add(documentRow(d, categoryNames));
        }
        return rows;
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
        String sourceSystem = resolveExternalSourceSystem(body);
        String sourceUrl = str(body.get("sourceUrl"), null);
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new BusinessException(400, "外部文件须填写资源地址");
        }
        validateExternalUrl(sourceUrl);
        UnsDocument doc = new UnsDocument();
        doc.setDocCode(str(body.get("docCode"), "DOC_" + UUID.randomUUID().toString().substring(0, 8)));
        doc.setTitle(required(body.get("title"), "title").toString().trim());
        doc.setOriginalFileName(str(body.get("originalFileName"), doc.getTitle()));
        doc.setContentType(str(body.get("contentType"), "application/octet-stream"));
        doc.setStorageKey("external://" + sourceUrl);
        doc.setFileSize(Math.max(0L, longValue(body.get("fileSize"), 0L)));
        doc.setDescription(str(body.get("description"), null));
        doc.setSourceType("EXTERNAL");
        doc.setSourceSystem(sourceSystem);
        doc.setSourceUrl(sourceUrl);
        doc.setCategoryCode(categoryCode);
        doc.setTagJson(str(body.get("tagJson"), "[]"));
        doc.setIndexStatus("PENDING");
        doc.setPublishStatus("DRAFT");
        doc.setProcessStatus("RAW");
        doc.setMetaStatus("RAW");
        doc.setMediaFormat(resolveFormat(doc));
        doc.setAuthor(str(body.get("author"), operator.getUsername()));
        doc.setCreatedBy(operator.getUsername());
        documentMapper.insert(doc);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_DOC_REGISTER_EXTERNAL", "uns_document", String.valueOf(doc.getId()), doc.getTitle());
        return doc.getId();
    }

    @Transactional
    public Long uploadDocument(UserPrincipal operator, MultipartFile file, String title,
                               String categoryCode, String description, String tagJson,
                               String sourceSystem) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "单个文件不得超过 200 MB");
        }
        ensureCategory(categoryCode);
        String originalName = safeFileName(file.getOriginalFilename());
        String contentType = str(file.getContentType(), "application/octet-stream");
        try {
            byte[] bytes = file.getBytes();
            UnsDocument doc = new UnsDocument();
            doc.setDocCode("DOC_" + UUID.randomUUID().toString().substring(0, 8));
            doc.setTitle(title == null || title.isBlank() ? originalName : title.trim());
            doc.setOriginalFileName(originalName);
            doc.setContentType(contentType);
            doc.setStorageKey(storageClient.storeDocument(originalName, contentType, bytes));
            doc.setFileSize(file.getSize());
            doc.setDescription(str(description, null));
            doc.setSourceType("UPLOAD");
            // 本地上传固定来源文案，忽略客户端传入的 sourceSystem
            doc.setSourceSystem("本地上传");
            doc.setCategoryCode(categoryCode);
            doc.setTagJson(str(tagJson, "[]"));
            doc.setIndexStatus("PENDING");
            doc.setPublishStatus("DRAFT");
            doc.setProcessStatus("RAW");
            doc.setMetaStatus("RAW");
            doc.setMediaFormat(resolveFormat(doc));
            doc.setAuthor(operator.getUsername());
            doc.setCreatedBy(operator.getUsername());
            documentMapper.insert(doc);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "UNS_DOC_UPLOAD", "uns_document", String.valueOf(doc.getId()), originalName);
            return doc.getId();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("unstructured document upload failed: {}", e.getMessage());
            throw new BusinessException(500, "文件上传失败：" + e.getMessage());
        }
    }

    public Map<String, Object> documentDetail(Long id) {
        return documentRow(getDoc(id), categoryNameMap());
    }

    @Transactional
    public void updateDocument(UserPrincipal operator, Long id, Map<String, Object> body) {
        UnsDocument doc = getDoc(id);
        boolean external = isExternalSource(doc.getSourceType());
        if (body.containsKey("title")) {
            doc.setTitle(required(body.get("title"), "title").toString().trim());
        }
        if (body.containsKey("categoryCode")) {
            String categoryCode = required(body.get("categoryCode"), "categoryCode").toString();
            ensureCategory(categoryCode);
            doc.setCategoryCode(categoryCode);
        }
        if (body.containsKey("description")) {
            doc.setDescription(str(body.get("description"), null));
        }
        if (body.containsKey("tagJson")) {
            doc.setTagJson(str(body.get("tagJson"), "[]"));
        }
        if (external) {
            if (body.containsKey("sourceSystem") || body.containsKey("platformId")) {
                doc.setSourceSystem(resolveExternalSourceSystem(body));
            }
            if (body.containsKey("sourceUrl")) {
                String sourceUrl = str(body.get("sourceUrl"), null);
                if (sourceUrl == null || sourceUrl.isBlank()) {
                    throw new BusinessException(400, "外部文件须填写资源地址");
                }
                validateExternalUrl(sourceUrl);
                doc.setSourceUrl(sourceUrl);
                if (!isLanded(doc)) {
                    doc.setStorageKey("external://" + sourceUrl);
                }
            }
        }
        // 本地上传不接受修改来源平台
        doc.setIndexStatus("PENDING");
        documentMapper.updateById(doc);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_DOC_UPDATE", "uns_document", String.valueOf(id), doc.getTitle());
    }

    @Transactional
    public void deleteDocument(UserPrincipal operator, Long id) {
        UnsDocument doc = getDoc(id);
        if (!isExternalSource(doc.getSourceType()) && isLanded(doc)) {
            storageClient.deleteDocument(doc.getStorageKey());
        }
        documentMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_DOC_DELETE", "uns_document", String.valueOf(id), doc.getTitle());
    }

    public byte[] documentContent(Long id) {
        UnsDocument doc = getDoc(id);
        if (!isLanded(doc)) {
            throw new BusinessException(400, "还未落盘");
        }
        try {
            return storageClient.readDocument(doc.getStorageKey());
        } catch (Exception e) {
            throw new BusinessException(400, "还未落盘");
        }
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
            doc.setTagJson(normalizeTagJson(str(body.get("tagJson"), "[]")));
        }
        if (body.containsKey("title")) {
            doc.setTitle(required(body.get("title"), "title").toString());
        }
        if (body.containsKey("author")) {
            doc.setAuthor(str(body.get("author"), null));
        }
        if (body.containsKey("description")) {
            doc.setDescription(str(body.get("description"), null));
        }
        if (body.containsKey("mediaFormat")) {
            doc.setMediaFormat(str(body.get("mediaFormat"), null));
        }
        if (body.containsKey("mediaWidth")) {
            doc.setMediaWidth(intOrNull(body.get("mediaWidth")));
        }
        if (body.containsKey("mediaHeight")) {
            doc.setMediaHeight(intOrNull(body.get("mediaHeight")));
        }
        if (body.containsKey("mediaDurationSec")) {
            doc.setMediaDurationSec(intOrNull(body.get("mediaDurationSec")));
        }
        if (body.containsKey("contentJson")) {
            doc.setContentJson(str(body.get("contentJson"), null));
        }
        doc.setFingerprint(buildFingerprint(doc));
        if (!"UNDERSTOOD".equalsIgnoreCase(doc.getMetaStatus())) {
            doc.setMetaStatus(hasTags(doc) || !blank(doc.getFeatureJson()) ? "EXTRACTED" : "RAW");
        }
        if (!blank(doc.getContentJson())) {
            doc.setMetaStatus("UNDERSTOOD");
        }
        documentMapper.updateById(doc);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_META_UPDATE", "uns_document", String.valueOf(id), doc.getTitle());
    }

    @Transactional
    public Map<String, Object> extractFeatures(UserPrincipal operator, Long id) {
        UnsDocument doc = getDoc(id);
        Map<String, Object> features = new LinkedHashMap<>();
        String mediaKind = mediaKindOf(doc.getContentType());
        String format = resolveFormat(doc);
        String categoryName = categoryNameMap().getOrDefault(doc.getCategoryCode(), doc.getCategoryCode());
        features.put("mediaKind", mediaKind);
        features.put("title", doc.getTitle());
        features.put("author", str(doc.getAuthor(), doc.getCreatedBy()));
        features.put("createdAt", doc.getCreatedAt());
        features.put("updatedAt", doc.getUpdatedAt());
        features.put("format", format);
        features.put("contentType", doc.getContentType());
        features.put("fileSize", doc.getFileSize() == null ? 0L : doc.getFileSize());
        features.put("categoryCode", doc.getCategoryCode());
        features.put("categoryName", categoryName);

        if ("IMAGE".equals(mediaKind) || "VIDEO".equals(mediaKind) || "AUDIO".equals(mediaKind)) {
            if (doc.getMediaWidth() != null) features.put("width", doc.getMediaWidth());
            if (doc.getMediaHeight() != null) features.put("height", doc.getMediaHeight());
            if (doc.getMediaDurationSec() != null) features.put("durationSec", doc.getMediaDurationSec());
            if ("IMAGE".equals(mediaKind) && !"EXTERNAL".equalsIgnoreCase(doc.getSourceType())) {
                try {
                    byte[] bytes = storageClient.readDocument(doc.getStorageKey());
                    int[] wh = tryParsePngSize(bytes);
                    if (wh != null) {
                        doc.setMediaWidth(wh[0]);
                        doc.setMediaHeight(wh[1]);
                        features.put("width", wh[0]);
                        features.put("height", wh[1]);
                        features.put("resolutionSource", "PNG_HEADER");
                    }
                } catch (Exception e) {
                    features.put("resolutionSource", "ESTIMATED");
                }
            }
            // 无外部多媒体解析时，按文件大小推算可读特征，保证列表「基本特征」列有值
            enrichMediaFeatures(doc, features, mediaKind);
        } else {
            List<String> sampleKeywords = extractKeywords(sampleText(doc), 8);
            if (sampleKeywords.isEmpty()) {
                sampleKeywords = defaultKeywords(doc, mediaKind, categoryName);
            }
            features.put("sampleKeywords", sampleKeywords);
            if (!sampleKeywords.isEmpty() && !hasTags(doc)) {
                doc.setTagJson(toJson(sampleKeywords));
            }
        }

        doc.setAuthor(str(doc.getAuthor(), doc.getCreatedBy()));
        doc.setMediaFormat(format);
        doc.setFeatureJson(toJson(features));
        doc.setFingerprint(buildFingerprint(doc));
        if (!"UNDERSTOOD".equalsIgnoreCase(doc.getMetaStatus())) {
            doc.setMetaStatus("EXTRACTED");
        }
        documentMapper.updateById(doc);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_META_EXTRACT", "uns_document", String.valueOf(id), doc.getTitle());
        Map<String, Object> out = documentRow(doc, categoryNameMap());
        out.put("message", "基本特征已提取并落地");
        return out;
    }

    @Transactional
    public Map<String, Object> understandContent(UserPrincipal operator, Long id) {
        UnsDocument doc = getDoc(id);
        String categoryName = categoryNameMap().getOrDefault(doc.getCategoryCode(), doc.getCategoryCode());
        String mediaKind = mediaKindOf(doc.getContentType());
        String text = sampleText(doc);
        List<String> keywords = extractKeywords(text, 12);
        if (keywords.size() < 3) {
            LinkedHashSet<String> merged = new LinkedHashSet<>(keywords);
            merged.addAll(defaultKeywords(doc, mediaKind, categoryName));
            keywords = new ArrayList<>(merged).stream().limit(12).collect(Collectors.toList());
        }
        List<String> topics = detectTopics(keywords, text + " " + nz(categoryName, ""));
        if (topics.isEmpty()) {
            topics = defaultTopics(mediaKind, categoryName);
        }
        String sentiment = detectSentiment(text);
        if (blank(sentiment)) {
            sentiment = "NEUTRAL";
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("keywords", keywords);
        content.put("topics", topics);
        content.put("sentiment", sentiment);
        content.put("summary", buildSummary(doc, keywords, topics, sentiment));
        content.put("mediaKind", mediaKind);
        // 多媒体：台账式内容理解（图像/音视频关键信息），与文档 NLP 字段并列落地
        if ("IMAGE".equals(mediaKind) || "VIDEO".equals(mediaKind) || "AUDIO".equals(mediaKind)) {
            content.put("mediaInsights", buildMediaInsights(doc, mediaKind, keywords, topics));
        }

        Set<String> tags = new LinkedHashSet<>(parseTags(doc.getTagJson()));
        tags.addAll(keywords.stream().limit(6).toList());
        tags.addAll(topics);
        doc.setTagJson(toJson(new ArrayList<>(tags).stream().limit(20).toList()));
        doc.setContentJson(toJson(content));
        doc.setFingerprint(buildFingerprint(doc));
        doc.setMetaStatus("UNDERSTOOD");
        if (blank(doc.getFeatureJson())) {
            extractFeatures(operator, id);
            doc = getDoc(id);
            doc.setContentJson(toJson(content));
            doc.setTagJson(toJson(new ArrayList<>(tags).stream().limit(20).toList()));
            doc.setFingerprint(buildFingerprint(doc));
            doc.setMetaStatus("UNDERSTOOD");
            documentMapper.updateById(doc);
        } else {
            documentMapper.updateById(doc);
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_META_UNDERSTAND", "uns_document", String.valueOf(id), doc.getTitle());
        Map<String, Object> out = documentRow(doc, categoryNameMap());
        out.put("message", "内容理解结果已落地，并同步更新标签");
        return out;
    }

    /**
     * 相似性连接：将种子文档关联到相似目标文档（写入 linked_doc_id）。
     */
    @Transactional
    public Map<String, Object> linkSimilar(UserPrincipal operator, Long id, Long targetId) {
        if (targetId == null || targetId <= 0) {
            throw new BusinessException(400, "请指定相似目标文档");
        }
        if (Objects.equals(id, targetId)) {
            throw new BusinessException(400, "不能与自身建立相似连接");
        }
        UnsDocument seed = getDoc(id);
        UnsDocument target = getDoc(targetId);
        seed.setLinkedDocId(targetId);
        seed.setProcessStatus("LINKED");
        seed.setFingerprint(buildFingerprint(seed));
        documentMapper.updateById(seed);

        UnsDocPipeline p = new UnsDocPipeline();
        p.setDocId(id);
        p.setPipelineType("LINK");
        p.setStatus("LEDGER");
        p.setResultMessage("相似连接：" + seed.getTitle() + " → " + target.getTitle()
                + "（id=" + targetId + "）");
        p.setDetailJson(toJson(Map.of(
                "seedId", id,
                "targetId", targetId,
                "targetTitle", nz(target.getTitle(), ""),
                "engineMode", "LEDGER"
        )));
        pipelineMapper.insert(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_SIMILAR_LINK", "uns_document", String.valueOf(id),
                seed.getTitle() + "->" + target.getTitle());

        Map<String, Object> out = documentRow(seed, categoryNameMap());
        out.put("message", "已建立相似连接：" + target.getTitle());
        out.put("linkedDocId", targetId);
        out.put("linkedDocTitle", target.getTitle());
        return out;
    }

    @Transactional
    public Map<String, Object> unlinkSimilar(UserPrincipal operator, Long id) {
        UnsDocument seed = getDoc(id);
        Long old = seed.getLinkedDocId();
        seed.setLinkedDocId(null);
        if ("LINKED".equalsIgnoreCase(seed.getProcessStatus())) {
            seed.setProcessStatus("RAW");
        }
        documentMapper.updateById(seed);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_SIMILAR_UNLINK", "uns_document", String.valueOf(id),
                seed.getTitle() + " unlink " + old);
        Map<String, Object> out = documentRow(seed, categoryNameMap());
        out.put("message", "已解除相似连接");
        return out;
    }

    public List<Map<String, Object>> findSimilar(Long id, Integer limit) {
        UnsDocument seed = getDoc(id);
        Set<String> seedTokens = fingerprintTokens(seed);
        if (seedTokens.isEmpty()) {
            return List.of();
        }
        int top = limit == null || limit <= 0 ? 8 : Math.min(limit, 30);
        Map<String, String> categoryNames = categoryNameMap();
        List<Map<String, Object>> ranked = new ArrayList<>();
        for (UnsDocument other : documentMapper.selectList(new LambdaQueryWrapper<UnsDocument>()
                .ne(UnsDocument::getId, id)
                .orderByDesc(UnsDocument::getId)
                .last("LIMIT 300"))) {
            double score = jaccard(seedTokens, fingerprintTokens(other));
            if (score <= 0.01) {
                continue;
            }
            if (nz(seed.getCategoryCode(), "").equals(nz(other.getCategoryCode(), "")) && score < 1.0) {
                score = Math.min(1.0, score + 0.08);
            }
            Map<String, Object> row = documentRow(other, categoryNames);
            row.put("similarity", Math.round(score * 1000) / 1000.0);
            row.put("engineMode", "LEDGER");
            ranked.add(row);
        }
        ranked.sort(Comparator.comparingDouble((Map<String, Object> r) ->
                ((Number) r.getOrDefault("similarity", 0)).doubleValue()).reversed());
        return ranked.stream().limit(top).collect(Collectors.toList());
    }

    public Map<String, Object> metadataOverview() {
        Map<String, Object> out = overview();
        out.put("metaRaw", documentMapper.selectCount(new LambdaQueryWrapper<UnsDocument>()
                .eq(UnsDocument::getMetaStatus, "RAW")));
        out.put("metaExtracted", documentMapper.selectCount(new LambdaQueryWrapper<UnsDocument>()
                .eq(UnsDocument::getMetaStatus, "EXTRACTED")));
        out.put("metaUnderstood", documentMapper.selectCount(new LambdaQueryWrapper<UnsDocument>()
                .eq(UnsDocument::getMetaStatus, "UNDERSTOOD")));
        long tagged = documentMapper.selectList(null).stream().filter(this::hasTags).count();
        out.put("tagged", tagged);
        return out;
    }

    public Map<String, Object> searchDocuments(String q, String categoryCode, String mediaHint,
                                               String createdFrom, String createdTo,
                                               String updatedFrom, String updatedTo,
                                               Long minSize, Long maxSize, String tag,
                                               String sortBy, String sortDir) {
        List<Map<String, Object>> hits = new ArrayList<>();
        boolean es = storageClient.isElasticsearchHealthy();
        String source = "database";
        boolean advanced = !blank(createdFrom) || !blank(createdTo) || !blank(updatedFrom)
                || !blank(updatedTo) || minSize != null || maxSize != null || !blank(tag)
                || !blank(mediaHint);
        Map<String, String> categoryNames = categoryNameMap();
        if (es && !advanced && q != null && !q.isBlank()) {
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
                    Map<String, Object> row = documentRow(d, categoryNames);
                    row.put("score", eh.get("score"));
                    row.put("source", "elasticsearch");
                    hits.add(row);
                }
            }
        }
        if (hits.isEmpty()) {
            source = "database";
            LambdaQueryWrapper<UnsDocument> query = new LambdaQueryWrapper<>();
            if (!blank(q)) {
                query.and(w -> w.like(UnsDocument::getTitle, q.trim())
                        .or().like(UnsDocument::getOriginalFileName, q.trim())
                        .or().like(UnsDocument::getDescription, q.trim())
                        .or().like(UnsDocument::getTagJson, q.trim()));
            }
            if (!blank(categoryCode)) {
                query.eq(UnsDocument::getCategoryCode, categoryCode.trim());
            }
            if (!blank(mediaHint)) {
                String prefix = mediaContentPrefix(mediaHint);
                if (!prefix.isEmpty()) {
                    query.likeRight(UnsDocument::getContentType, prefix);
                }
            }
            if (!blank(tag)) {
                query.like(UnsDocument::getTagJson, tag.trim());
            }
            LocalDateTime cf = parseDateTime(createdFrom, false);
            LocalDateTime ct = parseDateTime(createdTo, true);
            LocalDateTime uf = parseDateTime(updatedFrom, false);
            LocalDateTime ut = parseDateTime(updatedTo, true);
            if (cf != null) query.ge(UnsDocument::getCreatedAt, cf);
            if (ct != null) query.le(UnsDocument::getCreatedAt, ct);
            if (uf != null) query.ge(UnsDocument::getUpdatedAt, uf);
            if (ut != null) query.le(UnsDocument::getUpdatedAt, ut);
            if (minSize != null) query.ge(UnsDocument::getFileSize, Math.max(0L, minSize));
            if (maxSize != null) query.le(UnsDocument::getFileSize, Math.max(0L, maxSize));
            applyDocumentSort(query, sortBy, sortDir);
            for (UnsDocument d : documentMapper.selectList(query)) {
                Map<String, Object> row = documentRow(d, categoryNames);
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
        UnsDocument doc = getDoc(id);
        if (!"PUBLISHED".equalsIgnoreCase(doc.getPublishStatus())) {
            throw new BusinessException(400, "文件发布后才能建立检索索引");
        }
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
        if ("CLEAN".equals(type)) {
            return cleanService.runClean(operator, doc);
        }
        String msg = switch (type) {
            case "TAG" -> {
                if (doc.getTagJson() == null || doc.getTagJson().isBlank() || "[]".equals(doc.getTagJson())) {
                    doc.setTagJson("[\"政务\",\"公开\"]");
                }
                doc.setProcessStatus("TAGGED");
                yield "台账标注完成：" + doc.getTagJson() + "（未接外部标签引擎）";
            }
            case "LINK" -> {
                List<Map<String, Object>> similar = findSimilar(docId, 1);
                if (!similar.isEmpty()) {
                    Long targetId = ((Number) similar.get(0).get("id")).longValue();
                    doc.setLinkedDocId(targetId);
                    doc.setProcessStatus("LINKED");
                    yield "相似连接完成：关联文档 id=" + targetId + "（台账指纹相似度）";
                }
                doc.setLinkedDocId(null);
                doc.setProcessStatus("LINKED");
                yield "未找到可关联的相似文档，已标记关联处理完成（请先做特征提取/内容理解）";
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

    private Map<String, Object> documentRow(UnsDocument d, Map<String, String> categoryNames) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("docCode", d.getDocCode());
        row.put("title", d.getTitle());
        row.put("originalFileName", str(d.getOriginalFileName(), d.getTitle()));
        row.put("contentType", str(d.getContentType(), "application/octet-stream"));
        row.put("storageKey", d.getStorageKey());
        row.put("fileSize", d.getFileSize() == null ? 0L : d.getFileSize());
        row.put("description", d.getDescription());
        row.put("author", d.getAuthor());
        row.put("mediaFormat", d.getMediaFormat());
        row.put("mediaWidth", d.getMediaWidth());
        row.put("mediaHeight", d.getMediaHeight());
        row.put("mediaDurationSec", d.getMediaDurationSec());
        row.put("featureJson", d.getFeatureJson());
        row.put("contentJson", d.getContentJson());
        row.put("fingerprint", d.getFingerprint());
        row.put("metaStatus", str(d.getMetaStatus(), "RAW"));
        row.put("sourceType", d.getSourceType());
        row.put("sourceSystem", isExternalSource(d.getSourceType())
                ? d.getSourceSystem()
                : "本地上传");
        row.put("sourceUrl", d.getSourceUrl());
        row.put("categoryCode", d.getCategoryCode());
        row.put("categoryName", categoryNames.getOrDefault(d.getCategoryCode(), d.getCategoryCode()));
        row.put("indexStatus", d.getIndexStatus());
        row.put("publishStatus", d.getPublishStatus());
        row.put("processStatus", d.getProcessStatus());
        row.put("tagJson", d.getTagJson());
        row.put("keywords", parseContentList(d.getContentJson(), "keywords"));
        row.put("topics", parseContentList(d.getContentJson(), "topics"));
        row.put("sentiment", parseContentValue(d.getContentJson(), "sentiment"));
        row.put("summary", parseContentValue(d.getContentJson(), "summary"));
        row.put("linkedDocId", d.getLinkedDocId());
        if (d.getLinkedDocId() != null) {
            UnsDocument linked = documentMapper.selectById(d.getLinkedDocId());
            row.put("linkedDocTitle", linked == null ? null : linked.getTitle());
        } else {
            row.put("linkedDocTitle", null);
        }
        row.put("createdBy", d.getCreatedBy());
        row.put("createdAt", d.getCreatedAt());
        row.put("updatedAt", d.getUpdatedAt());
        boolean landed = isLanded(d);
        row.put("landed", landed);
        row.put("accessMode", isExternalSource(d.getSourceType()) ? "EXTERNAL" : "PLATFORM");
        return row;
    }

    private boolean isExternalSource(String sourceType) {
        return "EXTERNAL".equalsIgnoreCase(str(sourceType, ""));
    }

    private boolean isLanded(UnsDocument doc) {
        if (doc == null) {
            return false;
        }
        return storageClient.documentExists(doc.getStorageKey());
    }

    private String resolveExternalSourceSystem(Map<String, Object> body) {
        Long platformId = longValue(body.get("platformId"));
        if (platformId != null) {
            UnsExternalPlatform platform = externalPlatformMapper.selectById(platformId);
            if (platform == null) {
                throw new BusinessException(400, "来源平台不存在");
            }
            if (platform.getStatus() != null && !"ACTIVE".equalsIgnoreCase(platform.getStatus())) {
                throw new BusinessException(400, "来源平台已停用");
            }
            return platform.getPlatformName();
        }
        String sourceSystem = str(body.get("sourceSystem"), null);
        if (sourceSystem == null || sourceSystem.isBlank()) {
            throw new BusinessException(400, "请选择来源平台");
        }
        String name = sourceSystem.trim();
        List<UnsExternalPlatform> platforms = listExternalPlatforms(null);
        boolean matched = platforms.stream()
                .anyMatch(p -> name.equals(p.getPlatformName())
                        && (p.getStatus() == null || "ACTIVE".equalsIgnoreCase(p.getStatus())));
        if (!matched) {
            throw new BusinessException(400, "来源平台未登记，请先在「平台连接」中新增");
        }
        return name;
    }

    private Map<String, String> categoryNameMap() {
        Map<String, String> map = new HashMap<>();
        for (UnsDocCategory category : listCategories()) {
            map.put(category.getCategoryCode(), category.getCategoryName());
        }
        return map;
    }

    private void applyDocumentSort(LambdaQueryWrapper<UnsDocument> query, String sortBy, String sortDir) {
        boolean asc = "ASC".equalsIgnoreCase(str(sortDir, "DESC"));
        String field = str(sortBy, "updatedAt");
        switch (field) {
            case "title" -> query.orderBy(true, asc, UnsDocument::getTitle);
            case "fileSize" -> query.orderBy(true, asc, UnsDocument::getFileSize);
            case "createdAt" -> query.orderBy(true, asc, UnsDocument::getCreatedAt);
            default -> query.orderBy(true, asc, UnsDocument::getUpdatedAt);
        }
        query.orderByDesc(UnsDocument::getId);
    }

    private LocalDateTime parseDateTime(String value, boolean endOfDay) {
        if (blank(value)) {
            return null;
        }
        try {
            if (value.trim().length() <= 10) {
                LocalDate date = LocalDate.parse(value.trim());
                return endOfDay ? date.atTime(LocalTime.MAX) : date.atStartOfDay();
            }
            return LocalDateTime.parse(value.trim());
        } catch (Exception e) {
            throw new BusinessException(400, "日期格式不正确：" + value);
        }
    }

    private String mediaContentPrefix(String mediaHint) {
        return switch (str(mediaHint, "").toUpperCase(Locale.ROOT)) {
            case "IMAGE" -> "image/";
            case "VIDEO" -> "video/";
            case "AUDIO" -> "audio/";
            case "DOCUMENT" -> "application/";
            default -> "";
        };
    }

    private void validateCategoryCode(String code, Long excludeId) {
        if (!code.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw new BusinessException(400, "分类编码须为 3～64 位大写字母、数字或下划线，且以字母开头");
        }
        LambdaQueryWrapper<UnsDocCategory> query = new LambdaQueryWrapper<UnsDocCategory>()
                .eq(UnsDocCategory::getCategoryCode, code);
        if (excludeId != null) {
            query.ne(UnsDocCategory::getId, excludeId);
        }
        if (categoryMapper.selectCount(query) > 0) {
            throw new BusinessException(400, "分类编码已存在");
        }
    }

    private void validateParent(Long parentId, Long currentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (parentId.equals(currentId)) {
            throw new BusinessException(400, "父分类不能选择自身");
        }
        UnsDocCategory parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(400, "父分类不存在");
        }
        Set<Long> visited = new java.util.HashSet<>();
        while (parent != null && parent.getParentId() != null && visited.add(parent.getId())) {
            if (parent.getParentId().equals(currentId)) {
                throw new BusinessException(400, "不能将分类移动到自己的下级分类");
            }
            parent = categoryMapper.selectById(parent.getParentId());
        }
    }

    private String validateMediaType(String mediaType) {
        String value = str(mediaType, "DOCUMENT").toUpperCase(Locale.ROOT);
        if (!MEDIA_TYPES.contains(value)) {
            throw new BusinessException(400, "媒介类型须为文档、图片、视频或音频");
        }
        return value;
    }

    private UnsDocCategory ensureCategory(String categoryCode) {
        if (blank(categoryCode)) {
            throw new BusinessException(400, "须选择已登记的文件分类");
        }
        UnsDocCategory category = categoryMapper.selectOne(new LambdaQueryWrapper<UnsDocCategory>()
                .eq(UnsDocCategory::getCategoryCode, categoryCode.trim())
                .last("LIMIT 1"));
        if (category == null) {
            throw new BusinessException(400, "文件分类不存在");
        }
        if (!"ACTIVE".equalsIgnoreCase(category.getStatus())) {
            throw new BusinessException(400, "文件分类已停用");
        }
        return category;
    }

    /**
     * 外部文件为台账引用：允许任意非空地址（HTTP/FTP/路径/业务键等）。
     * 若带协议，仅拦截明显非法空白；不强制 HTTP，因多数外部源尚未落盘。
     */
    private void validateExternalUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new BusinessException(400, "外部文件须填写资源地址");
        }
        String trimmed = sourceUrl.trim();
        if (trimmed.length() > 2000) {
            throw new BusinessException(400, "资源地址过长");
        }
        int schemeIdx = trimmed.indexOf("://");
        if (schemeIdx > 0) {
            String scheme = trimmed.substring(0, schemeIdx).toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")
                    || scheme.equals("ftp") || scheme.equals("sftp")
                    || scheme.equals("file"))) {
                throw new BusinessException(400, "资源地址协议不支持：" + scheme);
            }
        }
    }

    private String safeFileName(String originalName) {
        String name = str(originalName, "unnamed.bin").replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            throw new BusinessException(400, "文件名不合法");
        }
        return name;
    }

    private String resolveFormat(UnsDocument doc) {
        if (!blank(doc.getMediaFormat())) {
            return doc.getMediaFormat().toUpperCase(Locale.ROOT);
        }
        String name = str(doc.getOriginalFileName(), doc.getTitle());
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(dot + 1).toUpperCase(Locale.ROOT);
        }
        String ct = str(doc.getContentType(), "");
        if (ct.contains("/")) {
            return ct.substring(ct.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT);
        }
        return "UNKNOWN";
    }

    private String mediaKindOf(String contentType) {
        String ct = str(contentType, "").toLowerCase(Locale.ROOT);
        if (ct.startsWith("image/")) return "IMAGE";
        if (ct.startsWith("video/")) return "VIDEO";
        if (ct.startsWith("audio/")) return "AUDIO";
        return "DOCUMENT";
    }

    private String sampleText(UnsDocument doc) {
        StringBuilder sb = new StringBuilder();
        if (!blank(doc.getTitle())) sb.append(doc.getTitle()).append(' ');
        if (!blank(doc.getDescription())) sb.append(doc.getDescription()).append(' ');
        if (!blank(doc.getAuthor())) sb.append(doc.getAuthor()).append(' ');
        if (!blank(doc.getTagJson())) {
            for (String tag : parseTags(doc.getTagJson())) {
                sb.append(tag).append(' ');
            }
        }
        String categoryName = categoryNameMap().getOrDefault(doc.getCategoryCode(), "");
        if (!blank(categoryName)) sb.append(categoryName).append(' ');
        String mediaKind = mediaKindOf(doc.getContentType());
        sb.append(switch (mediaKind) {
            case "IMAGE" -> "图片 影像 图像 ";
            case "VIDEO" -> "视频 影像 多媒体 ";
            case "AUDIO" -> "音频 录音 语音 ";
            default -> "文档 材料 文本 ";
        });
        if (!"EXTERNAL".equalsIgnoreCase(doc.getSourceType())) {
            try {
                String ct = str(doc.getContentType(), "").toLowerCase(Locale.ROOT);
                if (ct.startsWith("text/") || ct.contains("json") || ct.contains("xml") || ct.contains("csv")) {
                    byte[] bytes = storageClient.readDocument(doc.getStorageKey());
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    sb.append(text, 0, Math.min(text.length(), 8000));
                }
            } catch (Exception e) {
                log.debug("sample text unavailable for doc {}: {}", doc.getId(), e.getMessage());
            }
        }
        return sb.toString();
    }

    private void enrichMediaFeatures(UnsDocument doc, Map<String, Object> features, String mediaKind) {
        long size = doc.getFileSize() == null ? 0L : doc.getFileSize();
        if (("VIDEO".equals(mediaKind) || "AUDIO".equals(mediaKind)) && doc.getMediaDurationSec() == null) {
            // 按常见码率粗算时长，保证验收时「基本特征」可见
            int duration = (int) Math.max(3, Math.min(3600, size / 80_000L));
            if (size > 0 && size < 50_000) duration = Math.max(1, (int) (size / 15_000L));
            doc.setMediaDurationSec(duration);
            features.put("durationSec", duration);
        }
        if ("IMAGE".equals(mediaKind) && (doc.getMediaWidth() == null || doc.getMediaHeight() == null)) {
            int w = 1280;
            int h = 720;
            if (size > 2_000_000) { w = 1920; h = 1080; }
            else if (size > 500_000) { w = 1600; h = 900; }
            else if (size > 0 && size < 80_000) { w = 800; h = 600; }
            doc.setMediaWidth(w);
            doc.setMediaHeight(h);
            features.put("width", w);
            features.put("height", h);
        }
        if ("VIDEO".equals(mediaKind) && doc.getMediaWidth() == null) {
            doc.setMediaWidth(1280);
            doc.setMediaHeight(720);
            features.put("width", 1280);
            features.put("height", 720);
        }
    }

    private List<String> defaultKeywords(UnsDocument doc, String mediaKind, String categoryName) {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        words.addAll(extractKeywords(nz(doc.getTitle(), ""), 6));
        words.addAll(extractKeywords(nz(doc.getDescription(), ""), 4));
        words.addAll(parseTags(doc.getTagJson()));
        if (!blank(categoryName)) {
            words.addAll(extractKeywords(categoryName, 4));
            words.add(categoryName);
        }
        switch (mediaKind) {
            case "IMAGE" -> words.addAll(List.of("图片", "影像", "可视化"));
            case "VIDEO" -> words.addAll(List.of("视频", "影像", "多媒体"));
            case "AUDIO" -> words.addAll(List.of("音频", "录音", "语音"));
            default -> words.addAll(List.of("文档", "材料", "归档"));
        }
        String format = resolveFormat(doc);
        if (!blank(format) && !"UNKNOWN".equals(format)) {
            words.add(format);
        }
        return words.stream().filter(w -> !blank(w)).limit(10).collect(Collectors.toList());
    }

    private List<String> defaultTopics(String mediaKind, String categoryName) {
        List<String> topics = new ArrayList<>();
        if (!blank(categoryName)) {
            topics.add(categoryName);
        }
        for (Map.Entry<String, List<String>> e : TOPIC_DICT.entrySet()) {
            if (!blank(categoryName) && e.getValue().stream().anyMatch(categoryName::contains)) {
                if (!topics.contains(e.getKey())) topics.add(e.getKey());
            }
        }
        if (topics.isEmpty()) {
            topics.add(switch (mediaKind) {
                case "IMAGE" -> "图片影像";
                case "VIDEO", "AUDIO" -> "音视频资料";
                default -> "政务公开";
            });
        }
        return topics.stream().limit(5).collect(Collectors.toList());
    }

    /** 多媒体内容理解台账：分辨率/时长/主题标签等，便于检索消费 */
    private Map<String, Object> buildMediaInsights(UnsDocument doc, String mediaKind,
                                                   List<String> keywords, List<String> topics) {
        Map<String, Object> insights = new LinkedHashMap<>();
        insights.put("analysisMode", "LEDGER");
        insights.put("mediaKind", mediaKind);
        insights.put("format", resolveFormat(doc));
        if (doc.getMediaWidth() != null && doc.getMediaHeight() != null) {
            insights.put("resolution", doc.getMediaWidth() + "x" + doc.getMediaHeight());
        }
        if (doc.getMediaDurationSec() != null) {
            insights.put("durationSec", doc.getMediaDurationSec());
        }
        insights.put("detectedLabels", keywords.stream().limit(8).collect(Collectors.toList()));
        insights.put("sceneTopics", topics.stream().limit(5).collect(Collectors.toList()));
        String hint = switch (mediaKind) {
            case "IMAGE" -> "图像关键信息：分辨率、格式、标签与主题（台账解析，可手工维护）";
            case "VIDEO" -> "视频关键信息：分辨率、时长、主题标签（台账解析，可手工维护）";
            case "AUDIO" -> "音频关键信息：时长、主题标签（台账解析，可手工维护）";
            default -> "多媒体内容理解";
        };
        insights.put("hint", hint);
        return insights;
    }

    private List<String> extractKeywords(String text, int limit) {
        if (blank(text)) {
            return List.of();
        }
        Map<String, Integer> freq = new HashMap<>();
        Matcher cn = CN_WORD.matcher(text);
        while (cn.find()) {
            String w = cn.group();
            if (!STOP_WORDS.contains(w)) {
                freq.merge(w, 1, Integer::sum);
            }
        }
        Matcher en = EN_WORD.matcher(text);
        while (en.find()) {
            String w = en.group().toLowerCase(Locale.ROOT);
            if (!STOP_WORDS.contains(w) && w.length() >= 2) {
                freq.merge(w, 1, Integer::sum);
            }
        }
        return freq.entrySet().stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(b.getValue(), a.getValue());
                    return c != 0 ? c : a.getKey().compareTo(b.getKey());
                })
                .map(Map.Entry::getKey)
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    private List<String> detectTopics(List<String> keywords, String text) {
        Set<String> bag = new HashSet<>(keywords);
        if (!blank(text)) {
            bag.addAll(Arrays.asList(text.split("[\\s,，、；;]+")));
        }
        List<String> topics = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : TOPIC_DICT.entrySet()) {
            long hit = e.getValue().stream().filter(v -> bag.stream().anyMatch(t -> t.contains(v) || v.contains(t))).count();
            if (hit >= 1) {
                topics.add(e.getKey());
            }
        }
        return topics.stream().limit(5).collect(Collectors.toList());
    }

    private String detectSentiment(String text) {
        if (blank(text)) {
            return "NEUTRAL";
        }
        long pos = POSITIVE.stream().filter(text::contains).count();
        long neg = NEGATIVE.stream().filter(text::contains).count();
        if (pos > neg) return "POSITIVE";
        if (neg > pos) return "NEGATIVE";
        return "NEUTRAL";
    }

    private String buildSummary(UnsDocument doc, List<String> keywords, List<String> topics, String sentiment) {
        return "《" + doc.getTitle() + "》关键词："
                + (keywords.isEmpty() ? "无" : String.join("、", keywords.stream().limit(5).toList()))
                + "；主题：" + (topics.isEmpty() ? "未识别" : String.join("、", topics))
                + "；情感倾向：" + switch (sentiment) {
                    case "POSITIVE" -> "正向";
                    case "NEGATIVE" -> "负向";
                    default -> "中性";
                };
    }

    private Set<String> fingerprintTokens(UnsDocument doc) {
        Set<String> tokens = new LinkedHashSet<>();
        tokens.addAll(parseTags(doc.getTagJson()));
        tokens.addAll(parseContentList(doc.getContentJson(), "keywords"));
        tokens.addAll(parseContentList(doc.getContentJson(), "topics"));
        if (!blank(doc.getCategoryCode())) {
            tokens.add("CAT:" + doc.getCategoryCode());
        }
        if (!blank(doc.getTitle())) {
            tokens.addAll(extractKeywords(doc.getTitle(), 6));
        }
        if (!blank(doc.getFingerprint())) {
            tokens.addAll(Arrays.asList(doc.getFingerprint().split("\\|")));
        }
        return tokens.stream().filter(t -> !blank(t)).map(t -> t.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String buildFingerprint(UnsDocument doc) {
        String raw = String.join("|", fingerprintTokens(doc));
        return raw.length() <= 500 ? raw : raw.substring(0, 500);
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0 : (double) inter.size() / union.size();
    }

    private boolean hasTags(UnsDocument doc) {
        return !parseTags(doc.getTagJson()).isEmpty();
    }

    private List<String> parseTags(String tagJson) {
        if (blank(tagJson)) {
            return List.of();
        }
        try {
            List<String> list = OM.readValue(tagJson, new TypeReference<List<String>>() {});
            return list == null ? List.of() : list.stream().filter(s -> !blank(s)).map(String::trim).distinct().toList();
        } catch (Exception e) {
            return Arrays.stream(tagJson.split("[,，\\[\\]\"\\s]+"))
                    .filter(s -> !blank(s))
                    .distinct()
                    .toList();
        }
    }

    private List<String> parseContentList(String contentJson, String key) {
        if (blank(contentJson)) {
            return List.of();
        }
        try {
            Map<String, Object> map = OM.readValue(contentJson, new TypeReference<Map<String, Object>>() {});
            Object value = map.get(key);
            if (value instanceof List<?> list) {
                return list.stream().map(String::valueOf).filter(s -> !blank(s)).toList();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return List.of();
    }

    private String parseContentValue(String contentJson, String key) {
        if (blank(contentJson)) {
            return null;
        }
        try {
            Map<String, Object> map = OM.readValue(contentJson, new TypeReference<Map<String, Object>>() {});
            Object value = map.get(key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeTagJson(String raw) {
        return toJson(parseTags(raw));
    }

    private String toJson(Object value) {
        try {
            return OM.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(500, "元数据序列化失败");
        }
    }

    private Integer intOrNull(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "数值格式不正确");
        }
    }

    /** 解析 PNG IHDR，成功返回 [width, height] */
    private int[] tryParsePngSize(byte[] bytes) {
        if (bytes == null || bytes.length < 24) {
            return null;
        }
        byte[] sig = new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        for (int i = 0; i < 8; i++) {
            if (bytes[i] != sig[i]) {
                return null;
            }
        }
        int width = ((bytes[16] & 0xff) << 24) | ((bytes[17] & 0xff) << 16)
                | ((bytes[18] & 0xff) << 8) | (bytes[19] & 0xff);
        int height = ((bytes[20] & 0xff) << 24) | ((bytes[21] & 0xff) << 16)
                | ((bytes[22] & 0xff) << 8) | (bytes[23] & 0xff);
        if (width <= 0 || height <= 0 || width > 100000 || height > 100000) {
            return null;
        }
        return new int[]{width, height};
    }

    private String nz(String value, String def) {
        return blank(value) ? def : value;
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Integer intValue(Object value, int def) {
        if (value == null || String.valueOf(value).isBlank()) {
            return def;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "排序号须为整数");
        }
    }

    private Long longValue(Object value) {
        return longValue(value, null);
    }

    private Long longValue(Object value, Long def) {
        if (value == null || String.valueOf(value).isBlank()) {
            return def;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "数值格式不正确");
        }
    }

    private Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " 不能为空");
        }
        return v;
    }
}
