package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngAssetTag;
import com.chengde.smartcity.exchange.entity.IngAssetTagBinding;
import com.chengde.smartcity.exchange.entity.IngDataColumn;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngTagAuditLog;
import com.chengde.smartcity.exchange.entity.IngTagDim;
import com.chengde.smartcity.exchange.entity.IngTagRule;
import com.chengde.smartcity.exchange.mapper.IngAssetTagBindingMapper;
import com.chengde.smartcity.exchange.mapper.IngAssetTagMapper;
import com.chengde.smartcity.exchange.mapper.IngDataColumnMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngTagAuditLogMapper;
import com.chengde.smartcity.exchange.mapper.IngTagDimMapper;
import com.chengde.smartcity.exchange.mapper.IngTagRuleMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据标签管理：体系 / 智能识别规则 / 打标确认 / 检索导航 / 覆盖率治理 / 审计。
 * 人工标签默认不被低置信度规则静默覆盖（KEEP_MANUAL）。
 */
@Service
public class TagManageService {

    private static final Logger log = LoggerFactory.getLogger(TagManageService.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private final IngAssetTagMapper tagMapper;
    private final IngAssetTagBindingMapper bindingMapper;
    private final IngTagDimMapper dimMapper;
    private final IngTagRuleMapper ruleMapper;
    private final IngTagAuditLogMapper auditMapper;
    private final IngDataTableMapper tableMapper;
    private final IngDataColumnMapper columnMapper;

    public TagManageService(
            IngAssetTagMapper tagMapper,
            IngAssetTagBindingMapper bindingMapper,
            IngTagDimMapper dimMapper,
            IngTagRuleMapper ruleMapper,
            IngTagAuditLogMapper auditMapper,
            IngDataTableMapper tableMapper,
            IngDataColumnMapper columnMapper) {
        this.tagMapper = tagMapper;
        this.bindingMapper = bindingMapper;
        this.dimMapper = dimMapper;
        this.ruleMapper = ruleMapper;
        this.auditMapper = auditMapper;
        this.tableMapper = tableMapper;
        this.columnMapper = columnMapper;
    }

    public Map<String, Object> overview() {
        long tagCnt = tagMapper.selectCount(new LambdaQueryWrapper<IngAssetTag>().eq(IngAssetTag::getStatus, "ACTIVE"));
        long customCnt = tagMapper.selectCount(new LambdaQueryWrapper<IngAssetTag>()
                .eq(IngAssetTag::getTagSource, "CUSTOM").eq(IngAssetTag::getStatus, "ACTIVE"));
        long ruleCnt = ruleMapper.selectCount(new LambdaQueryWrapper<IngTagRule>().eq(IngTagRule::getStatus, "ACTIVE"));
        long bindCnt = bindingMapper.selectCount(new LambdaQueryWrapper<IngAssetTagBinding>()
                .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED"));
        long suggested = bindingMapper.selectCount(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getConfirmStatus, "SUGGESTED"));
        long tableCnt = tableMapper.selectCount(null);
        long taggedTables = bindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                        .eq(IngAssetTagBinding::getAssetType, "TABLE")
                        .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED")
                        .select(IngAssetTagBinding::getAssetId)).stream()
                .map(IngAssetTagBinding::getAssetId).collect(Collectors.toSet()).size();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activeTags", tagCnt);
        out.put("customTags", customCnt);
        out.put("activeRules", ruleCnt);
        out.put("bindings", bindCnt);
        out.put("pendingConfirm", suggested);
        out.put("tableCount", tableCnt);
        out.put("taggedTables", taggedTables);
        out.put("coverageRate", tableCnt == 0 ? 0 : Math.round(taggedTables * 1000.0 / tableCnt) / 10.0);
        return out;
    }

    public List<IngTagDim> listDims() {
        return dimMapper.selectList(new LambdaQueryWrapper<IngTagDim>()
                .orderByAsc(IngTagDim::getSortNo).orderByAsc(IngTagDim::getId));
    }

