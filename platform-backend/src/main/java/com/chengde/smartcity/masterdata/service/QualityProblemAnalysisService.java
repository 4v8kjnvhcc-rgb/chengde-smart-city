package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovQualityAnalysisCase;
import com.chengde.smartcity.masterdata.entity.GovQualityCodeImpact;
import com.chengde.smartcity.masterdata.entity.GovQualityIssue;
import com.chengde.smartcity.masterdata.entity.GovQualityKnowledge;
import com.chengde.smartcity.masterdata.entity.GovStandardMapping;
import com.chengde.smartcity.masterdata.mapper.GovQualityAnalysisCaseMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityCodeImpactMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityIssueMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityKnowledgeMapper;
import com.chengde.smartcity.masterdata.mapper.GovStandardMappingMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 数据质量问题分析：快速定位、编码映射影响、知识沉淀。
 */
@Service
public class QualityProblemAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(QualityProblemAnalysisService.class);

    private final GovQualityAnalysisCaseMapper caseMapper;
    private final GovQualityCodeImpactMapper impactMapper;
    private final GovQualityKnowledgeMapper knowledgeMapper;
    private final GovQualityIssueMapper issueMapper;
    private final GovStandardMappingMapper mappingMapper;

    public QualityProblemAnalysisService(GovQualityAnalysisCaseMapper caseMapper,
                                         GovQualityCodeImpactMapper impactMapper,
                                         GovQualityKnowledgeMapper knowledgeMapper,
                                         GovQualityIssueMapper issueMapper,
                                         GovStandardMappingMapper mappingMapper) {
        this.caseMapper = caseMapper;
        this.impactMapper = impactMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.issueMapper = issueMapper;
        this.mappingMapper = mappingMapper;
    }

    public Map<String, Object> locate(String keyword, String targetTable, String issueType, String severity) {
        LambdaQueryWrapper<GovQualityAnalysisCase> qw = new LambdaQueryWrapper<GovQualityAnalysisCase>()
                .orderByDesc(GovQualityAnalysisCase::getId);
        if (StringUtils.hasText(targetTable)) {
            qw.eq(GovQualityAnalysisCase::getTargetTable, targetTable.trim());
        }
        if (StringUtils.hasText(issueType)) {
            qw.eq(GovQualityAnalysisCase::getIssueType, issueType.trim());
        }
        if (StringUtils.hasText(severity)) {
            qw.eq(GovQualityAnalysisCase::getSeverity, severity.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(GovQualityAnalysisCase::getCaseName, kw)
                    .or().like(GovQualityAnalysisCase::getLocateSummary, kw)
                    .or().like(GovQualityAnalysisCase::getTargetColumn, kw)
                    .or().like(GovQualityAnalysisCase::getRootCause, kw));
        }
        List<GovQualityAnalysisCase> cases = caseMapper.selectList(qw);

        List<GovQualityIssue> recentIssues = issueMapper.selectList(new LambdaQueryWrapper<GovQualityIssue>()
                .orderByDesc(GovQualityIssue::getId)
                .last("LIMIT 100"));
        List<Map<String, Object>> hotspots = buildHotspots(recentIssues, targetTable, issueType, severity, keyword);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cases", cases);
        out.put("hotspots", hotspots);
        out.put("caseCount", cases.size());
        out.put("hotspotCount", hotspots.size());
        return out;
    }

    public List<GovQualityCodeImpact> listCodeImpacts(String keyword, String impactLevel, String mappingStatus) {
        LambdaQueryWrapper<GovQualityCodeImpact> qw = new LambdaQueryWrapper<GovQualityCodeImpact>()
                .orderByDesc(GovQualityCodeImpact::getId);
        if (StringUtils.hasText(impactLevel)) {
            qw.eq(GovQualityCodeImpact::getImpactLevel, impactLevel.trim());
        }
        if (StringUtils.hasText(mappingStatus)) {
            qw.eq(GovQualityCodeImpact::getMappingStatus, mappingStatus.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(GovQualityCodeImpact::getStandardName, kw)
                    .or().like(GovQualityCodeImpact::getSourceTable, kw)
                    .or().like(GovQualityCodeImpact::getSourceColumn, kw)
                    .or().like(GovQualityCodeImpact::getImpactDesc, kw));
        }
        List<GovQualityCodeImpact> stored = impactMapper.selectList(qw);
        if (!stored.isEmpty()) {
            return stored;
        }
        // 无台账时：用标准映射 + 近期问题做辅助影响推断
        return inferImpactsFromMappings(keyword, impactLevel, mappingStatus);
    }

    public Map<String, Object> codeImpactDetail(Long id) {
        GovQualityCodeImpact impact = impactMapper.selectById(id);
        if (impact == null) {
            throw new BusinessException(404, "编码映射影响记录不存在: " + id);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("impact", impact);
        if (impact.getCaseId() != null) {
            out.put("analysisCase", caseMapper.selectById(impact.getCaseId()));
        }
        List<GovStandardMapping> mappings = mappingMapper.selectList(new LambdaQueryWrapper<GovStandardMapping>()
                .eq(StringUtils.hasText(impact.getSourceTable()), GovStandardMapping::getSourceTable, impact.getSourceTable())
                .eq(StringUtils.hasText(impact.getSourceColumn()), GovStandardMapping::getSourceColumn, impact.getSourceColumn())
                .orderByDesc(GovStandardMapping::getId)
                .last("LIMIT 20"));
        out.put("mappings", mappings);
        List<String> downstream = new ArrayList<>();
        if (StringUtils.hasText(impact.getDownstreamRefs())) {
            for (String part : impact.getDownstreamRefs().split("[;,]")) {
                if (StringUtils.hasText(part)) {
                    downstream.add(part.trim());
                }
            }
        }
        out.put("downstreamList", downstream);
        return out;
    }

    public List<GovQualityKnowledge> listKnowledge(String keyword, String issueType, String category) {
        LambdaQueryWrapper<GovQualityKnowledge> qw = new LambdaQueryWrapper<GovQualityKnowledge>()
                .orderByDesc(GovQualityKnowledge::getHitCount)
                .orderByDesc(GovQualityKnowledge::getId);
        if (StringUtils.hasText(issueType)) {
            qw.eq(GovQualityKnowledge::getIssueType, issueType.trim());
        }
        if (StringUtils.hasText(category)) {
            qw.eq(GovQualityKnowledge::getCategory, category.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(GovQualityKnowledge::getTitle, kw)
                    .or().like(GovQualityKnowledge::getSymptom, kw)
                    .or().like(GovQualityKnowledge::getSolution, kw));
        }
        return knowledgeMapper.selectList(qw);
    }

    @Transactional
    public GovQualityKnowledge saveKnowledge(UserPrincipal operator, Map<String, Object> body) {
        Long id = asLong(body.get("id"));
        GovQualityKnowledge k;
        if (id != null) {
            k = knowledgeMapper.selectById(id);
            if (k == null) {
                throw new BusinessException(404, "知识条目不存在: " + id);
            }
        } else {
            k = new GovQualityKnowledge();
            k.setKnowledgeCode("QK_" + System.currentTimeMillis());
            k.setHitCount(0);
            k.setCreatedAt(LocalDateTime.now());
            k.setCreatedBy(operator != null ? operator.getUsername() : "system");
            k.setStatus("ACTIVE");
        }
        k.setTitle(requiredStr(body.get("title"), "标题"));
        k.setIssueType(str(body.get("issueType"), k.getIssueType()));
        k.setCategory(str(body.get("category"), k.getCategory() != null ? k.getCategory() : "OTHER"));
        k.setSymptom(str(body.get("symptom"), k.getSymptom()));
        k.setRootCause(str(body.get("rootCause"), k.getRootCause()));
        k.setSolution(str(body.get("solution"), k.getSolution()));
        k.setRelatedStandard(str(body.get("relatedStandard"), k.getRelatedStandard()));
        if (body.get("status") != null) {
            k.setStatus(String.valueOf(body.get("status")));
        }
        k.setUpdatedAt(LocalDateTime.now());
        if (id == null) {
            knowledgeMapper.insert(k);
        } else {
            knowledgeMapper.updateById(k);
        }
        log.info("quality knowledge saved id={} code={}", k.getId(), k.getKnowledgeCode());
        return k;
    }

    @Transactional
    public void deleteKnowledge(Long id) {
        GovQualityKnowledge k = knowledgeMapper.selectById(id);
        if (k == null) {
            throw new BusinessException(404, "知识条目不存在: " + id);
        }
        knowledgeMapper.deleteById(id);
    }

    @Transactional
    public GovQualityKnowledge hitKnowledge(Long id) {
        GovQualityKnowledge k = knowledgeMapper.selectById(id);
        if (k == null) {
            throw new BusinessException(404, "知识条目不存在: " + id);
        }
        int hits = k.getHitCount() == null ? 0 : k.getHitCount();
        k.setHitCount(hits + 1);
        k.setUpdatedAt(LocalDateTime.now());
        knowledgeMapper.updateById(k);
        return k;
    }

    public Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("caseCount", caseMapper.selectCount(null));
        out.put("openCaseCount", caseMapper.selectCount(new LambdaQueryWrapper<GovQualityAnalysisCase>()
                .in(GovQualityAnalysisCase::getStatus, List.of("OPEN", "IN_ANALYSIS"))));
        out.put("impactCount", impactMapper.selectCount(null));
        out.put("highImpactCount", impactMapper.selectCount(new LambdaQueryWrapper<GovQualityCodeImpact>()
                .eq(GovQualityCodeImpact::getImpactLevel, "HIGH")));
        out.put("knowledgeCount", knowledgeMapper.selectCount(new LambdaQueryWrapper<GovQualityKnowledge>()
                .eq(GovQualityKnowledge::getStatus, "ACTIVE")));
        return out;
    }

    private List<Map<String, Object>> buildHotspots(List<GovQualityIssue> issues,
                                                    String targetTable,
                                                    String issueType,
                                                    String severity,
                                                    String keyword) {
        Map<String, Map<String, Object>> agg = new LinkedHashMap<>();
        for (GovQualityIssue iss : issues) {
            if (iss == null) continue;
            if (StringUtils.hasText(targetTable) && !targetTable.equalsIgnoreCase(nullToEmpty(iss.getTargetTable()))) {
                continue;
            }
            if (StringUtils.hasText(issueType) && !issueType.equalsIgnoreCase(nullToEmpty(iss.getIssueType()))) {
                continue;
            }
            if (StringUtils.hasText(severity) && !severity.equalsIgnoreCase(nullToEmpty(iss.getSeverity()))) {
                continue;
            }
            if (StringUtils.hasText(keyword)) {
                String kw = keyword.toLowerCase(Locale.ROOT);
                String blob = (nullToEmpty(iss.getTargetTable()) + " " + nullToEmpty(iss.getTargetColumn())
                        + " " + nullToEmpty(iss.getIssueValue()) + " " + nullToEmpty(iss.getIssueType()))
                        .toLowerCase(Locale.ROOT);
                if (!blob.contains(kw)) {
                    continue;
                }
            }
            String key = nullToEmpty(iss.getTargetTable()) + "|" + nullToEmpty(iss.getTargetColumn())
                    + "|" + nullToEmpty(iss.getIssueType());
            Map<String, Object> row = agg.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("targetTable", iss.getTargetTable());
                m.put("targetColumn", iss.getTargetColumn());
                m.put("issueType", iss.getIssueType());
                m.put("severity", iss.getSeverity());
                m.put("issueCount", 0);
                m.put("sampleValue", iss.getIssueValue());
                return m;
            });
            int cnt = (Integer) row.get("issueCount");
            int add = iss.getIssueCount() == null ? 1 : iss.getIssueCount();
            row.put("issueCount", cnt + add);
            if (row.get("sampleValue") == null && iss.getIssueValue() != null) {
                row.put("sampleValue", iss.getIssueValue());
            }
        }
        List<Map<String, Object>> list = new ArrayList<>(agg.values());
        list.sort((a, b) -> Integer.compare((Integer) b.get("issueCount"), (Integer) a.get("issueCount")));
        // 挂知识建议
        List<GovQualityKnowledge> knowledge = knowledgeMapper.selectList(new LambdaQueryWrapper<GovQualityKnowledge>()
                .eq(GovQualityKnowledge::getStatus, "ACTIVE")
                .last("LIMIT 200"));
        for (Map<String, Object> row : list) {
            String type = String.valueOf(row.get("issueType"));
            List<GovQualityKnowledge> matched = knowledge.stream()
                    .filter(k -> type != null && type.equalsIgnoreCase(nullToEmpty(k.getIssueType())))
                    .limit(2)
                    .collect(Collectors.toList());
            row.put("knowledgeHints", matched);
            if (!matched.isEmpty()) {
                row.put("suggestedRootCause", matched.get(0).getRootCause());
                row.put("suggestedSolution", matched.get(0).getSolution());
            }
        }
        if (list.size() > 50) {
            return list.subList(0, 50);
        }
        return list;
    }

    private List<GovQualityCodeImpact> inferImpactsFromMappings(String keyword, String impactLevel, String mappingStatus) {
        List<GovStandardMapping> mappings = mappingMapper.selectList(new LambdaQueryWrapper<GovStandardMapping>()
                .orderByDesc(GovStandardMapping::getId)
                .last("LIMIT 50"));
        List<GovQualityCodeImpact> inferred = new ArrayList<>();
        int i = 0;
        for (GovStandardMapping m : mappings) {
            if (StringUtils.hasText(mappingStatus) && !mappingStatus.equalsIgnoreCase(nullToEmpty(m.getMappingStatus()))) {
                continue;
            }
            if (StringUtils.hasText(keyword)) {
                String kw = keyword.toLowerCase(Locale.ROOT);
                String blob = (nullToEmpty(m.getSourceTable()) + " " + nullToEmpty(m.getSourceColumn())
                        + " " + nullToEmpty(m.getRemark())).toLowerCase(Locale.ROOT);
                if (!blob.contains(kw)) {
                    continue;
                }
            }
            String level = "MAPPED".equalsIgnoreCase(m.getMappingStatus()) ? "LOW"
                    : "PARTIAL".equalsIgnoreCase(m.getMappingStatus()) ? "MEDIUM" : "HIGH";
            if (StringUtils.hasText(impactLevel) && !impactLevel.equalsIgnoreCase(level)) {
                continue;
            }
            GovQualityCodeImpact impact = new GovQualityCodeImpact();
            impact.setId(-(long) (++i));
            impact.setImpactCode("INFER_" + i);
            impact.setStandardItemId(m.getStandardItemId());
            impact.setSourceSystem(m.getSourceSystem());
            impact.setSourceTable(m.getSourceTable());
            impact.setSourceColumn(m.getSourceColumn());
            impact.setMappingStatus(m.getMappingStatus());
            impact.setImpactLevel(level);
            impact.setImpactDesc(StringUtils.hasText(m.getRemark()) ? m.getRemark() : "由标准映射自动推断的影响项");
            impact.setDownstreamRefs("待补充下游引用");
            impact.setIssueCount(0);
            impact.setStatus("OPEN");
            inferred.add(impact);
        }
        return inferred;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static String requiredStr(Object v, String label) {
        String s = str(v, null);
        if (!StringUtils.hasText(s)) {
            throw new BusinessException(400, label + "不能为空");
        }
        return s;
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}
