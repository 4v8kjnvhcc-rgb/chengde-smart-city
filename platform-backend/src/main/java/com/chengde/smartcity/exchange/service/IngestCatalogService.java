package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.IngCatalogApproval;
import com.chengde.smartcity.exchange.entity.IngCategoryNode;
import com.chengde.smartcity.exchange.entity.IngCategoryResource;
import com.chengde.smartcity.exchange.entity.IngResourceRegistry;
import com.chengde.smartcity.exchange.mapper.BizCatalogItemMapper;
import com.chengde.smartcity.exchange.mapper.IngCatalogApprovalMapper;
import com.chengde.smartcity.exchange.mapper.IngCategoryNodeMapper;
import com.chengde.smartcity.exchange.mapper.IngCategoryResourceMapper;
import com.chengde.smartcity.exchange.mapper.IngResourceRegistryMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestCatalogService {

    private final IngCategoryNodeMapper categoryMapper;
    private final IngResourceRegistryMapper registryMapper;
    private final IngCategoryResourceMapper bindMapper;
    private final IngCatalogApprovalMapper approvalMapper;
    private final BizCatalogItemMapper portalCatalogMapper;

    public IngestCatalogService(IngCategoryNodeMapper categoryMapper,
                                IngResourceRegistryMapper registryMapper,
                                IngCategoryResourceMapper bindMapper,
                                IngCatalogApprovalMapper approvalMapper,
                                BizCatalogItemMapper portalCatalogMapper) {
        this.categoryMapper = categoryMapper;
        this.registryMapper = registryMapper;
        this.bindMapper = bindMapper;
        this.approvalMapper = approvalMapper;
        this.portalCatalogMapper = portalCatalogMapper;
    }

    // ── Categories ──────────────────────────────────────────────

    public List<IngCategoryNode> listCategories(String keyword) {
        LambdaQueryWrapper<IngCategoryNode> q = new LambdaQueryWrapper<IngCategoryNode>()
                .orderByAsc(IngCategoryNode::getSortOrder)
                .orderByAsc(IngCategoryNode::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.like(IngCategoryNode::getNodeName, keyword.trim());
        }
        return categoryMapper.selectList(q);
    }

    @Transactional
    public Long createCategory(UserPrincipal operator, Map<String, Object> body) {
        String name = required(body.get("nodeName"), "资源目录名称").toString().trim();
        String code = str(body.get("nodeCode"), "").trim();
        if (code.isBlank()) {
            throw new BusinessException(400, "分类代码不能为空");
        }
        if (categoryMapper.selectCount(new LambdaQueryWrapper<IngCategoryNode>()
                .eq(IngCategoryNode::getNodeCode, code)) > 0) {
            throw new BusinessException(400, "分类代码已存在");
        }
        Object parentRaw = body.get("parentId");
        if (parentRaw == null || String.valueOf(parentRaw).isBlank()) {
            throw new BusinessException(400, "所属资源目录不能为空");
        }
        Long parentId = Long.valueOf(String.valueOf(parentRaw));
        if (parentId != 0L) {
            IngCategoryNode parent = categoryMapper.selectById(parentId);
            if (parent == null) throw new BusinessException(400, "所属资源目录不存在");
        }
        Integer secretFlag = toInt(body.get("secretFlag"), null);
        if (secretFlag == null) {
            throw new BusinessException(400, "是否涉密为必填项");
        }
        IngCategoryNode node = new IngCategoryNode();
        node.setNodeCode(code);
        node.setNodeName(name);
        node.setParentId(parentId);
        node.setSecretFlag(secretFlag == 1 ? 1 : 0);
        node.setSecretLevel(node.getSecretFlag() == 1 ? "SECRET" : "INTERNAL");
        node.setDescription(blankToNull(str(body.get("description"), "")));
        node.setSortOrder(toInt(body.get("sortOrder"), 0));
        categoryMapper.insert(node);
        return node.getId();
    }

    @Transactional
    public void updateCategory(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngCategoryNode node = categoryMapper.selectById(id);
        if (node == null) throw new BusinessException(404, "分类不存在");
        if (body.get("nodeName") != null) {
            node.setNodeName(required(body.get("nodeName"), "资源目录名称").toString().trim());
        }
        if (body.get("nodeCode") != null) {
            String code = required(body.get("nodeCode"), "分类代码").toString().trim();
            Long dup = categoryMapper.selectCount(new LambdaQueryWrapper<IngCategoryNode>()
                    .eq(IngCategoryNode::getNodeCode, code).ne(IngCategoryNode::getId, id));
            if (dup != null && dup > 0) throw new BusinessException(400, "分类代码已存在");
            node.setNodeCode(code);
        }
        if (body.get("parentId") != null) {
            Long parentId = Long.valueOf(String.valueOf(body.get("parentId")));
            if (id.equals(parentId)) throw new BusinessException(400, "所属目录不能是自身");
            if (parentId != 0L && collectSubtreeIds(id).contains(parentId)) {
                throw new BusinessException(400, "不能移动到子节点下");
            }
            node.setParentId(parentId);
        }
        if (body.get("secretFlag") != null) {
            int flag = toInt(body.get("secretFlag"), 0) == 1 ? 1 : 0;
            node.setSecretFlag(flag);
            node.setSecretLevel(flag == 1 ? "SECRET" : "INTERNAL");
        }
        if (body.containsKey("description")) {
            node.setDescription(blankToNull(str(body.get("description"), "")));
        }
        if (body.get("sortOrder") != null) {
            node.setSortOrder(toInt(body.get("sortOrder"), 0));
        }
        categoryMapper.updateById(node);
    }

    @Transactional
    public void deleteCategory(UserPrincipal operator, Long id) {
        IngCategoryNode node = categoryMapper.selectById(id);
        if (node == null) throw new BusinessException(404, "分类不存在");
        long child = categoryMapper.selectCount(new LambdaQueryWrapper<IngCategoryNode>()
                .eq(IngCategoryNode::getParentId, id));
        if (child > 0) throw new BusinessException(400, "请先删除子分类");
        List<IngCategoryResource> binds = bindMapper.selectList(new LambdaQueryWrapper<IngCategoryResource>()
                .eq(IngCategoryResource::getCategoryId, id));
        for (IngCategoryResource b : binds) {
            IngResourceRegistry r = registryMapper.selectById(b.getRegistryId());
            if (r != null) {
                r.setCategoryId(null);
                registryMapper.updateById(r);
            }
            bindMapper.deleteById(b.getId());
        }
        categoryMapper.deleteById(id);
    }

    // ── Registries ──────────────────────────────────────────────

    public List<IngResourceRegistry> listRegistries(UserPrincipal operator, String keyword,
                                                    String approvalStatus, String shareType,
                                                    Boolean unboundOnly, Long categoryId) {
        LambdaQueryWrapper<IngResourceRegistry> q = new LambdaQueryWrapper<IngResourceRegistry>()
                .orderByDesc(IngResourceRegistry::getId);
        applyOrgScope(q, operator);
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            q.and(w -> w.like(IngResourceRegistry::getTitle, k)
                    .or().like(IngResourceRegistry::getRegistryCode, k)
                    .or().like(IngResourceRegistry::getResourceCode, k));
        }
        if (approvalStatus != null && !approvalStatus.isBlank()) {
            q.eq(IngResourceRegistry::getApprovalStatus, approvalStatus.trim());
        }
        if (shareType != null && !shareType.isBlank()) {
            q.eq(IngResourceRegistry::getShareType, shareType.trim());
        }
        if (categoryId != null) {
            q.eq(IngResourceRegistry::getCategoryId, categoryId);
        }
        if (Boolean.TRUE.equals(unboundOnly)) {
            Set<Long> bound = bindMapper.selectList(new LambdaQueryWrapper<>()).stream()
                    .map(IngCategoryResource::getRegistryId)
                    .collect(Collectors.toSet());
            if (!bound.isEmpty()) {
                q.notIn(IngResourceRegistry::getId, bound);
            }
        }
        return registryMapper.selectList(q);
    }

    public IngResourceRegistry getRegistry(UserPrincipal operator, Long id) {
        IngResourceRegistry r = requireRegistry(id);
        assertOrgAccess(operator, r);
        return r;
    }

    @Transactional
    public Long createRegistry(UserPrincipal operator, Map<String, Object> body) {
        IngResourceRegistry r = new IngResourceRegistry();
        applyRegistryBody(r, body, true);
        r.setRegistryCode(str(body.get("registryCode"), "REG_" + System.currentTimeMillis()));
        if (r.getResourceCode() == null || r.getResourceCode().isBlank()) {
            r.setResourceCode(r.getRegistryCode());
        }
        r.setPublishStatus("DRAFT");
        r.setApprovalStatus("DRAFT");
        r.setCreatedBy(operator.getUsername());
        r.setOrgId(operator.getOrgId());
        r.setCreatedAt(LocalDateTime.now());
        registryMapper.insert(r);
        return r.getId();
    }

    @Transactional
    public List<Long> batchCreateFromTables(UserPrincipal operator, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            throw new BusinessException(400, "请提供批量编目条目");
        }
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> item : items) {
            ids.add(createRegistry(operator, item));
        }
        return ids;
    }

    @Transactional
    public void updateRegistry(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngResourceRegistry r = requireRegistry(id);
        assertOrgAccess(operator, r);
        if ("PUBLISHED".equals(r.getPublishStatus())) {
            throw new BusinessException(400, "已发布资源请先下线再编辑");
        }
        if ("PENDING".equals(r.getApprovalStatus())) {
            throw new BusinessException(400, "审批中不可编辑");
        }
        applyRegistryBody(r, body, false);
        r.setUpdatedAt(LocalDateTime.now());
        registryMapper.updateById(r);
    }

    @Transactional
    public void deleteRegistry(UserPrincipal operator, Long id) {
        IngResourceRegistry r = requireRegistry(id);
        assertOrgAccess(operator, r);
        if ("PUBLISHED".equals(r.getPublishStatus())) {
            throw new BusinessException(400, "已发布资源请先提交下线审批");
        }
        if ("PENDING".equals(r.getApprovalStatus())) {
            throw new BusinessException(400, "审批中不可删除");
        }
        bindMapper.delete(new LambdaQueryWrapper<IngCategoryResource>()
                .eq(IngCategoryResource::getRegistryId, id));
        registryMapper.deleteById(id);
    }

    @Transactional
    public Map<String, Object> importRegistries(UserPrincipal operator, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(400, "导入数据为空");
        }
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            try {
                if (row.get("title") == null || String.valueOf(row.get("title")).isBlank()) {
                    throw new BusinessException(400, "信息资源名称不能为空");
                }
                createRegistry(operator, row);
                ok++;
            } catch (Exception e) {
                errors.add("第" + (i + 1) + "行: " + e.getMessage());
            }
        }
        return Map.of("success", ok, "failed", errors.size(), "errors", errors);
    }

    // ── Bind / Publish ──────────────────────────────────────────

    public List<IngResourceRegistry> listBound(UserPrincipal operator, Long categoryId) {
        if (categoryId == null) return List.of();
        List<Long> ids = bindMapper.selectList(new LambdaQueryWrapper<IngCategoryResource>()
                        .eq(IngCategoryResource::getCategoryId, categoryId))
                .stream().map(IngCategoryResource::getRegistryId).toList();
        if (ids.isEmpty()) return List.of();
        LambdaQueryWrapper<IngResourceRegistry> q = new LambdaQueryWrapper<IngResourceRegistry>()
                .in(IngResourceRegistry::getId, ids)
                .orderByDesc(IngResourceRegistry::getId);
        applyOrgScope(q, operator);
        return registryMapper.selectList(q);
    }

    @Transactional
    public void bindResources(UserPrincipal operator, Long categoryId, List<Long> registryIds) {
        IngCategoryNode cat = categoryMapper.selectById(categoryId);
        if (cat == null) throw new BusinessException(404, "分类不存在");
        if (registryIds == null || registryIds.isEmpty()) {
            throw new BusinessException(400, "请选择资源");
        }
        String path = buildCategoryPath(categoryId);
        for (Long rid : registryIds) {
            IngResourceRegistry r = requireRegistry(rid);
            assertOrgAccess(operator, r);
            if ("PUBLISHED".equals(r.getPublishStatus())) {
                throw new BusinessException(400, "已发布资源「" + r.getTitle() + "」不可改关联，请先下线");
            }
            Long exists = bindMapper.selectCount(new LambdaQueryWrapper<IngCategoryResource>()
                    .eq(IngCategoryResource::getRegistryId, rid));
            if (exists != null && exists > 0) {
                throw new BusinessException(400, "资源「" + r.getTitle() + "」已被关联");
            }
            IngCategoryResource bind = new IngCategoryResource();
            bind.setCategoryId(categoryId);
            bind.setRegistryId(rid);
            bind.setBoundBy(operator.getUsername());
            bind.setBoundAt(LocalDateTime.now());
            bindMapper.insert(bind);
            r.setCategoryId(categoryId);
            r.setCategoryPath(path);
            registryMapper.updateById(r);
        }
    }

    @Transactional
    public void unbindResources(UserPrincipal operator, List<Long> registryIds) {
        if (registryIds == null || registryIds.isEmpty()) {
            throw new BusinessException(400, "请选择资源");
        }
        for (Long rid : registryIds) {
            IngResourceRegistry r = requireRegistry(rid);
            assertOrgAccess(operator, r);
            if ("PUBLISHED".equals(r.getPublishStatus())) {
                throw new BusinessException(400, "已发布资源请先下线再解除关联");
            }
            bindMapper.delete(new LambdaQueryWrapper<IngCategoryResource>()
                    .eq(IngCategoryResource::getRegistryId, rid));
            r.setCategoryId(null);
            registryMapper.updateById(r);
        }
    }

    @Transactional
    public Long submitPublish(UserPrincipal operator, List<Long> registryIds, String comment) {
        if (registryIds == null || registryIds.isEmpty()) {
            throw new BusinessException(400, "请选择要发布的资源");
        }
        Long lastId = null;
        for (Long rid : registryIds) {
            IngResourceRegistry r = requireRegistry(rid);
            assertOrgAccess(operator, r);
            if (r.getCategoryId() == null) {
                throw new BusinessException(400, "资源「" + r.getTitle() + "」尚未关联分类");
            }
            if ("PUBLISHED".equals(r.getPublishStatus())) {
                throw new BusinessException(400, "资源「" + r.getTitle() + "」已发布");
            }
            if ("PENDING".equals(r.getApprovalStatus())) {
                throw new BusinessException(400, "资源「" + r.getTitle() + "」已在审批中");
            }
            r.setApprovalStatus("PENDING");
            r.setPublishStatus("PENDING_PUBLISH");
            registryMapper.updateById(r);
            lastId = createApproval(operator, rid, r.getCategoryId(), "PUBLISH", comment, null);
        }
        return lastId;
    }

    @Transactional
    public Long submitOffline(UserPrincipal operator, List<Long> registryIds, String comment) {
        if (registryIds == null || registryIds.isEmpty()) {
            throw new BusinessException(400, "请选择要下线的资源");
        }
        Long lastId = null;
        for (Long rid : registryIds) {
            IngResourceRegistry r = requireRegistry(rid);
            assertOrgAccess(operator, r);
            if (!"PUBLISHED".equals(r.getPublishStatus())) {
                throw new BusinessException(400, "仅已发布资源可下线");
            }
            if ("PENDING".equals(r.getApprovalStatus())) {
                throw new BusinessException(400, "资源「" + r.getTitle() + "」已在审批中");
            }
            r.setApprovalStatus("PENDING");
            registryMapper.updateById(r);
            lastId = createApproval(operator, rid, r.getCategoryId(), "OFFLINE", comment, null);
        }
        return lastId;
    }

    // ── Approvals ───────────────────────────────────────────────

    public List<Map<String, Object>> listApprovals(String status) {
        LambdaQueryWrapper<IngCatalogApproval> q = new LambdaQueryWrapper<IngCatalogApproval>()
                .orderByDesc(IngCatalogApproval::getId);
        if (status != null && !status.isBlank()) {
            q.eq(IngCatalogApproval::getStatus, status.trim());
        }
        List<IngCatalogApproval> list = approvalMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (IngCatalogApproval a : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("registryId", a.getRegistryId());
            m.put("categoryId", a.getCategoryId());
            m.put("actionType", a.getActionType());
            m.put("status", a.getStatus());
            m.put("submitComment", a.getSubmitComment());
            m.put("reviewComment", a.getReviewComment());
            m.put("submittedBy", a.getSubmittedBy());
            m.put("submittedAt", a.getSubmittedAt());
            m.put("reviewedBy", a.getReviewedBy());
            m.put("reviewedAt", a.getReviewedAt());
            if (a.getRegistryId() != null) {
                IngResourceRegistry r = registryMapper.selectById(a.getRegistryId());
                if (r != null) {
                    m.put("resourceCode", r.getResourceCode() != null ? r.getResourceCode() : r.getRegistryCode());
                    m.put("resourceName", r.getTitle());
                    m.put("publishStatus", r.getPublishStatus());
                    m.put("shareType", r.getShareType());
                }
            }
            out.add(m);
        }
        return out;
    }

    @Transactional
    public void approve(UserPrincipal operator, Long id, String comment) {
        IngCatalogApproval a = requireApproval(id);
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessException(400, "仅待处理审批可操作");
        }
        applyApprove(operator, a, comment);
    }

    @Transactional
    public void reject(UserPrincipal operator, Long id, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new BusinessException(400, "拒绝时必须填写审批意见");
        }
        IngCatalogApproval a = requireApproval(id);
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessException(400, "仅待处理审批可操作");
        }
        a.setStatus("REJECTED");
        a.setReviewComment(comment.trim());
        a.setReviewedBy(operator.getUsername());
        a.setReviewedAt(LocalDateTime.now());
        approvalMapper.updateById(a);
        if (a.getRegistryId() != null) {
            IngResourceRegistry r = registryMapper.selectById(a.getRegistryId());
            if (r != null) {
                r.setApprovalStatus("REJECTED");
                if ("PENDING_PUBLISH".equals(r.getPublishStatus())) {
                    r.setPublishStatus("DRAFT");
                }
                registryMapper.updateById(r);
            }
        }
    }

    @Transactional
    public Map<String, Object> batchApprove(UserPrincipal operator, List<Long> ids, String comment) {
        int n = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                approve(operator, id, comment);
                n++;
            } catch (Exception e) {
                errors.add(id + ": " + e.getMessage());
            }
        }
        return Map.of("approved", n, "errors", errors);
    }

    @Transactional
    public Map<String, Object> batchReject(UserPrincipal operator, List<Long> ids, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new BusinessException(400, "拒绝时必须填写审批意见");
        }
        int n = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                reject(operator, id, comment);
                n++;
            } catch (Exception e) {
                errors.add(id + ": " + e.getMessage());
            }
        }
        return Map.of("rejected", n, "errors", errors);
    }

    /** 兼容旧接口：直接审批编目行 */
    @Transactional
    public void approveRegistryLegacy(UserPrincipal operator, Long registryId, Map<String, Object> body) {
        String action = str(body.get("action"), "APPROVE").toUpperCase(Locale.ROOT);
        IngResourceRegistry r = requireRegistry(registryId);
        if ("APPROVE".equals(action) || "APPROVED".equals(action)) {
            r.setApprovalStatus("APPROVED");
            r.setPublishStatus("PUBLISHED");
            registryMapper.updateById(r);
            syncPortal(r);
        } else {
            r.setApprovalStatus("REJECTED");
            if ("PENDING_PUBLISH".equals(r.getPublishStatus())) {
                r.setPublishStatus("DRAFT");
            }
            registryMapper.updateById(r);
        }
    }

    // ── internals ───────────────────────────────────────────────

    private void applyApprove(UserPrincipal operator, IngCatalogApproval a, String comment) {
        a.setStatus("APPROVED");
        a.setReviewComment(comment);
        a.setReviewedBy(operator.getUsername());
        a.setReviewedAt(LocalDateTime.now());
        approvalMapper.updateById(a);
        if (a.getRegistryId() == null) return;
        IngResourceRegistry r = requireRegistry(a.getRegistryId());
        if ("PUBLISH".equals(a.getActionType())) {
            r.setApprovalStatus("APPROVED");
            r.setPublishStatus("PUBLISHED");
            registryMapper.updateById(r);
            syncPortal(r);
        } else if ("OFFLINE".equals(a.getActionType())) {
            r.setApprovalStatus("APPROVED");
            r.setPublishStatus("OFFLINE");
            registryMapper.updateById(r);
            offlinePortal(r);
        } else if ("DELETE".equals(a.getActionType())) {
            offlinePortal(r);
            bindMapper.delete(new LambdaQueryWrapper<IngCategoryResource>()
                    .eq(IngCategoryResource::getRegistryId, r.getId()));
            registryMapper.deleteById(r.getId());
        }
    }

    private void syncPortal(IngResourceRegistry r) {
        // 涉密资源不同步到门户统一展现
        if ("SECRET".equalsIgnoreCase(r.getSecretLevel())
                || "CONFIDENTIAL".equalsIgnoreCase(r.getSecretLevel())
                || "TOP_SECRET".equalsIgnoreCase(r.getSecretLevel())) {
            return;
        }
        BizCatalogItem item = null;
        if (r.getPortalCatalogId() != null) {
            item = portalCatalogMapper.selectById(r.getPortalCatalogId());
        }
        if (item == null) {
            String code = "ING_" + (r.getResourceCode() != null ? r.getResourceCode() : r.getRegistryCode());
            item = portalCatalogMapper.selectOne(new LambdaQueryWrapper<BizCatalogItem>()
                    .eq(BizCatalogItem::getCatalogCode, code).last("LIMIT 1"));
            if (item == null) {
                item = new BizCatalogItem();
                item.setCatalogCode(code);
                item.setCreatedBy(r.getCreatedBy());
                item.setCreatedAt(LocalDateTime.now());
            }
        }
        item.setTitle(r.getTitle());
        item.setDescription(r.getDescription() != null ? r.getDescription() : r.getAssetSummary());
        item.setCatalogKind("DATA");
        item.setThemeName(r.getCategoryPath());
        item.setProviderOrg(r.getProviderOrg());
        item.setShareModes(mapShareModes(r.getResourceFormat(), r.getShareType()));
        item.setResourceCount(1);
        item.setHotScore(item.getHotScore() == null ? 1 : item.getHotScore());
        item.setPublishStatus("PUBLISHED");
        item.setPublishedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        if (item.getId() == null) {
            portalCatalogMapper.insert(item);
        } else {
            portalCatalogMapper.updateById(item);
        }
        r.setPortalCatalogId(item.getId());
        registryMapper.updateById(r);
    }

    private void offlinePortal(IngResourceRegistry r) {
        if (r.getPortalCatalogId() == null) return;
        BizCatalogItem item = portalCatalogMapper.selectById(r.getPortalCatalogId());
        if (item == null) return;
        item.setPublishStatus("OFFLINE");
        item.setUpdatedAt(LocalDateTime.now());
        portalCatalogMapper.updateById(item);
    }

    private static String mapShareModes(String format, String shareType) {
        if ("NOT_SHARE".equalsIgnoreCase(shareType)) return "TABLE";
        if ("API".equalsIgnoreCase(format)) return "API";
        if ("FILE".equalsIgnoreCase(format)) return "FILE";
        return "TABLE,FILE,API";
    }

    private Long createApproval(UserPrincipal operator, Long registryId, Long categoryId,
                                String action, String comment, String payload) {
        IngCatalogApproval a = new IngCatalogApproval();
        a.setRegistryId(registryId);
        a.setCategoryId(categoryId);
        a.setActionType(action);
        a.setStatus("PENDING");
        a.setSubmitComment(comment);
        a.setSubmittedBy(operator.getUsername());
        a.setSubmittedOrgId(operator.getOrgId());
        a.setSubmittedAt(LocalDateTime.now());
        a.setPayloadJson(payload);
        approvalMapper.insert(a);
        return a.getId();
    }

    private void applyRegistryBody(IngResourceRegistry r, Map<String, Object> body, boolean creating) {
        if (creating || body.get("title") != null) {
            r.setTitle(required(body.get("title"), "信息资源名称").toString().trim());
        }
        if (body.get("resourceCode") != null) {
            r.setResourceCode(blankToNull(str(body.get("resourceCode"), "")));
        }
        if (body.get("providerOrg") != null) {
            r.setProviderOrg(blankToNull(str(body.get("providerOrg"), "")));
        }
        if (body.get("resourceFormat") != null) {
            r.setResourceFormat(str(body.get("resourceFormat"), "DATABASE"));
        } else if (creating) {
            r.setResourceFormat("DATABASE");
        }
        if (body.get("shareType") != null) {
            r.setShareType(str(body.get("shareType"), "CONDITIONAL"));
        } else if (creating) {
            r.setShareType("CONDITIONAL");
        }
        if (body.get("updateCycle") != null) {
            r.setUpdateCycle(str(body.get("updateCycle"), "MONTHLY"));
        } else if (creating) {
            r.setUpdateCycle("MONTHLY");
        }
        if (body.containsKey("description")) {
            r.setDescription(blankToNull(str(body.get("description"), "")));
        }
        if (body.get("categoryPath") != null) {
            r.setCategoryPath(str(body.get("categoryPath"), r.getCategoryPath()));
        }
        if (body.get("secretLevel") != null) {
            r.setSecretLevel(str(body.get("secretLevel"), "INTERNAL"));
        } else if (creating) {
            r.setSecretLevel("INTERNAL");
        }
        if (body.get("refSourceId") != null && !String.valueOf(body.get("refSourceId")).isBlank()) {
            r.setRefSourceId(Long.valueOf(String.valueOf(body.get("refSourceId"))));
        }
        if (body.get("refTableId") != null && !String.valueOf(body.get("refTableId")).isBlank()) {
            r.setRefTableId(Long.valueOf(String.valueOf(body.get("refTableId"))));
        }
        if (body.get("assetSummary") != null) {
            r.setAssetSummary(blankToNull(str(body.get("assetSummary"), "")));
        }
        if (body.get("categoryId") != null && !String.valueOf(body.get("categoryId")).isBlank()) {
            r.setCategoryId(Long.valueOf(String.valueOf(body.get("categoryId"))));
        }
    }

    private void applyOrgScope(LambdaQueryWrapper<IngResourceRegistry> q, UserPrincipal operator) {
        if (operator == null || operator.isSystemAdmin()) return;
        if (operator.getRoleCodes() != null && operator.getRoleCodes().contains("PLATFORM_ADMIN")) return;
        if (operator.isDeptAdmin() && operator.getOrgId() != null) {
            q.eq(IngResourceRegistry::getOrgId, operator.getOrgId());
        }
    }

    private void assertOrgAccess(UserPrincipal operator, IngResourceRegistry r) {
        if (operator == null || operator.isSystemAdmin()) return;
        if (operator.getRoleCodes() != null && operator.getRoleCodes().contains("PLATFORM_ADMIN")) return;
        if (operator.isDeptAdmin() && operator.getOrgId() != null
                && r.getOrgId() != null && !operator.getOrgId().equals(r.getOrgId())) {
            throw new BusinessException(403, "无权操作其他部门的数据资源");
        }
    }

    private IngResourceRegistry requireRegistry(Long id) {
        IngResourceRegistry r = registryMapper.selectById(id);
        if (r == null) throw new BusinessException(404, "编目不存在");
        return r;
    }

    private IngCatalogApproval requireApproval(Long id) {
        IngCatalogApproval a = approvalMapper.selectById(id);
        if (a == null) throw new BusinessException(404, "审批单不存在");
        return a;
    }

    private String buildCategoryPath(Long categoryId) {
        List<String> parts = new ArrayList<>();
        Long cur = categoryId;
        int guard = 0;
        while (cur != null && cur != 0L && guard++ < 20) {
            IngCategoryNode n = categoryMapper.selectById(cur);
            if (n == null) break;
            parts.add(0, n.getNodeName());
            cur = n.getParentId();
        }
        return String.join("/", parts);
    }

    private Set<Long> collectSubtreeIds(Long rootId) {
        List<IngCategoryNode> all = categoryMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, List<Long>> children = new HashMap<>();
        for (IngCategoryNode n : all) {
            Long p = n.getParentId() == null ? 0L : n.getParentId();
            children.computeIfAbsent(p, k -> new ArrayList<>()).add(n.getId());
        }
        Set<Long> result = new HashSet<>();
        ArrayList<Long> stack = new ArrayList<>();
        stack.add(rootId);
        while (!stack.isEmpty()) {
            Long id = stack.remove(stack.size() - 1);
            if (!result.add(id)) continue;
            List<Long> kids = children.get(id);
            if (kids != null) stack.addAll(kids);
        }
        return result;
    }

    private static String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v).trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + "不能为空");
        }
        return v;
    }

    private static int toInt(Object v, Integer def) {
        if (v == null || String.valueOf(v).isBlank()) {
            if (def == null) throw new BusinessException(400, "数值不能为空");
            return def;
        }
        return Integer.parseInt(String.valueOf(v));
    }
}
