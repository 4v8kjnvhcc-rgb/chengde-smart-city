package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.UnsCleanIssue;
import com.chengde.smartcity.masterdata.entity.UnsCleanRule;
import com.chengde.smartcity.masterdata.entity.UnsDocPipeline;
import com.chengde.smartcity.masterdata.entity.UnsDocument;
import com.chengde.smartcity.masterdata.mapper.UnsCleanIssueMapper;
import com.chengde.smartcity.masterdata.mapper.UnsCleanRuleMapper;
import com.chengde.smartcity.masterdata.mapper.UnsDocPipelineMapper;
import com.chengde.smartcity.masterdata.mapper.UnsDocumentMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 非结构化数据清洗转换（M127）：规则定义、执行过滤/去重/校验/转换、问题库确认、一致性检查。
 * 引擎模式诚实标注 LEDGER（平台规则引擎，未接外部清洗软件）。
 */
@Service
public class UnstructuredCleanService {

    private static final Logger log = LoggerFactory.getLogger(UnstructuredCleanService.class);
    private static final ObjectMapper OM = new ObjectMapper();
    private static final Pattern MOBILE_LOOSE = Pattern.compile("(?:\\+?86[\\s-]?)?(1[3-9]\\d[\\s-]?\\d{4}[\\s-]?\\d{4})");
    private static final Pattern MOBILE_STRICT = Pattern.compile("^1[3-9]\\d{9}$");
    private static final java.util.Set<String> RULE_TYPES = java.util.Set.of("FILTER", "DEDUP", "VALIDATE", "TRANSFORM");
    private static final java.util.Set<String> ERROR_LEVELS = java.util.Set.of("INFO", "WARN", "ERROR", "CRITICAL");
    private static final java.util.Set<String> TARGET_FIELDS = java.util.Set.of("title", "description", "author", "tagJson", "mediaFormat");
    private static final java.util.Set<String> ISSUE_HANDLES = java.util.Set.of("CLEANED_IN", "ABANDONED", "OTHER");

    private final UnsCleanRuleMapper ruleMapper;
    private final UnsCleanIssueMapper issueMapper;
    private final UnsDocumentMapper documentMapper;
    private final UnsDocPipelineMapper pipelineMapper;
    private final AuditService auditService;

    public UnstructuredCleanService(UnsCleanRuleMapper ruleMapper, UnsCleanIssueMapper issueMapper,
                                    UnsDocumentMapper documentMapper, UnsDocPipelineMapper pipelineMapper,
                                    AuditService auditService) {
        this.ruleMapper = ruleMapper;
        this.issueMapper = issueMapper;
        this.documentMapper = documentMapper;
        this.pipelineMapper = pipelineMapper;
        this.auditService = auditService;
    }

    public List<UnsCleanRule> listRules() {
        return ruleMapper.selectList(new LambdaQueryWrapper<UnsCleanRule>()
                .orderByAsc(UnsCleanRule::getSortOrder)
                .orderByAsc(UnsCleanRule::getId));
    }

    @Transactional
    public Long createRule(UserPrincipal operator, Map<String, Object> body) {
        UnsCleanRule rule = new UnsCleanRule();
        applyRuleBody(rule, body, true);
        rule.setStatus("ACTIVE");
        ruleMapper.insert(rule);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_CLEAN_RULE_CREATE", "uns_clean_rule", String.valueOf(rule.getId()), rule.getRuleCode());
        return rule.getId();
    }

    @Transactional
    public void updateRule(UserPrincipal operator, Long id, Map<String, Object> body) {
        UnsCleanRule rule = getRule(id);
        applyRuleBody(rule, body, false);
        ruleMapper.updateById(rule);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_CLEAN_RULE_UPDATE", "uns_clean_rule", String.valueOf(id), rule.getRuleCode());
    }

