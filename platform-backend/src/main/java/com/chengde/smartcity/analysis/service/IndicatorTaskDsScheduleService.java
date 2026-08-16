package com.chengde.smartcity.analysis.service;

import com.chengde.smartcity.analysis.entity.IndJob;
import com.chengde.smartcity.analysis.mapper.IndJobMapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.ds.DolphinSchedulerClient;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndicatorTaskDsScheduleService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorTaskDsScheduleService.class);
    private static final String DS_PROJECT = "chengde_indicator_calc";

    private final DolphinSchedulerClient dsClient;
    private final IndJobMapper jobMapper;
    private final IntegrationProperties integrationProperties;
    private final AuditService auditService;

    public IndicatorTaskDsScheduleService(DolphinSchedulerClient dsClient,
                                          IndJobMapper jobMapper,
                                          IntegrationProperties integrationProperties,
                                          AuditService auditService) {
        this.dsClient = dsClient;
        this.jobMapper = jobMapper;
        this.integrationProperties = integrationProperties;
        this.auditService = auditService;
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
        return pwd == null ? "chengde-indicator-callback" : pwd;
    }

    public void assertCallbackToken(String token) {
        String expected = resolveCallbackToken();
        if (token == null || !expected.equals(token.trim())) {
            throw new BusinessException(403, "DS 回调令牌无效");
        }
    }

    @Transactional
    public Map<String, Object> startSchedule(UserPrincipal operator, IndJob task) {
        String cron = resolveCron(task);
        if (cron == null || cron.isBlank()) {
            throw new BusinessException(400, "请先配置执行周期后再启动");
        }
        if (!"PUBLISHED".equalsIgnoreCase(task.getPublishStatus())) {
            throw new BusinessException(400, "已下线任务不能启动调度，请先发布指标组");
        }
        if (!isDsAvailable()) {
            throw new BusinessException(502, "DolphinScheduler 不可用，无法启动定时任务");
        }
        offlineScheduleQuiet(task);

        long projectCode = dsClient.ensureProject(DS_PROJECT);
        String tenant = dsClient.resolveTenant();
        String defName = "指标计算_" + safeName(task.getTaskName()) + "_" + task.getId();
        String script = buildTriggerScript(task.getId());
        long definitionCode = dsClient.createAndReleaseShellChain(
                projectCode, defName, List.of("指标计算执行"), List.of(script), tenant);
        int scheduleId = dsClient.createAndOnlineSchedule(projectCode, definitionCode, cron);

        task.setDsProjectCode(projectCode);
        task.setDsDefinitionCode(definitionCode);
        task.setDsScheduleId(scheduleId);
        task.setScheduleCron(cron);
        task.setStatus(3);
        task.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(task);

        auditService.log(operator != null ? operator.getUserId() : null,
                operator != null ? operator.getUsername() : "system",
                operator != null ? operator.getOrgId() : null,
                "ANA_IND_TASK_DS_START", "ind_job", task.getId(),
                "project=" + projectCode + " def=" + definitionCode + " schedule=" + scheduleId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", task.getId());
        out.put("scheduleStatus", "STARTED");
        out.put("projectCode", projectCode);
        out.put("definitionCode", definitionCode);
        out.put("scheduleId", scheduleId);
        return out;
    }

    @Transactional
    public Map<String, Object> stopSchedule(UserPrincipal operator, IndJob task) {
        offlineScheduleQuiet(task);
        task.setStatus(2);
        task.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(task);
        auditService.log(operator != null ? operator.getUserId() : null,
                operator != null ? operator.getUsername() : "system",
                operator != null ? operator.getOrgId() : null,
                "ANA_IND_TASK_DS_STOP", "ind_job", task.getId(), "offline");
        return Map.of("taskId", task.getId(), "scheduleStatus", "STOPPED");
    }

    @Transactional
    public Map<String, Object> startOnce(UserPrincipal operator, IndJob task) {
        if (!isDsAvailable()) {
            throw new BusinessException(502, "DolphinScheduler 不可用");
        }
        if (task.getDsProjectCode() == null || task.getDsDefinitionCode() == null) {
            long projectCode = dsClient.ensureProject(DS_PROJECT);
            String tenant = dsClient.resolveTenant();
            String defName = "指标计算_" + safeName(task.getTaskName()) + "_" + task.getId();
            String script = buildTriggerScript(task.getId());
            long definitionCode = dsClient.createAndReleaseShellChain(
                    projectCode, defName, List.of("指标计算执行"), List.of(script), tenant);
            task.setDsProjectCode(projectCode);
            task.setDsDefinitionCode(definitionCode);
            task.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(task);
        }
        long instanceId = dsClient.startInstance(task.getDsProjectCode(), task.getDsDefinitionCode());
        task.setDsInstanceId(instanceId);
        task.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(task);
        auditService.log(operator != null ? operator.getUserId() : null,
                operator != null ? operator.getUsername() : "system",
                operator != null ? operator.getOrgId() : null,
                "ANA_IND_TASK_DS_RUN", "ind_job", task.getId(),
                "instance=" + instanceId);
        return Map.of("taskId", task.getId(), "dsInstanceId", instanceId);
    }

    public void offlineScheduleQuiet(IndJob task) {
        if (task.getDsProjectCode() != null && task.getDsScheduleId() != null && isDsAvailable()) {
            try {
                dsClient.offlineSchedule(task.getDsProjectCode(), task.getDsScheduleId());
            } catch (Exception e) {
                log.warn("offline DS schedule taskId={}: {}", task.getId(), e.getMessage());
            }
        }
    }

    public String buildTriggerScript(String taskId) {
        String base = integrationProperties.getDs().getCallbackBaseUrl();
        if (base == null || base.isBlank()) {
            throw new BusinessException(500, "未配置 app.integration.ds.callback-base-url");
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String token = resolveCallbackToken();
        String url = base + "/api/v1/analytics/domain/indicator-tasks/" + taskId + "/ds-trigger";
        return "curl -sf -X POST \"" + url + "\" "
                + "-H \"X-Ds-Callback-Token: " + token + "\" "
                + "-H \"Content-Type: application/json\" "
                + "-d \"{\\\"dsInstanceId\\\":${system.task.instance.id}}\" "
                + "|| exit 1";
    }

    static String resolveCron(IndJob task) {
        if (task.getScheduleCron() != null && !task.getScheduleCron().isBlank()) {
            return task.getScheduleCron().trim();
        }
        if (task.getCronCodeId() != null && task.getCronCodeId() == 1) {
            return "0 0 2 * * ?";
        }
        return "0 0 2 1 * ?";
    }

    private String safeName(String name) {
        if (name == null) return "task";
        return name.replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5_\\-]", "_");
    }
}
