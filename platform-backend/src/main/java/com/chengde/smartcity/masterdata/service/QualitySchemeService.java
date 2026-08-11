package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.ds.DolphinSchedulerClient;
import com.chengde.smartcity.masterdata.entity.GovQualityModel;
import com.chengde.smartcity.masterdata.entity.GovQualityModelRule;
import com.chengde.smartcity.masterdata.entity.GovQualityScheme;
import com.chengde.smartcity.masterdata.entity.GovQualitySchemeRule;
import com.chengde.smartcity.masterdata.entity.GovQualityTask;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskDetail;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskRun;
import com.chengde.smartcity.masterdata.mapper.GovQualityModelMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityModelRuleMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualitySchemeMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualitySchemeRuleMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskDetailMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskRunMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 质量方案管理：选用质量模型规则组成方案，定时经 DolphinScheduler，执行复用质量稽核引擎。
 */
@Service
public class QualitySchemeService {

    private static final Logger log = LoggerFactory.getLogger(QualitySchemeService.class);
    private static final String DS_PROJECT = "chengde_quality_scheme";

    private final GovQualitySchemeMapper schemeMapper;
    private final GovQualitySchemeRuleMapper schemeRuleMapper;
    private final GovQualityModelMapper modelMapper;
    private final GovQualityModelRuleMapper modelRuleMapper;
    private final GovQualityTaskMapper taskMapper;
    private final GovQualityTaskDetailMapper detailMapper;
    private final GovQualityTaskRunMapper runMapper;
    private final QualityExecuteService executeService;
    private final QualityAlertService alertService;
    private final DolphinSchedulerClient dsClient;
    private final IntegrationProperties integrationProperties;
    private final AuditService auditService;

    public QualitySchemeService(GovQualitySchemeMapper schemeMapper,
                                GovQualitySchemeRuleMapper schemeRuleMapper,
                                GovQualityModelMapper modelMapper,
                                GovQualityModelRuleMapper modelRuleMapper,
                                GovQualityTaskMapper taskMapper,
                                GovQualityTaskDetailMapper detailMapper,
                                GovQualityTaskRunMapper runMapper,
                                QualityExecuteService executeService,
                                QualityAlertService alertService,
                                DolphinSchedulerClient dsClient,
                                IntegrationProperties integrationProperties,
                                AuditService auditService) {
        this.schemeMapper = schemeMapper;
        this.schemeRuleMapper = schemeRuleMapper;
        this.modelMapper = modelMapper;
        this.modelRuleMapper = modelRuleMapper;
        this.taskMapper = taskMapper;
        this.detailMapper = detailMapper;
        this.runMapper = runMapper;
        this.executeService = executeService;
        this.alertService = alertService;
        this.dsClient = dsClient;
        this.integrationProperties = integrationProperties;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> listPublished() {
        List<GovQualityScheme> rows = schemeMapper.selectList(new LambdaQueryWrapper<GovQualityScheme>()
                .eq(GovQualityScheme::getGenerateStatus, "SUCCESS")
                .orderByDesc(GovQualityScheme::getUpdatedAt));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityScheme s : rows) {
            out.add(toMap(s, false));
        }
        return out;
    }

