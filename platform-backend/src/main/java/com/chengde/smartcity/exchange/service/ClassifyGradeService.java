package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizDataAsset;
import com.chengde.smartcity.exchange.entity.IngClsAssetMark;
import com.chengde.smartcity.exchange.entity.IngClsAuditLog;
import com.chengde.smartcity.exchange.entity.IngClsCategory;
import com.chengde.smartcity.exchange.entity.IngClsHitLog;
import com.chengde.smartcity.exchange.entity.IngClsLevel;
import com.chengde.smartcity.exchange.entity.IngClsScopeRule;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngResourceRegistry;
import com.chengde.smartcity.exchange.mapper.BizDataAssetMapper;
import com.chengde.smartcity.exchange.mapper.IngClsAssetMarkMapper;
import com.chengde.smartcity.exchange.mapper.IngClsAuditLogMapper;
import com.chengde.smartcity.exchange.mapper.IngClsCategoryMapper;
import com.chengde.smartcity.exchange.mapper.IngClsHitLogMapper;
import com.chengde.smartcity.exchange.mapper.IngClsLevelMapper;
import com.chengde.smartcity.exchange.mapper.IngClsScopeRuleMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngResourceRegistryMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据分级分类：级别字典、多维分类、资产标注、使用范围校验（冲突从严）。
 * 对齐 GB/T 43697-2024 管理闭环：识别→分类→分级→管控→复核。
 */
@Service
public class ClassifyGradeService {

    private static final Logger log = LoggerFactory.getLogger(ClassifyGradeService.class);

    private final IngClsLevelMapper levelMapper;
    private final IngClsCategoryMapper categoryMapper;
    private final IngClsAssetMarkMapper markMapper;
    private final IngClsScopeRuleMapper scopeMapper;
    private final IngClsAuditLogMapper auditMapper;
    private final IngClsHitLogMapper hitMapper;
    private final IngDataTableMapper tableMapper;
    private final BizDataAssetMapper assetMapper;
    private final IngResourceRegistryMapper registryMapper;

    public ClassifyGradeService(
            IngClsLevelMapper levelMapper,
            IngClsCategoryMapper categoryMapper,
            IngClsAssetMarkMapper markMapper,
            IngClsScopeRuleMapper scopeMapper,
            IngClsAuditLogMapper auditMapper,
            IngClsHitLogMapper hitMapper,
            IngDataTableMapper tableMapper,
            BizDataAssetMapper assetMapper,
            IngResourceRegistryMapper registryMapper) {
        this.levelMapper = levelMapper;
        this.categoryMapper = categoryMapper;
        this.markMapper = markMapper;
        this.scopeMapper = scopeMapper;
        this.auditMapper = auditMapper;
        this.hitMapper = hitMapper;
        this.tableMapper = tableMapper;
        this.assetMapper = assetMapper;
        this.registryMapper = registryMapper;
    }

    public List<IngClsLevel> listLevels() {
        return levelMapper.selectList(new LambdaQueryWrapper<IngClsLevel>().orderByAsc(IngClsLevel::getSortNo));
    }

