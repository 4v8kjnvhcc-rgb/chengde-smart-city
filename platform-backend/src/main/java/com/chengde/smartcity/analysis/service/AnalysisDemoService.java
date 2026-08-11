package com.chengde.smartcity.analysis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.analysis.entity.AnaAnalysisModel;
import com.chengde.smartcity.analysis.entity.AnaBiDashboard;
import com.chengde.smartcity.analysis.entity.AnaDsWorkflow;
import com.chengde.smartcity.analysis.entity.AnaModelSample;
import com.chengde.smartcity.analysis.mapper.AnaAnalysisModelMapper;
import com.chengde.smartcity.analysis.mapper.AnaBiDashboardMapper;
import com.chengde.smartcity.analysis.mapper.AnaDsWorkflowMapper;
import com.chengde.smartcity.analysis.mapper.AnaModelSampleMapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.dataease.DataEaseClient;
import com.chengde.smartcity.integration.ds.DolphinSchedulerClient;
import com.chengde.smartcity.security.UserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisDemoService {

    private final AnaAnalysisModelMapper modelMapper;
    private final AnaModelSampleMapper sampleMapper;
    private final AnaBiDashboardMapper dashboardMapper;
    private final AnaDsWorkflowMapper workflowMapper;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final IntegrationProperties integrationProperties;
    private final DataEaseClient dataEaseClient;
    private final DolphinSchedulerClient dsClient;

    public AnalysisDemoService(AnaAnalysisModelMapper modelMapper, AnaModelSampleMapper sampleMapper,
                               AnaBiDashboardMapper dashboardMapper, AnaDsWorkflowMapper workflowMapper,
                               AuditService auditService, JdbcTemplate jdbcTemplate,
                               IntegrationProperties integrationProperties,
                               DataEaseClient dataEaseClient, DolphinSchedulerClient dsClient) {
        this.modelMapper = modelMapper;
        this.sampleMapper = sampleMapper;
        this.dashboardMapper = dashboardMapper;
        this.workflowMapper = workflowMapper;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
        this.integrationProperties = integrationProperties;
        this.dataEaseClient = dataEaseClient;
        this.dsClient = dsClient;
    }

    public List<AnaAnalysisModel> listModels(String domain) {
        LambdaQueryWrapper<AnaAnalysisModel> q = new LambdaQueryWrapper<AnaAnalysisModel>()
                .eq(AnaAnalysisModel::getStatus, "PUBLISHED")
                .orderByAsc(AnaAnalysisModel::getModelCode);
        if (domain != null && !domain.isBlank()) {
            q.eq(AnaAnalysisModel::getDomainCode, domain);
        }
        return modelMapper.selectList(q);
    }

    public AnaAnalysisModel getModel(Long id) {
        AnaAnalysisModel model = modelMapper.selectById(id);
        if (model == null) {
            throw new BusinessException(404, "model not found");
        }
        return model;
    }

    @Transactional
    public List<AnaModelSample> samples(Long modelId) {
        AnaAnalysisModel model = getModel(modelId);
        long count = sampleMapper.selectCount(new LambdaQueryWrapper<AnaModelSample>().eq(AnaModelSample::getModelId, modelId));
        int need = model.getSampleRowCount() == null ? 100 : model.getSampleRowCount();
        boolean population = model.getDomainCode() != null
                && "population".equalsIgnoreCase(model.getDomainCode().trim());
        boolean stale = false;
        if (population && count > 0) {
            AnaModelSample first = sampleMapper.selectOne(new LambdaQueryWrapper<AnaModelSample>()
                    .eq(AnaModelSample::getModelId, modelId)
                    .orderByAsc(AnaModelSample::getRowNo)
                    .last("LIMIT 1"));
            String d1 = first == null || first.getDim1() == null ? "" : first.getDim1();
            stale = d1.startsWith("Y202");
        }
        if (count < need || stale) {
            ensureSamples(modelId, need);
        }
        return sampleMapper.selectList(new LambdaQueryWrapper<AnaModelSample>()
                .eq(AnaModelSample::getModelId, modelId)
                .orderByAsc(AnaModelSample::getRowNo)
                .last("LIMIT " + need));
    }

    private void ensureSamples(Long modelId, int need) {
        AnaAnalysisModel model = getModel(modelId);
        sampleMapper.delete(new LambdaQueryWrapper<AnaModelSample>().eq(AnaModelSample::getModelId, modelId));
        String mCode = model.getMCode() == null ? "" : model.getMCode().trim().toUpperCase();
        String[] dims = populationDimLabels(mCode);
        for (int i = 1; i <= need; i++) {
            AnaModelSample s = new AnaModelSample();
            s.setModelId(modelId);
            s.setRowNo(i);
            s.setDim1(dims[0] + "_" + (i % 8 + 1));
            s.setDim2(dims[1] + "_" + (i % 6 + 1));
            s.setMetric1(BigDecimal.valueOf(100 + i * 1.7).setScale(2, RoundingMode.HALF_UP));
            s.setMetric2(BigDecimal.valueOf(50 + i * 0.8).setScale(2, RoundingMode.HALF_UP));
            sampleMapper.insert(s);
        }
    }

    /** 人口十四模型样例维度标签（对齐规格设计卡）；非人口/未知 M 码回落通用标签 */
    private static String[] populationDimLabels(String mCode) {
        return switch (mCode) {
            case "M161" -> new String[]{"区县", "年龄段"};
            case "M162" -> new String[]{"区县", "年份"};
            case "M163" -> new String[]{"年龄段", "年份"};
            case "M164" -> new String[]{"学历", "区县"};
            case "M165" -> new String[]{"年份", "性别"};
            case "M166" -> new String[]{"年份", "原因"};
            case "M167" -> new String[]{"区县", "致贫因"};
            case "M168" -> new String[]{"类别", "区县"};
            case "M169" -> new String[]{"残疾类型", "区县"};
            case "M170" -> new String[]{"区县", "年份"};
            case "M171" -> new String[]{"区县", "同比期"};
            case "M172" -> new String[]{"区县", "同比期"};
            case "M173" -> new String[]{"行政区", "网格"};
            case "M174" -> new String[]{"学区", "行政区"};
            default -> new String[]{"维度A", "维度B"};
        };
    }

    public Map<String, Object> domainSummary() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT domain_code AS domainCode, COUNT(*) AS modelCount FROM ana_analysis_model WHERE status='PUBLISHED' GROUP BY domain_code");
        long total = modelMapper.selectCount(new LambdaQueryWrapper<AnaAnalysisModel>().eq(AnaAnalysisModel::getStatus, "PUBLISHED"));
        Map<String, Object> out = new HashMap<>();
        out.put("totalModels", total);
        out.put("domains", rows);
        return out;
    }

    public List<AnaBiDashboard> listDashboards() {
        return dashboardMapper.selectList(new LambdaQueryWrapper<AnaBiDashboard>().orderByAsc(AnaBiDashboard::getId));
    }

    public List<AnaDsWorkflow> listWorkflows() {
        if (integrationProperties.isEnabled() && dsClient.isHealthy()) {
            List<Map<String, Object>> live = dsClient.listWorkflows();
            if (!live.isEmpty()) {
                return live.stream().map(row -> {
                    AnaDsWorkflow wf = new AnaDsWorkflow();
                    wf.setId(((Number) row.get("id")).longValue());
                    wf.setWorkflowCode(String.valueOf(row.get("workflowCode")));
                    wf.setWorkflowName(String.valueOf(row.get("workflowName")));
                    wf.setStatus(String.valueOf(row.get("status")));
                    wf.setLastMessage(String.valueOf(row.get("lastMessage")));
                    return wf;
                }).toList();
            }
        }
        return workflowMapper.selectList(new LambdaQueryWrapper<AnaDsWorkflow>().orderByAsc(AnaDsWorkflow::getId));
    }

    @Transactional
    public Map<String, Object> runWorkflow(UserPrincipal operator, Long id) {
        if (integrationProperties.isEnabled() && dsClient.isHealthy()) {
            Map<String, Object> res = dsClient.startWorkflow(id);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "DS_WORKFLOW_RUN", "dolphinscheduler", String.valueOf(id), String.valueOf(res.get("status")));
            return res;
        }
        AnaDsWorkflow wf = workflowMapper.selectById(id);
        if (wf == null) {
            throw new BusinessException(404, "workflow not found");
        }
        wf.setStatus("SUCCESS");
        wf.setLastRunAt(LocalDateTime.now());
        wf.setLastMessage("DS workflow run ok");
        workflowMapper.updateById(wf);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DS_WORKFLOW_RUN", "ana_ds_workflow", String.valueOf(id), wf.getWorkflowCode());
        return Map.of("workflowCode", wf.getWorkflowCode(), "status", "SUCCESS", "message", wf.getLastMessage());
    }

    /** DataEase embed SSO token；未就绪时仅签发门户预览令牌（mode=LEDGER），不伪造在线嵌入 */
    @Transactional
    public Map<String, Object> issueEmbedToken(UserPrincipal operator, String targetType, String targetId) {
        LocalDateTime expires = LocalDateTime.now().plusMinutes(30);
        if (integrationProperties.isEnabled() && dataEaseClient.isHealthy()) {
            Map<String, Object> embed = new HashMap<>(dataEaseClient.buildEmbed(targetType, targetId, operator.getUserId()));
            jdbcTemplate.update(
                    "INSERT INTO ana_embed_token(token, user_id, target_type, target_id, expires_at) VALUES (?,?,?,?,?)",
                    embed.get("token"), operator.getUserId(), targetType, targetId, expires);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "DE_EMBED_TOKEN", targetType, targetId, String.valueOf(embed.get("token")));
            embed.put("expiresAt", expires.toString());
            embed.put("mode", "LIVE");
            embed.put("message", "DataEase 嵌入令牌已签发");
            return embed;
        }
        String token = "DE_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
                "INSERT INTO ana_embed_token(token, user_id, target_type, target_id, expires_at) VALUES (?,?,?,?,?)",
                token, operator.getUserId(), targetType, targetId, expires);
        String embedUrl = "/analytics/embed-preview?targetType=" + targetType
                + "&targetId=" + targetId + "&token=" + token;
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DE_EMBED_TOKEN", targetType, targetId, token);
        Map<String, Object> out = new HashMap<>();
        out.put("token", token);
        out.put("expiresAt", expires.toString());
        out.put("embedUrl", embedUrl);
        out.put("dataeaseUrl", null);
        out.put("mode", "LEDGER");
        out.put("message", "DataEase 未就绪：已签发门户预览令牌，未连接真实嵌入");
        out.put("targetType", targetType);
        out.put("targetId", targetId);
        return out;
    }

    public Map<String, Object> validateEmbedToken(String token) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT token, user_id AS userId, target_type AS targetType, target_id AS targetId, expires_at AS expiresAt FROM ana_embed_token WHERE token=?",
                token);
        if (rows.isEmpty()) {
            throw new BusinessException(401, "invalid embed token");
        }
        Map<String, Object> row = rows.get(0);
        if (isExpired(row.get("expiresAt"))) {
            throw new BusinessException(401, "embed token expired");
        }
        String storedToken = String.valueOf(row.get("token"));
        String targetId = String.valueOf(row.get("targetId"));
        Long userId = row.get("userId") == null ? 0L : ((Number) row.get("userId")).longValue();
        row.put("valid", true);
        if (integrationProperties.isEnabled() && dataEaseClient.isHealthy()) {
            row.put("dataeaseUrl", dataEaseClient.buildEmbedUrl(targetId, storedToken, userId));
            row.put("mode", "LIVE");
        } else {
            row.put("dataeaseUrl", null);
            row.put("mode", "LEDGER");
            row.put("message", "DataEase 未就绪，无真实嵌入地址");
        }
        return row;
    }

    private boolean isExpired(Object exp) {
        if (exp == null) {
            return false;
        }
        LocalDateTime ldt;
        if (exp instanceof LocalDateTime local) {
            ldt = local;
        } else if (exp instanceof Timestamp ts) {
            ldt = ts.toLocalDateTime();
        } else if (exp instanceof Date d) {
            ldt = LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        } else {
            return false;
        }
        return ldt.isBefore(LocalDateTime.now());
    }
}