    public List<Map<String, Object>> list(String keyword, String scheduleStatus, String cycleName) {
        LambdaQueryWrapper<GovQualityScheme> q = new LambdaQueryWrapper<GovQualityScheme>()
                .orderByDesc(GovQualityScheme::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(GovQualityScheme::getSchemeName, keyword.trim())
                    .or().like(GovQualityScheme::getModelName, keyword.trim()));
        }
        if (scheduleStatus != null && !scheduleStatus.isBlank()) {
            q.eq(GovQualityScheme::getScheduleStatus, scheduleStatus.trim());
        }
        if (cycleName != null && !cycleName.isBlank()) {
            q.eq(GovQualityScheme::getCycleName, cycleName.trim());
        }
        List<GovQualityScheme> rows = schemeMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityScheme s : rows) {
            out.add(toMap(s, false));
        }
        return out;
    }

    public Map<String, Object> get(Long id) {
        return toMap(require(id), true);
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        String name = str(body.get("schemeName"), null);
        if (name == null) {
            throw new BusinessException(400, "方案名称不能为空");
        }
        Long modelId = toLong(body.get("modelId"));
        if (modelId == null) {
            throw new BusinessException(400, "请选择质量模型");
        }
        GovQualityModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException(404, "质量模型不存在: " + modelId);
        }
        String cron = str(body.get("cronExpr"), null);
        if (cron == null) {
            throw new BusinessException(400, "请选择执行周期");
        }

        GovQualityScheme s = new GovQualityScheme();
        s.setSchemeName(name);
        s.setModelId(modelId);
        s.setModelName(str(body.get("modelName"), model.getModelName()));
        s.setExecutorAddress(str(body.get("executorAddress"), "default"));
        s.setCronExpr(cron);
        s.setCycleName(str(body.get("cycleName"), null));
        s.setDescription(str(body.get("description"), null));
        s.setScheduleStatus("STOPPED");
        s.setGenerateStatus("NONE");
        s.setExecStatus("NOT_RUN");
        if (operator != null) {
            s.setCreatedBy(operator.getUsername());
        }
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        schemeMapper.insert(s);
        replaceRules(s.getId(), modelId, body.get("ruleIds"));
        syncQualityTask(s);
        audit(operator, "QUALITY_SCHEME_CREATE", s.getId(), s.getSchemeName());
        return s.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovQualityScheme s = require(id);
        if ("RUNNING".equals(s.getScheduleStatus())) {
            throw new BusinessException(400, "调度运行中不可修改，请先停止");
        }
        if (body.containsKey("schemeName")) {
            String name = str(body.get("schemeName"), null);
            if (name == null) {
                throw new BusinessException(400, "方案名称不能为空");
            }
            s.setSchemeName(name);
        }
        if (body.containsKey("modelId")) {
            Long modelId = toLong(body.get("modelId"));
            if (modelId == null) {
                throw new BusinessException(400, "请选择质量模型");
            }
            GovQualityModel model = modelMapper.selectById(modelId);
            if (model == null) {
                throw new BusinessException(404, "质量模型不存在: " + modelId);
            }
            s.setModelId(modelId);
            s.setModelName(str(body.get("modelName"), model.getModelName()));
        }
        if (body.containsKey("executorAddress")) {
            s.setExecutorAddress(str(body.get("executorAddress"), "default"));
        }
        if (body.containsKey("cronExpr")) {
            String cron = str(body.get("cronExpr"), null);
            if (cron == null) {
                throw new BusinessException(400, "请选择执行周期");
            }
            s.setCronExpr(cron);
        }
        if (body.containsKey("cycleName")) {
            s.setCycleName(str(body.get("cycleName"), null));
        }
        if (body.containsKey("description")) {
            s.setDescription(str(body.get("description"), null));
        }
        s.setGenerateStatus("NONE");
        s.setUpdatedAt(LocalDateTime.now());
        schemeMapper.updateById(s);
        if (body.containsKey("ruleIds")) {
            replaceRules(s.getId(), s.getModelId(), body.get("ruleIds"));
        }
        syncQualityTask(s);
        audit(operator, "QUALITY_SCHEME_UPDATE", s.getId(), s.getSchemeName());
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        GovQualityScheme s = require(id);
        if ("RUNNING".equals(s.getScheduleStatus())) {
            throw new BusinessException(400, "请先停止调度再删除");
        }
        offlineDsQuietly(s);
        schemeRuleMapper.delete(new LambdaQueryWrapper<GovQualitySchemeRule>()
                .eq(GovQualitySchemeRule::getSchemeId, id));
        if (s.getQualityTaskId() != null) {
            detailMapper.delete(new LambdaQueryWrapper<GovQualityTaskDetail>()
                    .eq(GovQualityTaskDetail::getTaskId, s.getQualityTaskId()));
            taskMapper.deleteById(s.getQualityTaskId());
        }
        schemeMapper.deleteById(id);
        audit(operator, "QUALITY_SCHEME_DELETE", id, s.getSchemeName());
    }

    /** 生成：同步稽核任务 + 在 DolphinScheduler 创建流程定义（调度默认下线，需「启动」）。 */
    @Transactional
    public Map<String, Object> generate(UserPrincipal operator, Long id) {
        GovQualityScheme s = require(id);
        List<GovQualitySchemeRule> rules = listRules(id);
        if (rules.isEmpty()) {
            throw new BusinessException(400, "方案未配置规则，请先新增并选择规则");
        }
        if (s.getCronExpr() == null || s.getCronExpr().isBlank()) {
            throw new BusinessException(400, "方案未配置执行周期");
        }
        if (!isDsAvailable()) {
            throw new BusinessException(502, "DolphinScheduler 不可用，无法生成调度流程");
        }
        syncQualityTask(s);
        s = require(id);

        try {
            offlineDsQuietly(s);
            long projectCode = dsClient.ensureProject(DS_PROJECT);
            String tenant = dsClient.resolveTenant();
            String defName = "质量方案_" + safeName(s.getSchemeName()) + "_" + id;
            String script = buildCallbackScript(id);
            long definitionCode = dsClient.createAndReleaseShellChain(
                    projectCode, defName, List.of("质量方案稽核"), List.of(script), tenant);
            int scheduleId = dsClient.createAndOnlineSchedule(projectCode, definitionCode, s.getCronExpr());
            // 生成后先下线，避免未点「启动」就跑；与列表「已停止」一致
            dsClient.offlineSchedule(projectCode, scheduleId);

            s.setDsProjectCode(projectCode);
            s.setDsDefinitionCode(definitionCode);
            s.setDsScheduleId(scheduleId);
            s.setGenerateStatus("SUCCESS");
            s.setScheduleStatus("STOPPED");
            s.setLastMessage("已生成 DolphinScheduler 流程，可启动调度或立即执行");
            s.setUpdatedAt(LocalDateTime.now());
            schemeMapper.updateById(s);
            audit(operator, "QUALITY_SCHEME_GENERATE", id,
                    "project=" + projectCode + " def=" + definitionCode + " schedule=" + scheduleId);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("projectCode", projectCode);
            out.put("definitionCode", definitionCode);
            out.put("scheduleId", scheduleId);
            out.put("generateStatus", "SUCCESS");
            return out;
        } catch (BusinessException e) {
            s.setGenerateStatus("FAILED");
            s.setLastMessage(e.getMessage());
            s.setUpdatedAt(LocalDateTime.now());
            schemeMapper.updateById(s);
            throw e;
        } catch (Exception e) {
            s.setGenerateStatus("FAILED");
            s.setLastMessage(e.getMessage());
            s.setUpdatedAt(LocalDateTime.now());
            schemeMapper.updateById(s);
            throw new BusinessException(502, "生成 DS 流程失败: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> start(UserPrincipal operator, Long id) {
        GovQualityScheme s = require(id);
        if (!"SUCCESS".equals(s.getGenerateStatus())
                || s.getDsProjectCode() == null
                || s.getDsScheduleId() == null) {
            throw new BusinessException(400, "请先「生成」方案调度流程");
        }
        if ("RUNNING".equals(s.getScheduleStatus())) {
            throw new BusinessException(400, "调度已在运行中");
        }
        if (!isDsAvailable()) {
            throw new BusinessException(502, "DolphinScheduler 不可用");
        }
        dsClient.onlineSchedule(s.getDsProjectCode(), s.getDsScheduleId());
        s.setScheduleStatus("RUNNING");
        s.setLastMessage("定时调度已启动");
        s.setUpdatedAt(LocalDateTime.now());
        schemeMapper.updateById(s);
        audit(operator, "QUALITY_SCHEME_START", id, "schedule=" + s.getDsScheduleId());
        return Map.of("id", id, "scheduleStatus", "RUNNING");
    }

    @Transactional
    public Map<String, Object> stop(UserPrincipal operator, Long id) {
        GovQualityScheme s = require(id);
        if (s.getDsProjectCode() != null && s.getDsScheduleId() != null && isDsAvailable()) {
            try {
                dsClient.offlineSchedule(s.getDsProjectCode(), s.getDsScheduleId());
            } catch (Exception e) {
                log.warn("offline quality scheme schedule id={}: {}", id, e.getMessage());
            }
        }
        s.setScheduleStatus("STOPPED");
        s.setLastMessage("定时调度已停止");
        s.setUpdatedAt(LocalDateTime.now());
        schemeMapper.updateById(s);
        audit(operator, "QUALITY_SCHEME_STOP", id, "offline");
        return Map.of("id", id, "scheduleStatus", "STOPPED");
    }

    /** 立即执行：走本地质量稽核引擎（不等待 DS 实例）。 */
    @Transactional
    public Map<String, Object> execute(UserPrincipal operator, Long id) {
        GovQualityScheme s = require(id);
        syncQualityTask(s);
        s = require(id);
        if (s.getQualityTaskId() == null) {
            throw new BusinessException(400, "方案未同步稽核任务");
        }
        s.setExecStatus("RUNNING");
        s.setUpdatedAt(LocalDateTime.now());
        schemeMapper.updateById(s);
        try {
            Map<String, Object> run = executeService.executeTask(operator, s.getQualityTaskId());
            s.setExecStatus("SUCCESS");
            s.setLastExecAt(LocalDateTime.now());
            Object score = run.get("score");
            s.setLastMessage("执行完成" + (score != null ? " · 评分 " + score : ""));
            s.setUpdatedAt(LocalDateTime.now());
            schemeMapper.updateById(s);
            audit(operator, "QUALITY_SCHEME_EXECUTE", id, s.getLastMessage());
            Long runId = run.get("runId") == null ? null : Long.valueOf(String.valueOf(run.get("runId")));
            if (runId != null) {
                try {
                    Map<String, Object> notify = alertService.notifyAfterRun(
                            id, s.getQualityTaskId(), runId, s.getSchemeName());
                    run.put("alert", notify);
                } catch (Exception ne) {
                    log.warn("quality scheme alert notify failed id={}: {}", id, ne.getMessage());
                    run.put("alertError", ne.getMessage());
                }
            }
            Map<String, Object> out = new LinkedHashMap<>(run);
            out.put("schemeId", id);
            out.put("execStatus", "SUCCESS");
            return out;
        } catch (RuntimeException e) {
            s.setExecStatus("FAILED");
            s.setLastExecAt(LocalDateTime.now());
            s.setLastMessage(e.getMessage());
            s.setUpdatedAt(LocalDateTime.now());
            schemeMapper.updateById(s);
            throw e;
        }
    }

    /** DS Shell 回调：定时触发时执行方案稽核。 */
    @Transactional
    public Map<String, Object> runCallback(Long id, String token) {
        assertCallbackToken(token);
        return execute(null, id);
    }

    public List<Map<String, Object>> logs(Long id) {
        GovQualityScheme s = require(id);
        if (s.getQualityTaskId() == null) {
            return List.of();
        }
        List<GovQualityTaskRun> runs = runMapper.selectList(new LambdaQueryWrapper<GovQualityTaskRun>()
                .eq(GovQualityTaskRun::getTaskId, s.getQualityTaskId())
                .orderByDesc(GovQualityTaskRun::getId)
                .last("LIMIT 50"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityTaskRun r : runs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("status", r.getStatus());
            row.put("score", r.getScore());
            row.put("issueCount", r.getIssueCount());
            row.put("totalChecks", r.getTotalChecks());
            row.put("startedAt", r.getStartedAt());
            row.put("endedAt", r.getEndedAt());
            row.put("message", r.getMessage());
            out.add(row);
        }
        return out;
    }

    private void replaceRules(Long schemeId, Long modelId, Object ruleIdsObj) {
        schemeRuleMapper.delete(new LambdaQueryWrapper<GovQualitySchemeRule>()
                .eq(GovQualitySchemeRule::getSchemeId, schemeId));
        if (!(ruleIdsObj instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(400, "请至少选择一条质量规则");
        }
        int sort = 0;
        for (Object o : list) {
            Long ruleId = toLong(o);
            if (ruleId == null) continue;
            GovQualityModelRule mr = modelRuleMapper.selectById(ruleId);
            if (mr == null || !modelId.equals(mr.getModelId())) {
                throw new BusinessException(400, "规则不属于所选模型: " + ruleId);
            }
            GovQualitySchemeRule sr = new GovQualitySchemeRule();
            sr.setSchemeId(schemeId);
            sr.setModelRuleId(ruleId);
            sr.setRuleTypeName(mr.getRuleTypeName());
            sr.setRuleName(mr.getRuleName());
            sr.setTableName(mr.getTableName());
            sr.setFieldNames(mr.getFieldNames());
            sr.setCheckType(mr.getCheckType());
            sr.setSortNo(sort++);
            sr.setCreatedAt(LocalDateTime.now());
            schemeRuleMapper.insert(sr);
        }
        if (sort == 0) {
            throw new BusinessException(400, "请至少选择一条质量规则");
        }
    }

    private void syncQualityTask(GovQualityScheme s) {
        List<GovQualitySchemeRule> rules = listRules(s.getId());
        GovQualityModel model = modelMapper.selectById(s.getModelId());
        if (model == null) {
            throw new BusinessException(404, "质量模型不存在");
        }
        GovQualityTask task;
        if (s.getQualityTaskId() != null) {
            task = taskMapper.selectById(s.getQualityTaskId());
        } else {
            task = null;
        }
        if (task == null) {
            task = new GovQualityTask();
            task.setCreatedAt(LocalDateTime.now());
            task.setCreatedBy(s.getCreatedBy());
        }
        task.setTaskName("方案·" + s.getSchemeName());
        task.setDescription("质量方案 #" + s.getId());
        task.setStatus("READY");
        task.setScheduleType("CRON");
        task.setCronExpr(s.getCronExpr());
        task.setDatasourceId(model.getDatasourceId());
        task.setUpdatedAt(LocalDateTime.now());
        if (task.getId() == null) {
            taskMapper.insert(task);
            s.setQualityTaskId(task.getId());
            schemeMapper.updateById(s);
        } else {
            taskMapper.updateById(task);
        }
        detailMapper.delete(new LambdaQueryWrapper<GovQualityTaskDetail>()
                .eq(GovQualityTaskDetail::getTaskId, task.getId()));
        int i = 0;
        for (GovQualitySchemeRule r : rules) {
            GovQualityModelRule mr = modelRuleMapper.selectById(r.getModelRuleId());
            String checkType = r.getCheckType();
            String tableName = r.getTableName();
            String fieldNames = r.getFieldNames();
            Long catalogRuleId = null;
            if (mr != null) {
                catalogRuleId = mr.getRuleCatalogId();
                if (checkType == null) checkType = mr.getCheckType();
                if (tableName == null) tableName = mr.getTableName();
                if (fieldNames == null) fieldNames = mr.getFieldNames();
            }
            GovQualityTaskDetail d = new GovQualityTaskDetail();
            d.setTaskId(task.getId());
            d.setRuleId(catalogRuleId != null ? catalogRuleId : 0L);
            d.setTargetTable(tableName);
            d.setTargetColumn(firstField(fieldNames));
            d.setCheckType(checkType == null ? "NULL_CHECK" : checkType);
            d.setSortOrder(i++);
            d.setStatus("ENABLED");
            d.setCreatedAt(LocalDateTime.now());
            d.setUpdatedAt(LocalDateTime.now());
            detailMapper.insert(d);
        }
    }

    private List<GovQualitySchemeRule> listRules(Long schemeId) {
        return schemeRuleMapper.selectList(new LambdaQueryWrapper<GovQualitySchemeRule>()
                .eq(GovQualitySchemeRule::getSchemeId, schemeId)
                .orderByAsc(GovQualitySchemeRule::getSortNo)
                .orderByAsc(GovQualitySchemeRule::getId));
    }

    private Map<String, Object> toMap(GovQualityScheme s, boolean withRules) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", s.getId());
        row.put("schemeName", s.getSchemeName());
        row.put("modelId", s.getModelId());
        row.put("modelName", s.getModelName());
        row.put("executorAddress", s.getExecutorAddress());
        row.put("cronExpr", s.getCronExpr());
        row.put("cycleName", s.getCycleName());
        row.put("description", s.getDescription());
        row.put("scheduleStatus", s.getScheduleStatus());
        row.put("generateStatus", s.getGenerateStatus());
        row.put("execStatus", s.getExecStatus());
        row.put("lastExecAt", s.getLastExecAt());
        row.put("lastMessage", s.getLastMessage());
        row.put("qualityTaskId", s.getQualityTaskId());
        row.put("dsProjectCode", s.getDsProjectCode());
        row.put("dsDefinitionCode", s.getDsDefinitionCode());
        row.put("dsScheduleId", s.getDsScheduleId());
        row.put("createdBy", s.getCreatedBy());
        row.put("createdAt", s.getCreatedAt());
        row.put("updatedAt", s.getUpdatedAt());
        if (withRules) {
            List<Map<String, Object>> rules = new ArrayList<>();
            for (GovQualitySchemeRule r : listRules(s.getId())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.getId());
                m.put("modelRuleId", r.getModelRuleId());
                m.put("ruleTypeName", r.getRuleTypeName());
                m.put("ruleName", r.getRuleName());
                m.put("tableName", r.getTableName());
                m.put("fieldNames", r.getFieldNames());
                m.put("checkType", r.getCheckType());
                rules.add(m);
            }
            row.put("rules", rules);
        }
        return row;
    }

    private boolean isDsAvailable() {
        return integrationProperties.isEnabled() && dsClient.isHealthy();
    }

    private String buildCallbackScript(Long schemeId) {
        String base = integrationProperties.getDs().getCallbackBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String token = resolveCallbackToken();
        String url = base + "/api/v1/governance/quality/schemes/" + schemeId + "/run-callback";
        return "curl -sf -X POST \"" + url + "\" "
                + "-H \"X-Ds-Callback-Token: " + token + "\" "
                + "-H \"Content-Type: application/json\" "
                + "-d \"{}\" "
                + "|| exit 1";
    }

    private String resolveCallbackToken() {
        String token = integrationProperties.getDs().getCallbackToken();
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        String pwd = integrationProperties.getDs().getPassword();
        return pwd == null ? "chengde-quality-callback" : pwd;
    }

    private void assertCallbackToken(String token) {
        String expected = resolveCallbackToken();
        if (token == null || !expected.equals(token.trim())) {
            throw new BusinessException(403, "DS 回调令牌无效");
        }
    }

    private void offlineDsQuietly(GovQualityScheme s) {
        if (s.getDsProjectCode() == null || s.getDsScheduleId() == null) {
            return;
        }
        if (!isDsAvailable()) {
            return;
        }
        try {
            dsClient.offlineSchedule(s.getDsProjectCode(), s.getDsScheduleId());
        } catch (Exception e) {
            log.warn("offline scheme schedule id={}: {}", s.getId(), e.getMessage());
        }
    }

    private GovQualityScheme require(Long id) {
        GovQualityScheme s = schemeMapper.selectById(id);
        if (s == null) {
            throw new BusinessException(404, "质量方案不存在: " + id);
        }
        return s;
    }

    private void audit(UserPrincipal operator, String action, Long id, String detail) {
        Long uid = operator == null ? null : operator.getUserId();
        String uname = operator == null ? "system" : operator.getUsername();
        Long orgId = operator == null ? null : operator.getOrgId();
        auditService.log(uid, uname, orgId, action, "gov_quality_scheme", String.valueOf(id), detail);
    }

    private static String firstField(String fieldNames) {
        if (fieldNames == null || fieldNames.isBlank()) {
            return null;
        }
        String[] parts = fieldNames.split("[,;\\s]+");
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p.trim();
            }
        }
        return null;
    }

    private static String safeName(String name) {
        if (name == null) return "scheme";
        return name.replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5_\\-]", "_");
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static Long toLong(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        return Long.valueOf(String.valueOf(v));
    }
}
