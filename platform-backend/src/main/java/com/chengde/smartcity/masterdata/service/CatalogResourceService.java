package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovCatalogApproval;
import com.chengde.smartcity.masterdata.entity.GovCatalogCategory;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovCatalogResourceVersion;
import com.chengde.smartcity.masterdata.mapper.GovCatalogApprovalMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogCategoryMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceVersionMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogResourceService {

    private static final Logger log = LoggerFactory.getLogger(CatalogResourceService.class);
    private static final Set<String> ACTION_TYPES = Set.of("PUBLISH", "OFFLINE", "UPDATE", "DELETE");
    private static final ObjectMapper OM = new ObjectMapper();
    private static final String[] EXPORT_HEADERS = {
            "resourceCode", "resourceName", "resourceType", "categoryId", "providerOrg",
            "resourceFormat", "shareType", "updateCycle", "description", "secretFlag"
    };

    private final GovCatalogResourceMapper resourceMapper;
    private final GovCatalogCategoryMapper categoryMapper;
    private final GovCatalogApprovalMapper approvalMapper;
    private final GovCatalogResourceVersionMapper versionMapper;

    public CatalogResourceService(GovCatalogResourceMapper resourceMapper,
                                  GovCatalogCategoryMapper categoryMapper,
                                  GovCatalogApprovalMapper approvalMapper,
                                  GovCatalogResourceVersionMapper versionMapper) {
        this.resourceMapper = resourceMapper;
        this.categoryMapper = categoryMapper;
        this.approvalMapper = approvalMapper;
        this.versionMapper = versionMapper;
    }

    public List<GovCatalogResource> list(Long categoryId, String resourceType, String publishStatus,
                                         String approvalStatus, String keyword) {
        LambdaQueryWrapper<GovCatalogResource> q = new LambdaQueryWrapper<GovCatalogResource>()
                .orderByDesc(GovCatalogResource::getId);
        if (categoryId != null) {
            q.eq(GovCatalogResource::getCategoryId, categoryId);
        }
        if (resourceType != null && !resourceType.isBlank()) {
            q.eq(GovCatalogResource::getResourceType, resourceType);
        }
        if (publishStatus != null && !publishStatus.isBlank()) {
            q.eq(GovCatalogResource::getPublishStatus, publishStatus);
        }
        if (approvalStatus != null && !approvalStatus.isBlank()) {
            q.eq(GovCatalogResource::getApprovalStatus, approvalStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(GovCatalogResource::getResourceCode, keyword)
                    .or().like(GovCatalogResource::getResourceName, keyword)
                    .or().like(GovCatalogResource::getProviderOrg, keyword));
        }
        return resourceMapper.selectList(q);
    }

    public GovCatalogResource get(Long id) {
        return require(id);
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        GovCatalogResource r = new GovCatalogResource();
        applyBody(r, body, true);
        r.setPublishStatus("DRAFT");
        r.setApprovalStatus("DRAFT");
        r.setVersionNo(1);
        r.setSecretFlag(intVal(body.get("secretFlag"), 0));
        if (operator != null) {
            r.setCreatedBy(operator.getUsername());
            r.setUpdatedBy(operator.getUsername());
        }
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        resourceMapper.insert(r);
        log.info("catalog resource created id={} code={}", r.getId(), r.getResourceCode());
        return r.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovCatalogResource r = require(id);
        if ("PUBLISHED".equalsIgnoreCase(r.getPublishStatus())) {
            throw new BusinessException(400, "已发布资源不可直接编辑，请先下线");
        }
        if ("PENDING".equalsIgnoreCase(r.getApprovalStatus())) {
            throw new BusinessException(400, "审批中不可编辑");
        }
        applyBody(r, body, false);
        if (operator != null) {
            r.setUpdatedBy(operator.getUsername());
        }
        r.setUpdatedAt(LocalDateTime.now());
        resourceMapper.updateById(r);
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        GovCatalogResource r = require(id);
        if (!"DRAFT".equalsIgnoreCase(r.getPublishStatus())
                && !"OFFLINE".equalsIgnoreCase(r.getPublishStatus())) {
            throw new BusinessException(400, "仅草稿或已下线资源可删除");
        }
        if ("PENDING".equalsIgnoreCase(r.getApprovalStatus())) {
            throw new BusinessException(400, "审批中不可删除");
        }
        resourceMapper.deleteById(id);
    }

    @Transactional
    public void batchDelete(UserPrincipal operator, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "ids 不能为空");
        }
        for (Long id : ids) {
            delete(operator, id);
        }
    }

    @Transactional
    public GovCatalogApproval submit(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovCatalogResource r = require(id);
        if ("PENDING".equalsIgnoreCase(r.getApprovalStatus())) {
            throw new BusinessException(400, "已有待审批申请");
        }
        if ("PUBLISHED".equalsIgnoreCase(r.getPublishStatus())) {
            throw new BusinessException(400, "已发布资源请走下线流程");
        }
        String actionType = str(body.get("actionType"), "PUBLISH").toUpperCase();
        if (!ACTION_TYPES.contains(actionType)) {
            throw new BusinessException(400, "actionType 须为 PUBLISH/OFFLINE/UPDATE/DELETE");
        }
        GovCatalogApproval a = new GovCatalogApproval();
        a.setResourceId(id);
        a.setActionType(actionType);
        a.setStatus("PENDING");
        a.setSubmitComment(str(body.get("comment"), null));
        if (operator != null) {
            a.setSubmittedBy(operator.getUsername());
        }
        a.setSubmittedAt(LocalDateTime.now());
        approvalMapper.insert(a);

        r.setApprovalStatus("PENDING");
        touch(r, operator);
        resourceMapper.updateById(r);
        return a;
    }

    @Transactional
    public GovCatalogApproval approve(UserPrincipal operator, Long approvalId, Map<String, Object> body) {
        GovCatalogApproval a = requireApproval(approvalId);
        if (!"PENDING".equalsIgnoreCase(a.getStatus())) {
            throw new BusinessException(400, "仅待处理审批可通过");
        }
        a.setStatus("APPROVED");
        a.setReviewComment(str(body.get("comment"), null));
        if (operator != null) {
            a.setReviewedBy(operator.getUsername());
        }
        a.setReviewedAt(LocalDateTime.now());
        approvalMapper.updateById(a);

        GovCatalogResource r = require(a.getResourceId());
        r.setApprovalStatus("APPROVED");
        if ("PUBLISH".equalsIgnoreCase(a.getActionType())) {
            r.setPublishStatus("PUBLISHED");
            snapshotOnPublish(r, operator, "审批发布 v");
        } else if ("OFFLINE".equalsIgnoreCase(a.getActionType())) {
            r.setPublishStatus("OFFLINE");
        }
        touch(r, operator);
        resourceMapper.updateById(r);
        return a;
    }

    @Transactional
    public GovCatalogApproval reject(UserPrincipal operator, Long approvalId, Map<String, Object> body) {
        GovCatalogApproval a = requireApproval(approvalId);
        if (!"PENDING".equalsIgnoreCase(a.getStatus())) {
            throw new BusinessException(400, "仅待处理审批可驳回");
        }
        String comment = str(body.get("comment"), null);
        if (comment == null || comment.isBlank()) {
            throw new BusinessException(400, "驳回须填写审批意见");
        }
        a.setStatus("REJECTED");
        a.setReviewComment(comment);
        if (operator != null) {
            a.setReviewedBy(operator.getUsername());
        }
        a.setReviewedAt(LocalDateTime.now());
        approvalMapper.updateById(a);

        GovCatalogResource r = require(a.getResourceId());
        r.setApprovalStatus("REJECTED");
        touch(r, operator);
        resourceMapper.updateById(r);
        return a;
    }

    @Transactional
    public GovCatalogApproval withdraw(UserPrincipal operator, Long approvalId) {
        GovCatalogApproval a = requireApproval(approvalId);
        if (!"PENDING".equalsIgnoreCase(a.getStatus())) {
            throw new BusinessException(400, "仅待处理审批可撤回");
        }
        a.setStatus("WITHDRAWN");
        a.setReviewedAt(LocalDateTime.now());
        if (operator != null) {
            a.setReviewedBy(operator.getUsername());
        }
        approvalMapper.updateById(a);

        GovCatalogResource r = require(a.getResourceId());
        r.setApprovalStatus("WITHDRAWN");
        touch(r, operator);
        resourceMapper.updateById(r);
        return a;
    }

    @Transactional
    public GovCatalogResource publish(UserPrincipal operator, Long id) {
        GovCatalogResource r = require(id);
        if ("PUBLISHED".equalsIgnoreCase(r.getPublishStatus())) {
            throw new BusinessException(400, "当前已是发布状态");
        }
        if (!"APPROVED".equalsIgnoreCase(r.getApprovalStatus())
                && !"DRAFT".equalsIgnoreCase(r.getApprovalStatus())) {
            throw new BusinessException(400, "须审批通过或草稿态后方可发布（建议先提交审批）");
        }
        // 直接发布：创建审批记录并自动通过
        GovCatalogApproval a = new GovCatalogApproval();
        a.setResourceId(id);
        a.setActionType("PUBLISH");
        a.setStatus("APPROVED");
        a.setSubmitComment("直接发布");
        a.setReviewComment("直接发布通过");
        if (operator != null) {
            a.setSubmittedBy(operator.getUsername());
            a.setReviewedBy(operator.getUsername());
        }
        a.setSubmittedAt(LocalDateTime.now());
        a.setReviewedAt(LocalDateTime.now());
        approvalMapper.insert(a);

        r.setPublishStatus("PUBLISHED");
        r.setApprovalStatus("APPROVED");
        snapshotOnPublish(r, operator, "直接发布 v");
        touch(r, operator);
        resourceMapper.updateById(r);
        return r;
    }

    @Transactional
    public GovCatalogResource offline(UserPrincipal operator, Long id) {
        GovCatalogResource r = require(id);
        if (!"PUBLISHED".equalsIgnoreCase(r.getPublishStatus())) {
            throw new BusinessException(400, "仅已发布资源可下线");
        }
        GovCatalogApproval a = new GovCatalogApproval();
        a.setResourceId(id);
        a.setActionType("OFFLINE");
        a.setStatus("APPROVED");
        a.setSubmitComment("直接下线");
        a.setReviewComment("直接下线通过");
        if (operator != null) {
            a.setSubmittedBy(operator.getUsername());
            a.setReviewedBy(operator.getUsername());
        }
        a.setSubmittedAt(LocalDateTime.now());
        a.setReviewedAt(LocalDateTime.now());
        approvalMapper.insert(a);

        r.setPublishStatus("OFFLINE");
        r.setApprovalStatus("APPROVED");
        touch(r, operator);
        resourceMapper.updateById(r);
        return r;
    }

    public List<Map<String, Object>> listApprovals(Long resourceId, String status) {
        LambdaQueryWrapper<GovCatalogApproval> q = new LambdaQueryWrapper<GovCatalogApproval>()
                .orderByDesc(GovCatalogApproval::getId);
        if (resourceId != null) {
            q.eq(GovCatalogApproval::getResourceId, resourceId);
        }
        if (status != null && !status.isBlank()) {
            q.eq(GovCatalogApproval::getStatus, status);
        }
        List<GovCatalogApproval> rows = approvalMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovCatalogApproval a : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("resourceId", a.getResourceId());
            m.put("actionType", a.getActionType());
            m.put("status", a.getStatus());
            m.put("submitComment", a.getSubmitComment());
            m.put("reviewComment", a.getReviewComment());
            m.put("submittedBy", a.getSubmittedBy());
            m.put("submittedAt", a.getSubmittedAt());
            m.put("reviewedBy", a.getReviewedBy());
            m.put("reviewedAt", a.getReviewedAt());
            GovCatalogResource r = resourceMapper.selectById(a.getResourceId());
            if (r != null) {
                m.put("resourceCode", r.getResourceCode());
                m.put("resourceName", r.getResourceName());
                m.put("resourceType", r.getResourceType());
                m.put("publishStatus", r.getPublishStatus());
            }
            out.add(m);
        }
        return out;
    }

    public List<GovCatalogResourceVersion> listVersions(Long resourceId) {
        require(resourceId);
        return versionMapper.selectList(new LambdaQueryWrapper<GovCatalogResourceVersion>()
                .eq(GovCatalogResourceVersion::getResourceId, resourceId)
                .orderByDesc(GovCatalogResourceVersion::getVersionNo));
    }

    public Map<String, Object> diffVersions(Long resourceId, Integer leftNo, Integer rightNo) {
        require(resourceId);
        if (leftNo == null || rightNo == null) {
            throw new BusinessException(400, "leftNo/rightNo 不能为空");
        }
        GovCatalogResourceVersion left = versionMapper.selectOne(new LambdaQueryWrapper<GovCatalogResourceVersion>()
                .eq(GovCatalogResourceVersion::getResourceId, resourceId)
                .eq(GovCatalogResourceVersion::getVersionNo, leftNo));
        GovCatalogResourceVersion right = versionMapper.selectOne(new LambdaQueryWrapper<GovCatalogResourceVersion>()
                .eq(GovCatalogResourceVersion::getResourceId, resourceId)
                .eq(GovCatalogResourceVersion::getVersionNo, rightNo));
        if (left == null || right == null) {
            throw new BusinessException(404, "版本不存在");
        }
        Map<String, Object> leftSnap = parseSnapshot(left.getSnapshotJson());
        Map<String, Object> rightSnap = parseSnapshot(right.getSnapshotJson());
        List<Map<String, Object>> basicDiff = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        keys.addAll(leftSnap.keySet());
        keys.addAll(rightSnap.keySet());
        for (String key : keys) {
            String lv = nvl(String.valueOf(leftSnap.getOrDefault(key, "")));
            String rv = nvl(String.valueOf(rightSnap.getOrDefault(key, "")));
            if (!lv.equals(rv)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("field", key);
                row.put("left", lv);
                row.put("right", rv);
                basicDiff.add(row);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("leftNo", leftNo);
        out.put("rightNo", rightNo);
        out.put("sameSnapshot", String.valueOf(left.getSnapshotJson()).equals(String.valueOf(right.getSnapshotJson())));
        out.put("basicDiff", basicDiff);
        return out;
    }

    @Transactional
    public Map<String, Object> importResources(UserPrincipal operator, Map<String, Object> body) {
        List<Map<String, Object>> items = parseImportItems(body);
        if (items.isEmpty()) {
            throw new BusinessException(400, "导入数据为空");
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> row = items.get(i);
            try {
                String code = str(row.get("resourceCode"), null);
                GovCatalogResource existing = null;
                if (code != null && !code.isBlank()) {
                    existing = resourceMapper.selectOne(new LambdaQueryWrapper<GovCatalogResource>()
                            .eq(GovCatalogResource::getResourceCode, code));
                }
                if (existing != null) {
                    if ("PUBLISHED".equalsIgnoreCase(existing.getPublishStatus())) {
                        skipped++;
                        continue;
                    }
                    applyBody(existing, row, false);
                    touch(existing, operator);
                    resourceMapper.updateById(existing);
                    updated++;
                } else {
                    create(operator, row);
                    created++;
                }
            } catch (Exception e) {
                errors.add("行" + (i + 1) + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

    public Object exportResources(Long categoryId, String format) {
        LambdaQueryWrapper<GovCatalogResource> q = new LambdaQueryWrapper<GovCatalogResource>()
                .orderByAsc(GovCatalogResource::getId);
        if (categoryId != null) {
            q.eq(GovCatalogResource::getCategoryId, categoryId);
        }
        List<GovCatalogResource> rows = resourceMapper.selectList(q);
        String fmt = format == null ? "json" : format.trim().toLowerCase();
        if ("csv".equals(fmt)) {
            return toCsv(rows);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovCatalogResource r : rows) {
            out.add(exportRow(r));
        }
        return out;
    }

    private void snapshotOnPublish(GovCatalogResource r, UserPrincipal operator, String summaryPrefix) {
        long versionCount = versionMapper.selectCount(new LambdaQueryWrapper<GovCatalogResourceVersion>()
                .eq(GovCatalogResourceVersion::getResourceId, r.getId()));
        int nextVersion;
        if (versionCount == 0) {
            nextVersion = r.getVersionNo() == null || r.getVersionNo() < 1 ? 1 : r.getVersionNo();
        } else {
            nextVersion = (r.getVersionNo() == null ? 1 : r.getVersionNo()) + 1;
        }
        r.setVersionNo(nextVersion);
        String publisher = operator != null ? operator.getUsername() : r.getUpdatedBy();
        LocalDateTime now = LocalDateTime.now();

        GovCatalogResourceVersion ver = new GovCatalogResourceVersion();
        ver.setResourceId(r.getId());
        ver.setVersionNo(nextVersion);
        ver.setSnapshotJson(toSnapshotJson(r));
        ver.setChangeSummary(summaryPrefix + nextVersion);
        ver.setPublishedBy(publisher);
        ver.setPublishedAt(now);
        versionMapper.insert(ver);
        log.info("catalog resource {} published as v{}", r.getId(), nextVersion);
    }

    private String toSnapshotJson(GovCatalogResource r) {
        Map<String, Object> snap = exportRow(r);
        snap.put("publishStatus", r.getPublishStatus());
        snap.put("approvalStatus", r.getApprovalStatus());
        snap.put("versionNo", r.getVersionNo());
        try {
            return OM.writeValueAsString(snap);
        } catch (Exception e) {
            throw new BusinessException(500, "版本快照序列化失败");
        }
    }

    private Map<String, Object> exportRow(GovCatalogResource r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resourceCode", r.getResourceCode());
        m.put("resourceName", r.getResourceName());
        m.put("resourceType", r.getResourceType());
        m.put("metadataEntryCode", r.getMetadataEntryCode());
        m.put("dataSourceId", r.getDataSourceId());
        m.put("physicalTableName", r.getPhysicalTableName());
        m.put("sourcePathType", r.getSourcePathType());
        m.put("qualityScore", r.getQualityScore());
        m.put("categoryId", r.getCategoryId());
        m.put("categoryPath", r.getCategoryPath());
        m.put("providerOrg", r.getProviderOrg());
        m.put("resourceFormat", r.getResourceFormat());
        m.put("shareType", r.getShareType());
        m.put("updateCycle", r.getUpdateCycle());
        m.put("description", r.getDescription());
        m.put("secretFlag", r.getSecretFlag());
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseImportItems(Map<String, Object> body) {
        Object itemsObj = body.get("items");
        if (itemsObj instanceof List<?> list) {
            return castItemList(list);
        }
        String format = str(body.get("format"), "json").toLowerCase();
        String content = str(body.get("content"), null);
        if (content != null && !content.isBlank()) {
            if ("csv".equals(format)) {
                return parseCsv(content);
            }
            try {
                Object parsed = OM.readValue(content, Object.class);
                if (parsed instanceof List<?> list) {
                    return castItemList(list);
                }
                throw new BusinessException(400, "JSON 内容须为数组");
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(400, "JSON 解析失败: " + e.getMessage());
            }
        }
        throw new BusinessException(400, "请提供 items 数组或 content 文本");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castItemList(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            } else {
                throw new BusinessException(400, "导入项须为对象");
            }
        }
        return out;
    }

    private List<Map<String, Object>> parseCsv(String content) {
        String[] lines = content.split("\\r?\\n");
        if (lines.length < 2) {
            throw new BusinessException(400, "CSV 至少包含表头与一行数据");
        }
        String[] headers = splitCsvLine(lines[0]);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            String[] cols = splitCsvLine(lines[i]);
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.length && c < cols.length; c++) {
                row.put(headers[c].trim(), cols[c].trim());
            }
            out.add(row);
        }
        return out;
    }

    private String[] splitCsvLine(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuote = !inQuote;
            } else if (ch == ',' && !inQuote) {
                cols.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        cols.add(cur.toString());
        return cols.toArray(new String[0]);
    }

    private String toCsv(List<GovCatalogResource> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", EXPORT_HEADERS)).append('\n');
        for (GovCatalogResource r : rows) {
            Map<String, Object> m = exportRow(r);
            List<String> cols = new ArrayList<>();
            for (String h : EXPORT_HEADERS) {
                cols.add(escapeCsv(String.valueOf(m.getOrDefault(h, ""))));
            }
            sb.append(String.join(",", cols)).append('\n');
        }
        return sb.toString();
    }

    private static String escapeCsv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private Map<String, Object> parseSnapshot(String json) {
        try {
            return OM.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String nvl(String s) {
        return "null".equalsIgnoreCase(s) ? "" : s;
    }

    private void applyBody(GovCatalogResource r, Map<String, Object> body, boolean creating) {
        if (creating || body.containsKey("resourceCode")) {
            r.setResourceCode(str(body.get("resourceCode"), creating ? "RES_" + System.currentTimeMillis() : r.getResourceCode()));
        }
        if (creating || body.containsKey("resourceName")) {
            r.setResourceName(required(body.get("resourceName"), "resourceName").toString());
        }
        if (creating || body.containsKey("resourceType")) {
            r.setResourceType(str(body.get("resourceType"), creating ? "DATA" : r.getResourceType()));
        }
        if (body.containsKey("metadataEntryCode") || creating) {
            r.setMetadataEntryCode(str(body.get("metadataEntryCode"), creating ? null : r.getMetadataEntryCode()));
        }
        if (body.containsKey("dataSourceId")) {
            r.setDataSourceId(longVal(body.get("dataSourceId"), r.getDataSourceId()));
        }
        if (body.containsKey("physicalTableName") || creating) {
            r.setPhysicalTableName(str(body.get("physicalTableName"), creating ? null : r.getPhysicalTableName()));
        }
        if (body.containsKey("sourcePathType") || creating) {
            r.setSourcePathType(str(body.get("sourcePathType"), creating ? "DIRECT" : r.getSourcePathType()));
        }
        if (body.containsKey("qualityScore")) {
            r.setQualityScore(decimalVal(body.get("qualityScore"), r.getQualityScore()));
        }
        if (body.containsKey("categoryId") || creating) {
            Long categoryId = longVal(body.get("categoryId"), null);
            r.setCategoryId(categoryId);
            if (categoryId != null) {
                GovCatalogCategory cat = categoryMapper.selectById(categoryId);
                if (cat == null) {
                    throw new BusinessException(400, "分类不存在");
                }
                r.setCategoryPath(cat.getCategoryPath());
            } else if (body.containsKey("categoryPath")) {
                r.setCategoryPath(str(body.get("categoryPath"), null));
            }
        } else if (body.containsKey("categoryPath")) {
            r.setCategoryPath(str(body.get("categoryPath"), r.getCategoryPath()));
        }
        if (body.containsKey("providerOrg") || creating) {
            r.setProviderOrg(str(body.get("providerOrg"), creating ? null : r.getProviderOrg()));
        }
        if (body.containsKey("resourceFormat") || creating) {
            r.setResourceFormat(str(body.get("resourceFormat"), creating ? "DATABASE" : r.getResourceFormat()));
        }
        if (body.containsKey("shareType") || creating) {
            r.setShareType(str(body.get("shareType"), creating ? "OPEN" : r.getShareType()));
        }
        if (body.containsKey("updateCycle") || creating) {
            r.setUpdateCycle(str(body.get("updateCycle"), creating ? "DAILY" : r.getUpdateCycle()));
        }
        if (body.containsKey("description") || creating) {
            r.setDescription(str(body.get("description"), creating ? null : r.getDescription()));
        }
        if (body.containsKey("secretFlag")) {
            r.setSecretFlag(intVal(body.get("secretFlag"), 0));
        }
    }

    private void touch(GovCatalogResource r, UserPrincipal operator) {
        if (operator != null) {
            r.setUpdatedBy(operator.getUsername());
        }
        r.setUpdatedAt(LocalDateTime.now());
    }

    private GovCatalogResource require(Long id) {
        GovCatalogResource r = resourceMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(404, "资源不存在");
        }
        return r;
    }

    private GovCatalogApproval requireApproval(Long id) {
        GovCatalogApproval a = approvalMapper.selectById(id);
        if (a == null) {
            throw new BusinessException(404, "审批单不存在");
        }
        return a;
    }

    private static Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " 不能为空");
        }
        return v;
    }

    private static String str(Object v, String def) {
        if (v == null || String.valueOf(v).isBlank()) {
            return def;
        }
        return String.valueOf(v).trim();
    }

    private static Long longVal(Object v, Long def) {
        if (v == null || String.valueOf(v).isBlank()) {
            return def;
        }
        return Long.valueOf(String.valueOf(v));
    }

    private static Integer intVal(Object v, Integer def) {
        if (v == null || String.valueOf(v).isBlank()) {
            return def;
        }
        return Integer.valueOf(String.valueOf(v));
    }

    private static BigDecimal decimalVal(Object v, BigDecimal def) {
        if (v == null || String.valueOf(v).isBlank()) {
            return def;
        }
        return new BigDecimal(String.valueOf(v));
    }
}
