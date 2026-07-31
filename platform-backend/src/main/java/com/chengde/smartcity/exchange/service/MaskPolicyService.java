package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngMaskAuditLog;
import com.chengde.smartcity.exchange.entity.IngMaskBinding;
import com.chengde.smartcity.exchange.entity.IngMaskCryptoRef;
import com.chengde.smartcity.exchange.entity.IngMaskPolicy;
import com.chengde.smartcity.exchange.entity.IngMaskRule;
import com.chengde.smartcity.exchange.mapper.IngMaskAuditLogMapper;
import com.chengde.smartcity.exchange.mapper.IngMaskBindingMapper;
import com.chengde.smartcity.exchange.mapper.IngMaskCryptoRefMapper;
import com.chengde.smartcity.exchange.mapper.IngMaskPolicyMapper;
import com.chengde.smartcity.exchange.mapper.IngMaskRuleMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 脱敏规则库 / 策略编排 / 预览执行 / 审计。
 * 加密仅通过 crypto_ref 别名派生演示密钥，生产须对接 KMS，禁止硬编码密钥材料。
 */
@Service
public class MaskPolicyService {

    private static final Logger log = LoggerFactory.getLogger(MaskPolicyService.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private final IngMaskRuleMapper ruleMapper;
    private final IngMaskPolicyMapper policyMapper;
    private final IngMaskBindingMapper bindingMapper;
    private final IngMaskCryptoRefMapper cryptoMapper;
    private final IngMaskAuditLogMapper auditMapper;

    public MaskPolicyService(
            IngMaskRuleMapper ruleMapper,
            IngMaskPolicyMapper policyMapper,
            IngMaskBindingMapper bindingMapper,
            IngMaskCryptoRefMapper cryptoMapper,
            IngMaskAuditLogMapper auditMapper) {
        this.ruleMapper = ruleMapper;
        this.policyMapper = policyMapper;
        this.bindingMapper = bindingMapper;
        this.cryptoMapper = cryptoMapper;
        this.auditMapper = auditMapper;
    }

    public List<IngMaskRule> listRules(String keyword) {
        LambdaQueryWrapper<IngMaskRule> q = new LambdaQueryWrapper<IngMaskRule>().orderByDesc(IngMaskRule::getUpdateTime);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(IngMaskRule::getRuleName, keyword).or().like(IngMaskRule::getRuleCode, keyword));
        }
        return ruleMapper.selectList(q);
    }

    @Transactional
    public Long saveRule(Map<String, Object> body) {
        String code = str(body.get("ruleCode"), "");
        String name = str(body.get("ruleName"), "");
        String algo = str(body.get("algoType"), "");
        if (code.isBlank() || name.isBlank() || algo.isBlank()) {
            throw new BusinessException(400, "规则编码、名称与算法不能为空");
        }
        Long id = longVal(body.get("id"));
        IngMaskRule row = id == null ? new IngMaskRule() : ruleMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "规则不存在");
        if (id == null) {
            if (ruleMapper.selectCount(new LambdaQueryWrapper<IngMaskRule>().eq(IngMaskRule::getRuleCode, code)) > 0) {
                throw new BusinessException(400, "规则编码已存在");
            }
            row.setRuleCode(code);
            row.setVersionNo(1);
            row.setCreateTime(LocalDateTime.now());
        } else {
            row.setVersionNo((row.getVersionNo() == null ? 1 : row.getVersionNo()) + 1);
        }
        row.setRuleName(name);
        row.setAlgoType(algo);
        row.setParamJson(str(body.get("paramJson"), row.getParamJson() == null ? "{}" : row.getParamJson()));
        row.setReversible(boolInt(body.get("reversible"), row.getReversible() == null ? 0 : row.getReversible()));
        row.setMatchFieldPattern(str(body.get("matchFieldPattern"), row.getMatchFieldPattern()));
        row.setMatchDataType(str(body.get("matchDataType"), row.getMatchDataType()));
        row.setMatchSensitiveTag(str(body.get("matchSensitiveTag"), row.getMatchSensitiveTag()));
        row.setFailPolicy(str(body.get("failPolicy"), row.getFailPolicy() == null ? "DENY" : row.getFailPolicy()));
        row.setStatus(str(body.get("status"), row.getStatus() == null ? "ACTIVE" : row.getStatus()));
        row.setDescription(str(body.get("description"), row.getDescription()));
        if (id == null) ruleMapper.insert(row);
        else ruleMapper.updateById(row);
        return row.getId();
    }

    @Transactional
    public Long cloneRule(Long id) {
        IngMaskRule src = ruleMapper.selectById(id);
        if (src == null) throw new BusinessException(404, "规则不存在");
        IngMaskRule copy = new IngMaskRule();
        copy.setRuleCode(src.getRuleCode() + "_COPY_" + System.currentTimeMillis() % 100000);
        copy.setRuleName(src.getRuleName() + "（克隆）");
        copy.setAlgoType(src.getAlgoType());
        copy.setParamJson(src.getParamJson());
        copy.setReversible(src.getReversible());
        copy.setMatchFieldPattern(src.getMatchFieldPattern());
        copy.setMatchDataType(src.getMatchDataType());
        copy.setMatchSensitiveTag(src.getMatchSensitiveTag());
        copy.setFailPolicy(src.getFailPolicy());
        copy.setVersionNo(1);
        copy.setStatus("ACTIVE");
        copy.setDescription(src.getDescription());
        copy.setCreateTime(LocalDateTime.now());
        ruleMapper.insert(copy);
        return copy.getId();
    }

    @Transactional
    public void deleteRule(Long id) {
        ruleMapper.deleteById(id);
    }

    public Map<String, Object> previewRule(Long ruleId, String sample) {
        IngMaskRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) throw new BusinessException(404, "规则不存在");
        String out = applyRule(rule, "sample", sample == null ? "" : sample, false);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("before", sample);
        res.put("after", out);
        res.put("algoType", rule.getAlgoType());
        res.put("reversible", Objects.equals(rule.getReversible(), 1));
        return res;
    }

    public List<IngMaskPolicy> listPolicies(String sceneCode, String status) {
        LambdaQueryWrapper<IngMaskPolicy> q = new LambdaQueryWrapper<IngMaskPolicy>()
                .orderByAsc(IngMaskPolicy::getPriority)
                .orderByDesc(IngMaskPolicy::getVersionNo);
        if (sceneCode != null && !sceneCode.isBlank()) q.eq(IngMaskPolicy::getSceneCode, sceneCode);
        if (status != null && !status.isBlank()) q.eq(IngMaskPolicy::getStatus, status);
        return policyMapper.selectList(q);
    }

    @Transactional
    public Long savePolicy(UserPrincipal user, Map<String, Object> body) {
        String code = str(body.get("policyCode"), "");
        String name = str(body.get("policyName"), "");
        String scene = str(body.get("sceneCode"), "");
        if (code.isBlank() || name.isBlank() || scene.isBlank()) {
            throw new BusinessException(400, "策略编码、名称与场景不能为空");
        }
        Long id = longVal(body.get("id"));
        boolean asNewVersion = bool(body.get("newVersion"));
        IngMaskPolicy row;
        if (id != null && asNewVersion) {
            IngMaskPolicy src = policyMapper.selectById(id);
            if (src == null) throw new BusinessException(404, "策略不存在");
            row = new IngMaskPolicy();
            row.setPolicyCode(src.getPolicyCode());
            row.setVersionNo((src.getVersionNo() == null ? 1 : src.getVersionNo()) + 1);
            row.setCreateTime(LocalDateTime.now());
            row.setStatus("DRAFT");
        } else if (id == null) {
            row = new IngMaskPolicy();
            row.setPolicyCode(code);
            row.setVersionNo(1);
            row.setCreateTime(LocalDateTime.now());
            row.setStatus("DRAFT");
        } else {
            row = policyMapper.selectById(id);
            if (row == null) throw new BusinessException(404, "策略不存在");
        }
        row.setPolicyName(name);
        row.setSceneCode(scene);
        row.setMatchLevelCode(blankToNull(str(body.get("matchLevelCode"), "")));
        row.setMatchCategoryId(longVal(body.get("matchCategoryId")));
        row.setMatchRole(blankToNull(str(body.get("matchRole"), "")));
        row.setMatchPurpose(blankToNull(str(body.get("matchPurpose"), "")));
        row.setMatchEnv(str(body.get("matchEnv"), row.getMatchEnv() == null ? "PROD" : row.getMatchEnv()));
        row.setPriority(intVal(body.get("priority"), row.getPriority() == null ? 100 : row.getPriority()));
        row.setStrictMode(boolInt(body.get("strictMode"), row.getStrictMode() == null ? 1 : row.getStrictMode()));
        row.setRuleIdsJson(str(body.get("ruleIdsJson"), row.getRuleIdsJson() == null ? "[]" : row.getRuleIdsJson()));
        row.setFieldOverridesJson(str(body.get("fieldOverridesJson"), row.getFieldOverridesJson()));
        row.setDescription(str(body.get("description"), row.getDescription()));
        if (body.get("status") != null && id != null && !asNewVersion) {
            row.setStatus(str(body.get("status"), row.getStatus()));
        }
        if (row.getId() == null) policyMapper.insert(row);
        else policyMapper.updateById(row);
        writeAudit(user, "CONFIG", row.getId(), row.getVersionNo(), null, null, null, null,
                truncate(sample(row.getRuleIdsJson()), 200), truncate(row.getPolicyName(), 200), "OK", null);
        return row.getId();
    }

    @Transactional
    public void publishPolicy(UserPrincipal user, Long id) {
        IngMaskPolicy p = policyMapper.selectById(id);
        if (p == null) throw new BusinessException(404, "策略不存在");
        // 同编码其它 ACTIVE 停用，保留版本
        List<IngMaskPolicy> same = policyMapper.selectList(new LambdaQueryWrapper<IngMaskPolicy>()
                .eq(IngMaskPolicy::getPolicyCode, p.getPolicyCode())
                .eq(IngMaskPolicy::getStatus, "ACTIVE"));
        for (IngMaskPolicy o : same) {
            if (!Objects.equals(o.getId(), id)) {
                o.setStatus("INACTIVE");
                policyMapper.updateById(o);
            }
        }
        p.setStatus("ACTIVE");
        p.setPublishedAt(LocalDateTime.now());
        policyMapper.updateById(p);
        writeAudit(user, "PUBLISH", p.getId(), p.getVersionNo(), null, null, p.getSceneCode(), null,
                null, null, "OK", "{\"policyCode\":\"" + p.getPolicyCode() + "\"}");
    }

    @Transactional
    public Long rollbackPolicy(UserPrincipal user, Long historyId) {
        IngMaskPolicy hist = policyMapper.selectById(historyId);
        if (hist == null) throw new BusinessException(404, "历史版本不存在");
        IngMaskPolicy neu = new IngMaskPolicy();
        neu.setPolicyCode(hist.getPolicyCode());
        neu.setPolicyName(hist.getPolicyName());
        neu.setSceneCode(hist.getSceneCode());
        neu.setMatchLevelCode(hist.getMatchLevelCode());
        neu.setMatchCategoryId(hist.getMatchCategoryId());
        neu.setMatchRole(hist.getMatchRole());
        neu.setMatchPurpose(hist.getMatchPurpose());
        neu.setMatchEnv(hist.getMatchEnv());
        neu.setPriority(hist.getPriority());
        neu.setStrictMode(hist.getStrictMode());
        neu.setRuleIdsJson(hist.getRuleIdsJson());
        neu.setFieldOverridesJson(hist.getFieldOverridesJson());
        neu.setDescription("回滚自版本 " + hist.getVersionNo());
        Integer maxVer = policyMapper.selectList(new LambdaQueryWrapper<IngMaskPolicy>()
                        .eq(IngMaskPolicy::getPolicyCode, hist.getPolicyCode())
                        .orderByDesc(IngMaskPolicy::getVersionNo)
                        .last("LIMIT 1"))
                .stream().findFirst().map(IngMaskPolicy::getVersionNo).orElse(1);
        neu.setVersionNo(maxVer + 1);
        neu.setStatus("DRAFT");
        neu.setCreateTime(LocalDateTime.now());
        policyMapper.insert(neu);
        writeAudit(user, "RESTORE", neu.getId(), neu.getVersionNo(), null, null, hist.getSceneCode(), 1,
                "v" + hist.getVersionNo(), "v" + neu.getVersionNo(), "OK", null);
        return neu.getId();
    }

    public List<IngMaskBinding> listBindings() {
        return bindingMapper.selectList(new LambdaQueryWrapper<IngMaskBinding>().orderByDesc(IngMaskBinding::getId));
    }

    @Transactional
    public Long saveBinding(Map<String, Object> body) {
        String code = str(body.get("bindingCode"), "");
        Long policyId = longVal(body.get("policyId"));
        String targetType = str(body.get("targetType"), "TABLE");
        if (code.isBlank() || policyId == null) throw new BusinessException(400, "绑定编码与策略不能为空");
        Long id = longVal(body.get("id"));
        IngMaskBinding row = id == null ? new IngMaskBinding() : bindingMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "绑定不存在");
        if (id == null) {
            if (bindingMapper.selectCount(new LambdaQueryWrapper<IngMaskBinding>().eq(IngMaskBinding::getBindingCode, code)) > 0) {
                throw new BusinessException(400, "绑定编码已存在");
            }
            row.setBindingCode(code);
            row.setCreateTime(LocalDateTime.now());
        }
        row.setPolicyId(policyId);
        row.setTargetType(targetType);
        row.setTargetId(longVal(body.get("targetId")));
        row.setTargetCode(str(body.get("targetCode"), row.getTargetCode()));
        row.setTargetName(str(body.get("targetName"), row.getTargetName()));
        row.setStatus(str(body.get("status"), "ACTIVE"));
        if (id == null) bindingMapper.insert(row);
        else bindingMapper.updateById(row);
        return row.getId();
    }

    @Transactional
    public void deleteBinding(Long id) {
        bindingMapper.deleteById(id);
    }

    public List<IngMaskCryptoRef> listCryptoRefs() {
        return cryptoMapper.selectList(new LambdaQueryWrapper<IngMaskCryptoRef>().orderByAsc(IngMaskCryptoRef::getId));
    }

    @Transactional
    public Long saveCryptoRef(Map<String, Object> body) {
        String code = str(body.get("refCode"), "");
        String name = str(body.get("refName"), "");
        String alias = str(body.get("keyAlias"), "");
        if (code.isBlank() || name.isBlank() || alias.isBlank()) {
            throw new BusinessException(400, "引用编码、名称与密钥别名不能为空");
        }
        if (alias.toLowerCase(Locale.ROOT).contains("password=") || alias.length() > 256) {
            throw new BusinessException(400, "请使用 KMS/密钥别名，禁止填写明文密钥材料");
        }
        Long id = longVal(body.get("id"));
        IngMaskCryptoRef row = id == null ? new IngMaskCryptoRef() : cryptoMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "密钥引用不存在");
        if (id == null) {
            if (cryptoMapper.selectCount(new LambdaQueryWrapper<IngMaskCryptoRef>().eq(IngMaskCryptoRef::getRefCode, code)) > 0) {
                throw new BusinessException(400, "引用编码已存在");
            }
            row.setRefCode(code);
            row.setCreateTime(LocalDateTime.now());
        }
        row.setRefName(name);
        row.setAlgo(str(body.get("algo"), "AES_GCM"));
        row.setKeyAlias(alias);
        row.setRotateDays(intVal(body.get("rotateDays"), 90));
        row.setOwnerOrg(str(body.get("ownerOrg"), row.getOwnerOrg()));
        row.setStatus(str(body.get("status"), "ACTIVE"));
        row.setRemark(str(body.get("remark"), row.getRemark()));
        if (id == null) cryptoMapper.insert(row);
        else cryptoMapper.updateById(row);
        return row.getId();
    }

    /**
     * 匹配策略并预览/应用一行字段。
     */
    @Transactional
    public Map<String, Object> apply(UserPrincipal user, Map<String, Object> body, boolean preview) {
        String scene = str(body.get("sceneCode"), "QUERY");
        String level = blankToNull(str(body.get("levelCode"), ""));
        String role = blankToNull(str(body.get("roleCode"), ""));
        String purpose = blankToNull(str(body.get("purpose"), ""));
        String env = str(body.get("env"), "PROD");
        boolean allowRestore = bool(body.get("allowRestore"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = body.get("fields") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of("sample", str(body.get("sample"), "13812345678"));

        IngMaskPolicy policy = resolvePolicy(scene, level, role, purpose, env);
        if (policy == null) {
            Map<String, Object> none = new LinkedHashMap<>();
            none.put("matched", false);
            none.put("reason", "无匹配已发布策略，返回原值（生产应拦截未配置场景）");
            none.put("fields", fields);
            return none;
        }

        List<Long> ruleIds = parseIdList(policy.getRuleIdsJson());
        Map<String, Long> overrides = parseOverrides(policy.getFieldOverridesJson());
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> applied = new ArrayList<>();
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            String field = e.getKey();
            String raw = e.getValue() == null ? "" : String.valueOf(e.getValue());
            IngMaskRule rule = pickRule(field, ruleIds, overrides);
            String after;
            if (rule == null) {
                after = raw;
            } else if (Objects.equals(rule.getReversible(), 1) && allowRestore) {
                if (!userCanRestore(user)) {
                    throw new BusinessException(403, "还原/解密需系统管理员或明确授权，并记入高敏审计");
                }
                after = applyRule(rule, field, raw, true);
                writeAudit(user, "RESTORE", policy.getId(), policy.getVersionNo(), rule.getId(), null, scene, 1,
                        truncate(raw, 64), truncate(after, 64), "OK", null);
            } else {
                after = applyRule(rule, field, raw, false);
            }
            out.put(field, after);
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("field", field);
            one.put("ruleId", rule == null ? null : rule.getId());
            one.put("ruleCode", rule == null ? null : rule.getRuleCode());
            one.put("algoType", rule == null ? null : rule.getAlgoType());
            one.put("before", truncate(raw, 64));
            one.put("after", truncate(after, 64));
            applied.add(one);
        }

        writeAudit(user, preview ? "PREVIEW" : "APPLY", policy.getId(), policy.getVersionNo(), null, null, scene, 0,
                truncate(String.valueOf(fields), 120), truncate(String.valueOf(out), 120), "OK", null);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("matched", true);
        res.put("policyId", policy.getId());
        res.put("policyCode", policy.getPolicyCode());
        res.put("policyVersion", policy.getVersionNo());
        res.put("policyName", policy.getPolicyName());
        res.put("sceneCode", policy.getSceneCode());
        res.put("fields", out);
        res.put("details", applied);
        return res;
    }

    public List<IngMaskAuditLog> listAudit(int limit) {
        return auditMapper.selectList(new LambdaQueryWrapper<IngMaskAuditLog>()
                .orderByDesc(IngMaskAuditLog::getCreatedAt)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
    }

    public Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ruleCount", ruleMapper.selectCount(new LambdaQueryWrapper<IngMaskRule>().eq(IngMaskRule::getStatus, "ACTIVE")));
        out.put("policyActive", policyMapper.selectCount(new LambdaQueryWrapper<IngMaskPolicy>().eq(IngMaskPolicy::getStatus, "ACTIVE")));
        out.put("bindingCount", bindingMapper.selectCount(new LambdaQueryWrapper<IngMaskBinding>().eq(IngMaskBinding::getStatus, "ACTIVE")));
        out.put("cryptoRefCount", cryptoMapper.selectCount(new LambdaQueryWrapper<IngMaskCryptoRef>().eq(IngMaskCryptoRef::getStatus, "ACTIVE")));
        return out;
    }

    private IngMaskPolicy resolvePolicy(String scene, String level, String role, String purpose, String env) {
        List<IngMaskPolicy> list = policyMapper.selectList(new LambdaQueryWrapper<IngMaskPolicy>()
                .eq(IngMaskPolicy::getStatus, "ACTIVE")
                .eq(IngMaskPolicy::getSceneCode, scene)
                .eq(IngMaskPolicy::getMatchEnv, env));
        List<IngMaskPolicy> matched = new ArrayList<>();
        for (IngMaskPolicy p : list) {
            if (p.getMatchLevelCode() != null && level != null && !p.getMatchLevelCode().equals(level)) continue;
            if (p.getMatchLevelCode() != null && level == null) continue;
            if (p.getMatchRole() != null && role != null && !p.getMatchRole().equals(role)) continue;
            if (p.getMatchPurpose() != null && purpose != null && !p.getMatchPurpose().equals(purpose)) continue;
            matched.add(p);
        }
        if (matched.isEmpty()) {
            // 回退：同场景无级别限制的策略
            for (IngMaskPolicy p : list) {
                if (p.getMatchLevelCode() == null && p.getMatchRole() == null) matched.add(p);
            }
        }
        if (matched.isEmpty()) return null;
        matched.sort(Comparator.comparingInt(p -> p.getPriority() == null ? 100 : p.getPriority()));
        if (matched.size() == 1 || !Objects.equals(matched.get(0).getStrictMode(), 1)) {
            return matched.get(0);
        }
        // 从严：同优先级取规则更多 / 级别更严的
        IngMaskPolicy best = matched.get(0);
        int bestScore = severityScore(best);
        for (IngMaskPolicy p : matched) {
            int s = severityScore(p);
            if (s > bestScore) {
                best = p;
                bestScore = s;
            }
        }
        return best;
    }

    private int severityScore(IngMaskPolicy p) {
        int s = 0;
        if ("CORE".equals(p.getMatchLevelCode())) s += 30;
        else if ("IMPORTANT".equals(p.getMatchLevelCode())) s += 20;
        else if ("GENERAL".equals(p.getMatchLevelCode())) s += 5;
        s += parseIdList(p.getRuleIdsJson()).size();
        return s;
    }

    private IngMaskRule pickRule(String field, List<Long> ruleIds, Map<String, Long> overrides) {
        if (overrides != null && overrides.containsKey(field)) {
            return ruleMapper.selectById(overrides.get(field));
        }
        for (Long rid : ruleIds) {
            IngMaskRule r = ruleMapper.selectById(rid);
            if (r == null || !"ACTIVE".equals(r.getStatus())) continue;
            if (matchField(r.getMatchFieldPattern(), field)) return r;
        }
        // 无字段匹配时用第一条通用规则（无 pattern）
        for (Long rid : ruleIds) {
            IngMaskRule r = ruleMapper.selectById(rid);
            if (r != null && "ACTIVE".equals(r.getStatus())
                    && (r.getMatchFieldPattern() == null || r.getMatchFieldPattern().isBlank())) {
                return r;
            }
        }
        return null;
    }

    private boolean matchField(String pattern, String field) {
        if (pattern == null || pattern.isBlank()) return false;
        String f = field.toLowerCase(Locale.ROOT);
        for (String part : pattern.toLowerCase(Locale.ROOT).split("\\|")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            if (p.startsWith("*") && p.endsWith("*") && p.length() > 2) {
                if (f.contains(p.substring(1, p.length() - 1))) return true;
            } else if (p.startsWith("*")) {
                if (f.endsWith(p.substring(1))) return true;
            } else if (p.endsWith("*")) {
                if (f.startsWith(p.substring(0, p.length() - 1))) return true;
            } else if (f.equals(p)) {
                return true;
            }
        }
        return false;
    }

    String applyRule(IngMaskRule rule, String field, String value, boolean restore) {
        if (value == null) return null;
        try {
            Map<String, Object> params = parseMap(rule.getParamJson());
            return switch (str(rule.getAlgoType(), "MASK")) {
                case "MASK" -> mask(value, intVal(params.get("keepPrefix"), 3), intVal(params.get("keepSuffix"), 4),
                        str(params.get("maskChar"), "*"));
                case "REPLACE", "CONSTANT" -> str(params.get("replacement"), "***");
                case "TRUNCATE" -> truncate(value, intVal(params.get("maxLen"), 6));
                case "GENERALIZE" -> generalize(value, str(params.get("level"), "district"));
                case "HASH" -> hash(value, str(params.get("saltRef"), "DEFAULT_SALT"));
                case "CLEAR" -> "";
                case "ENCRYPT", "FPE" -> restore ? decryptDemo(value, str(params.get("cryptoRefCode"), "CRYPTO_DEFAULT"))
                        : encryptDemo(value, str(params.get("cryptoRefCode"), "CRYPTO_DEFAULT"));
                default -> value;
            };
        } catch (Exception e) {
            log.warn("mask rule {} failed on {}: {}", rule.getRuleCode(), field, e.getMessage());
            return switch (str(rule.getFailPolicy(), "DENY")) {
                case "PASSTHROUGH" -> value;
                case "CLEAR" -> "";
                default -> throw new BusinessException(500, "脱敏失败：" + e.getMessage());
            };
        }
    }

    private String mask(String v, int prefix, int suffix, String ch) {
        if (v.length() <= prefix + suffix) return ch.repeat(Math.max(v.length(), 1));
        String mid = ch.repeat(v.length() - prefix - suffix);
        return v.substring(0, prefix) + mid + v.substring(v.length() - suffix);
    }

    private String generalize(String v, String level) {
        if ("yearMonth".equals(level) && v.length() >= 7) return v.substring(0, 7);
        // 地址示意：保留前 6 个字符 + …
        if (v.length() <= 6) return v;
        return v.substring(0, 6) + "…";
    }

    private String hash(String v, String saltRef) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(saltRef.getBytes(StandardCharsets.UTF_8));
        md.update(v.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(md.digest());
    }

    /** 演示用：由 key_alias 派生密钥，生产必须换 KMS */
    private byte[] deriveKey(String cryptoRefCode) throws Exception {
        IngMaskCryptoRef ref = cryptoMapper.selectOne(new LambdaQueryWrapper<IngMaskCryptoRef>()
                .eq(IngMaskCryptoRef::getRefCode, cryptoRefCode));
        String alias = ref == null ? "kms://local/demo" : ref.getKeyAlias();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(("MASK_DEMO|" + alias).getBytes(StandardCharsets.UTF_8));
    }

    private String encryptDemo(String plain, String cryptoRefCode) throws Exception {
        byte[] key = deriveKey(cryptoRefCode);
        byte[] iv = MessageDigest.getInstance("MD5").digest(plain.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return "ENC:" + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(enc);
    }

    private String decryptDemo(String cipherText, String cryptoRefCode) throws Exception {
        if (cipherText == null || !cipherText.startsWith("ENC:")) return cipherText;
        String[] parts = cipherText.split(":", 3);
        if (parts.length < 3) return cipherText;
        byte[] iv = Base64.getDecoder().decode(parts[1]);
        byte[] enc = Base64.getDecoder().decode(parts[2]);
        byte[] key = deriveKey(cryptoRefCode);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
    }

    private boolean userCanRestore(UserPrincipal user) {
        return user != null && user.isSystemAdmin();
    }

    private void writeAudit(UserPrincipal user, String action, Long policyId, Integer ver, Long ruleId,
                            Long targetId, String scene, Integer restored, String before, String after,
                            String result, String detail) {
        IngMaskAuditLog row = new IngMaskAuditLog();
        row.setActionType(action);
        row.setPolicyId(policyId);
        row.setPolicyVersion(ver);
        row.setRuleId(ruleId);
        row.setTargetId(targetId);
        row.setSceneCode(scene);
        row.setRestored(restored == null ? 0 : restored);
        row.setSampleBefore(before);
        row.setSampleAfter(after);
        row.setResult(result);
        row.setOperatorId(user == null ? null : user.getUserId());
        row.setOperatorName(user == null ? null : user.getUsername());
        row.setDetailJson(detail);
        row.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(row);
    }

    private List<Long> parseIdList(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            return OM.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Long> parseOverrides(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return OM.readValue(json, new TypeReference<Map<String, Long>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> parseMap(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return OM.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String sample(String s) {
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
