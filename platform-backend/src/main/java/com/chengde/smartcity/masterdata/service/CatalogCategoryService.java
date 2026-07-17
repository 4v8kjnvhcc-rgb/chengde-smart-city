package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovCatalogCategory;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.mapper.GovCatalogCategoryMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogCategoryService {

    private static final Logger log = LoggerFactory.getLogger(CatalogCategoryService.class);

    private final GovCatalogCategoryMapper categoryMapper;
    private final GovCatalogResourceMapper resourceMapper;

    public CatalogCategoryService(GovCatalogCategoryMapper categoryMapper,
                                  GovCatalogResourceMapper resourceMapper) {
        this.categoryMapper = categoryMapper;
        this.resourceMapper = resourceMapper;
    }

    public List<GovCatalogCategory> list() {
        return categoryMapper.selectList(new LambdaQueryWrapper<GovCatalogCategory>()
                .orderByAsc(GovCatalogCategory::getSortOrder)
                .orderByAsc(GovCatalogCategory::getId));
    }

    public List<Map<String, Object>> tree() {
        List<GovCatalogCategory> all = list();
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
            throw new BusinessException(404, "分类不存在");
        }
        return c;
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        String name = required(body.get("categoryName"), "categoryName").toString();
        Long parentId = longVal(body.get("parentId"), 0L);
        GovCatalogCategory c = new GovCatalogCategory();
        c.setCategoryCode(str(body.get("categoryCode"), "CAT_" + System.currentTimeMillis()));
        c.setCategoryName(name);
        c.setParentId(parentId);
        c.setSortOrder(intVal(body.get("sortOrder"), 0));
        c.setStatus(str(body.get("status"), "ACTIVE"));
        c.setCategoryPath(buildPath(parentId, name));
        if (operator != null) {
            c.setCreatedBy(operator.getUsername());
        }
        c.setCreatedAt(LocalDateTime.now());
        categoryMapper.insert(c);
        log.info("catalog category created id={} code={}", c.getId(), c.getCategoryCode());
        return c.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovCatalogCategory c = get(id);
        if (body.containsKey("categoryName")) {
            c.setCategoryName(required(body.get("categoryName"), "categoryName").toString());
        }
        if (body.containsKey("parentId")) {
            c.setParentId(longVal(body.get("parentId"), 0L));
        }
        if (body.containsKey("sortOrder")) {
            c.setSortOrder(intVal(body.get("sortOrder"), c.getSortOrder() == null ? 0 : c.getSortOrder()));
        }
        if (body.containsKey("status")) {
            c.setStatus(str(body.get("status"), c.getStatus()));
        }
        c.setCategoryPath(buildPath(c.getParentId(), c.getCategoryName()));
        categoryMapper.updateById(c);
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        get(id);
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<GovCatalogCategory>()
                .eq(GovCatalogCategory::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(400, "存在子分类，无法删除");
        }
        Long resCount = resourceMapper.selectCount(new LambdaQueryWrapper<GovCatalogResource>()
                .eq(GovCatalogResource::getCategoryId, id));
        if (resCount != null && resCount > 0) {
            throw new BusinessException(400, "分类下仍有资源，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    private Map<String, Object> toNode(GovCatalogCategory c, List<GovCatalogCategory> all) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", c.getId());
        node.put("categoryCode", c.getCategoryCode());
        node.put("categoryName", c.getCategoryName());
        node.put("label", c.getCategoryName());
        node.put("parentId", c.getParentId());
        node.put("categoryPath", c.getCategoryPath());
        node.put("sortOrder", c.getSortOrder());
        node.put("status", c.getStatus());
        List<Map<String, Object>> children = new ArrayList<>();
        for (GovCatalogCategory child : all) {
            if (c.getId().equals(child.getParentId())) {
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
            throw new BusinessException(400, "父分类不存在");
        }
        String base = parent.getCategoryPath();
        if (base == null || base.isBlank()) {
            base = parent.getCategoryName();
        }
        return base + "/" + name;
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
}
