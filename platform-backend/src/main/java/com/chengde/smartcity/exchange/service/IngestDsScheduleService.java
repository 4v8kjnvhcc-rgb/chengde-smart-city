package com.chengde.smartcity.exchange.service;

import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.ds.DolphinSchedulerClient;
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
 * 库表汇聚任务 DolphinScheduler 定时：启动=调度上线，停止=调度下线；回调触发 Carte 执行。
 */
@Service
public class IngestDsScheduleService {

    private static final Logger log = LoggerFactory.getLogger(IngestDsScheduleService.class);
    private static final String INGEST_PROJECT = "chengde_ingest_collect";

    private final DolphinSchedulerClient dsClient;
    private final IngIngestTaskMapper taskMapper;
    private final IntegrationProperties integrationProperties;
    private final AuditService auditService;
    private final TableIngestEngine tableIngestEngine;

    public IngestDsScheduleService(DolphinSchedulerClient dsClient,
                                   IngIngestTaskMapper taskMapper,
                                   IntegrationProperties integrationProperties,
                                   AuditService auditService,
                                   @Lazy TableIngestEngine tableIngestEngine) {
        this.dsClient = dsClient;
        this.taskMapper = taskMapper;
        this.integrationProperties = integrationProperties;
        this.auditService = auditService;
        this.tableIngestEngine = tableIngestEngine;
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
        return pwd == null ? "chengde-ingest-callback" : pwd;
    }

    public void assertCallbackToken(String token) {
        String expected = resolveCallbackToken();
        if (token == null || !expected.equals(token.trim())) {
            throw new BusinessException(403, "DS 回调令牌无效");
        }
    }

    /** 启动定时：创建/更新 DS 流程并上线调度。 */
    @Transactional
    public Map<String, Object> startSchedule(UserPrincipal operator, IngIngestTask task) {
        if (task.getScheduleCron() == null || task.getScheduleCron().isBlank()) {
            throw new BusinessException(400, "请先配置执行周期后再启动");
        }
        if (isRealtime(task)) {
            throw new BusinessException(400, "实时任务不支持定时启动，请使用手动执行");
        }
        if (!isDsAvailable()) {
            throw new BusinessException(502, "DolphinScheduler 不可用，无法启动定时任务");
        }
        // 若已有调度，先下线再建（简化为重建）
        offlineScheduleQuiet(task);

        long projectCode = dsClient.ensureProject(INGEST_PROJECT);
        String tenant = dsClient.resolveTenant();
        String defName = "库表汇聚_" + safeName(task.getTaskName()) + "_" + task.getId();
        String script = buildTriggerScript(task.getId());
        long definitionCode = dsClient.createAndReleaseShellChain(
                projectCode, defName, List.of("库表汇聚执行"), List.of(script), tenant);
        int scheduleId = dsClient.createAndOnlineSchedule(projectCode, definitionCode, task.getScheduleCron());

        task.setDsProjectCode(projectCode);
        task.setDsDefinitionCode(definitionCode);
        task.setDsScheduleId(scheduleId);
        task.setEnabled(1);
        task.setLifecycleStatus("STARTED");
        taskMapper.updateById(task);

        auditService.log(operator != null ? operator.getUserId() : null,
                operator != null ? operator.getUsername() : "system",
                operator != null ? operator.getOrgId() : null,
                "ING_JOB_DS_START", "ing_ingest_task", String.valueOf(task.getId()),
                "project=" + projectCode + " def=" + definitionCode + " schedule=" + scheduleId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", task.getId());
        out.put("lifecycleStatus", "STARTED");
        out.put("projectCode", projectCode);
        out.put("definitionCode", definitionCode);
        out.put("scheduleId", scheduleId);
        return out;
    }

    /** 停止定时：下线 DS 调度。 */
    @Transactional
    public Map<String, Object> stopSchedule(UserPrincipal operator, IngIngestTask task) {
        offlineScheduleQuiet(task);
        task.setEnabled(0);
        task.setLifecycleStatus("STOPPED");
        taskMapper.updateById(task);
        auditService.log(operator != null ? operator.getUserId() : null,
                operator != null ? operator.getUsername() : "system",
                operator != null ? operator.getOrgId() : null,
                "ING_JOB_DS_STOP", "ing_ingest_task", String.valueOf(task.getId()), "offline");
        return Map.of("taskId", task.getId(), "lifecycleStatus", "STOPPED");
    }

    public void offlineScheduleQuiet(IngIngestTask task) {
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
        IngIngestTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "接入任务不存在");
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
        Map<String, Object> result = tableIngestEngine.runJobScheduled(taskId, dsInstanceId);
        Object statusObj = result.get("status");
        String st = statusObj == null ? "" : String.valueOf(statusObj);
        if ("FAILED".equalsIgnoreCase(st) || "PARTIAL".equalsIgnoreCase(st)) {
            throw new BusinessException(500,
                    String.valueOf(result.getOrDefault("message",
                            result.getOrDefault("lastRunMessage", "归集执行失败"))));
        }
        auditService.log(null, "dolphinscheduler", null,
                "ING_JOB_DS_CALLBACK", "ing_ingest_task", String.valueOf(taskId),
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
        String url = base + "/api/v1/exchange/ingestion/collect/jobs/" + taskId + "/ds-trigger";
        return "curl -sf -X POST \"" + url + "\" "
                + "-H \"X-Ds-Callback-Token: " + token + "\" "
                + "-H \"Content-Type: application/json\" "
                + "-d \"{\\\"dsInstanceId\\\":${system.task.instance.id}}\" "
                + "|| exit 1";
    }

    private boolean isRealtime(IngIngestTask task) {
        String json = task.getConfigJson();
        if (json == null || json.isBlank()) {
            return false;
        }
        return json.contains("\"syncMode\":\"REALTIME\"") || json.contains("\"syncMode\": \"REALTIME\"");
    }

    private String safeName(String name) {
        if (name == null) return "task";
        return name.replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5_\\-]", "_");
    }
}