    @Transactional
    public void deleteRule(UserPrincipal operator, Long id) {
        UnsCleanRule rule = getRule(id);
        ruleMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_CLEAN_RULE_DELETE", "uns_clean_rule", String.valueOf(id), rule.getRuleCode());
    }

    public List<Map<String, Object>> listIssues(String issueStatus, String errorLevel, Long docId) {
        LambdaQueryWrapper<UnsCleanIssue> q = new LambdaQueryWrapper<UnsCleanIssue>()
                .orderByDesc(UnsCleanIssue::getId);
        if (!blank(issueStatus)) {
            q.eq(UnsCleanIssue::getIssueStatus, issueStatus.trim().toUpperCase(Locale.ROOT));
        }
        if (!blank(errorLevel)) {
            q.eq(UnsCleanIssue::getErrorLevel, errorLevel.trim().toUpperCase(Locale.ROOT));
        }
        if (docId != null) {
            q.eq(UnsCleanIssue::getDocId, docId);
        }
        List<UnsCleanIssue> rows = issueMapper.selectList(q);
        Map<Long, String> titles = new java.util.HashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (UnsCleanIssue issue : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", issue.getId());
            row.put("docId", issue.getDocId());
            row.put("docTitle", titles.computeIfAbsent(issue.getDocId(), this::docTitle));
            row.put("ruleId", issue.getRuleId());
            row.put("ruleCode", issue.getRuleCode());
            row.put("pipelineId", issue.getPipelineId());
            row.put("targetField", issue.getTargetField());
            row.put("errorLevel", issue.getErrorLevel());
            row.put("issueStatus", issue.getIssueStatus());
            row.put("beforeValue", issue.getBeforeValue());
            row.put("afterValue", issue.getAfterValue());
            row.put("message", issue.getMessage());
            row.put("handleNote", issue.getHandleNote());
            row.put("handledBy", issue.getHandledBy());
            row.put("handledAt", issue.getHandledAt());
            row.put("createdAt", issue.getCreatedAt());
            out.add(row);
        }
        return out;
    }

    @Transactional
    public void handleIssue(UserPrincipal operator, Long id, Map<String, Object> body) {
        UnsCleanIssue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw new BusinessException(404, "问题数据不存在");
        }
        String action = str(body.get("action"), "").toUpperCase(Locale.ROOT);
        if (!ISSUE_HANDLES.contains(action)) {
            throw new BusinessException(400, "处置方式须为 CLEANED_IN / ABANDONED / OTHER");
        }
        issue.setIssueStatus(action);
        issue.setHandleNote(str(body.get("handleNote"), null));
        issue.setHandledBy(operator.getUsername());
        issue.setHandledAt(LocalDateTime.now());
        issueMapper.updateById(issue);

        UnsDocument doc = documentMapper.selectById(issue.getDocId());
        if (doc != null) {
            if ("ABANDONED".equals(action)) {
                doc.setProcessStatus("ABANDONED");
                documentMapper.updateById(doc);
            } else if ("CLEANED_IN".equals(action) && "PROBLEM".equalsIgnoreCase(doc.getProcessStatus())) {
                long open = issueMapper.selectCount(new LambdaQueryWrapper<UnsCleanIssue>()
                        .eq(UnsCleanIssue::getDocId, doc.getId())
                        .eq(UnsCleanIssue::getIssueStatus, "OPEN")
                        .in(UnsCleanIssue::getErrorLevel, List.of("ERROR", "CRITICAL")));
                if (open == 0) {
                    doc.setProcessStatus("CLEANED");
                    documentMapper.updateById(doc);
                }
            }
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_CLEAN_ISSUE_HANDLE", "uns_clean_issue", String.valueOf(id), action);
    }

