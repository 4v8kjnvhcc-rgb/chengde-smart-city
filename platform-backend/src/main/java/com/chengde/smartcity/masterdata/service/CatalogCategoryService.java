package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovCatalogApproval;
import com.chengde.smartcity.masterdata.entity.GovCatalogCategory;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.mapper.GovCatalogApprovalMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogCategoryMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogCategoryService {

    private static final Logger log = LoggerFactory.getLogger(CatalogCategoryService.class);

    private final GovCatalogCategoryMapper categoryMapper;
    private final GovCatalogResourceMapper resourceMapper;
    private final GovCatalogApprovalMapper approvalMapper;
    private final ObjectMapper objectMapper;

    public CatalogCategoryService(GovCatalogCategoryMapper categoryMapper,
                                  GovCatalogResourceMapper resourceMapper,
                                  GovCatalogApprovalMapper approvalMapper,
                                  ObjectMapper objectMapper) {
        this.categoryMapper = categoryMapper;
        this.resourceMapper = resourceMapper;
        this.approvalMapper = approvalMapper;
        this.objectMapper = objectMapper;
    }

    public List<GovCatalogCategory> list() {
        return list(null);
    }

    public List<GovCatalogCategory> list(String catalogOrigin) {
        LambdaQueryWrapper<GovCatalogCategory> q = new LambdaQueryWrapper<GovCatalogCategory>()
                .orderByAsc(GovCatalogCategory::getSortOrder)
                .orderByAsc(GovCatalogCategory::getId);
        if (catalogOrigin != null && !catalogOrigin.isBlank()) {
            q.eq(GovCatalogCategory::getCatalogOrigin, catalogOrigin.trim().toUpperCase(Locale.ROOT));
        }
        // ???????????????????
        q.and(w -> w.isNull(GovCatalogCategory::getStatus)
                .or().ne(GovCatalogCategory::getStatus, "OFFLINE"));
        return categoryMapper.selectList(q);
    }

    public List<Map<String, Object>> tree() {
        return tree(null);
    }

    public List<Map<String, Object>> tree(String catalogOrigin) {
        List<GovCatalogCategory> all = list(catalogOrigin);
        List<Map<String, Object>> roots = new ArrayList<>();
        for (GovCatalogCategory c : all) {
            if (c.getParentId() == null || c.getParentId() == 0L) {
                roots.add(toNode(c, all));
            }
        }
        return roots;
    }

    public GovCatalogCategory get(Long id) {
        GovCatalogCategory c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BusinessException(404, "?????");
        }
        return c;
    }

    /** ???????????????? */
    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        validateCreateBody(body);
        String origin = normalizeOrigin(str(body.get("catalogOrigin"), "GOVERNANCE"));
        assertParentValid(longVal(body.get("parentId"), 0L), origin, null);
        assertNoPendingCatAction(null, origin, "CAT_CREATE");
        Map<String, Object> payload = new LinkedHashMap<>(body);
        payload.put("catalogOrigin", origin);
        payload.put("parentId", longVal(body.get("parentId"), 0L));
        Long approvalId = insertCategoryApproval(operator, null, origin, "CAT_CREATE",
                "????????", payload);
        log.info("catalog category create submitted approvalId={}", approvalId);
        return approvalId;
    }

    /** ???????? */
    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovCatalogCategory c = get(id);
        Long parentId = body.containsKey("parentId")
                ? longVal(body.get("parentId"), 0L)
                : (c.getParentId() == null ? 0L : c.getParentId());
        assertParentValid(parentId, c.getCatalogOrigin(), id);
        assertNoPendingCatAction(id, c.getCatalogOrigin(), "CAT_UPDATE");
        Map<String, Object> payload = new LinkedHashMap<>(body);
        payload.put("categoryId", id);
        payload.put("parentId", parentId);
        payload.put("categoryName", body.containsKey("categoryName")
                ? body.get("categoryName") : c.getCategoryName());
        payload.put("categoryCode", c.getCategoryCode());
        insertCategoryApproval(operator, id, c.getCatalogOrigin(), "CAT_UPDATE",
                "????????", payload);
    }

    /** ???????? */
    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        GovCatalogCategory c = get(id);
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<GovCatalogCategory>()
                .eq(GovCatalogCategory::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(400, "??????????");
        }
        assertNoPendingCatAction(id, c.getCatalogOrigin(), "CAT_DELETE");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("categoryId", id);
        payload.put("categoryName", c.getCategoryName());
        payload.put("categoryCode", c.getCategoryCode());
        insertCategoryApproval(operator, id, c.getCatalogOrigin(), "CAT_DELETE",
                "????????", payload);
    }

    /** ??????????? */
    @Transactional
    public Long applyCreate(UserPrincipal operator, Map<String, Object> body) {
        validateCreateBody(body);
        String name = required(body.get("categoryName"), "categoryName").toString();
        Long parentId = longVal(body.get("parentId"), 0L);
        String origin = normalizeOrigin(str(body.get("catalogOrigin"), "GOVERNANCE"));
        assertParentValid(parentId, origin, null);
        GovCatalogCategory c = new GovCatalogCategory();
        c.setCategoryCode(str(body.get("categoryCode"), "CAT_" + System.currentTimeMillis()));
        c.setCategoryName(name);
        c.setCatalogOrigin(origin);
        c.setParentId(parentId);
        c.setSortOrder(intVal(body.get("sortOrder"), 0));
        c.setStatus(str(body.get("status"), "ACTIVE"));
        c.setSecretFlag(intVal(body.get("secretFlag"), 0));
        c.setDescription(str(body.get("description"), null));
        c.setCategoryPath(buildPath(parentId, name));
        if (operator != null) {
            c.setCreatedBy(operator.getUsername());
        }
        c.setCreatedAt(LocalDateTime.now());
        categoryMapper.insert(c);
        log.info("catalog category applied id={} code={} parentId={}", c.getId(), c.getCategoryCode(), parentId);
        return c.getId();
    }

    /** ??????????? */
    @Transactional
    public void applyUpdate(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovCatalogCategory c = get(id);
        if (body.containsKey("categoryName")) {
            c.setCategoryName(required(body.get("categoryName"), "categoryName").toString());
        }
        if (body.containsKey("parentId")) {
            Long parentId = longVal(body.get("parentId"), 0L);
            assertParentValid(parentId, c.getCatalogOrigin(), id);
            c.setParentId(parentId);
        }
        if (body.containsKey("sortOrder")) {
            c.setSortOrder(intVal(body.get("sortOrder"), c.getSortOrder() == null ? 0 : c.getSortOrder()));
        }
        if (body.containsKey("status")) {
            c.setStatus(str(body.get("status"), c.getStatus()));
        }
        if (body.containsKey("secretFlag")) {
            c.setSecretFlag(intVal(body.get("secretFlag"), c.getSecretFlag() == null ? 0 : c.getSecretFlag()));
        }
        if (body.containsKey("description")) {
            c.setDescription(str(body.get("description"), null));
        }
        if (body.containsKey("catalogOrigin")) {
            String origin = str(body.get("catalogOrigin"), c.getCatalogOrigin());
            if (origin != null) {
                c.setCatalogOrigin(origin.toUpperCase(Locale.ROOT));
            }
        }
        c.setCategoryPath(buildPath(c.getParentId(), c.getCategoryName()));
        categoryMapper.updateById(c);
        // ??/??????????????
        refreshDescendantPaths(c.getId());
    }

    /** ???????????????? */
    @Transactional
    public void applyDelete(UserPrincipal operator, Long id) {
        get(id);
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<GovCatalogCategory>()
                .eq(GovCatalogCategory::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(400, "??????????");
        }
        resourceMapper.update(null, new LambdaUpdateWrapper<GovCatalogResource>()
                .eq(GovCatalogResource::getCategoryId, id)
                .set(GovCatalogResource::getCategoryId, null)
                .set(GovCatalogResource::getCategoryPath, null));
        categoryMapper.deleteById(id);
    }

    private void validateCreateBody(Map<String, Object> body) {
        required(body.get("categoryName"), "categoryName");
        required(body.get("categoryCode"), "categoryCode");
        if (body.get("secretFlag") == null) {
            throw new BusinessException(400, "???????");
        }
    }

    /**
     * ????????????????????????????
     * parentId=0 ???????
     */
    private void assertParentValid(Long parentId, String origin, Long selfId) {
        if (parentId == null || parentId <= 0L) {
            return;
        }
        if (selfId != null && Objects.equals(selfId, parentId)) {
            throw new BusinessException(400, "????????????");
        }
        GovCatalogCategory parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(400, "???????????????????????????");
        }
        String parentOrigin = parent.getCatalogOrigin() == null ? "GOVERNANCE" : parent.getCatalogOrigin();
        String expectOrigin = origin == null ? "GOVERNANCE" : origin.toUpperCase(Locale.ROOT);
        if (!parentOrigin.equalsIgnoreCase(expectOrigin)) {
            throw new BusinessException(400, "??????????????");
        }
        if (selfId != null && isDescendantOf(parentId, selfId)) {
            throw new BusinessException(400, "?????????????????");
        }
    }

    /** ?? candidateId ??? ancestorId ??? */
    private boolean isDescendantOf(Long candidateId, Long ancestorId) {
        Long cursor = candidateId;
        int guard = 0;
        while (cursor != null && cursor > 0L && guard++ < 64) {
            if (Objects.equals(cursor, ancestorId)) {
                return true;
            }
            GovCatalogCategory node = categoryMapper.selectById(cursor);
            if (node == null) {
                return false;
            }
            cursor = node.getParentId();
        }
        return false;
    }

    private void refreshDescendantPaths(Long parentId) {
        List<GovCatalogCategory> children = categoryMapper.selectList(new LambdaQueryWrapper<GovCatalogCategory>()
                .eq(GovCatalogCategory::getParentId, parentId));
        for (GovCatalogCategory child : children) {
            child.setCategoryPath(buildPath(parentId, child.getCategoryName()));
            categoryMapper.updateById(child);
            refreshDescendantPaths(child.getId());
        }
    }

    private void assertNoPendingCatAction(Long categoryId, String origin, String actionType) {
        LambdaQueryWrapper<GovCatalogApproval> q = new LambdaQueryWrapper<GovCatalogApproval>()
                .eq(GovCatalogApproval::getStatus, "PENDING")
                .eq(GovCatalogApproval::getActionType, actionType);
        if (categoryId != null) {
            q.eq(GovCatalogApproval::getCategoryId, categoryId);
        } else if (origin != null) {
            // ?????????????
            return;
        }
        Long n = approvalMapper.selectCount(q);
        if (n != null && n > 0) {
            throw new BusinessException(400, "??????????" + actionType + "???");
        }
    }

    private Long insertCategoryApproval(UserPrincipal operator, Long categoryId, String origin,
                                        String actionType, String comment, Map<String, Object> payload) {
        GovCatalogApproval a = new GovCatalogApproval();
        a.setResourceId(null);
        a.setCategoryId(categoryId);
        a.setCatalogOrigin(origin);
        a.setActionType(actionType);
        a.setStatus("PENDING");
        a.setSubmitComment(comment);
        if (operator != null) {
            a.setSubmittedBy(operator.getUsername());
        }
        a.setSubmittedAt(LocalDateTime.now());
        try {
            a.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new BusinessException(500, "?????????");
        }
        approvalMapper.insert(a);
        return a.getId();
    }

    private Map<String, Object> toNode(GovCatalogCategory c, List<GovCatalogCategory> all) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", c.getId());
        node.put("categoryCode", c.getCategoryCode());
        node.put("categoryName", c.getCategoryName());
        node.put("label", c.getCategoryName());
        node.put("parentId", c.getParentId());
        node.put("categoryPath", c.getCategoryPath());
        node.put("catalogOrigin", c.getCatalogOrigin());
        node.put("secretFlag", c.getSecretFlag());
        node.put("description", c.getDescription());
        node.put("sortOrder", c.getSortOrder());
        node.put("status", c.getStatus());
        List<Map<String, Object>> children = new ArrayList<>();
        for (GovCatalogCategory child : all) {
            if (Objects.equals(c.getId(), child.getParentId())) {
                children.add(toNode(child, all));
            }
        }
        node.put("children", children);
        return node;
    }

    private String buildPath(Long parentId, String name) {
        if (parentId == null || parentId == 0L) {
            return name;
        }
        GovCatalogCategory parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(400, "??????");
        }
        String base = parent.getCategoryPath();
        if (base == null || base.isBlank()) {
            base = parent.getCategoryName();
        }
        return base + "/" + name;
    }

    private static String normalizeOrigin(String origin) {
        String o = origin == null ? "GOVERNANCE" : origin.trim().toUpperCase(Locale.ROOT);
        if (!"INGEST".equals(o) && !"GOVERNANCE".equals(o)) {
            throw new BusinessException(400, "catalogOrigin ??? INGEST / GOVERNANCE");
        }
        return o;
    }

    private static Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " ????");
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
}