    @Transactional
    public Long saveLevel(Map<String, Object> body) {
        String code = str(body.get("levelCode"), "");
        if (code.isBlank()) throw new BusinessException(400, "级别编码不能为空");
        Long id = longVal(body.get("id"));
        IngClsLevel row = id == null ? new IngClsLevel() : levelMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "级别不存在");
        if (id == null) {
            Long exists = levelMapper.selectCount(new LambdaQueryWrapper<IngClsLevel>().eq(IngClsLevel::getLevelCode, code));
            if (exists != null && exists > 0) throw new BusinessException(400, "级别编码已存在");
            row.setLevelCode(code);
            row.setCreateTime(LocalDateTime.now());
        }
        row.setLevelName(str(body.get("levelName"), row.getLevelName()));
        row.setSortNo(intVal(body.get("sortNo"), row.getSortNo() == null ? 0 : row.getSortNo()));
        row.setSensitivityScore(intVal(body.get("sensitivityScore"), row.getSensitivityScore() == null ? 1 : row.getSensitivityScore()));
        row.setControlStrength(str(body.get("controlStrength"), row.getControlStrength() == null ? "LOW" : row.getControlStrength()));
        row.setDescription(str(body.get("description"), row.getDescription()));
        row.setShareAllowed(boolInt(body.get("shareAllowed"), row.getShareAllowed() == null ? 1 : row.getShareAllowed()));
        row.setOpenAllowed(boolInt(body.get("openAllowed"), row.getOpenAllowed() == null ? 0 : row.getOpenAllowed()));
        row.setMaskRequired(boolInt(body.get("maskRequired"), row.getMaskRequired() == null ? 0 : row.getMaskRequired()));
        row.setApprovalLevel(str(body.get("approvalLevel"), row.getApprovalLevel() == null ? "NONE" : row.getApprovalLevel()));
        row.setStatus(str(body.get("status"), row.getStatus() == null ? "ACTIVE" : row.getStatus()));
        if (id == null) levelMapper.insert(row);
        else levelMapper.updateById(row);
        return row.getId();
    }

    public List<IngClsCategory> listCategories(String dimType) {
        LambdaQueryWrapper<IngClsCategory> q = new LambdaQueryWrapper<IngClsCategory>().orderByAsc(IngClsCategory::getSortNo);
        if (dimType != null && !dimType.isBlank()) q.eq(IngClsCategory::getDimType, dimType);
        return categoryMapper.selectList(q);
    }

    @Transactional
    public Long saveCategory(Map<String, Object> body) {
        String code = str(body.get("categoryCode"), "");
        String name = str(body.get("categoryName"), "");
        if (code.isBlank() || name.isBlank()) throw new BusinessException(400, "分类编码与名称不能为空");
        Long id = longVal(body.get("id"));
        IngClsCategory row = id == null ? new IngClsCategory() : categoryMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "分类不存在");
        if (id == null) {
            Long exists = categoryMapper.selectCount(new LambdaQueryWrapper<IngClsCategory>().eq(IngClsCategory::getCategoryCode, code));
            if (exists != null && exists > 0) throw new BusinessException(400, "分类编码已存在");
            row.setCategoryCode(code);
            row.setCreateTime(LocalDateTime.now());
        }
        row.setCategoryName(name);
        row.setParentId(longVal(body.get("parentId")));
        row.setDimType(str(body.get("dimType"), row.getDimType() == null ? "BUSINESS" : row.getDimType()));
        row.setSortNo(intVal(body.get("sortNo"), row.getSortNo() == null ? 0 : row.getSortNo()));
        row.setShareScopeHint(str(body.get("shareScopeHint"), row.getShareScopeHint()));
        row.setDescription(str(body.get("description"), row.getDescription()));
        row.setStatus(str(body.get("status"), row.getStatus() == null ? "ACTIVE" : row.getStatus()));
        String parentPath = "";
        if (row.getParentId() != null) {
            IngClsCategory p = categoryMapper.selectById(row.getParentId());
            if (p != null) parentPath = p.getPath() == null ? "" : p.getPath();
        }
        row.setPath(parentPath + "/" + row.getCategoryCode());
        if (id == null) categoryMapper.insert(row);
        else categoryMapper.updateById(row);
        return row.getId();
    }

    @Transactional
    public void deleteCategory(Long id) {
        Long children = categoryMapper.selectCount(new LambdaQueryWrapper<IngClsCategory>().eq(IngClsCategory::getParentId, id));
        if (children != null && children > 0) throw new BusinessException(400, "请先删除子分类");
        Long used = markMapper.selectCount(new LambdaQueryWrapper<IngClsAssetMark>().eq(IngClsAssetMark::getCategoryId, id));
        if (used != null && used > 0) throw new BusinessException(400, "分类已被资产引用，无法删除");
        categoryMapper.deleteById(id);
    }

    public List<IngClsAssetMark> listMarks(String assetType, String levelCode, String keyword) {
        LambdaQueryWrapper<IngClsAssetMark> q = new LambdaQueryWrapper<IngClsAssetMark>()
                .eq(IngClsAssetMark::getStatus, "ACTIVE")
                .orderByDesc(IngClsAssetMark::getUpdateTime);
        if (assetType != null && !assetType.isBlank()) q.eq(IngClsAssetMark::getAssetType, assetType);
        if (levelCode != null && !levelCode.isBlank()) q.eq(IngClsAssetMark::getLevelCode, levelCode);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(IngClsAssetMark::getAssetName, keyword).or().like(IngClsAssetMark::getAssetCode, keyword));
        }
        return markMapper.selectList(q);
    }

    @Transactional
    public Long saveMark(UserPrincipal user, Map<String, Object> body) {
        String assetType = str(body.get("assetType"), "TABLE");
        Long assetId = longVal(body.get("assetId"));
        String levelCode = str(body.get("levelCode"), "");
        if (assetId == null || levelCode.isBlank()) throw new BusinessException(400, "资产与级别不能为空");
        IngClsLevel level = levelMapper.selectOne(new LambdaQueryWrapper<IngClsLevel>().eq(IngClsLevel::getLevelCode, levelCode));
        if (level == null || !"ACTIVE".equals(level.getStatus())) throw new BusinessException(400, "级别无效");

        IngClsAssetMark existing = markMapper.selectOne(new LambdaQueryWrapper<IngClsAssetMark>()
                .eq(IngClsAssetMark::getAssetType, assetType)
                .eq(IngClsAssetMark::getAssetId, assetId));
        String beforeJson = existing == null ? null : markSnapshot(existing);
        boolean isNew = existing == null;
        IngClsAssetMark mark = isNew ? new IngClsAssetMark() : existing;
        if (isNew) {
            mark.setAssetType(assetType);
            mark.setAssetId(assetId);
            mark.setVersionNo(1);
            mark.setCreateTime(LocalDateTime.now());
            fillAssetMeta(mark);
        } else {
            mark.setVersionNo((mark.getVersionNo() == null ? 1 : mark.getVersionNo()) + 1);
        }
        if (body.get("assetCode") != null) mark.setAssetCode(str(body.get("assetCode"), mark.getAssetCode()));
        if (body.get("assetName") != null) mark.setAssetName(str(body.get("assetName"), mark.getAssetName()));
        mark.setCategoryId(longVal(body.get("categoryId")));
        mark.setLevelCode(levelCode);
        mark.setGradeBasis(str(body.get("gradeBasis"), isNew ? "MANUAL" : "REVIEWED"));
        mark.setGradeReason(str(body.get("gradeReason"), mark.getGradeReason()));
        mark.setFeaturesJson(str(body.get("featuresJson"), mark.getFeaturesJson()));
        mark.setGradedBy(user == null ? null : user.getUsername());
        mark.setGradedAt(LocalDateTime.now());
        mark.setStatus("ACTIVE");
        if (isNew) markMapper.insert(mark);
        else markMapper.updateById(mark);

        writeAudit(user, mark, isNew ? "CREATE" : "UPDATE", beforeJson, markSnapshot(mark));
        return mark.getId();
    }

    @Transactional
    public Map<String, Object> batchMark(UserPrincipal user, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) throw new BusinessException(400, "批量项不能为空");
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (Map<String, Object> item : items) {
            try {
                Map<String, Object> one = new HashMap<>(item);
                if (!one.containsKey("levelCode") && body.get("levelCode") != null) one.put("levelCode", body.get("levelCode"));
                if (!one.containsKey("categoryId") && body.get("categoryId") != null) one.put("categoryId", body.get("categoryId"));
                if (!one.containsKey("gradeBasis")) one.put("gradeBasis", "BATCH");
                saveMark(user, one);
                ok++;
            } catch (Exception e) {
                errors.add(e.getMessage());
            }
        }
        return Map.of("success", ok, "failed", errors.size(), "errors", errors);
    }

    /** 规则建议定级：依据特征分（个人信息/业务关键/泄露影响）映射到级别字典 */
    public Map<String, Object> suggestLevel(Map<String, Object> body) {
        int personal = intVal(body.get("personalInfoScore"), 0);
        int critical = intVal(body.get("businessCriticalScore"), 0);
        int impact = intVal(body.get("leakImpactScore"), 0);
        int total = personal * 2 + critical + impact * 2;
        String suggested;
        String reason;
        if (total >= 14) {
            suggested = "CORE";
            reason = "综合评分 " + total + "，建议定为核心数据";
        } else if (total >= 7) {
            suggested = "IMPORTANT";
            reason = "综合评分 " + total + "，建议定为重要数据";
        } else {
            suggested = "GENERAL";
            reason = "综合评分 " + total + "，建议定为一般数据";
        }
        IngClsLevel level = levelMapper.selectOne(new LambdaQueryWrapper<IngClsLevel>().eq(IngClsLevel::getLevelCode, suggested));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("suggestedLevelCode", suggested);
        out.put("suggestedLevelName", level == null ? suggested : level.getLevelName());
        out.put("score", total);
        out.put("reason", reason);
        out.put("basis", "RULE_SUGGEST");
        return out;
    }

    public List<IngClsScopeRule> listScopeRules(String actionType) {
        LambdaQueryWrapper<IngClsScopeRule> q = new LambdaQueryWrapper<IngClsScopeRule>().orderByDesc(IngClsScopeRule::getSeverityWeight);
        if (actionType != null && !actionType.isBlank()) q.eq(IngClsScopeRule::getActionType, actionType);
        return scopeMapper.selectList(q);
    }

    @Transactional
    public Long saveScopeRule(Map<String, Object> body) {
        String code = str(body.get("ruleCode"), "");
        String name = str(body.get("ruleName"), "");
        String action = str(body.get("actionType"), "");
        if (code.isBlank() || name.isBlank() || action.isBlank()) {
            throw new BusinessException(400, "规则编码、名称与操作类型不能为空");
        }
        Long id = longVal(body.get("id"));
        IngClsScopeRule row = id == null ? new IngClsScopeRule() : scopeMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "策略不存在");
        if (id == null) {
            Long exists = scopeMapper.selectCount(new LambdaQueryWrapper<IngClsScopeRule>().eq(IngClsScopeRule::getRuleCode, code));
            if (exists != null && exists > 0) throw new BusinessException(400, "规则编码已存在");
            row.setRuleCode(code);
            row.setCreateTime(LocalDateTime.now());
        }
        row.setRuleName(name);
        row.setLevelCode(blankToNull(str(body.get("levelCode"), "")));
        row.setCategoryId(longVal(body.get("categoryId")));
        row.setActionType(action);
        row.setAllowFlag(boolInt(body.get("allowFlag"), 1));
        row.setMaskRequired(boolInt(body.get("maskRequired"), 0));
        row.setApprovalRequired(boolInt(body.get("approvalRequired"), 0));
        row.setSubjectScope(str(body.get("subjectScope"), "ALL"));
        row.setSeverityWeight(intVal(body.get("severityWeight"), 1));
        row.setRemark(str(body.get("remark"), row.getRemark()));
        row.setStatus(str(body.get("status"), "ACTIVE"));
        if (id == null) scopeMapper.insert(row);
        else scopeMapper.updateById(row);
        return row.getId();
    }

    @Transactional
    public void deleteScopeRule(Long id) {
        scopeMapper.deleteById(id);
    }

    /**
     * 使用范围校验：匹配级别与类别策略，冲突取 severity 最大（从严）。
     */
    @Transactional
    public Map<String, Object> evaluate(UserPrincipal user, Map<String, Object> body) {
        String assetType = str(body.get("assetType"), "TABLE");
        Long assetId = longVal(body.get("assetId"));
        String action = str(body.get("actionType"), "SHARE");
        boolean masked = bool(body.get("masked"));
        boolean approved = bool(body.get("approved"));
        if (assetId == null) throw new BusinessException(400, "资产ID不能为空");

        IngClsAssetMark mark = markMapper.selectOne(new LambdaQueryWrapper<IngClsAssetMark>()
                .eq(IngClsAssetMark::getAssetType, assetType)
                .eq(IngClsAssetMark::getAssetId, assetId)
                .eq(IngClsAssetMark::getStatus, "ACTIVE"));
        if (mark == null) {
            Map<String, Object> deny = denyResult(null, "资产尚未完成分类分级，禁止" + action);
            writeHit(user, assetType, assetId, action, null, null, null, "DENY", String.valueOf(deny.get("reason")));
            return deny;
        }

        List<IngClsScopeRule> all = scopeMapper.selectList(new LambdaQueryWrapper<IngClsScopeRule>()
                .eq(IngClsScopeRule::getStatus, "ACTIVE")
                .eq(IngClsScopeRule::getActionType, action));

        List<IngClsScopeRule> matched = new ArrayList<>();
        for (IngClsScopeRule r : all) {
            boolean levelOk = r.getLevelCode() == null || Objects.equals(r.getLevelCode(), mark.getLevelCode());
            boolean catOk = r.getCategoryId() == null || Objects.equals(r.getCategoryId(), mark.getCategoryId());
            // 至少绑定级别或类别之一，且与当前标注匹配
            if ((r.getLevelCode() != null || r.getCategoryId() != null) && levelOk && catOk) {
                matched.add(r);
            }
        }
        if (matched.isEmpty()) {
            // 回退级别内置属性
            IngClsLevel level = levelMapper.selectOne(new LambdaQueryWrapper<IngClsLevel>().eq(IngClsLevel::getLevelCode, mark.getLevelCode()));
            boolean allow = switch (action) {
                case "OPEN" -> level != null && Objects.equals(level.getOpenAllowed(), 1);
                case "SHARE", "EXPORT", "TRANSFER" -> level != null && Objects.equals(level.getShareAllowed(), 1);
                default -> true;
            };
            boolean needMask = level != null && Objects.equals(level.getMaskRequired(), 1);
            boolean needApproval = level != null && level.getApprovalLevel() != null && !"NONE".equals(level.getApprovalLevel());
            Map<String, Object> res = applyGate(allow, needMask, needApproval, masked, approved, "级别默认属性", null, mark);
            writeHit(user, assetType, assetId, action, mark.getLevelCode(), mark.getCategoryId(), null,
                    String.valueOf(res.get("result")), String.valueOf(res.get("reason")));
            return res;
        }

        IngClsScopeRule strictest = matched.get(0);
        for (IngClsScopeRule r : matched) {
            int w = r.getSeverityWeight() == null ? 0 : r.getSeverityWeight();
            int cur = strictest.getSeverityWeight() == null ? 0 : strictest.getSeverityWeight();
            if (w > cur) strictest = r;
            else if (w == cur) {
                // 同权重：不允许 > 允许（从严）
                if (Objects.equals(r.getAllowFlag(), 0) && !Objects.equals(strictest.getAllowFlag(), 0)) {
                    strictest = r;
                }
            }
        }
        boolean allow = Objects.equals(strictest.getAllowFlag(), 1);
        boolean needMask = Objects.equals(strictest.getMaskRequired(), 1);
        boolean needApproval = Objects.equals(strictest.getApprovalRequired(), 1);
        Map<String, Object> res = applyGate(allow, needMask, needApproval, masked, approved,
                "命中策略「" + strictest.getRuleName() + "」(从严权重 " + strictest.getSeverityWeight() + ")",
                strictest.getId(), mark);
        writeHit(user, assetType, assetId, action, mark.getLevelCode(), mark.getCategoryId(), strictest.getId(),
                String.valueOf(res.get("result")), String.valueOf(res.get("reason")));
        return res;
    }

    public List<IngClsAuditLog> listAudit(int limit) {
        return auditMapper.selectList(new LambdaQueryWrapper<IngClsAuditLog>()
                .orderByDesc(IngClsAuditLog::getCreatedAt)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
    }

    public List<IngClsHitLog> listHits(int limit) {
        return hitMapper.selectList(new LambdaQueryWrapper<IngClsHitLog>()
                .orderByDesc(IngClsHitLog::getCreatedAt)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
    }

    public Map<String, Object> overview() {
        long marks = markMapper.selectCount(new LambdaQueryWrapper<IngClsAssetMark>().eq(IngClsAssetMark::getStatus, "ACTIVE"));
        long levels = levelMapper.selectCount(new LambdaQueryWrapper<IngClsLevel>().eq(IngClsLevel::getStatus, "ACTIVE"));
        long cats = categoryMapper.selectCount(new LambdaQueryWrapper<IngClsCategory>().eq(IngClsCategory::getStatus, "ACTIVE"));
        long rules = scopeMapper.selectCount(new LambdaQueryWrapper<IngClsScopeRule>().eq(IngClsScopeRule::getStatus, "ACTIVE"));
        long assets = assetMapper.selectCount(null);
        long tables = tableMapper.selectCount(null);
        long registries = registryMapper.selectCount(null);
        Map<String, Object> byLevel = new LinkedHashMap<>();
        for (IngClsLevel lv : listLevels()) {
            long c = markMapper.selectCount(new LambdaQueryWrapper<IngClsAssetMark>()
                    .eq(IngClsAssetMark::getLevelCode, lv.getLevelCode())
                    .eq(IngClsAssetMark::getStatus, "ACTIVE"));
            byLevel.put(lv.getLevelCode(), c);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("markedAssets", marks);
        out.put("levelCount", levels);
        out.put("categoryCount", cats);
        out.put("scopeRuleCount", rules);
        out.put("totalAssets", assets);
        out.put("totalTables", tables);
        out.put("totalRegistries", registries);
        out.put("marksByLevel", byLevel);
        out.put("coveragePct", tables == 0 ? 0 : Math.round(marks * 1000.0 / tables) / 10.0);
        return out;
    }

    public List<Map<String, Object>> candidateAssets(String assetType, String keyword) {
        List<Map<String, Object>> list = new ArrayList<>();
        String type = str(assetType, "TABLE");
        if ("TABLE".equals(type)) {
            LambdaQueryWrapper<IngDataTable> q = new LambdaQueryWrapper<IngDataTable>().last("LIMIT 50");
            if (keyword != null && !keyword.isBlank()) {
                q.and(w -> w.like(IngDataTable::getTableName, keyword).or().like(IngDataTable::getTableCode, keyword));
            }
            for (IngDataTable t : tableMapper.selectList(q)) {
                list.add(Map.of("assetType", "TABLE", "assetId", t.getId(),
                        "assetCode", nullToEmpty(t.getTableCode()), "assetName", nullToEmpty(t.getTableName())));
            }
        } else if ("ASSET".equals(type)) {
            LambdaQueryWrapper<BizDataAsset> q = new LambdaQueryWrapper<BizDataAsset>().last("LIMIT 50");
            if (keyword != null && !keyword.isBlank()) {
                q.and(w -> w.like(BizDataAsset::getAssetName, keyword).or().like(BizDataAsset::getAssetCode, keyword));
            }
            for (BizDataAsset a : assetMapper.selectList(q)) {
                list.add(Map.of("assetType", "ASSET", "assetId", a.getId(),
                        "assetCode", nullToEmpty(a.getAssetCode()), "assetName", nullToEmpty(a.getAssetName())));
            }
        } else {
            LambdaQueryWrapper<IngResourceRegistry> q = new LambdaQueryWrapper<IngResourceRegistry>().last("LIMIT 50");
            if (keyword != null && !keyword.isBlank()) q.like(IngResourceRegistry::getTitle, keyword);
            for (IngResourceRegistry r : registryMapper.selectList(q)) {
                list.add(Map.of("assetType", "REGISTRY", "assetId", r.getId(),
                        "assetCode", nullToEmpty(r.getRegistryCode()), "assetName", nullToEmpty(r.getTitle())));
            }
        }
        return list;
    }

    private void fillAssetMeta(IngClsAssetMark mark) {
        if ("TABLE".equals(mark.getAssetType())) {
            IngDataTable t = tableMapper.selectById(mark.getAssetId());
            if (t != null) {
                mark.setAssetCode(t.getTableCode());
                mark.setAssetName(t.getTableName());
            }
        } else if ("ASSET".equals(mark.getAssetType())) {
            BizDataAsset a = assetMapper.selectById(mark.getAssetId());
            if (a != null) {
                mark.setAssetCode(a.getAssetCode());
                mark.setAssetName(a.getAssetName());
            }
        } else if ("REGISTRY".equals(mark.getAssetType())) {
            IngResourceRegistry r = registryMapper.selectById(mark.getAssetId());
            if (r != null) {
                mark.setAssetCode(r.getRegistryCode());
                mark.setAssetName(r.getTitle());
            }
        }
    }

    private void writeAudit(UserPrincipal user, IngClsAssetMark mark, String changeType, String before, String after) {
        IngClsAuditLog logRow = new IngClsAuditLog();
        logRow.setMarkId(mark.getId());
        logRow.setAssetType(mark.getAssetType());
        logRow.setAssetId(mark.getAssetId());
        logRow.setChangeType(changeType);
        logRow.setBeforeJson(before);
        logRow.setAfterJson(after);
        logRow.setOperatorId(user == null ? null : user.getUserId());
        logRow.setOperatorName(user == null ? null : user.getUsername());
        logRow.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(logRow);
    }

    private void writeHit(UserPrincipal user, String assetType, Long assetId, String action,
                          String levelCode, Long categoryId, Long ruleId, String result, String reason) {
        IngClsHitLog hit = new IngClsHitLog();
        hit.setAssetType(assetType);
        hit.setAssetId(assetId);
        hit.setActionType(action);
        hit.setLevelCode(levelCode);
        hit.setCategoryId(categoryId);
        hit.setRuleId(ruleId);
        hit.setResult(result);
        hit.setReason(reason);
        hit.setOperatorName(user == null ? null : user.getUsername());
        hit.setCreatedAt(LocalDateTime.now());
        hitMapper.insert(hit);
        log.debug("classify evaluate {} {} -> {}", assetType, assetId, result);
    }

    private Map<String, Object> applyGate(boolean allow, boolean needMask, boolean needApproval,
                                          boolean masked, boolean approved, String baseReason,
                                          Long ruleId, IngClsAssetMark mark) {
        if (!allow) {
            return denyResult(ruleId, baseReason + "：策略禁止该操作");
        }
        if (needMask && !masked) {
            return denyResult(ruleId, baseReason + "：须先脱敏");
        }
        if (needApproval && !approved) {
            return denyResult(ruleId, baseReason + "：须完成审批");
        }
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("result", "ALLOW");
        ok.put("allowed", true);
        ok.put("maskRequired", needMask);
        ok.put("approvalRequired", needApproval);
        ok.put("reason", baseReason);
        ok.put("ruleId", ruleId);
        ok.put("levelCode", mark.getLevelCode());
        ok.put("categoryId", mark.getCategoryId());
        return ok;
    }

    private Map<String, Object> denyResult(Long ruleId, String reason) {
        Map<String, Object> deny = new LinkedHashMap<>();
        deny.put("result", "DENY");
        deny.put("allowed", false);
        deny.put("maskRequired", true);
        deny.put("approvalRequired", true);
        deny.put("reason", reason);
        deny.put("ruleId", ruleId);
        return deny;
    }

    private String markSnapshot(IngClsAssetMark m) {
        return "{\"levelCode\":\"" + nullToEmpty(m.getLevelCode())
                + "\",\"categoryId\":" + (m.getCategoryId() == null ? "null" : m.getCategoryId())
                + ",\"gradeBasis\":\"" + nullToEmpty(m.getGradeBasis())
                + "\",\"versionNo\":" + (m.getVersionNo() == null ? 1 : m.getVersionNo()) + "}";
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        return Long.valueOf(String.valueOf(v));
    }

    private static int intVal(Object v, int def) {
        if (v == null || String.valueOf(v).isBlank()) return def;
        return Integer.parseInt(String.valueOf(v));
    }

    private static int boolInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b ? 1 : 0;
        String s = String.valueOf(v);
        if ("1".equals(s) || "true".equalsIgnoreCase(s)) return 1;
        if ("0".equals(s) || "false".equalsIgnoreCase(s)) return 0;
        return def;
    }

    private static boolean bool(Object v) {
        return boolInt(v, 0) == 1;
    }
}
