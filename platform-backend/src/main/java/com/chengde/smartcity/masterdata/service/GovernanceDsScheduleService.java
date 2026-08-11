package com.chengde.smartcity.masterdata.service;

import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.ds.DolphinSchedulerClient;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTask;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 治理/融合任务 DolphinScheduler 定时：启动=调度上线，停止=调度下线；回调触发 Carte。
 */
@Service
public class GovernanceDsScheduleService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceDsScheduleService.class);
    private static final String GOV_PROJECT = "chengde_governance_etl";

    private final DolphinSchedulerClient dsClient;
    private final GovGovernanceTaskMapper taskMapper;
    private final IntegrationProperties integrationProperties;
    private final AuditService auditService;
    private final GovernanceTaskService taskService;
    private final KettleExecuteService kettleExecuteService;

    public GovernanceDsScheduleService(DolphinSchedulerClient dsClient,
                                       GovGovernanceTaskMapper taskMapper,
                                       IntegrationProperties integrationProperties,
                                       AuditService auditService,
                                       @Lazy GovernanceTaskService taskService,
                                       KettleExecuteService kettleExecuteService) {
        this.dsClient = dsClient;
        this.taskMapper = taskMapper;
        this.integrationProperties = integrationProperties;
        this.auditService = auditService;
        this.taskService = taskService;
        this.kettleExecuteService = kettleExecuteService;
    }

    public boolean isDsAvailable() {
        return integrationProperties.isEnabled() && dsClient.isHealthy();
    }

    public String resolveCallbackToken() {
        String token = integrationProperties.getDs().getCallbackToken();
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        String pwd = integrationProperties.getDs().getPassword();
        return pwd == null ? "chengde-gov-callback" : pwd;
    }

    public void assertCallbackToken(String token) {
        String expected = resolveCallbackToken();
        if (token == null || !expected.equals(token.trim())) {
            throw new BusinessException(403, "DS 回调令牌无效");
        }
    }

    /** 解析用于 DS 的 Cron：优先 scheduleCron；SIMPLE 按起始时间转每日一次。 */
    public String resolveCronForDs(GovGovernanceTask task) {
        if (task.getScheduleCron() != null && !task.getScheduleCron().isBlank()) {
            return task.getScheduleCron().trim();
        }
        if ("SIMPLE".equalsIgnoreCase(task.getScheduleMode()) && task.getStartTime() != null) {
            int interval = task.getIntervalValue() == null ? 1 : task.getIntervalValue();
            String unit = task.getTimeUnit() == null ? "DAY" : task.getTimeUnit().toUpperCase();
            if (interval != 1 || !"DAY".equals(unit)) {
                throw new BusinessException(400,
                        "简单调度发布到 DolphinScheduler 仅支持每日一次，请改用执行周期（Cron）");
            }
            var t = task.getStartTime();
            return String.format("0 %d %d %d * * ?", t.getSecond(), t.getMinute(), t.getHour());
        }
        throw new BusinessException(400, "请先配置执行周期（Cron）后再启动定时");
    }

    @Transactional
    public Map<String, Object> startSchedule(UserPrincipal operator, GovGovernanceTask task) {
        String cron = resolveCronForDs(task);
        if (!isDsAvailable()) {
            throw new BusinessException(502, "DolphinScheduler 不可用，无法启动定时任务");
        }
        offlineScheduleQuiet(task);

        long projectCode = dsClient.ensureProject(GOV_PROJECT);
        String tenant = dsClient.resolveTenant();
        String domain = task.getTaskDomain() == null ? "GOVERNANCE" : task.getTaskDomain();
        String defName = domain + "_" + safeName(task.getTaskName()) + "_" + task.getId();
        String script = buildTriggerScript(task.getId());
        long definitionCode = dsClient.createAndReleaseShellChain(
                projectCode, defName, List.of("治理融合执行"), List.of(script), tenant);
        int scheduleId = dsClient.createAndOnlineSchedule(projectCode, definitionCode, cron);

        task.setDsProjectCode(projectCode);
        task.setDsDefinitionCode(definitionCode);
        task.setDsScheduleId(scheduleId);
        task.setScheduleEnabled(1);
        if (task.getScheduleCron() == null || task.getScheduleCron().isBlank()) {
            task.setScheduleCron(cron);
        }
        taskMapper.updateById(task);

        auditService.log(operator != null ? operator.getUserId() : null,
                operator != null ? operator.getUsername() : "system",
                operator != null ? operator.getOrgId() : null,
                "GOV_TASK_DS_START", "gov_governance_task", String.valueOf(task.getId()),
                "project=" + projectCode + " def=" + definitionCode + " schedule=" + scheduleId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", task.getId());
        out.put("scheduleEnabled", true);
        out.put("projectCode", projectCode);
        out.put("definitionCode", definitionCode);
        out.put("scheduleId", scheduleId);
        out.put("cron", cron);
        return out;
    }

    @Transactional
    public Map<String, Object> stopSchedule(UserPrincipal operator, GovGovernanceTask task) {
        offlineScheduleQuiet(task);
        task.setScheduleEnabled(0);
        task.setDsScheduleId(null);
        task.setNextRunAt(null);
        taskMapper.updateById(task);
        auditService.log(operator != null ? operator.getUserId() : null,
                operator != null ? operator.getUsername() : "system",
                operator != null ? operator.getOrgId() : null,
                "GOV_TASK_DS_STOP", "gov_governance_task", String.valueOf(task.getId()), "offline");
        return Map.of("taskId", task.getId(), "scheduleEnabled", false);
    }

    public void offlineScheduleQuiet(GovGovernanceTask task) {
        if (task.getDsProjectCode() != null && task.getDsScheduleId() != null && isDsAvailable()) {
            try {
                dsClient.offlineSchedule(task.getDsProjectCode(), task.getDsScheduleId());
            } catch (Exception e) {
                log.warn("offline DS schedule taskId={}: {}", task.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public Map<String, Object> runFromDsCallback(Long taskId, String token, Long dsInstanceId) {
        assertCallbackToken(token);
        GovGovernanceTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "治理/融合任务不存在");
        }
        if (dsInstanceId == null && task.getDsProjectCode() != null && task.getDsDefinitionCode() != null) {
            try {
                dsInstanceId = dsClient.latestInstanceId(task.getDsProjectCode(), task.getDsDefinitionCode());
            } catch (Exception ignored) {
            }
        }
        if (dsInstanceId != null) {
            task.setDsInstanceId(dsInstanceId);
            taskMapper.updateById(task);
        }
        Map<String, Object> started = taskService.run(null, taskId);
        Object statusObj = started.get("status");
        String st = statusObj == null ? "" : String.valueOf(statusObj);
        if ("FAILED".equalsIgnoreCase(st)) {
            throw new BusinessException(500, String.valueOf(started.getOrDefault("message", "治理/融合任务启动失败")));
        }
        Long runId = null;
        Object runIdObj = started.get("runId");
        if (runIdObj != null) {
            try {
                runId = Long.valueOf(String.valueOf(runIdObj));
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> result = started;
        if (runId != null) {
            result = kettleExecuteService.waitForRunTerminal(runId);
            String terminal = String.valueOf(result.getOrDefault("runStatus", result.get("status")));
            if (!"SUCCESS".equalsIgnoreCase(terminal) && !"FINISHED".equalsIgnoreCase(terminal)) {
                throw new BusinessException(500,
                        String.valueOf(result.getOrDefault("runMessage",
                                result.getOrDefault("message", "治理/融合任务执行失败"))));
            }
        }
        auditService.log(null, "dolphinscheduler", null,
                "GOV_TASK_DS_CALLBACK", "gov_governance_task", String.valueOf(taskId),
                "instance=" + dsInstanceId);
        return result;
    }

    private String buildTriggerScript(Long taskId) {
        String base = integrationProperties.getDs().getCallbackBaseUrl();
        if (base == null || base.isBlank()) {
            throw new BusinessException(500, "未配置 app.integration.ds.callback-base-url");
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String token = resolveCallbackToken();
        String url = base + "/api/v1/governance/gov-tasks/" + taskId + "/ds-trigger";
        return "curl -sf -X POST \"" + url + "\" "
                + "-H \"X-Ds-Callback-Token: " + token + "\" "
                + "-H \"Content-Type: application/json\" "
                + "-d \"{\\\"dsInstanceId\\\":${system.task.instance.id}}\" "
                + "|| exit 1";
    }

    private String safeName(String name) {
        if (name == null) return "task";
        return name.replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5_\\-]", "_");
    }
}
