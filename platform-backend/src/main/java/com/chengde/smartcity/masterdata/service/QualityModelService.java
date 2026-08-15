package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovQualityModel;
import com.chengde.smartcity.masterdata.entity.GovQualityModelRule;
import com.chengde.smartcity.masterdata.entity.GovQualityModelTable;
import com.chengde.smartcity.masterdata.entity.GovQualityRule;
import com.chengde.smartcity.masterdata.mapper.GovQualityModelMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityModelRuleMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityModelTableMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityRuleMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityModelService {

    private static final Logger log = LoggerFactory.getLogger(QualityModelService.class);
    private static final Set<String> CHECK_TYPES = Set.of("NULL_CHECK", "UNIQUENESS", "ACCURACY", "RECORD_COUNT");

    private final GovQualityModelMapper modelMapper;
    private final GovQualityModelTableMapper tableMapper;
    private final GovQualityModelRuleMapper modelRuleMapper;
    private final GovQualityRuleMapper ruleCatalogMapper;

    public QualityModelService(GovQualityModelMapper modelMapper,
                               GovQualityModelTableMapper tableMapper,
                               GovQualityModelRuleMapper modelRuleMapper,
                               GovQualityRuleMapper ruleCatalogMapper) {
        this.modelMapper = modelMapper;
        this.tableMapper = tableMapper;
        this.modelRuleMapper = modelRuleMapper;
        this.ruleCatalogMapper = ruleCatalogMapper;
    }

    public List<Map<String, Object>> listModels() {
        List<GovQualityModel> models = modelMapper.selectList(new LambdaQueryWrapper<GovQualityModel>()
                .orderByDesc(GovQualityModel::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityModel m : models) {
            out.add(toModelRow(m, false));
        }
        return out;
    }

    public Map<String, Object> getModel(Long id) {
        return toModelRow(requireModel(id), true);
    }

    public List<Map<String, Object>> tree() {
        List<GovQualityModel> models = modelMapper.selectList(new LambdaQueryWrapper<GovQualityModel>()
                .orderByAsc(GovQualityModel::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityModel m : models) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "m-" + m.getId());
            node.put("modelId", m.getId());
            node.put("label", m.getModelName());
            node.put("type", "model");
            node.put("datasourceName", m.getDatasourceName());
            List<GovQualityModelTable> tables = listTables(m.getId());
            List<Map<String, Object>> children = new ArrayList<>();
            for (GovQualityModelTable t : tables) {
                Map<String, Object> child = new LinkedHashMap<>();
                child.put("id", "t-" + t.getId());
                child.put("modelId", m.getId());
                child.put("modelTableId", t.getId());
                child.put("tableName", t.getTableName());
                child.put("type", "table");
                String comment = t.getTableComment() == null || t.getTableComment().isBlank()
                        ? "" : " (" + t.getTableComment() + ")";
                child.put("label", t.getTableName() + comment);
                children.add(child);
            }
            node.put("children", children);
            out.add(node);
        }
        return out;
    }

    @Transactional
    public Long createModel(UserPrincipal operator, Map<String, Object> body) {
        String name = str(body.get("modelName"), null);
        if (name == null) {
            throw new BusinessException(400, "模型名称不能为空");
        }
        Long dsId = toLong(body.get("datasourceId"), null);
        if (dsId == null) {
            throw new BusinessException(400, "数据源不能为空");
        }
        GovQualityModel model = new GovQualityModel();
        model.setModelName(name);
        model.setDatasourceId(dsId);
        model.setDatasourceName(str(body.get("datasourceName"), String.valueOf(dsId)));
        model.setDescription(str(body.get("description"), null));
        model.setStatus(str(body.get("status"), "ENABLED"));
        if (operator != null) {
            model.setCreatedBy(operator.getUsername());
        }
        model.setCreatedAt(LocalDateTime.now());
        model.setUpdatedAt(LocalDateTime.now());
        modelMapper.insert(model);
        replaceTables(model.getId(), body.get("tables"));
        log.info("quality model created id={} name={}", model.getId(), name);
        return model.getId();
    }

    @Transactional
    public void updateModel(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovQualityModel model = requireModel(id);
        String name = str(body.get("modelName"), null);
        if (name == null) {
            throw new BusinessException(400, "模型名称不能为空");
        }
        model.setModelName(name);
        model.setDescription(str(body.get("description"), null));
        if (body.containsKey("status")) {
            model.setStatus(str(body.get("status"), model.getStatus()));
        }
        // 对照旧页：编辑时数据源不可改
        model.setUpdatedAt(LocalDateTime.now());
        modelMapper.updateById(model);
        if (body.containsKey("tables")) {
            syncTables(model.getId(), body.get("tables"));
        }
        log.info("quality model updated id={} by={}", id, operator != null ? operator.getUsername() : null);
    }

    @Transactional
    public void deleteModel(UserPrincipal operator, Long id) {
        requireModel(id);
        modelRuleMapper.delete(new LambdaQueryWrapper<GovQualityModelRule>()
                .eq(GovQualityModelRule::getModelId, id));
        tableMapper.delete(new LambdaQueryWrapper<GovQualityModelTable>()
                .eq(GovQualityModelTable::getModelId, id));
        modelMapper.deleteById(id);
        log.info("quality model deleted id={} by={}", id, operator != null ? operator.getUsername() : null);
    }

    public List<Map<String, Object>> listRules(Long modelId, Long modelTableId, String keyword) {
        requireModel(modelId);
        LambdaQueryWrapper<GovQualityModelRule> q = new LambdaQueryWrapper<GovQualityModelRule>()
                .eq(GovQualityModelRule::getModelId, modelId)
                .orderByDesc(GovQualityModelRule::getId);
        if (modelTableId != null) {
            q.eq(GovQualityModelRule::getModelTableId, modelTableId);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(GovQualityModelRule::getRuleName, keyword.trim())
                    .or().like(GovQualityModelRule::getRuleTypeName, keyword.trim())
                    .or().like(GovQualityModelRule::getRemark, keyword.trim()));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityModelRule r : modelRuleMapper.selectList(q)) {
            out.add(toRuleRow(r));
        }
        return out;
    }

    @Transactional
    public Long createRule(UserPrincipal operator, Long modelId, Map<String, Object> body) {
        GovQualityModel model = requireModel(modelId);
        Long tableId = toLong(body.get("modelTableId"), null);
        if (tableId == null) {
            throw new BusinessException(400, "请先选择模型下的表");
        }
        GovQualityModelTable table = requireTable(tableId);
        if (!modelId.equals(table.getModelId())) {
            throw new BusinessException(400, "表不属于该质量模型");
        }

        Long catalogId = toLong(body.get("ruleCatalogId"), null);
        String typeCode;
        String typeName;
        String checkType;
        if (catalogId != null) {
            GovQualityRule catalog = ruleCatalogMapper.selectById(catalogId);
            if (catalog == null) {
                throw new BusinessException(404, "校验规则类型不存在");
            }
            typeCode = catalog.getRuleCode();
            typeName = catalog.getRuleName();
            checkType = resolveCheckType(catalog.getRuleType(), catalog.getRuleCode(), body.get("checkType"));
        } else {
            typeCode = str(body.get("ruleTypeCode"), null);
            typeName = str(body.get("ruleTypeName"), null);
            if (typeName == null) {
                throw new BusinessException(400, "规则类型不能为空");
            }
            checkType = resolveCheckType(null, typeCode, body.get("checkType"));
        }

        String fields = normalizeFields(body.get("fieldNames"));
        if (needsField(checkType, typeCode) && (fields == null || fields.isBlank())) {
            throw new BusinessException(400, "请选择字段");
        }

        String ruleName = str(body.get("ruleName"), null);
        if (ruleName == null) {
            String ds = model.getDatasourceName() == null ? "DS" : model.getDatasourceName();
            ruleName = ds + "_" + table.getTableName() + "_" + typeName;
            if (ruleName.length() > 250) {
                ruleName = ruleName.substring(0, 250);
            }
        }

        GovQualityModelRule rule = new GovQualityModelRule();
        rule.setModelId(modelId);
        rule.setModelTableId(tableId);
        rule.setRuleCatalogId(catalogId);
        rule.setRuleTypeCode(typeCode);
        rule.setRuleTypeName(typeName);
        rule.setRuleName(ruleName);
        rule.setTableName(table.getTableName());
        rule.setFieldNames(fields);
        rule.setRemark(str(body.get("remark"), typeName));
        rule.setCheckType(checkType);
        if (body.containsKey("configJson")) {
            Object cj = body.get("configJson");
            rule.setConfigJson(cj == null ? null : String.valueOf(cj));
        }
        if (body.containsKey("threshold")) {
            rule.setThreshold(toDecimal(body.get("threshold")));
        }
        rule.setStatus(str(body.get("status"), "ENABLED"));
        if (operator != null) {
            rule.setCreatedBy(operator.getUsername());
        }
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        modelRuleMapper.insert(rule);
        log.info("quality model rule created id={} modelId={}", rule.getId(), modelId);
        return rule.getId();
    }

    @Transactional
    public void updateRule(UserPrincipal operator, Long modelId, Long ruleId, Map<String, Object> body) {
        requireModel(modelId);
        GovQualityModelRule rule = requireModelRule(ruleId);
        if (!modelId.equals(rule.getModelId())) {
            throw new BusinessException(404, "规则不属于该模型");
        }
        if (body.containsKey("ruleName")) {
            String n = str(body.get("ruleName"), null);
            if (n == null) {
                throw new BusinessException(400, "规则名称不能为空");
            }
            rule.setRuleName(n);
        }
        if (body.containsKey("fieldNames")) {
            String fields = normalizeFields(body.get("fieldNames"));
            if (needsField(rule.getCheckType(), rule.getRuleTypeCode()) && (fields == null || fields.isBlank())) {
                throw new BusinessException(400, "请选择字段");
            }
            rule.setFieldNames(fields);
        }
        if (body.containsKey("remark")) {
            rule.setRemark(str(body.get("remark"), null));
        }
        if (body.containsKey("checkType")) {
            String ct = str(body.get("checkType"), rule.getCheckType());
            if (ct != null && !CHECK_TYPES.contains(ct)) {
                throw new BusinessException(400, "checkType 无效");
            }
            rule.setCheckType(ct);
        }
        if (body.containsKey("configJson")) {
            Object cj = body.get("configJson");
            rule.setConfigJson(cj == null ? null : String.valueOf(cj));
        }
        if (body.containsKey("threshold")) {
            rule.setThreshold(toDecimal(body.get("threshold")));
        }
        if (body.containsKey("status")) {
            rule.setStatus(str(body.get("status"), rule.getStatus()));
        }
        rule.setUpdatedAt(LocalDateTime.now());
        modelRuleMapper.updateById(rule);
        log.info("quality model rule updated id={} by={}", ruleId, operator != null ? operator.getUsername() : null);
    }

    @Transactional
    public void deleteRule(UserPrincipal operator, Long modelId, Long ruleId) {
        requireModel(modelId);
        GovQualityModelRule rule = requireModelRule(ruleId);
        if (!modelId.equals(rule.getModelId())) {
            throw new BusinessException(404, "规则不属于该模型");
        }
        modelRuleMapper.deleteById(ruleId);
        log.info("quality model rule deleted id={} by={}", ruleId, operator != null ? operator.getUsername() : null);
    }

    /**
     * 清除指定表下、fieldNames 中包含该字段的全部模型规则。
     */
    @Transactional
    public Map<String, Object> clearRulesByField(UserPrincipal operator, Long modelId, Long modelTableId, String fieldName) {
        requireModel(modelId);
        if (modelTableId == null) {
            throw new BusinessException(400, "请指定模型表");
        }
        if (fieldName == null || fieldName.isBlank()) {
            throw new BusinessException(400, "请指定字段名");
        }
        String field = fieldName.trim();
        List<GovQualityModelRule> rules = modelRuleMapper.selectList(new LambdaQueryWrapper<GovQualityModelRule>()
                .eq(GovQualityModelRule::getModelId, modelId)
                .eq(GovQualityModelRule::getModelTableId, modelTableId));
        int deleted = 0;
        for (GovQualityModelRule r : rules) {
            String fn = r.getFieldNames();
            if (fn == null || fn.isBlank()) {
                continue;
            }
            boolean hit = false;
            for (String part : fn.split("[,，;；\\s]+")) {
                if (field.equalsIgnoreCase(part.trim())) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                modelRuleMapper.deleteById(r.getId());
                deleted++;
            }
        }
        log.info("quality clearRulesByField modelId={} tableId={} field={} deleted={} by={}",
                modelId, modelTableId, field, deleted, operator != null ? operator.getUsername() : null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deleted", deleted);
        out.put("fieldName", field);
        out.put("message", "已清除字段「" + field + "」下 " + deleted + " 条规则");
        return out;
    }

    private void replaceTables(Long modelId, Object tablesRaw) {
        if (!(tablesRaw instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(400, "请至少添加一张表");
        }
        int i = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            String tn = str(m.get("tableName"), null);
            if (tn == null) {
                continue;
            }
            GovQualityModelTable t = new GovQualityModelTable();
            t.setModelId(modelId);
            t.setTableName(tn);
            t.setTableComment(str(m.get("tableComment"), null));
            t.setSortNo(i++);
            t.setCreatedAt(LocalDateTime.now());
            tableMapper.insert(t);
        }
        if (i == 0) {
            throw new BusinessException(400, "请至少添加一张表");
        }
    }

    /** 同步模型表：保留仍存在的表及其规则，删除移除的表及其规则，新增新表 */
    private void syncTables(Long modelId, Object tablesRaw) {
        if (!(tablesRaw instanceof List<?> list)) {
            throw new BusinessException(400, "tables 格式无效");
        }
        List<GovQualityModelTable> existing = listTables(modelId);
        Map<String, GovQualityModelTable> byName = existing.stream()
                .collect(Collectors.toMap(GovQualityModelTable::getTableName, t -> t, (a, b) -> a, LinkedHashMap::new));

        Set<String> keep = list.stream()
                .filter(o -> o instanceof Map<?, ?>)
                .map(o -> str(((Map<?, ?>) o).get("tableName"), null))
                .filter(n -> n != null)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (keep.isEmpty()) {
            throw new BusinessException(400, "请至少保留一张表");
        }

        for (GovQualityModelTable old : existing) {
            if (!keep.contains(old.getTableName())) {
                modelRuleMapper.delete(new LambdaQueryWrapper<GovQualityModelRule>()
                        .eq(GovQualityModelRule::getModelTableId, old.getId()));
                tableMapper.deleteById(old.getId());
            }
        }

        int sort = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            String tn = str(m.get("tableName"), null);
            if (tn == null) {
                continue;
            }
            String comment = str(m.get("tableComment"), null);
            GovQualityModelTable cur = byName.get(tn);
            if (cur == null) {
                GovQualityModelTable t = new GovQualityModelTable();
                t.setModelId(modelId);
                t.setTableName(tn);
                t.setTableComment(comment);
                t.setSortNo(sort++);
                t.setCreatedAt(LocalDateTime.now());
                tableMapper.insert(t);
            } else {
                cur.setTableComment(comment);
                cur.setSortNo(sort++);
                tableMapper.updateById(cur);
            }
        }
    }

    private List<GovQualityModelTable> listTables(Long modelId) {
        return tableMapper.selectList(new LambdaQueryWrapper<GovQualityModelTable>()
                .eq(GovQualityModelTable::getModelId, modelId)
                .orderByAsc(GovQualityModelTable::getSortNo)
                .orderByAsc(GovQualityModelTable::getId));
    }

    private Map<String, Object> toModelRow(GovQualityModel m, boolean withTables) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId());
        row.put("modelName", m.getModelName());
        row.put("datasourceId", m.getDatasourceId());
        row.put("datasourceName", m.getDatasourceName());
        row.put("description", m.getDescription());
        row.put("status", m.getStatus());
        row.put("createdBy", m.getCreatedBy());
        row.put("createdAt", m.getCreatedAt());
        if (withTables) {
            List<Map<String, Object>> tables = new ArrayList<>();
            for (GovQualityModelTable t : listTables(m.getId())) {
                Map<String, Object> tr = new LinkedHashMap<>();
                tr.put("id", t.getId());
                tr.put("tableName", t.getTableName());
                tr.put("tableComment", t.getTableComment());
                tr.put("sortNo", t.getSortNo());
                tables.add(tr);
            }
            row.put("tables", tables);
        }
        return row;
    }

    private Map<String, Object> toRuleRow(GovQualityModelRule r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getId());
        row.put("modelId", r.getModelId());
        row.put("modelTableId", r.getModelTableId());
        row.put("ruleCatalogId", r.getRuleCatalogId());
        row.put("ruleTypeCode", r.getRuleTypeCode());
        row.put("ruleTypeName", r.getRuleTypeName());
        row.put("ruleName", r.getRuleName());
        row.put("tableName", r.getTableName());
        row.put("fieldNames", r.getFieldNames());
        row.put("remark", r.getRemark());
        row.put("checkType", r.getCheckType());
        row.put("configJson", r.getConfigJson());
        row.put("threshold", r.getThreshold());
        row.put("status", r.getStatus());
        row.put("createdAt", r.getCreatedAt());
        return row;
    }

    private GovQualityModel requireModel(Long id) {
        GovQualityModel m = modelMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(404, "质量模型不存在: " + id);
        }
        return m;
    }

    private GovQualityModelTable requireTable(Long id) {
        GovQualityModelTable t = tableMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "模型表不存在: " + id);
        }
        return t;
    }

    private GovQualityModelRule requireModelRule(Long id) {
        GovQualityModelRule r = modelRuleMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(404, "模型规则不存在: " + id);
        }
        return r;
    }

    private static String resolveCheckType(String ruleType, String ruleCode, Object override) {
        String fromBody = str(override, null);
        if (fromBody != null && CHECK_TYPES.contains(fromBody)) {
            return fromBody;
        }
        String code = (ruleCode == null ? "" : ruleCode).toLowerCase(Locale.ROOT);
        String type = (ruleType == null ? "" : ruleType).toUpperCase(Locale.ROOT);
        if (code.contains("null") || code.contains("completeness") || "COMPLETENESS".equals(type) || "INTEGRITY".equals(type)) {
            return "NULL_CHECK";
        }
        if (code.contains("unique") || "UNIQUENESS".equals(type)) {
            return "UNIQUENESS";
        }
        if (code.contains("record") || code.contains("count") || "TIMELINESS".equals(type) || "RECORD_COUNT".equals(type)) {
            return "RECORD_COUNT";
        }
        return "ACCURACY";
    }

    private static boolean needsField(String checkType, String ruleCode) {
        if ("RECORD_COUNT".equalsIgnoreCase(checkType)) {
            return false;
        }
        String code = ruleCode == null ? "" : ruleCode.toLowerCase(Locale.ROOT);
        if (code.contains("java") || code.contains("script") || code.contains("fluctuat") || code.contains("record")) {
            return false;
        }
        return true;
    }

    private static String normalizeFields(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof List<?> list) {
            return list.stream()
                    .map(x -> x == null ? "" : String.valueOf(x).trim())
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(","));
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String str(Object v, String defaultVal) {
        if (v == null) {
            return defaultVal;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? defaultVal : s;
    }

    private static Long toLong(Object v, Long defaultVal) {
        if (v == null || String.valueOf(v).isBlank()) {
            return defaultVal;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new BusinessException(400, "数值格式无效");
        }
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            throw new BusinessException(400, "threshold 格式无效");
        }
    }
}
