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
        if (count < need) {
            ensureSamples(modelId, need);
        }
        return sampleMapper.selectList(new LambdaQueryWrapper<AnaModelSample>()
                .eq(AnaModelSample::getModelId, modelId)
                .orderByAsc(AnaModelSample::getRowNo)
                .last("LIMIT " + need));
    }

    private void ensureSamples(Long modelId, int need) {
        sampleMapper.delete(new LambdaQueryWrapper<AnaModelSample>().eq(AnaModelSample::getModelId, modelId));
        for (int i = 1; i <= need; i++) {
            AnaModelSample s = new AnaModelSample();
            s.setModelId(modelId);
            s.setRowNo(i);
            s.setDim1("Y202" + (i % 5));
            s.setDim2("R" + (i % 10));
            s.setMetric1(BigDecimal.valueOf(100 + i * 1.7).setScale(2, RoundingMode.HALF_UP));
            s.setMetric2(BigDecimal.valueOf(50 + i * 0.8).setScale(2, RoundingMode.HALF_UP));
            sampleMapper.insert(s);
        }
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

    /** DataEase embed SSO token (capability-equivalent POC) */
    @Transactional
    public Map<String, Object> issueEmbedToken(UserPrincipal operator, String targetType, String targetId) {
        if (integrationProperties.isEnabled() && dataEaseClient.isHealthy()) {
            Map<String, Object> embed = new HashMap<>(dataEaseClient.buildEmbed(targetType, targetId, operator.getUserId()));
            LocalDateTime expires = LocalDateTime.now().plusMinutes(30);
            jdbcTemplate.update(
                    "INSERT INTO ana_embed_token(token, user_id, target_type, target_id, expires_at) VALUES (?,?,?,?,?)",
                    embed.get("token"), operator.getUserId(), targetType, targetId, expires);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "DE_EMBED_TOKEN", targetType, targetId, String.valueOf(embed.get("token")));
            embed.put("expiresAt", expires.toString());
            return embed;
        }
        String token = "DE_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expires = LocalDateTime.now().plusMinutes(30);
        jdbcTemplate.update(
                "INSERT INTO ana_embed_token(token, user_id, target_type, target_id, expires_at) VALUES (?,?,?,?,?)",
                token, operator.getUserId(), targetType, targetId, expires);
        String embedUrl = "/analytics/embed-preview?targetType=" + targetType
                + "&targetId=" + targetId + "&token=" + token;
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DE_EMBED_TOKEN", targetType, targetId, token);
        return Map.of(
                "token", token,
                "expiresAt", expires.toString(),
                "embedUrl", embedUrl,
                "dataeaseUrl", "https://dataease.local/embedded/" + targetId + "?token=" + token
        );
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
        } else {
            row.put("dataeaseUrl", "https://dataease.local/embedded/" + targetId + "?token=" + storedToken);
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