    public Map<String, Object> cleanOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rules", ruleMapper.selectCount(new LambdaQueryWrapper<UnsCleanRule>().eq(UnsCleanRule::getEnabled, 1)));
        out.put("rulesTotal", ruleMapper.selectCount(null));
        out.put("cleaned", documentMapper.selectCount(new LambdaQueryWrapper<UnsDocument>()
                .eq(UnsDocument::getProcessStatus, "CLEANED")));
        out.put("problem", documentMapper.selectCount(new LambdaQueryWrapper<UnsDocument>()
                .eq(UnsDocument::getProcessStatus, "PROBLEM")));
        out.put("abandoned", documentMapper.selectCount(new LambdaQueryWrapper<UnsDocument>()
                .eq(UnsDocument::getProcessStatus, "ABANDONED")));
        out.put("openIssues", issueMapper.selectCount(new LambdaQueryWrapper<UnsCleanIssue>()
                .eq(UnsCleanIssue::getIssueStatus, "OPEN")));
        out.put("pipelines", pipelineMapper.selectCount(new LambdaQueryWrapper<UnsDocPipeline>()
                .eq(UnsDocPipeline::getPipelineType, "CLEAN")));
        out.put("tasks", pipelineMapper.selectCount(new LambdaQueryWrapper<UnsDocPipeline>()
                .eq(UnsDocPipeline::getPipelineType, "CLEAN")));
        out.put("engineMode", "LEDGER");
        return out;
    }

    @Transactional
    public Map<String, Object> runClean(UserPrincipal operator, UnsDocument doc) {
        Map<String, String> before = snapshot(doc);
        List<UnsCleanRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<UnsCleanRule>()
                .eq(UnsCleanRule::getEnabled, 1)
                .eq(UnsCleanRule::getStatus, "ACTIVE")
                .orderByAsc(UnsCleanRule::getSortOrder)
                .orderByAsc(UnsCleanRule::getId));

        List<Map<String, Object>> applied = new ArrayList<>();
        List<UnsCleanIssue> pendingIssues = new ArrayList<>();
        int errorCount = 0;
        int criticalCount = 0;

        for (UnsCleanRule rule : rules) {
            String field = normalizeField(rule.getTargetField());
            String beforeVal = fieldValue(doc, field);
            RuleHit hit = evaluate(rule, doc, beforeVal);
            if (!hit.hit) {
                continue;
            }
            String afterVal = hit.afterValue != null ? hit.afterValue : beforeVal;
            boolean auto = rule.getAutoApply() != null && rule.getAutoApply() == 1 && hit.changed;
            if (auto) {
                setFieldValue(doc, field, afterVal);
            }
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("ruleCode", rule.getRuleCode());
            step.put("ruleName", rule.getRuleName());
            step.put("ruleType", rule.getRuleType());
            step.put("targetField", field);
            step.put("errorLevel", rule.getErrorLevel());
            step.put("before", beforeVal);
            step.put("after", auto ? afterVal : beforeVal);
            step.put("autoApplied", auto);
            step.put("message", hit.message);
            applied.add(step);

            if (severityIssue(rule.getErrorLevel()) || "FILTER".equals(rule.getRuleType()) || "DEDUP".equals(rule.getRuleType())
                    || "VALIDATE".equals(rule.getRuleType())) {
                if (!"INFO".equalsIgnoreCase(rule.getErrorLevel()) || !auto) {
                    UnsCleanIssue issue = new UnsCleanIssue();
                    issue.setDocId(doc.getId());
                    issue.setRuleId(rule.getId());
                    issue.setRuleCode(rule.getRuleCode());
                    issue.setTargetField(field);
                    issue.setErrorLevel(str(rule.getErrorLevel(), "WARN"));
                    issue.setIssueStatus("OPEN");
                    issue.setBeforeValue(truncate(beforeVal));
                    issue.setAfterValue(truncate(auto ? afterVal : beforeVal));
                    issue.setMessage(hit.message);
                    pendingIssues.add(issue);
                }
            }
            if ("ERROR".equalsIgnoreCase(rule.getErrorLevel())) errorCount++;
            if ("CRITICAL".equalsIgnoreCase(rule.getErrorLevel())) criticalCount++;
        }

        Map<String, String> after = snapshot(doc);
        Map<String, Object> consistency = consistencyCheck(before, after, applied);
        boolean consistent = Boolean.TRUE.equals(consistency.get("passed"));

        UnsDocPipeline p = new UnsDocPipeline();
        p.setDocId(doc.getId());
        p.setPipelineType("CLEAN");
        p.setStatus("LEDGER");

        boolean hasBlocking = criticalCount > 0 || errorCount > 0;
        if (hasBlocking) {
            doc.setProcessStatus("PROBLEM");
        } else {
            doc.setProcessStatus("CLEANED");
        }
        documentMapper.updateById(doc);

        String msg = "台账清洗完成：命中规则 " + applied.size() + " 条，问题 " + pendingIssues.size()
                + " 条，阻塞级 " + (criticalCount + errorCount) + "；一致性检查"
                + (consistent ? "通过" : "未通过")
                + "（未接外部清洗软件）";
        p.setResultMessage(truncate(msg, 500));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("engineMode", "LEDGER");
        detail.put("before", before);
        detail.put("after", after);
        detail.put("appliedRules", applied);
        detail.put("consistency", consistency);
        detail.put("issueCount", pendingIssues.size());
        detail.put("errorCount", errorCount);
        detail.put("criticalCount", criticalCount);
        p.setDetailJson(toJson(detail));
        pipelineMapper.insert(p);

        for (UnsCleanIssue issue : pendingIssues) {
            issue.setPipelineId(p.getId());
            issueMapper.insert(issue);
        }

        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_PIPELINE", "uns_doc_pipeline", String.valueOf(p.getId()), "CLEAN");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pipelineId", p.getId());
        out.put("pipelineType", "CLEAN");
        out.put("status", "LEDGER");
        out.put("engineMode", "LEDGER");
        out.put("message", msg);
        out.put("processStatus", doc.getProcessStatus());
        out.put("appliedRules", applied);
        out.put("consistency", consistency);
        out.put("issueCount", pendingIssues.size());
        return out;
    }

    private RuleHit evaluate(UnsCleanRule rule, UnsDocument doc, String value) {
        String type = str(rule.getRuleType(), "").toUpperCase(Locale.ROOT);
        Map<String, Object> cfg = parseConfig(rule.getConfigJson());
        return switch (type) {
            case "FILTER" -> evalFilter(rule, value, cfg);
            case "TRANSFORM" -> evalTransform(rule, value, cfg);
            case "VALIDATE" -> evalValidate(rule, value, cfg);
            case "DEDUP" -> evalDedup(rule, doc, value, cfg);
            default -> RuleHit.miss();
        };
    }

    private RuleHit evalFilter(UnsCleanRule rule, String value, Map<String, Object> cfg) {
        String text = nz(value);
        @SuppressWarnings("unchecked")
        List<String> patterns = cfg.get("patterns") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        for (String p : patterns) {
            if (!blank(p) && text.toLowerCase(Locale.ROOT).contains(p.toLowerCase(Locale.ROOT))) {
                return RuleHit.hit(text, text, false,
                        "命中垃圾/冗余过滤规则「" + p + "」：" + rule.getRuleName());
            }
        }
        return RuleHit.miss();
    }

    private RuleHit evalTransform(UnsCleanRule rule, String value, Map<String, Object> cfg) {
        String original = value == null ? "" : value;
        String cur = original;
        @SuppressWarnings("unchecked")
        List<String> ops = cfg.get("ops") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        for (String op : ops) {
            if ("trim".equalsIgnoreCase(op)) {
                cur = cur.trim();
            } else if ("collapse_space".equalsIgnoreCase(op)) {
                cur = cur.replaceAll("\\s+", " ").trim();
            }
        }
        if (cfg.get("removeChars") instanceof String chars && !chars.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cur.length(); i++) {
                char c = cur.charAt(i);
                if (chars.indexOf(c) < 0 && c != '\t' && c != '\r' && c != '\n') {
                    sb.append(c);
                }
            }
            // also remove explicit chars from config string that may be escaped literally
            for (char c : new char[]{'<', '>', '{', '}'}) {
                if (chars.indexOf(c) >= 0) {
                    // already handled by indexOf above when present
                }
            }
            cur = sb.toString().replace("<", "").replace(">", "").replace("{", "").replace("}", "");
        }
        if (Boolean.TRUE.equals(cfg.get("fullToHalf"))) {
            cur = fullToHalf(cur);
        }
        if (Boolean.TRUE.equals(cfg.get("upperAsciiExt"))) {
            cur = upperAsciiExt(cur);
        }
        if (Boolean.TRUE.equals(cfg.get("normalizePhone"))) {
            cur = normalizePhonesInText(cur);
        }
        if (Objects.equals(original, cur)) {
            return RuleHit.miss();
        }
        return RuleHit.hit(original, cur, true, "已按规则转换：" + rule.getRuleName());
    }

    private RuleHit evalValidate(UnsCleanRule rule, String value, Map<String, Object> cfg) {
        String text = value == null ? "" : value;
        if (Boolean.TRUE.equals(cfg.get("notBlank")) && text.isBlank()) {
            return RuleHit.hit(text, text, false, "空值校验失败：" + rule.getRuleName());
        }
        if (cfg.get("min") != null || cfg.get("max") != null) {
            int min = cfg.get("min") instanceof Number n ? n.intValue() : 0;
            int max = cfg.get("max") instanceof Number n ? n.intValue() : Integer.MAX_VALUE;
            int len = text.trim().length();
            if (len < min || len > max) {
                return RuleHit.hit(text, text, false,
                        "长度校验失败（" + min + "～" + max + "）：当前 " + len);
            }
        }
        if (Boolean.TRUE.equals(cfg.get("requireMobileIfPresent"))) {
            Matcher m = MOBILE_LOOSE.matcher(text);
            while (m.find()) {
                String digits = m.group(1).replaceAll("[\\s-]", "");
                if (digits.startsWith("86") && digits.length() > 11) {
                    digits = digits.substring(digits.length() - 11);
                }
                if (!MOBILE_STRICT.matcher(digits).matches()) {
                    return RuleHit.hit(text, text, false, "手机号规范性校验失败：" + digits);
                }
            }
        }
        return RuleHit.miss();
    }

    private RuleHit evalDedup(UnsCleanRule rule, UnsDocument doc, String value, Map<String, Object> cfg) {
        if (blank(value)) {
            return RuleHit.miss();
        }
        LambdaQueryWrapper<UnsDocument> q = new LambdaQueryWrapper<UnsDocument>()
                .eq(UnsDocument::getTitle, value.trim())
                .ne(UnsDocument::getId, doc.getId());
        if ("category".equalsIgnoreCase(String.valueOf(cfg.getOrDefault("scope", "category")))
                && !blank(doc.getCategoryCode())) {
            q.eq(UnsDocument::getCategoryCode, doc.getCategoryCode());
        }
        long dup = documentMapper.selectCount(q);
        if (dup <= 0) {
            return RuleHit.miss();
        }
        return RuleHit.hit(value, value, false,
                "去重命中：同范围存在 " + dup + " 条同名文档（策略：标记问题数据）");
    }

    private Map<String, Object> consistencyCheck(Map<String, String> before, Map<String, String> after,
                                                 List<Map<String, Object>> applied) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> checks = new ArrayList<>();
        boolean passed = true;
        String afterTitle = nz(after.get("title"));
        if (afterTitle.isBlank()) {
            passed = false;
            checks.add("清洗后标题为空");
        } else {
            checks.add("清洗后标题非空");
        }
        for (Map<String, Object> step : applied) {
            if (!Boolean.TRUE.equals(step.get("autoApplied"))) {
                continue;
            }
            String field = String.valueOf(step.get("targetField"));
            String expect = String.valueOf(step.get("after"));
            String actual = nz(after.get(field));
            if (!Objects.equals(expect, actual)) {
                passed = false;
                checks.add("字段 " + field + " 自动转换未落地");
            }
        }
        boolean changed = !before.equals(after);
        if (changed) {
            checks.add("清洗前后字段快照已变化");
        } else {
            checks.add("清洗前后字段快照一致（无自动改写或仅标记问题）");
        }
        out.put("passed", passed);
        out.put("changed", changed);
        out.put("checks", checks);
        out.put("beforeFingerprint", before.toString());
        out.put("afterFingerprint", after.toString());
        return out;
    }

    private Map<String, String> snapshot(UnsDocument doc) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("title", nz(doc.getTitle()));
        map.put("description", nz(doc.getDescription()));
        map.put("author", nz(doc.getAuthor()));
        map.put("tagJson", nz(doc.getTagJson()));
        map.put("mediaFormat", nz(doc.getMediaFormat()));
        return map;
    }

    private String fieldValue(UnsDocument doc, String field) {
        return switch (field) {
            case "description" -> doc.getDescription();
            case "author" -> doc.getAuthor();
            case "tagJson" -> doc.getTagJson();
            case "mediaFormat" -> doc.getMediaFormat();
            default -> doc.getTitle();
        };
    }

    private void setFieldValue(UnsDocument doc, String field, String value) {
        switch (field) {
            case "description" -> doc.setDescription(value);
            case "author" -> doc.setAuthor(value);
            case "tagJson" -> doc.setTagJson(value);
            case "mediaFormat" -> doc.setMediaFormat(value);
            default -> doc.setTitle(value);
        }
    }

    private void applyRuleBody(UnsCleanRule rule, Map<String, Object> body, boolean creating) {
        if (creating || body.containsKey("ruleCode")) {
            String code = str(body.get("ruleCode"), creating ? null : rule.getRuleCode());
            if (blank(code)) {
                throw new BusinessException(400, "ruleCode 不能为空");
            }
            code = code.trim().toUpperCase(Locale.ROOT);
            LambdaQueryWrapper<UnsCleanRule> q = new LambdaQueryWrapper<UnsCleanRule>()
                    .eq(UnsCleanRule::getRuleCode, code);
            if (!creating) {
                q.ne(UnsCleanRule::getId, rule.getId());
            }
            if (ruleMapper.selectCount(q) > 0) {
                throw new BusinessException(400, "规则编码已存在");
            }
            rule.setRuleCode(code);
        }
        if (creating || body.containsKey("ruleName")) {
            rule.setRuleName(required(body.get("ruleName"), "ruleName"));
        }
        if (creating || body.containsKey("ruleType")) {
            String type = str(body.get("ruleType"), "VALIDATE").toUpperCase(Locale.ROOT);
            if (!RULE_TYPES.contains(type)) {
                throw new BusinessException(400, "ruleType 须为 FILTER/DEDUP/VALIDATE/TRANSFORM");
            }
            rule.setRuleType(type);
        }
        if (creating || body.containsKey("targetField")) {
            String field = normalizeField(str(body.get("targetField"), "title"));
            rule.setTargetField(field);
        }
        if (creating || body.containsKey("errorLevel")) {
            String level = str(body.get("errorLevel"), "WARN").toUpperCase(Locale.ROOT);
            if (!ERROR_LEVELS.contains(level)) {
                throw new BusinessException(400, "errorLevel 须为 INFO/WARN/ERROR/CRITICAL");
            }
            rule.setErrorLevel(level);
        }
        if (creating || body.containsKey("enabled")) {
            rule.setEnabled(bool01(body.get("enabled"), 1));
        }
        if (creating || body.containsKey("autoApply")) {
            rule.setAutoApply(bool01(body.get("autoApply"), 1));
        }
        if (creating || body.containsKey("sortOrder")) {
            rule.setSortOrder(intValue(body.get("sortOrder"), 0));
        }
        if (creating || body.containsKey("configJson")) {
            rule.setConfigJson(str(body.get("configJson"), "{}"));
        }
        if (creating || body.containsKey("description")) {
            rule.setDescription(str(body.get("description"), null));
        }
        if (creating) {
            if (blank(rule.getConfigJson())) rule.setConfigJson("{}");
            if (rule.getEnabled() == null) rule.setEnabled(1);
            if (rule.getAutoApply() == null) rule.setAutoApply(0);
            if (rule.getSortOrder() == null) rule.setSortOrder(0);
        }
    }

    private String normalizeField(String field) {
        String f = str(field, "title");
        if (!TARGET_FIELDS.contains(f)) {
            throw new BusinessException(400, "目标字段仅支持 title/description/author/tagJson/mediaFormat");
        }
        return f;
    }

    private boolean severityIssue(String level) {
        return "WARN".equalsIgnoreCase(level) || "ERROR".equalsIgnoreCase(level) || "CRITICAL".equalsIgnoreCase(level);
    }

    private String fullToHalf(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (c == 12288) {
                sb.append(' ');
            } else if (c >= 65281 && c <= 65374) {
                sb.append((char) (c - 65248));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String upperAsciiExt(String input) {
        int dot = input.lastIndexOf('.');
        if (dot > 0 && dot < input.length() - 1) {
            String name = input.substring(0, dot);
            String ext = input.substring(dot + 1);
            if (ext.chars().allMatch(ch -> ch < 128)) {
                return name + "." + ext.toUpperCase(Locale.ROOT);
            }
        }
        return input;
    }

    private String normalizePhonesInText(String text) {
        if (blank(text)) {
            return text;
        }
        Matcher m = MOBILE_LOOSE.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String digits = m.group(1).replaceAll("[\\s-]", "");
            if (digits.startsWith("86") && digits.length() > 11) {
                digits = digits.substring(digits.length() - 11);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(digits));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private Map<String, Object> parseConfig(String json) {
        if (blank(json)) {
            return Map.of();
        }
        try {
            return OM.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("clean rule config parse failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return OM.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(500, "清洗明细序列化失败");
        }
    }

    private UnsCleanRule getRule(Long id) {
        UnsCleanRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "清洗规则不存在");
        }
        return rule;
    }

    private String docTitle(Long id) {
        UnsDocument doc = documentMapper.selectById(id);
        return doc == null ? ("#" + id) : doc.getTitle();
    }

    private Integer bool01(Object value, int def) {
        if (value == null || String.valueOf(value).isBlank()) {
            return def;
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        String s = String.valueOf(value).trim();
        if ("1".equals(s) || "true".equalsIgnoreCase(s)) return 1;
        if ("0".equals(s) || "false".equalsIgnoreCase(s)) return 0;
        return def;
    }

    private Integer intValue(Object value, int def) {
        if (value == null || String.valueOf(value).isBlank()) {
            return def;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "排序号须为整数");
        }
    }

    private String required(Object v, String field) {
        String s = str(v, null);
        if (blank(s)) {
            throw new BusinessException(400, field + " 不能为空");
        }
        return s;
    }

    private String truncate(String value) {
        return truncate(value, 1000);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v).trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }

    private static final class RuleHit {
        final boolean hit;
        final String afterValue;
        final boolean changed;
        final String message;

        private RuleHit(boolean hit, String afterValue, boolean changed, String message) {
            this.hit = hit;
            this.afterValue = afterValue;
            this.changed = changed;
            this.message = message;
        }

        static RuleHit miss() {
            return new RuleHit(false, null, false, null);
        }

        static RuleHit hit(String before, String after, boolean changed, String message) {
            return new RuleHit(true, after, changed, message);
        }
    }
}