    @Transactional
    public Long saveDim(Map<String, Object> body) {
        String code = str(body.get("dimCode"), "");
        String name = str(body.get("dimName"), "");
        if (code.isBlank() || name.isBlank()) throw new BusinessException(400, "维度编码与名称不能为空");
        Long id = longVal(body.get("id"));
        IngTagDim row = id == null ? new IngTagDim() : dimMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "维度不存在");
        if (id == null) {
            if (dimMapper.selectCount(new LambdaQueryWrapper<IngTagDim>().eq(IngTagDim::getDimCode, code)) > 0) {
                throw new BusinessException(400, "维度编码已存在");
            }
            row.setDimCode(code);
            row.setCreateTime(LocalDateTime.now());
        }
        row.setDimName(name);
        row.setDescription(str(body.get("description"), row.getDescription()));
        row.setSortNo(intVal(body.get("sortNo"), row.getSortNo() == null ? 0 : row.getSortNo()));
        row.setStatus(str(body.get("status"), row.getStatus() == null ? "ACTIVE" : row.getStatus()));
        if (id == null) dimMapper.insert(row);
        else dimMapper.updateById(row);
        return row.getId();
    }

    public List<IngAssetTag> listTags(String dimType, String keyword, String tagSource) {
        LambdaQueryWrapper<IngAssetTag> q = new LambdaQueryWrapper<IngAssetTag>()
                .orderByAsc(IngAssetTag::getSortNo)
                .orderByAsc(IngAssetTag::getTagSource)
                .orderByAsc(IngAssetTag::getId);
        if (dimType != null && !dimType.isBlank()) q.eq(IngAssetTag::getDimType, dimType.trim());
        if (tagSource != null && !tagSource.isBlank()) q.eq(IngAssetTag::getTagSource, tagSource.trim());
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(IngAssetTag::getTagName, kw)
                    .or().like(IngAssetTag::getTagCode, kw)
                    .or().like(IngAssetTag::getSynonyms, kw)
                    .or().like(IngAssetTag::getStdCode, kw));
        }
        List<IngAssetTag> list = tagMapper.selectList(q);
        for (IngAssetTag t : list) {
            long cnt = bindingMapper.selectCount(new LambdaQueryWrapper<IngAssetTagBinding>()
                    .eq(IngAssetTagBinding::getTagId, t.getId())
                    .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED"));
            t.setBindingCount((int) cnt);
        }
        return list;
    }

    @Transactional
    public Long saveTag(UserPrincipal principal, Map<String, Object> body) {
        Long id = longVal(body.get("id"));
        IngAssetTag tag = id == null ? new IngAssetTag() : tagMapper.selectById(id);
        if (tag == null) throw new BusinessException(404, "标签不存在");
        String before = toJson(tag);
        if (id == null) {
            String code = str(body.get("tagCode"), "TAG_" + System.currentTimeMillis());
            if (tagMapper.selectCount(new LambdaQueryWrapper<IngAssetTag>().eq(IngAssetTag::getTagCode, code)) > 0) {
                throw new BusinessException(400, "标签编码已存在");
            }
            tag.setTagCode(code);
            tag.setTagSource(str(body.get("tagSource"), "CUSTOM"));
            tag.setHitCount(0);
            tag.setStatus("ACTIVE");
        } else if ("STANDARD".equals(tag.getTagSource())) {
            // 国标仅允许维护规则/同义词/维度/颜色等，不改名称编码
            tag.setRuleExpr(str(body.get("ruleExpr"), tag.getRuleExpr()));
            tag.setSynonyms(str(body.get("synonyms"), tag.getSynonyms()));
            tag.setDimType(str(body.get("dimType"), tag.getDimType() == null ? "BUSINESS" : tag.getDimType()));
            tag.setColor(str(body.get("color"), tag.getColor()));
            tag.setSortNo(intVal(body.get("sortNo"), tag.getSortNo() == null ? 0 : tag.getSortNo()));
            if (body.containsKey("status")) tag.setStatus(str(body.get("status"), tag.getStatus()));
            tagMapper.updateById(tag);
            audit(principal, "UPDATE_TAG", tag.getId(), null, null, before, toJson(tag), "MANUAL");
            return tag.getId();
        }
        String name = str(body.get("tagName"), tag.getTagName());
        if (name == null || name.isBlank()) throw new BusinessException(400, "标签名称不能为空");
        tag.setTagName(name);
        tag.setRuleExpr(str(body.get("ruleExpr"), tag.getRuleExpr() == null ? "" : tag.getRuleExpr()));
        tag.setTagDesc(str(body.get("tagDesc"), tag.getTagDesc()));
        tag.setSynonyms(str(body.get("synonyms"), tag.getSynonyms()));
        tag.setDimType(str(body.get("dimType"), tag.getDimType() == null ? "BUSINESS" : tag.getDimType()));
        tag.setValueType(str(body.get("valueType"), tag.getValueType() == null ? "ENUM" : tag.getValueType()));
        tag.setColor(str(body.get("color"), tag.getColor()));
        tag.setMultiSelect(intVal(body.get("multiSelect"), tag.getMultiSelect() == null ? 1 : tag.getMultiSelect()));
        tag.setRequiredFlag(intVal(body.get("requiredFlag"), tag.getRequiredFlag() == null ? 0 : tag.getRequiredFlag()));
        tag.setSortNo(intVal(body.get("sortNo"), tag.getSortNo() == null ? 0 : tag.getSortNo()));
        tag.setParentId(longVal(body.get("parentId")));
        if (body.containsKey("status")) tag.setStatus(str(body.get("status"), "ACTIVE"));
        if (id == null) {
            tagMapper.insert(tag);
            audit(principal, "CREATE_TAG", tag.getId(), null, null, null, toJson(tag), "MANUAL");
        } else {
            tagMapper.updateById(tag);
            audit(principal, "UPDATE_TAG", tag.getId(), null, null, before, toJson(tag), "MANUAL");
        }
        return tag.getId();
    }

    @Transactional
    public void disableTag(UserPrincipal principal, Long id) {
        IngAssetTag tag = tagMapper.selectById(id);
        if (tag == null) throw new BusinessException(404, "标签不存在");
        if ("STANDARD".equals(tag.getTagSource())) throw new BusinessException(400, "国标类目不可停用");
        String before = toJson(tag);
        tag.setStatus("INACTIVE");
        tagMapper.updateById(tag);
        audit(principal, "UPDATE_TAG", id, null, null, before, toJson(tag), "MANUAL");
    }

    @Transactional
    public Map<String, Object> mergeTags(UserPrincipal principal, Long keepId, Long dropId) {
        if (keepId == null || dropId == null || keepId.equals(dropId)) {
            throw new BusinessException(400, "请指定保留与废弃标签");
        }
        IngAssetTag keep = tagMapper.selectById(keepId);
        IngAssetTag drop = tagMapper.selectById(dropId);
        if (keep == null || drop == null) throw new BusinessException(404, "标签不存在");
        if ("STANDARD".equals(drop.getTagSource())) throw new BusinessException(400, "国标类目不可合并废弃");
        List<IngAssetTagBinding> drops = bindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getTagId, dropId));
        int migrated = 0;
        for (IngAssetTagBinding b : drops) {
            IngAssetTagBinding exists = bindingMapper.selectOne(new LambdaQueryWrapper<IngAssetTagBinding>()
                    .eq(IngAssetTagBinding::getTagId, keepId)
                    .eq(IngAssetTagBinding::getAssetType, b.getAssetType())
                    .eq(IngAssetTagBinding::getAssetId, b.getAssetId())
                    .last("LIMIT 1"));
            if (exists == null) {
                b.setTagId(keepId);
                b.setNote((b.getNote() == null ? "" : b.getNote() + ";") + "merged_from=" + drop.getTagCode());
                bindingMapper.updateById(b);
                migrated++;
            } else {
                bindingMapper.deleteById(b.getId());
            }
        }
        String before = toJson(drop);
        drop.setStatus("INACTIVE");
        tagMapper.updateById(drop);
        audit(principal, "MERGE", keepId, null, null, before,
                "{\"keepId\":" + keepId + ",\"dropId\":" + dropId + ",\"migrated\":" + migrated + "}", "MANUAL");
        return Map.of("keepId", keepId, "dropId", dropId, "migrated", migrated);
    }

    public List<IngTagRule> listRules(String keyword) {
        LambdaQueryWrapper<IngTagRule> q = new LambdaQueryWrapper<IngTagRule>()
                .orderByAsc(IngTagRule::getPriority).orderByDesc(IngTagRule::getUpdateTime);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(IngTagRule::getRuleName, keyword).or().like(IngTagRule::getRuleCode, keyword));
        }
        List<IngTagRule> list = ruleMapper.selectList(q);
        for (IngTagRule r : list) {
            IngAssetTag t = tagMapper.selectById(r.getTagId());
            if (t != null) {
                r.setTagName(t.getTagName());
                r.setTagCode(t.getTagCode());
            }
        }
        return list;
    }

    @Transactional
    public Long saveRule(UserPrincipal principal, Map<String, Object> body) {
        String code = str(body.get("ruleCode"), "");
        String name = str(body.get("ruleName"), "");
        Long tagId = longVal(body.get("tagId"));
        if (code.isBlank() || name.isBlank() || tagId == null) {
            throw new BusinessException(400, "规则编码、名称与目标标签不能为空");
        }
        if (tagMapper.selectById(tagId) == null) throw new BusinessException(404, "目标标签不存在");
        Long id = longVal(body.get("id"));
        IngTagRule row = id == null ? new IngTagRule() : ruleMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "规则不存在");
        if (id == null) {
            if (ruleMapper.selectCount(new LambdaQueryWrapper<IngTagRule>().eq(IngTagRule::getRuleCode, code)) > 0) {
                throw new BusinessException(400, "规则编码已存在");
            }
            row.setRuleCode(code);
            row.setVersionNo(1);
            row.setCreateTime(LocalDateTime.now());
        } else {
            row.setVersionNo((row.getVersionNo() == null ? 1 : row.getVersionNo()) + 1);
        }
        row.setRuleName(name);
        row.setTagId(tagId);
        String cond = str(body.get("conditionJson"), row.getConditionJson());
        if (cond == null || cond.isBlank()) cond = "{}";
        validateJson(cond);
        row.setConditionJson(cond);
        row.setActionType(str(body.get("actionType"), row.getActionType() == null ? "SUGGEST" : row.getActionType()));
        row.setPriority(intVal(body.get("priority"), row.getPriority() == null ? 100 : row.getPriority()));
        row.setConflictPolicy(str(body.get("conflictPolicy"),
                row.getConflictPolicy() == null ? "KEEP_MANUAL" : row.getConflictPolicy()));
        row.setStatus(str(body.get("status"), row.getStatus() == null ? "ACTIVE" : row.getStatus()));
        row.setDescription(str(body.get("description"), row.getDescription()));
        if (id == null) ruleMapper.insert(row);
        else ruleMapper.updateById(row);
        return row.getId();
    }

    @Transactional
    public void publishRule(UserPrincipal principal, Long id) {
        IngTagRule row = ruleMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "规则不存在");
        row.setStatus("ACTIVE");
        row.setVersionNo((row.getVersionNo() == null ? 1 : row.getVersionNo()) + 1);
        ruleMapper.updateById(row);
        audit(principal, "RULE_RUN", row.getTagId(), null, null, null,
                "{\"action\":\"publish\",\"ruleId\":" + id + "}", "RULE");
    }

    @Transactional
    public void deleteRule(Long id) {
        ruleMapper.deleteById(id);
    }

    /** 试跑：只返回命中样例，不写库。 */
    public Map<String, Object> dryRunRule(Long ruleId, Integer limit) {
        IngTagRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) throw new BusinessException(404, "规则不存在");
        int lim = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        List<Map<String, Object>> hits = matchRule(rule, lim, false);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ruleId", ruleId);
        out.put("ruleCode", rule.getRuleCode());
        out.put("hitCount", hits.size());
        out.put("samples", hits);
        return out;
    }

    /** 执行规则：写入建议/自动打标。 */
    @Transactional
    public Map<String, Object> runRule(UserPrincipal principal, Long ruleId, boolean dryRun, Integer limit) {
        if (dryRun) return dryRunRule(ruleId, limit);
        IngTagRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) throw new BusinessException(404, "规则不存在");
        if (!"ACTIVE".equals(rule.getStatus())) throw new BusinessException(400, "规则未启用");
        int lim = limit == null || limit < 1 ? 500 : Math.min(limit, 5000);
        List<Map<String, Object>> hits = matchRule(rule, lim, true);
        int applied = 0;
        int skipped = 0;
        int suggested = 0;
        for (Map<String, Object> hit : hits) {
            String assetType = String.valueOf(hit.get("assetType"));
            Long assetId = longVal(hit.get("assetId"));
            BigDecimal conf = new BigDecimal(String.valueOf(hit.getOrDefault("confidence", 70)));
            ApplyResult r = applyBinding(principal, rule.getTagId(), assetType, assetId,
                    "RULE", conf, rule.getActionType(), rule.getConflictPolicy(),
                    "rule=" + rule.getRuleCode());
            if (r == ApplyResult.APPLIED_AUTO || r == ApplyResult.APPLIED_SUGGEST) {
                applied++;
                if (r == ApplyResult.APPLIED_SUGGEST) suggested++;
            } else {
                skipped++;
            }
        }
        IngAssetTag tag = tagMapper.selectById(rule.getTagId());
        if (tag != null) {
            tag.setHitCount((tag.getHitCount() == null ? 0 : tag.getHitCount()) + applied);
            tagMapper.updateById(tag);
        }
        audit(principal, "RULE_RUN", rule.getTagId(), null, null, null,
                "{\"ruleId\":" + ruleId + ",\"hits\":" + hits.size()
                        + ",\"applied\":" + applied + ",\"skipped\":" + skipped + "}", "RULE");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ruleId", ruleId);
        out.put("hitCount", hits.size());
        out.put("applied", applied);
        out.put("suggested", suggested);
        out.put("skipped", skipped);
        out.put("samples", hits.stream().limit(20).toList());
        return out;
    }

    /** 全量/按规则批量跑所有启用规则。 */
    @Transactional
    public Map<String, Object> runAllRules(UserPrincipal principal, Integer limitPerRule) {
        List<IngTagRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<IngTagRule>()
                .eq(IngTagRule::getStatus, "ACTIVE")
                .orderByAsc(IngTagRule::getPriority));
        int totalApplied = 0;
        int totalHits = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (IngTagRule r : rules) {
            Map<String, Object> one = runRule(principal, r.getId(), false, limitPerRule);
            totalApplied += ((Number) one.get("applied")).intValue();
            totalHits += ((Number) one.get("hitCount")).intValue();
            details.add(Map.of("ruleCode", r.getRuleCode(), "applied", one.get("applied"), "hits", one.get("hitCount")));
        }
        return Map.of("rules", rules.size(), "totalHits", totalHits, "totalApplied", totalApplied, "details", details);
    }

    @Transactional
    public Long bind(UserPrincipal principal, Map<String, Object> body) {
        Long tagId = longVal(body.get("tagId"));
        String assetType = str(body.get("assetType"), "").trim().toUpperCase(Locale.ROOT);
        Long assetId = longVal(body.get("assetId"));
        if (tagId == null || assetId == null || assetType.isBlank()) {
            throw new BusinessException(400, "tagId/assetType/assetId required");
        }
        assertTagBindable(tagId);
        assertAssetExists(assetType, assetId);
        String source = str(body.get("source"), "MANUAL").toUpperCase(Locale.ROOT);
        BigDecimal conf = body.get("confidence") == null ? new BigDecimal("100")
                : new BigDecimal(String.valueOf(body.get("confidence")));
        String confirm = str(body.get("confirmStatus"), "CONFIRMED");
        ApplyResult r = applyBinding(principal, tagId, assetType, assetId, source, conf,
                "AUTO".equalsIgnoreCase(confirm) ? "AUTO" : ("SUGGESTED".equals(confirm) ? "SUGGEST" : "AUTO"),
                "OVERRIDE", str(body.get("note"), "manual"));
        IngAssetTagBinding b = bindingMapper.selectOne(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getTagId, tagId)
                .eq(IngAssetTagBinding::getAssetType, assetType)
                .eq(IngAssetTagBinding::getAssetId, assetId)
                .last("LIMIT 1"));
        if (b == null) throw new BusinessException(500, "打标失败: " + r);
        return b.getId();
    }

    @Transactional
    public Map<String, Object> batchBind(UserPrincipal principal, Map<String, Object> body) {
        Long tagId = longVal(body.get("tagId"));
        String assetType = str(body.get("assetType"), "TABLE").trim().toUpperCase(Locale.ROOT);
        @SuppressWarnings("unchecked")
        List<Object> idsRaw = body.get("assetIds") instanceof List ? (List<Object>) body.get("assetIds") : List.of();
        if (tagId == null || idsRaw.isEmpty()) throw new BusinessException(400, "tagId 与 assetIds 不能为空");
        assertTagBindable(tagId);
        int ok = 0;
        int fail = 0;
        for (Object o : idsRaw) {
            Long assetId = longVal(o);
            if (assetId == null) { fail++; continue; }
            try {
                assertAssetExists(assetType, assetId);
                applyBinding(principal, tagId, assetType, assetId, "MANUAL", new BigDecimal("100"),
                        "AUTO", "OVERRIDE", "batch");
                ok++;
            } catch (Exception e) {
                log.warn("batch bind fail assetId={}: {}", assetId, e.getMessage());
                fail++;
            }
        }
        return Map.of("ok", ok, "fail", fail);
    }

    @Transactional
    public void unbind(UserPrincipal principal, Long bindingId) {
        IngAssetTagBinding b = bindingMapper.selectById(bindingId);
        if (b == null) throw new BusinessException(404, "绑定不存在");
        String before = toJson(b);
        bindingMapper.deleteById(bindingId);
        audit(principal, "UNBIND", b.getTagId(), b.getAssetType(), b.getAssetId(), before, null, b.getSource());
    }

    public List<IngAssetTagBinding> listPending(Integer limit) {
        int lim = limit == null || limit < 1 ? 100 : Math.min(limit, 500);
        List<IngAssetTagBinding> list = bindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getConfirmStatus, "SUGGESTED")
                .orderByDesc(IngAssetTagBinding::getCreatedAt)
                .last("LIMIT " + lim));
        enrichBindings(list);
        return list;
    }

    @Transactional
    public void confirm(UserPrincipal principal, Long bindingId, boolean accept) {
        IngAssetTagBinding b = bindingMapper.selectById(bindingId);
        if (b == null) throw new BusinessException(404, "绑定不存在");
        String before = toJson(b);
        if (accept) {
            b.setConfirmStatus("CONFIRMED");
            b.setSource("MANUAL".equals(b.getSource()) ? "MANUAL" : b.getSource());
            b.setTaggedBy(operatorName(principal));
            bindingMapper.updateById(b);
            audit(principal, "CONFIRM", b.getTagId(), b.getAssetType(), b.getAssetId(), before, toJson(b), b.getSource());
        } else {
            b.setConfirmStatus("REJECTED");
            bindingMapper.updateById(b);
            audit(principal, "REJECT", b.getTagId(), b.getAssetType(), b.getAssetId(), before, toJson(b), b.getSource());
        }
    }

    @Transactional
    public Map<String, Object> batchConfirm(UserPrincipal principal, List<Long> ids, boolean accept) {
        int n = 0;
        if (ids != null) {
            for (Long id : ids) {
                if (id == null) continue;
                confirm(principal, id, accept);
                n++;
            }
        }
        return Map.of("count", n, "accepted", accept);
    }

    public List<IngAssetTagBinding> listBindings(Long tagId, String assetType, String confirmStatus, Integer limit) {
        LambdaQueryWrapper<IngAssetTagBinding> q = new LambdaQueryWrapper<IngAssetTagBinding>()
                .orderByDesc(IngAssetTagBinding::getCreatedAt);
        if (tagId != null) q.eq(IngAssetTagBinding::getTagId, tagId);
        if (assetType != null && !assetType.isBlank()) q.eq(IngAssetTagBinding::getAssetType, assetType.trim());
        if (confirmStatus != null && !confirmStatus.isBlank()) q.eq(IngAssetTagBinding::getConfirmStatus, confirmStatus.trim());
        int lim = limit == null || limit < 1 ? 200 : Math.min(limit, 1000);
        q.last("LIMIT " + lim);
        List<IngAssetTagBinding> list = bindingMapper.selectList(q);
        enrichBindings(list);
        return list;
    }

    /** 按标签检索资产（AND：须同时含全部 tagIds；OR：含任一）。 */
    public Map<String, Object> searchByTags(List<Long> tagIds, String mode, String excludeTagIds, Integer limit) {
        String m = mode == null || mode.isBlank() ? "OR" : mode.trim().toUpperCase(Locale.ROOT);
        int lim = limit == null || limit < 1 ? 100 : Math.min(limit, 500);
        Set<Long> include = tagIds == null ? Set.of() : tagIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> exclude = parseIds(excludeTagIds);
        if (include.isEmpty()) {
            return Map.of("total", 0, "items", List.of());
        }
        List<IngAssetTagBinding> binds = bindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                .in(IngAssetTagBinding::getTagId, include)
                .eq(IngAssetTagBinding::getAssetType, "TABLE")
                .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED"));
        Map<Long, Set<Long>> assetTags = new HashMap<>();
        for (IngAssetTagBinding b : binds) {
            assetTags.computeIfAbsent(b.getAssetId(), k -> new HashSet<>()).add(b.getTagId());
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> e : assetTags.entrySet()) {
            Set<Long> got = e.getValue();
            boolean ok = "AND".equals(m) ? got.containsAll(include) : got.stream().anyMatch(include::contains);
            if (!ok) continue;
            if (!exclude.isEmpty()) {
                List<IngAssetTagBinding> ex = bindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                        .eq(IngAssetTagBinding::getAssetType, "TABLE")
                        .eq(IngAssetTagBinding::getAssetId, e.getKey())
                        .in(IngAssetTagBinding::getTagId, exclude)
                        .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED"));
                if (!ex.isEmpty()) continue;
            }
            IngDataTable t = tableMapper.selectById(e.getKey());
            if (t == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("assetType", "TABLE");
            row.put("assetId", t.getId());
            row.put("assetCode", t.getTableCode());
            row.put("assetName", t.getTableName());
            row.put("matchedTagIds", got);
            List<IngAssetTagBinding> all = bindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                    .eq(IngAssetTagBinding::getAssetType, "TABLE")
                    .eq(IngAssetTagBinding::getAssetId, t.getId())
                    .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED"));
            enrichBindings(all);
            row.put("tags", all.stream().map(b -> Map.of(
                    "tagId", b.getTagId(),
                    "tagName", b.getTagName() == null ? "" : b.getTagName(),
                    "tagCode", b.getTagCode() == null ? "" : b.getTagCode(),
                    "source", b.getSource() == null ? "" : b.getSource()
            )).toList());
            items.add(row);
            if (items.size() >= lim) break;
        }
        return Map.of("total", items.size(), "mode", m, "items", items);
    }

    public List<Map<String, Object>> navigateByDim(String dimType) {
        String dim = dimType == null || dimType.isBlank() ? "BUSINESS" : dimType.trim();
        List<IngAssetTag> tags = tagMapper.selectList(new LambdaQueryWrapper<IngAssetTag>()
                .eq(IngAssetTag::getDimType, dim)
                .eq(IngAssetTag::getStatus, "ACTIVE")
                .orderByAsc(IngAssetTag::getSortNo));
        List<Map<String, Object>> out = new ArrayList<>();
        for (IngAssetTag t : tags) {
            if (t.getLevel() != null && t.getLevel() == 1) continue;
            long cnt = bindingMapper.selectCount(new LambdaQueryWrapper<IngAssetTagBinding>()
                    .eq(IngAssetTagBinding::getTagId, t.getId())
                    .eq(IngAssetTagBinding::getAssetType, "TABLE")
                    .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tagId", t.getId());
            row.put("tagCode", t.getTagCode());
            row.put("tagName", t.getTagName());
            row.put("synonyms", t.getSynonyms());
            row.put("color", t.getColor());
            row.put("assetCount", cnt);
            out.add(row);
        }
        return out;
    }

    public Map<String, Object> coverageReport() {
        long tables = tableMapper.selectCount(null);
        Set<Long> tagged = bindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                        .eq(IngAssetTagBinding::getAssetType, "TABLE")
                        .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED")
                        .select(IngAssetTagBinding::getAssetId)).stream()
                .map(IngAssetTagBinding::getAssetId).collect(Collectors.toSet());
        List<IngDataTable> all = tableMapper.selectList(new LambdaQueryWrapper<IngDataTable>().orderByDesc(IngDataTable::getId).last("LIMIT 500"));
        List<Map<String, Object>> untagged = new ArrayList<>();
        for (IngDataTable t : all) {
            if (tagged.contains(t.getId())) continue;
            untagged.add(Map.of("assetId", t.getId(), "assetCode", nullToEmpty(t.getTableCode()),
                    "assetName", nullToEmpty(t.getTableName())));
            if (untagged.size() >= 50) break;
        }
        List<IngAssetTag> tags = tagMapper.selectList(new LambdaQueryWrapper<IngAssetTag>()
                .eq(IngAssetTag::getStatus, "ACTIVE")
                .eq(IngAssetTag::getTagSource, "CUSTOM"));
        List<Map<String, Object>> hot = new ArrayList<>();
        List<Map<String, Object>> cold = new ArrayList<>();
        for (IngAssetTag t : tags) {
            long cnt = bindingMapper.selectCount(new LambdaQueryWrapper<IngAssetTagBinding>()
                    .eq(IngAssetTagBinding::getTagId, t.getId())
                    .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tagId", t.getId());
            row.put("tagName", t.getTagName());
            row.put("tagCode", t.getTagCode());
            row.put("count", cnt);
            if (cnt >= 3) hot.add(row);
            else cold.add(row);
        }
        hot.sort(Comparator.comparingLong(a -> -((Number) a.get("count")).longValue()));
        cold.sort(Comparator.comparingLong(a -> ((Number) a.get("count")).longValue()));

        List<IngAssetTag> required = tagMapper.selectList(new LambdaQueryWrapper<IngAssetTag>()
                .eq(IngAssetTag::getRequiredFlag, 1).eq(IngAssetTag::getStatus, "ACTIVE"));
        List<Map<String, Object>> missingRequired = new ArrayList<>();
        if (!required.isEmpty()) {
            for (IngDataTable t : all) {
                for (IngAssetTag req : required) {
                    long c = bindingMapper.selectCount(new LambdaQueryWrapper<IngAssetTagBinding>()
                            .eq(IngAssetTagBinding::getTagId, req.getId())
                            .eq(IngAssetTagBinding::getAssetType, "TABLE")
                            .eq(IngAssetTagBinding::getAssetId, t.getId())
                            .eq(IngAssetTagBinding::getConfirmStatus, "CONFIRMED"));
                    if (c == 0) {
                        missingRequired.add(Map.of(
                                "assetId", t.getId(),
                                "assetName", nullToEmpty(t.getTableName()),
                                "tagId", req.getId(),
                                "tagName", req.getTagName()));
                        if (missingRequired.size() >= 30) break;
                    }
                }
                if (missingRequired.size() >= 30) break;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tableCount", tables);
        out.put("taggedTables", tagged.size());
        out.put("coverageRate", tables == 0 ? 0 : Math.round(tagged.size() * 1000.0 / tables) / 10.0);
        out.put("untaggedSamples", untagged);
        out.put("hotTags", hot.stream().limit(10).toList());
        out.put("coldTags", cold.stream().limit(10).toList());
        out.put("missingRequired", missingRequired);
        return out;
    }

    public List<IngTagAuditLog> auditLogs(Integer limit) {
        int lim = limit == null || limit < 1 ? 50 : Math.min(limit, 200);
        return auditMapper.selectList(new LambdaQueryWrapper<IngTagAuditLog>()
                .orderByDesc(IngTagAuditLog::getCreatedAt)
                .last("LIMIT " + lim));
    }

    public List<Map<String, Object>> relatedTags(Long tagId, Integer limit) {
        int lim = limit == null || limit < 1 ? 8 : Math.min(limit, 20);
        List<IngAssetTagBinding> mine = bindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getTagId, tagId)
                .eq(IngAssetTagBinding::getAssetType, "TABLE")
                .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED")
                .last("LIMIT 200"));
        Map<Long, Integer> co = new HashMap<>();
        for (IngAssetTagBinding b : mine) {
            List<IngAssetTagBinding> others = bindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                    .eq(IngAssetTagBinding::getAssetType, "TABLE")
                    .eq(IngAssetTagBinding::getAssetId, b.getAssetId())
                    .ne(IngAssetTagBinding::getTagId, tagId)
                    .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED"));
            for (IngAssetTagBinding o : others) {
                co.merge(o.getTagId(), 1, Integer::sum);
            }
        }
        return co.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(lim)
                .map(e -> {
                    IngAssetTag t = tagMapper.selectById(e.getKey());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("tagId", e.getKey());
                    row.put("tagName", t == null ? "" : t.getTagName());
                    row.put("tagCode", t == null ? "" : t.getTagCode());
                    row.put("cooccur", e.getValue());
                    return row;
                }).toList();
    }

    // ---------- internals ----------

    private enum ApplyResult { APPLIED_AUTO, APPLIED_SUGGEST, SKIPPED, EXISTS }

    private ApplyResult applyBinding(UserPrincipal principal, Long tagId, String assetType, Long assetId,
                                     String source, BigDecimal confidence, String actionType,
                                     String conflictPolicy, String note) {
        IngAssetTagBinding exists = bindingMapper.selectOne(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getTagId, tagId)
                .eq(IngAssetTagBinding::getAssetType, assetType)
                .eq(IngAssetTagBinding::getAssetId, assetId)
                .last("LIMIT 1"));
        String confirm = "AUTO".equalsIgnoreCase(actionType) ? "CONFIRMED" : "SUGGESTED";
        if ("MANUAL".equalsIgnoreCase(source)) confirm = "CONFIRMED";
        if (exists != null) {
            if ("KEEP_MANUAL".equalsIgnoreCase(conflictPolicy)
                    && "MANUAL".equalsIgnoreCase(exists.getSource())
                    && !"MANUAL".equalsIgnoreCase(source)) {
                return ApplyResult.SKIPPED;
            }
            if ("CONFIRMED".equals(exists.getConfirmStatus())
                    && "KEEP_MANUAL".equalsIgnoreCase(conflictPolicy)
                    && !"MANUAL".equalsIgnoreCase(source)
                    && !"OVERRIDE".equalsIgnoreCase(conflictPolicy)) {
                return ApplyResult.SKIPPED;
            }
            String before = toJson(exists);
            if ("OVERRIDE".equalsIgnoreCase(conflictPolicy) || "PRIORITY".equalsIgnoreCase(conflictPolicy)
                    || "MANUAL".equalsIgnoreCase(source)
                    || "REJECTED".equals(exists.getConfirmStatus())
                    || "SUGGESTED".equals(exists.getConfirmStatus())) {
                exists.setSource(source);
                exists.setConfidence(confidence);
                exists.setConfirmStatus(confirm);
                exists.setTaggedBy(operatorName(principal));
                exists.setNote(note);
                bindingMapper.updateById(exists);
                audit(principal, "BIND", tagId, assetType, assetId, before, toJson(exists), source);
                return "CONFIRMED".equals(confirm) ? ApplyResult.APPLIED_AUTO : ApplyResult.APPLIED_SUGGEST;
            }
            return ApplyResult.EXISTS;
        }
        IngAssetTagBinding b = new IngAssetTagBinding();
        b.setTagId(tagId);
        b.setAssetType(assetType);
        b.setAssetId(assetId);
        b.setSource(source);
        b.setConfidence(confidence);
        b.setConfirmStatus(confirm);
        b.setTaggedBy(operatorName(principal));
        b.setNote(note);
        b.setCreatedAt(LocalDateTime.now());
        bindingMapper.insert(b);
        audit(principal, "BIND", tagId, assetType, assetId, null, toJson(b), source);
        return "CONFIRMED".equals(confirm) ? ApplyResult.APPLIED_AUTO : ApplyResult.APPLIED_SUGGEST;
    }

    private List<Map<String, Object>> matchRule(IngTagRule rule, int limit, boolean forApply) {
        Map<String, Object> cond = parseCond(rule.getConditionJson());
        String nameRegex = str(cond.get("nameRegex"), "");
        @SuppressWarnings("unchecked")
        List<String> keywords = cond.get("commentKeywords") instanceof List
                ? ((List<?>) cond.get("commentKeywords")).stream().map(String::valueOf).toList()
                : List.of();
        @SuppressWarnings("unchecked")
        List<String> dataTypes = cond.get("dataTypes") instanceof List
                ? ((List<?>) cond.get("dataTypes")).stream().map(String::valueOf).toList()
                : List.of();
        @SuppressWarnings("unchecked")
        List<String> assetTypes = cond.get("assetTypes") instanceof List
                ? ((List<?>) cond.get("assetTypes")).stream().map(o -> String.valueOf(o).toUpperCase(Locale.ROOT)).toList()
                : List.of("TABLE");
        double minConf = cond.get("minConfidence") == null ? 60 : Double.parseDouble(String.valueOf(cond.get("minConfidence")));
        Pattern pattern = null;
        if (!nameRegex.isBlank()) {
            try {
                pattern = Pattern.compile(nameRegex, Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                throw new BusinessException(400, "nameRegex 非法: " + e.getMessage());
            }
        }
        List<Map<String, Object>> hits = new ArrayList<>();
        if (assetTypes.contains("TABLE")) {
            List<IngDataTable> tables = tableMapper.selectList(new LambdaQueryWrapper<IngDataTable>()
                    .orderByDesc(IngDataTable::getId).last("LIMIT 2000"));
            for (IngDataTable t : tables) {
                String text = (nullToEmpty(t.getTableCode()) + " " + nullToEmpty(t.getTableName())).toLowerCase(Locale.ROOT);
                double score = scoreMatch(text, nullToEmpty(t.getTableName()), pattern, keywords);
                if (score >= minConf) {
                    hits.add(hitRow("TABLE", t.getId(), t.getTableCode(), t.getTableName(), score, rule));
                    if (hits.size() >= limit) return hits;
                }
            }
        }
        if (assetTypes.contains("COLUMN") && hits.size() < limit) {
            List<IngDataColumn> cols = columnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                    .orderByDesc(IngDataColumn::getId).last("LIMIT 3000"));
            for (IngDataColumn c : cols) {
                if (!dataTypes.isEmpty() && c.getDataType() != null) {
                    boolean dtOk = dataTypes.stream().anyMatch(d -> c.getDataType().toLowerCase(Locale.ROOT).contains(d.toLowerCase(Locale.ROOT)));
                    if (!dtOk) continue;
                }
                String text = (nullToEmpty(c.getColumnCode()) + " " + nullToEmpty(c.getColumnName())
                        + " " + nullToEmpty(c.getSemanticDesc())).toLowerCase(Locale.ROOT);
                double score = scoreMatch(text, nullToEmpty(c.getSemanticDesc()), pattern, keywords);
                if (score >= minConf) {
                    hits.add(hitRow("COLUMN", c.getId(), c.getColumnCode(), c.getColumnName(), score, rule));
                    if (hits.size() >= limit) return hits;
                }
            }
        }
        return hits;
    }

    private Map<String, Object> hitRow(String type, Long id, String code, String name, double score, IngTagRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("assetType", type);
        row.put("assetId", id);
        row.put("assetCode", code);
        row.put("assetName", name);
        row.put("confidence", Math.round(score * 10) / 10.0);
        row.put("tagId", rule.getTagId());
        row.put("ruleCode", rule.getRuleCode());
        row.put("actionType", rule.getActionType());
        return row;
    }

    private double scoreMatch(String text, String comment, Pattern pattern, List<String> keywords) {
        double score = 0;
        if (pattern != null && pattern.matcher(text).find()) score += 70;
        if (keywords != null) {
            for (String kw : keywords) {
                if (kw == null || kw.isBlank()) continue;
                String k = kw.toLowerCase(Locale.ROOT);
                if (text.contains(k) || (comment != null && comment.toLowerCase(Locale.ROOT).contains(k))) {
                    score += 15;
                }
            }
        }
        return Math.min(score, 99);
    }

    private void assertTagBindable(Long tagId) {
        IngAssetTag tag = tagMapper.selectById(tagId);
        if (tag == null) throw new BusinessException(404, "标签不存在");
        if (tag.getLevel() != null && tag.getLevel() == 1) {
            throw new BusinessException(400, "请选择二级类目或业务标签挂标，一级类目仅作分组");
        }
        if ("INACTIVE".equals(tag.getStatus())) throw new BusinessException(400, "标签已停用");
    }

    private void assertAssetExists(String assetType, Long assetId) {
        if ("TABLE".equals(assetType)) {
            if (tableMapper.selectById(assetId) == null) throw new BusinessException(404, "数据表不存在");
        } else if ("COLUMN".equals(assetType)) {
            if (columnMapper.selectById(assetId) == null) throw new BusinessException(404, "字段不存在");
        } else {
            throw new BusinessException(400, "assetType 仅支持 TABLE 或 COLUMN");
        }
    }

    private void enrichBindings(List<IngAssetTagBinding> list) {
        for (IngAssetTagBinding b : list) {
            IngAssetTag tag = tagMapper.selectById(b.getTagId());
            if (tag != null) {
                b.setTagName(tag.getTagName());
                b.setTagCode(tag.getTagCode());
                b.setStdCode(tag.getStdCode());
                b.setDimType(tag.getDimType());
            }
            if ("TABLE".equals(b.getAssetType())) {
                IngDataTable t = tableMapper.selectById(b.getAssetId());
                if (t != null) {
                    b.setAssetLabel(Objects.toString(t.getTableName(), t.getTableCode()));
                    b.setAssetCode(t.getTableCode());
                }
            } else if ("COLUMN".equals(b.getAssetType())) {
                IngDataColumn c = columnMapper.selectById(b.getAssetId());
                if (c != null) {
                    b.setAssetLabel(Objects.toString(c.getColumnName(), c.getColumnCode()));
                    b.setAssetCode(c.getColumnCode());
                }
            }
        }
    }

    private void audit(UserPrincipal p, String action, Long tagId, String assetType, Long assetId,
                       String before, String after, String source) {
        IngTagAuditLog logRow = new IngTagAuditLog();
        logRow.setActionType(action);
        logRow.setTagId(tagId);
        logRow.setAssetType(assetType);
        logRow.setAssetId(assetId);
        logRow.setBeforeJson(before);
        logRow.setAfterJson(after);
        logRow.setSource(source);
        if (p != null) {
            logRow.setOperatorId(p.getUserId());
            logRow.setOperatorName(operatorName(p));
        }
        logRow.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(logRow);
    }

    private String operatorName(UserPrincipal p) {
        if (p == null) return "system";
        if (p.getDisplayName() != null && !p.getDisplayName().isBlank()) return p.getDisplayName();
        return p.getUsername();
    }

    private Map<String, Object> parseCond(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return OM.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException(400, "conditionJson 非法: " + e.getMessage());
        }
    }

    private void validateJson(String json) {
        parseCond(json);
    }

    private String toJson(Object o) {
        try {
            return OM.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private Set<Long> parseIds(String csv) {
        Set<Long> set = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) return set;
        for (String p : csv.split("[,;\\s]+")) {
            if (p.isBlank()) continue;
            try { set.add(Long.valueOf(p.trim())); } catch (Exception ignored) { }
        }
        return set;
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? def : s;
    }

    private static Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank() || "null".equalsIgnoreCase(String.valueOf(v))) return null;
        return Long.valueOf(String.valueOf(v));
    }

    private static int intVal(Object v, int def) {
        if (v == null || String.valueOf(v).isBlank()) return def;
        return Integer.parseInt(String.valueOf(v));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
