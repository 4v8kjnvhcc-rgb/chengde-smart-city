package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovQualityModelRule;
import com.chengde.smartcity.masterdata.entity.GovQualityRule;
import com.chengde.smartcity.masterdata.entity.GovQualityRuleConfig;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskDetail;
import com.chengde.smartcity.masterdata.mapper.GovQualityModelRuleMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityRuleConfigMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityRuleMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskDetailMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityRuleService {

    private static final Logger log = LoggerFactory.getLogger(QualityRuleService.class);
    private static final Set<String> CHECK_TYPES = Set.of("NULL_CHECK", "UNIQUENESS", "ACCURACY", "RECORD_COUNT");

    /** 与旧系统/目标截图对齐的 11 类校验规则类型（排序 1～11） */
    private static final List<StandardRule> STANDARD_RULES = List.of(
            new StandardRule("NullValueCheck", "空值检查", "COMPLETENESS", "用于检查字段是否为空"),
            new StandardRule("RangeCheck", "值域检查", "ACCURACY", "用于检查关键指标取值范围"),
            new StandardRule("StandardInspection", "规范检查", "ACCURACY", "用于检查字符型字段的格式是否规范"),
            new StandardRule("JavaScript", "Java脚本", "ACCURACY", "用于执行Java脚本检查数据"),
            new StandardRule("RecordCount", "记录数", "COMPLETENESS", "核查数据总量，校验条数完整性与缺失、冗余情况"),
            new StandardRule("Uniqueness", "唯一性", "UNIQUENESS", "校验关键字段，排查重复数据与重复录入问题"),
            new StandardRule("Accuracy", "准确性", "ACCURACY", "核对数据内容，确保数值、文本符合真实业务"),
            new StandardRule("DataFluctuation", "波动", "TIMELINESS", "监控数据变化，识别异常增减、突发等不合理情况"),
            new StandardRule("Consistency", "一致性", "CONSISTENCY", "比对关联数据，保障多表多源口径、格式统一"),
            new StandardRule("LogicCheck", "逻辑性", "ACCURACY", "校验业务规则，判断数据间关联关系是否合理"),
            new StandardRule("CustomRule", "自定义", "ACCURACY", "适配业务场景，按需配置专项精度校验规则")
    );

    private final GovQualityRuleMapper ruleMapper;
    private final GovQualityRuleConfigMapper configMapper;
    private final GovQualityModelRuleMapper modelRuleMapper;
    private final GovQualityTaskDetailMapper taskDetailMapper;

    public QualityRuleService(GovQualityRuleMapper ruleMapper,
                              GovQualityRuleConfigMapper configMapper,
                              GovQualityModelRuleMapper modelRuleMapper,
                              GovQualityTaskDetailMapper taskDetailMapper) {
        this.ruleMapper = ruleMapper;
        this.configMapper = configMapper;
        this.modelRuleMapper = modelRuleMapper;
        this.taskDetailMapper = taskDetailMapper;
    }

    @Transactional
    public List<Map<String, Object>> listWithConfig() {
        if (catalogNeedsRepair()) {
            try {
                repairCatalogQuietly();
            } catch (Exception e) {
                log.warn("auto repair quality rule catalog skipped: {}", e.getMessage());
            }
        }
        List<GovQualityRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<GovQualityRule>()
                .orderByAsc(GovQualityRule::getSortNo)
                .orderByAsc(GovQualityRule::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityRule rule : rules) {
            out.add(toRow(rule));
        }
        return out;
    }

    public Map<String, Object> get(Long id) {
        return toRow(requireRule(id));
    }

    /**
     * 仅在标准项排序异常或有临时垃圾数据时静默修复。
     * 用户自建规则（非标准 code）保留不动，不视为需要修复。
     */
    private boolean catalogNeedsRepair() {
        Set<String> standardCodes = standardCodeSet();
        List<GovQualityRule> all = ruleMapper.selectList(null);
        for (GovQualityRule rule : all) {
            if (isTempRule(rule)) {
                return true;
            }
            if (standardCodes.contains(rule.getRuleCode())
                    && (rule.getSortNo() == null || rule.getSortNo() <= 0)) {
                return true;
            }
        }
        return false;
    }

    /** 仅清理临时垃圾规则，并修正标准项的排序与文案；用户自建规则保留 */
    private void repairCatalogQuietly() {
        purgeTempRulesOnly();
        Set<String> standardCodes = standardCodeSet();
        List<GovQualityRule> remain = ruleMapper.selectList(new LambdaQueryWrapper<GovQualityRule>()
                .in(GovQualityRule::getRuleCode, standardCodes)
                .orderByAsc(GovQualityRule::getSortNo)
                .orderByAsc(GovQualityRule::getId));
        // 按标准清单顺序重排已存在项
        int sort = 1;
        for (StandardRule std : STANDARD_RULES) {
            GovQualityRule existing = null;
            for (GovQualityRule r : remain) {
                if (std.code().equals(r.getRuleCode())) {
                    existing = r;
                    break;
                }
            }
            if (existing == null) {
                continue;
            }
            existing.setRuleName(std.name());
            existing.setRuleType(std.ruleType());
            existing.setDescription(std.description());
            existing.setSortNo(sort++);
            existing.setUpdatedAt(LocalDateTime.now());
            ruleMapper.updateById(existing);
        }
    }

    private static Set<String> standardCodeSet() {
        Set<String> standardCodes = new LinkedHashSet<>();
        for (StandardRule std : STANDARD_RULES) {
            standardCodes.add(std.code());
        }
        return standardCodes;
    }

    /**
     * 对齐标准规则目录：重置为截图中的 11 类（会补回已删标准项），清理旧 QR_* / 临时规则，sort_no=1..11。
     * 仅「对齐标准目录」按钮调用，列表加载不会调用。
     */
    @Transactional
    public Map<String, Object> alignStandardCatalog(UserPrincipal operator) {
        int purged = purgeNonStandardRules();
        int upserted = 0;
        int sort = 1;
        for (StandardRule std : STANDARD_RULES) {
            GovQualityRule existing = ruleMapper.selectOne(new LambdaQueryWrapper<GovQualityRule>()
                    .eq(GovQualityRule::getRuleCode, std.code())
                    .last("LIMIT 1"));
            if (existing == null) {
                GovQualityRule rule = new GovQualityRule();
                rule.setRuleCode(std.code());
                rule.setRuleName(std.name());
                rule.setRuleType(std.ruleType());
                rule.setSortNo(sort);
                rule.setDescription(std.description());
                rule.setStatus("ENABLED");
                if (operator != null) {
                    rule.setOrgId(operator.getOrgId());
                    rule.setCreatedBy(operator.getUsername());
                } else {
                    rule.setCreatedBy("system");
                }
                rule.setCreatedAt(LocalDateTime.now());
                rule.setUpdatedAt(LocalDateTime.now());
                ruleMapper.insert(rule);
                upserted++;
            } else {
                existing.setRuleName(std.name());
                existing.setRuleType(std.ruleType());
                existing.setDescription(std.description());
                existing.setSortNo(sort);
                existing.setStatus(existing.getStatus() != null ? existing.getStatus() : "ENABLED");
                existing.setUpdatedAt(LocalDateTime.now());
                ruleMapper.updateById(existing);
                upserted++;
            }
            sort++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("purgedTemp", purged);
        result.put("purgedNonStandard", purged);
        result.put("alignedStandard", upserted);
        result.put("total", ruleMapper.selectCount(null));
        log.info("quality rule catalog aligned purged={} standard={} by={}",
                purged, upserted, operator != null ? operator.getUsername() : "system");
        return result;
    }

    /** 删除非标准目录项（含临时压测、旧 QR_COMPLETE_* 等），只留 11 类标准编码。仅 alignStandardCatalog 调用 */
    private int purgeNonStandardRules() {
        Set<String> standardCodes = standardCodeSet();
        List<GovQualityRule> all = ruleMapper.selectList(null);
        int purged = 0;
        for (GovQualityRule rule : all) {
            String code = rule.getRuleCode();
            if (code != null && standardCodes.contains(code) && !isTempRule(rule)) {
                continue;
            }
            Long id = rule.getId();
            clearRuleReferences(id);
            configMapper.delete(new LambdaQueryWrapper<GovQualityRuleConfig>()
                    .eq(GovQualityRuleConfig::getRuleId, id));
            ruleMapper.deleteById(id);
            purged++;
        }
        return purged;
    }

    /** 仅删除临时/垃圾规则，保留标准项和用户自建项 */
    private int purgeTempRulesOnly() {
        List<GovQualityRule> all = ruleMapper.selectList(null);
        int purged = 0;
        for (GovQualityRule rule : all) {
            if (isTempRule(rule)) {
                Long id = rule.getId();
                clearRuleReferences(id);
                configMapper.delete(new LambdaQueryWrapper<GovQualityRuleConfig>()
                        .eq(GovQualityRuleConfig::getRuleId, id));
                ruleMapper.deleteById(id);
                purged++;
            }
        }
        return purged;
    }

    /** 临时/压测类规则：空值率/阈值*_*、带长数字后缀等 */
    private static boolean isTempRule(GovQualityRule rule) {
        String name = rule.getRuleName() != null ? rule.getRuleName() : "";
        String code = rule.getRuleCode() != null ? rule.getRuleCode() : "";
        if (name.startsWith("空值率规则_") || name.startsWith("临时规则")) {
            return true;
        }
        if (name.startsWith("阈值") && name.contains("_")) {
            return true;
        }
        if (name.matches(".*_\\d{8,}$")) {
            return true;
        }
        if (code.startsWith("QR_") && (name.contains("阈值") || name.contains("空值率") || name.contains("临时"))) {
            return true;
        }
        return false;
    }

    private Map<String, Object> toRow(GovQualityRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rule.getId());
        row.put("ruleCode", rule.getRuleCode());
        row.put("ruleName", rule.getRuleName());
        row.put("ruleType", rule.getRuleType());
        row.put("sortNo", rule.getSortNo() != null ? rule.getSortNo() : 0);
        row.put("description", rule.getDescription());
        row.put("orgId", rule.getOrgId());
        row.put("status", rule.getStatus());
        row.put("createdBy", rule.getCreatedBy());
        row.put("createdAt", rule.getCreatedAt());
        row.put("config", findConfig(rule.getId()));
        return row;
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        String name = str(body.get("ruleName"), null);
        if (name == null) {
            throw new BusinessException(400, "规则名称不能为空");
        }
        String code = str(body.get("ruleCode"), "QR_" + UUID.randomUUID().toString().substring(0, 8));
        ensureCodeUnique(code, null);
        GovQualityRule rule = new GovQualityRule();
        rule.setRuleCode(code);
        rule.setRuleName(name);
        rule.setRuleType(str(body.get("ruleType"), "COMPLETENESS"));
        rule.setSortNo(toInt(body.get("sortNo"), nextSortNo()));
        rule.setDescription(str(body.get("description"), null));
        if (operator != null) {
            rule.setOrgId(operator.getOrgId());
            rule.setCreatedBy(operator.getUsername());
        }
        rule.setStatus(str(body.get("status"), "ENABLED"));
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.insert(rule);
        log.info("quality rule created id={} code={}", rule.getId(), rule.getRuleCode());
        return rule.getId();
    }

    /** 对照旧页：支持修改排序 / 名称 / 描述（编码创建后不可改） */
    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovQualityRule rule = requireRule(id);
        String name = str(body.get("ruleName"), null);
        if (name == null) {
            throw new BusinessException(400, "规则名称不能为空");
        }
        rule.setRuleName(name);
        if (body.containsKey("ruleType")) {
            rule.setRuleType(str(body.get("ruleType"), rule.getRuleType()));
        }
        if (body.containsKey("sortNo")) {
            rule.setSortNo(toInt(body.get("sortNo"), rule.getSortNo() != null ? rule.getSortNo() : 0));
        }
        if (body.containsKey("description")) {
            rule.setDescription(str(body.get("description"), null));
        }
        if (body.containsKey("status")) {
            rule.setStatus(str(body.get("status"), rule.getStatus()));
        }
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.updateById(rule);
        log.info("quality rule updated id={} by={}", id, operator != null ? operator.getUsername() : null);
    }

    private int nextSortNo() {
        List<GovQualityRule> all = ruleMapper.selectList(new LambdaQueryWrapper<GovQualityRule>()
                .orderByDesc(GovQualityRule::getSortNo)
                .last("LIMIT 1"));
        if (all.isEmpty() || all.get(0).getSortNo() == null) {
            return 1;
        }
        return all.get(0).getSortNo() + 1;
    }

    private void ensureCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<GovQualityRule> q = new LambdaQueryWrapper<GovQualityRule>()
                .eq(GovQualityRule::getRuleCode, code);
        if (excludeId != null) {
            q.ne(GovQualityRule::getId, excludeId);
        }
        Long cnt = ruleMapper.selectCount(q);
        if (cnt != null && cnt > 0) {
            throw new BusinessException(400, "规则编码已存在: " + code);
        }
    }

    public GovQualityRuleConfig getConfig(Long ruleId) {
        requireRule(ruleId);
        GovQualityRuleConfig cfg = findConfig(ruleId);
        if (cfg == null) {
            throw new BusinessException(404, "规则尚未配置");
        }
        return cfg;
    }

    @Transactional
    public GovQualityRuleConfig saveConfig(UserPrincipal operator, Long ruleId, Map<String, Object> body) {
        requireRule(ruleId);
        String checkType = str(body.get("checkType"), null);
        if (checkType == null || !CHECK_TYPES.contains(checkType)) {
            throw new BusinessException(400, "checkType 须为 NULL_CHECK/UNIQUENESS/ACCURACY/RECORD_COUNT");
        }
        GovQualityRuleConfig cfg = findConfig(ruleId);
        boolean creating = cfg == null;
        if (creating) {
            cfg = new GovQualityRuleConfig();
            cfg.setRuleId(ruleId);
            cfg.setCreatedAt(LocalDateTime.now());
            if (operator != null) {
                cfg.setCreatedBy(operator.getUsername());
            }
        }
        cfg.setMetadataEntryCode(str(body.get("metadataEntryCode"), cfg.getMetadataEntryCode()));
        cfg.setCheckType(checkType);
        cfg.setTargetTable(str(body.get("targetTable"), null));
        cfg.setTargetColumn(str(body.get("targetColumn"), null));
        if (cfg.getTargetTable() == null || cfg.getTargetTable().isBlank()) {
            throw new BusinessException(400, "目标表不能为空，请从平台分层库或登记源选择表");
        }
        if (!"RECORD_COUNT".equalsIgnoreCase(checkType)
                && (cfg.getTargetColumn() == null || cfg.getTargetColumn().isBlank())) {
            throw new BusinessException(400, "目标字段不能为空");
        }
        if (body.containsKey("configJson")) {
            Object cj = body.get("configJson");
            cfg.setConfigJson(cj == null ? null : String.valueOf(cj));
        }
        if (body.containsKey("threshold")) {
            cfg.setThreshold(toDecimal(body.get("threshold")));
        }
        cfg.setStatus(str(body.get("status"), cfg.getStatus() != null ? cfg.getStatus() : "ENABLED"));
        cfg.setUpdatedAt(LocalDateTime.now());
        if (creating) {
            configMapper.insert(cfg);
        } else {
            configMapper.updateById(cfg);
        }
        log.info("quality rule {} config saved checkType={}", ruleId, checkType);
        return cfg;
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        requireRule(id);
        clearRuleReferences(id);
        configMapper.delete(new LambdaQueryWrapper<GovQualityRuleConfig>()
                .eq(GovQualityRuleConfig::getRuleId, id));
        ruleMapper.deleteById(id);
        // 删除后重排剩余项排序，避免空洞；绝不自动补回已删标准项
        List<GovQualityRule> remain = ruleMapper.selectList(new LambdaQueryWrapper<GovQualityRule>()
                .orderByAsc(GovQualityRule::getSortNo)
                .orderByAsc(GovQualityRule::getId));
        int sort = 1;
        for (GovQualityRule r : remain) {
            if (r.getSortNo() == null || r.getSortNo() != sort) {
                r.setSortNo(sort);
                r.setUpdatedAt(LocalDateTime.now());
                ruleMapper.updateById(r);
            }
            sort++;
        }
        log.info("quality rule deleted id={} by={} remain={}", id,
                operator != null ? operator.getUsername() : null, remain.size());
    }

    /** 解除模型规则 / 任务明细引用，避免外键或业务约束导致删除失败 */
    private void clearRuleReferences(Long ruleId) {
        modelRuleMapper.update(null, new LambdaUpdateWrapper<GovQualityModelRule>()
                .eq(GovQualityModelRule::getRuleCatalogId, ruleId)
                .set(GovQualityModelRule::getRuleCatalogId, null));
        taskDetailMapper.delete(new LambdaQueryWrapper<GovQualityTaskDetail>()
                .eq(GovQualityTaskDetail::getRuleId, ruleId));
    }

    private GovQualityRule requireRule(Long id) {
        GovQualityRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "质量规则不存在: " + id);
        }
        return rule;
    }

    private GovQualityRuleConfig findConfig(Long ruleId) {
        return configMapper.selectOne(new LambdaQueryWrapper<GovQualityRuleConfig>()
                .eq(GovQualityRuleConfig::getRuleId, ruleId)
                .last("LIMIT 1"));
    }

    private static String str(Object v, String defaultVal) {
        if (v == null) {
            return defaultVal;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? defaultVal : s;
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

    private static int toInt(Object v, int defaultVal) {
        if (v == null || String.valueOf(v).isBlank()) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new BusinessException(400, "排序须为整数");
        }
    }

    private record StandardRule(String code, String name, String ruleType, String description) {}
}
