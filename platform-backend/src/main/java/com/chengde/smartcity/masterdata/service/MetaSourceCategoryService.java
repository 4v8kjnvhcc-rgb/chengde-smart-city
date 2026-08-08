package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovMetaSourceCategory;
import com.chengde.smartcity.masterdata.mapper.GovMetaSourceCategoryMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetaSourceCategoryService {

    private static final Logger log = LoggerFactory.getLogger(MetaSourceCategoryService.class);

    private final GovMetaSourceCategoryMapper categoryMapper;
    private final AuditService auditService;

    public MetaSourceCategoryService(GovMetaSourceCategoryMapper categoryMapper, AuditService auditService) {
        this.categoryMapper = categoryMapper;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> listTree(String keyword) {
        List<GovMetaSourceCategory> all = listAllActive(keyword);
        List<GovMetaSourceCategory> roots = all.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0L)
                .sorted(Comparator.comparingInt(c -> c.getSortOrder() == null ? 0 : c.getSortOrder()))
                .collect(Collectors.toList());
        List<Map<String, Object>> tree = new ArrayList<>();
        for (GovMetaSourceCategory root : roots) {
            tree.add(toTreeNode(root, all));
        }
        return tree;
    }

    /** 数据源标签下拉：全部有效分类（含层级路径） */
    public List<Map<String, Object>> listTagOptions() {
        List<GovMetaSourceCategory> all = listAllActive(null);
        Map<Long, GovMetaSourceCategory> index = all.stream()
                .collect(Collectors.toMap(GovMetaSourceCategory::getId, c -> c, (a, b) -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovMetaSourceCategory c : all) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("categoryName", c.getCategoryName());
            m.put("categoryCode", c.getCategoryCode());
            m.put("label", buildCategoryPath(c, index));
            out.add(m);
        }
        return out;
    }

    public List<Long> collectCategoryScope(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return List.of();
        }
        List<GovMetaSourceCategory> all = listAllActive(null);
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        collectChildrenIds(categoryId, all, ids);
        return ids;
    }

    public List<GovMetaSourceCategory> listChildren(Long parentId, String keyword) {
        Long pid = parentId == null ? 0L : parentId;
        LambdaQueryWrapper<GovMetaSourceCategory> q = new LambdaQueryWrapper<GovMetaSourceCategory>()
                .eq(GovMetaSourceCategory::getParentId, pid)
                .eq(GovMetaSourceCategory::getStatus, "ACTIVE")
                .orderByAsc(GovMetaSourceCategory::getSortOrder)
                .orderByAsc(GovMetaSourceCategory::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.like(GovMetaSourceCategory::getCategoryName, keyword.trim());
        }
        return categoryMapper.selectList(q);
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        String name = required(body.get("categoryName"), "名称").trim();
        Long parentId = parseParentId(body.get("parentId"));
        validateParent(parentId);
        String code = str(body.get("categoryCode"), "").trim();
        if (code.isBlank()) {
            code = "CAT_" + parentId + "_" + System.currentTimeMillis();
        }
        ensureCodeUnique(code, null);
        GovMetaSourceCategory row = new GovMetaSourceCategory();
        row.setCategoryCode(code.toUpperCase(Locale.ROOT));
        row.setCategoryName(name);
        row.setParentId(parentId);
        row.setLayerCode(str(body.get("layerCode"), null));
        row.setDescription(str(body.get("description"), null));
        row.setSortOrder(parseInt(body.get("sortOrder"), nextSortOrder(parentId)));
        row.setSystemFlag(0);
        row.setStatus("ACTIVE");
        categoryMapper.insert(row);
        audit(operator, "META_SOURCE_CATEGORY_CREATE", row);
        log.info("Meta source category created id={} parentId={} name={}", row.getId(), parentId, name);
        return row.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovMetaSourceCategory row = require(id);
        if (body.containsKey("categoryName")) {
            row.setCategoryName(required(body.get("categoryName"), "名称").trim());
        }
        if (body.containsKey("description")) {
            row.setDescription(str(body.get("description"), null));
        }
        if (body.containsKey("sortOrder")) {
            row.setSortOrder(parseInt(body.get("sortOrder"), row.getSortOrder()));
        }
        if (body.containsKey("layerCode") && (row.getSystemFlag() == null || row.getSystemFlag() == 0)) {
            row.setLayerCode(str(body.get("layerCode"), null));
        }
        categoryMapper.updateById(row);
        audit(operator, "META_SOURCE_CATEGORY_UPDATE", row);
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        GovMetaSourceCategory row = require(id);
        if (row.getSystemFlag() != null && row.getSystemFlag() == 1) {
            throw new BusinessException(400, "系统内置分类不可删除");
        }
        long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<GovMetaSourceCategory>()
                .eq(GovMetaSourceCategory::getParentId, id)
                .eq(GovMetaSourceCategory::getStatus, "ACTIVE"));
        if (childCount > 0) {
            throw new BusinessException(400, "请先删除子分类");
        }
        row.setStatus("DELETED");
        categoryMapper.updateById(row);
        audit(operator, "META_SOURCE_CATEGORY_DELETE", row);
    }

    private List<GovMetaSourceCategory> listAllActive(String keyword) {
        LambdaQueryWrapper<GovMetaSourceCategory> q = new LambdaQueryWrapper<GovMetaSourceCategory>()
                .eq(GovMetaSourceCategory::getStatus, "ACTIVE")
                .orderByAsc(GovMetaSourceCategory::getSortOrder)
                .orderByAsc(GovMetaSourceCategory::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.like(GovMetaSourceCategory::getCategoryName, keyword.trim());
        }
        return categoryMapper.selectList(q);
    }

    private void collectChildrenIds(Long parentId, List<GovMetaSourceCategory> all, List<Long> ids) {
        for (GovMetaSourceCategory c : all) {
            if (parentId.equals(c.getParentId())) {
                ids.add(c.getId());
                collectChildrenIds(c.getId(), all, ids);
            }
        }
    }

    private String buildCategoryPath(GovMetaSourceCategory node, Map<Long, GovMetaSourceCategory> index) {
        List<String> parts = new ArrayList<>();
        GovMetaSourceCategory cur = node;
        int guard = 0;
        while (cur != null && guard++ < 20) {
            parts.add(0, cur.getCategoryName());
            Long pid = cur.getParentId();
            if (pid == null || pid == 0L) {
                break;
            }
            cur = index.get(pid);
        }
        return String.join(" / ", parts);
    }

    private Map<String, Object> toTreeNode(GovMetaSourceCategory node, List<GovMetaSourceCategory> all) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", node.getId());
        m.put("label", node.getCategoryName());
        m.put("categoryCode", node.getCategoryCode());
        m.put("layerCode", node.getLayerCode());
        m.put("systemFlag", node.getSystemFlag());
        List<Map<String, Object>> children = all.stream()
                .filter(c -> node.getId().equals(c.getParentId()))
                .sorted(Comparator.comparingInt(c -> c.getSortOrder() == null ? 0 : c.getSortOrder()))
                .map(c -> toTreeNode(c, all))
                .collect(Collectors.toList());
        if (!children.isEmpty()) {
            m.put("children", children);
        }
        return m;
    }

    public GovMetaSourceCategory requireCategory(Long id) {
        return require(id);
    }

    public boolean isSourceCategory(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return false;
        }
        GovMetaSourceCategory row = requireCategory(categoryId);
        if ("SOURCE".equalsIgnoreCase(str(row.getLayerCode(), ""))) {
            return true;
        }
        if ("CAT_SOURCE".equalsIgnoreCase(str(row.getCategoryCode(), ""))) {
            return true;
        }
        return "来源".equals(row.getCategoryName());
    }

    private GovMetaSourceCategory require(Long id) {
        GovMetaSourceCategory row = categoryMapper.selectById(id);
        if (row == null || "DELETED".equals(row.getStatus())) {
            throw new BusinessException(404, "分类不存在");
        }
        return row;
    }

    private void validateParent(Long parentId) {
        if (parentId == null || parentId == 0L) {
            throw new BusinessException(400, "请在左侧选择上级分类后再新增");
        }
        require(parentId);
    }

    private void ensureCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<GovMetaSourceCategory> q = new LambdaQueryWrapper<GovMetaSourceCategory>()
                .eq(GovMetaSourceCategory::getCategoryCode, code)
                .ne(GovMetaSourceCategory::getStatus, "DELETED");
        if (excludeId != null) {
            q.ne(GovMetaSourceCategory::getId, excludeId);
        }
        if (categoryMapper.selectCount(q) > 0) {
            throw new BusinessException(400, "分类编码已存在");
        }
    }

    private int nextSortOrder(Long parentId) {
        GovMetaSourceCategory last = categoryMapper.selectOne(new LambdaQueryWrapper<GovMetaSourceCategory>()
                .eq(GovMetaSourceCategory::getParentId, parentId)
                .eq(GovMetaSourceCategory::getStatus, "ACTIVE")
                .orderByDesc(GovMetaSourceCategory::getSortOrder)
                .last("LIMIT 1"));
        return last == null || last.getSortOrder() == null ? 1 : last.getSortOrder() + 1;
    }

    private Long parseParentId(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new BusinessException(400, "所属分类不能为空");
        }
        return Long.valueOf(String.valueOf(raw));
    }

    private void audit(UserPrincipal operator, String action, GovMetaSourceCategory row) {
        if (operator == null) {
            return;
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                action, "gov_meta_source_category", String.valueOf(row.getId()), row.getCategoryName());
    }

    private static String required(Object v, String label) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, label + "不能为空");
        }
        return String.valueOf(v);
    }

    private static String str(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static int parseInt(Object v, Integer def) {
        if (v == null || String.valueOf(v).isBlank()) {
            return def == null ? 0 : def;
        }
        return Integer.parseInt(String.valueOf(v));
    }
}
