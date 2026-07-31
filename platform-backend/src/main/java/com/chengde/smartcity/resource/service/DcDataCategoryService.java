package com.chengde.smartcity.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.resource.entity.DcDataCategory;
import com.chengde.smartcity.resource.mapper.DcDataCategoryMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DcDataCategoryService {

    private static final Logger log = LoggerFactory.getLogger(DcDataCategoryService.class);

    private final DcDataCategoryMapper mapper;

    public DcDataCategoryService(DcDataCategoryMapper mapper) {
        this.mapper = mapper;
    }

    public Page<DcDataCategory> page(String keyword, int page, int size) {
        LambdaQueryWrapper<DcDataCategory> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.like(DcDataCategory::getCategoryName, keyword.trim());
        }
        q.orderByAsc(DcDataCategory::getSortNo).orderByAsc(DcDataCategory::getCategoryCode);
        return mapper.selectPage(new Page<>(page, size), q);
    }

    @Transactional
    public String save(Map<String, Object> body) {
        if (body == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String uuid = asStr(body.get("uuid"));
        String code = required(body.get("categoryCode"), "分类编码");
        String name = required(body.get("categoryName"), "分类名称");
        String type = required(body.get("categoryType"), "分类类型");
        String level = asStr(body.get("configLevel"));
        if (level == null || level.isBlank()) {
            level = "BASIC";
        }
        String configJson = asStr(body.get("configJson"));
        if (configJson == null || configJson.isBlank()) {
            throw new BusinessException(400, "配置明细不能为空");
        }

        DcDataCategory row;
        boolean insert = uuid == null || uuid.isBlank();
        if (insert) {
            row = new DcDataCategory();
            row.setUuid(UUID.randomUUID().toString().replace("-", ""));
            row.setCreateTime(LocalDateTime.now());
        } else {
            row = mapper.selectById(uuid);
            if (row == null) {
                throw new BusinessException(404, "分类不存在");
            }
        }

        // 编码唯一（排除自身）
        LambdaQueryWrapper<DcDataCategory> codeQ = new LambdaQueryWrapper<DcDataCategory>()
                .eq(DcDataCategory::getCategoryCode, code);
        if (!insert) {
            codeQ.ne(DcDataCategory::getUuid, row.getUuid());
        }
        DcDataCategory sameCode = mapper.selectOne(codeQ.last("LIMIT 1"));
        if (sameCode != null) {
            throw new BusinessException(400, "分类编码已存在");
        }

        row.setCategoryCode(code);
        row.setCategoryName(name);
        row.setCategoryType(type.toUpperCase());
        row.setConfigLevel(level.toUpperCase());
        row.setConfigJson(configJson);
        row.setDescription(asStr(body.get("description")));
        row.setSortNo(asInt(body.get("sortNo"), 0));
        Object st = body.get("status");
        if (st instanceof Boolean b) {
            row.setStatus(b ? 1 : 0);
        } else {
            row.setStatus(asInt(st, 1));
        }

        if (insert) {
            mapper.insert(row);
            log.info("新增数据分类 uuid={} code={}", row.getUuid(), code);
        } else {
            mapper.updateById(row);
            log.info("更新数据分类 uuid={} code={}", row.getUuid(), code);
        }
        return row.getUuid();
    }

    @Transactional
    public void delete(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new BusinessException(400, "uuid 必填");
        }
        DcDataCategory row = mapper.selectById(uuid);
        if (row == null) {
            throw new BusinessException(404, "分类不存在");
        }
        mapper.deleteById(uuid);
    }

    public Map<String, Object> toPageResult(Page<DcDataCategory> page) {
        Map<String, Object> m = new HashMap<>();
        m.put("records", page.getRecords());
        m.put("total", page.getTotal());
        return m;
    }

    private static String required(Object v, String label) {
        String s = asStr(v);
        if (s == null || s.isBlank()) {
            throw new BusinessException(400, label + "不能为空");
        }
        return s.trim();
    }

    private static String asStr(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static int asInt(Object v, int def) {
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
