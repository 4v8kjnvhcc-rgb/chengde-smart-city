package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.ds.DolphinSchedulerClient;
import com.chengde.smartcity.masterdata.entity.GovMetaCollectRun;
import com.chengde.smartcity.masterdata.entity.GovMetaCollectTask;
import com.chengde.smartcity.masterdata.mapper.GovMetaCollectRunMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaCollectTaskMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 元数据采集 DolphinScheduler 编排：定时任务发布/下线、实例状态同步、DS 回调鉴权。
 */
@Service
public class MetaCollectDsService {

    private static final Logger log = LoggerFactory.getLogger(MetaCollectDsService.class);
    private static final String META_PROJECT = "chengde_meta_collect";

    private final DolphinSchedulerClient dsClient;
    private final GovMetaCollectTaskMapper taskMapper;
    private final GovMetaCollectRunMapper runMapper;
    private final IntegrationProperties integrationProperties;
    private final AuditService auditService;
    private final MetadataSubsystemService metadataSubsystemService;

    public MetaCollectDsService(DolphinSchedulerClient dsClient,
                                GovMetaCollectTaskMapper taskMapper,
                                GovMetaCollectRunMapper runMapper,
                                IntegrationProperties integrationProperties,
                                AuditService auditService,
                                @Lazy MetadataSubsystemService metadataSubsystemService) {
        this.dsClient = dsClient;
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.integrationProperties = integrationProperties;
        this.auditService = auditService;
        this.metadataSubsystemService = metadataSubsystemService;
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
        return pwd == null ? "chengde-meta-callback" : pwd;
    }

    public void assertCallbackToken(String token) {
        String expected = resolveCallbackToken();
        if (token == null || !expected.equals(token.trim())) {
            throw new BusinessException(403, "DS 回调令牌无效");
        }
    }

    @Transactional
    public Map<String, Object> publishScheduledTask(UserPrincipal operator, Long taskId) {
        GovMetaCollectTask task = requireTask(taskId);
        if (!"SCHEDULED".equalsIgnoreCase(task.getScheduleType())) {
            throw new BusinessException(400, "仅定时任务可发布到 DolphinScheduler");
        }
        if (task.getCronExpr() == null || task.getCronExpr().isBlank()) {
            throw new BusinessException(400, "定时任务须配置执行周期");
        }
        if ("PUBLISHED".equals(task.getPublishStatus())) {
            throw new BusinessException(400, "任务已发布");
        }
        if (!isDsAvailable()) {
            throw new BusinessException(502, "DolphinScheduler 不可用，无法发布定时采集任务");
        }
        long projectCode = dsClient.ensureProject(META_PROJECT);
        String tenant = dsClient.resolveTenant();
        String defName = "元数据采集_" + safeName(task.getTaskName()) + "_" + taskId;
        String script = buildCollectScript(taskId);
        long definitionCode = dsClient.createAndReleaseShellChain(
                projectCode, defName, List.of("元数据采集"), List.of(script), tenant);
        int scheduleId = dsClient.createAndOnlineSchedule(projectCode, definitionCode, task.getCronExpr());

        task.setDsProjectCode(projectCode);
        task.setDsDefinitionCode(definitionCode);
        task.setDsScheduleId(scheduleId);
        task.setPublishStatus("PUBLISHED");
        task.setStatus("READY");
        taskMapper.updateById(task);

        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_COLLECT_DS_PUBLISH", "gov_meta_collect_task", String.valueOf(taskId),
                "project=" + projectCode + " def=" + definitionCode + " schedule=" + scheduleId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", taskId);
        out.put("projectCode", projectCode);
        out.put("definitionCode", definitionCode);
        out.put("scheduleId", scheduleId);
        out.put("publishStatus", "PUBLISHED");
        return out;
    }

    /** 定时采集检测到元数据变更：下线 DS 调度，待维护侧发布定版后再发布恢复。 */
    public void pauseScheduledTaskOnMetadataChange(GovMetaCollectTask task, String reason) {
        if (task == null || !"SCHEDULED".equalsIgnoreCase(task.getScheduleType())) {
            return;
        }
        GovMetaCollectTask latest = taskMapper.selectById(task.getId());
        if (latest == null) {
            return;
        }
        if ("PUBLISHED".equals(latest.getPublishStatus())
                && latest.getDsProjectCode() != null && latest.getDsScheduleId() != null) {
            try {
                dsClient.offlineSchedule(latest.getDsProjectCode(), latest.getDsScheduleId());
                log.info("paused DS schedule on metadata change taskId={} scheduleId={}",
                        latest.getId(), latest.getDsScheduleId());
            } catch (Exception e) {
                log.warn("pause DS schedule taskId={}: {}", latest.getId(), e.getMessage());
            }
        }
        latest.setPublishStatus("DRAFT");
        latest.setLastMessage(reason == null ? "检测到元数据变更，调度已暂停，请发布定版" : reason);
        taskMapper.updateById(latest);
        auditService.log(null, "system", null,
                "META_COLLECT_DS_PAUSE_CHANGE", "gov_meta_collect_task", String.valueOf(latest.getId()),
                latest.getLastMessage());
    }

    @Transactional
    public Map<String, Object> unpublishScheduledTask(UserPrincipal operator, Long taskId) {
        GovMetaCollectTask task = requireTask(taskId);
        if (!"PUBLISHED".equals(task.getPublishStatus())) {
            throw new BusinessException(400, "任务未发布");
        }
        if (task.getDsProjectCode() != null && task.getDsScheduleId() != null) {
            try {
                dsClient.offlineSchedule(task.getDsProjectCode(), task.getDsScheduleId());
            } catch (Exception e) {
                log.warn("offline DS schedule taskId={}: {}", taskId, e.getMessage());
            }
        }
        task.setPublishStatus("DRAFT");
        taskMapper.updateById(task);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_COLLECT_DS_UNPUBLISH", "gov_meta_collect_task", String.valueOf(taskId), "offline");
        return Map.of("taskId", taskId, "publishStatus", "DRAFT");
    }

    @Transactional
    public Map<String, Object> runFromDsCallback(Long taskId, String token, Long dsInstanceId) {
        assertCallbackToken(token);
        GovMetaCollectTask task = requireTask(taskId);
        if (dsInstanceId == null && task.getDsProjectCode() != null && task.getDsDefinitionCode() != null) {
            try {
                dsInstanceId = dsClient.latestInstanceId(task.getDsProjectCode(), task.getDsDefinitionCode());
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> result = metadataSubsystemService.runTaskBySystemScheduled(taskId, dsInstanceId);
        auditService.log(null, "dolphinscheduler", null,
                "META_COLLECT_DS_CALLBACK", "gov_meta_collect_task", String.valueOf(taskId),
                "instance=" + dsInstanceId);
        return result;
    }

    /** 监控页轮询：同步 DS 实例状态到 run 记录。 */
    public void syncRunningDsInstances() {
        if (!isDsAvailable()) {
            return;
        }
        List<GovMetaCollectRun> runs = runMapper.selectList(new LambdaQueryWrapper<GovMetaCollectRun>()
                .eq(GovMetaCollectRun::getStatus, "RUNNING")
                .isNotNull(GovMetaCollectRun::getDsInstanceId)
                .orderByDesc(GovMetaCollectRun::getId)
                .last("LIMIT 30"));
        for (GovMetaCollectRun run : runs) {
            GovMetaCollectTask task = taskMapper.selectById(run.getTaskId());
            if (task == null || task.getDsProjectCode() == null || run.getDsInstanceId() == null) {
                continue;
            }
            try {
                Map<String, Object> st = dsClient.instanceStatus(task.getDsProjectCode(), run.getDsInstanceId());
                String dsState = String.valueOf(st.getOrDefault("state", ""));
                run.setDsState(dsState);
                String mapped = DolphinSchedulerClient.mapDsStateToRunStatus(dsState);
                if (!"RUNNING".equals(mapped) && "RUNNING".equals(run.getStatus())) {
                    run.setStatus(mapped);
                    if (run.getEndedAt() == null) {
                        run.setEndedAt(LocalDateTime.now());
                    }
                    runMapper.updateById(run);
                    if (task.getStatus().equals("RUNNING")) {
                        task.setStatus("READY");
                        task.setLastMessage("ds:" + dsState);
                        taskMapper.updateById(task);
                    }
                } else {
                    runMapper.updateById(run);
                }
            } catch (Exception e) {
                log.debug("sync DS instance runId={}: {}", run.getId(), e.getMessage());
            }
        }
    }

    public void stopDsInstanceIfPresent(GovMetaCollectRun run, GovMetaCollectTask task) {
        if (run == null || task == null || run.getDsInstanceId() == null || task.getDsProjectCode() == null) {
            return;
        }
        if (!isDsAvailable()) {
            return;
        }
        try {
            dsClient.stopInstance(task.getDsProjectCode(), run.getDsInstanceId());
        } catch (Exception e) {
            log.warn("stop DS instance runId={} instanceId={}: {}",
                    run.getId(), run.getDsInstanceId(), e.getMessage());
        }
    }

    private String buildCollectScript(Long taskId) {
        String base = integrationProperties.getDs().getCallbackBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String token = resolveCallbackToken();
        String url = base + "/api/v1/governance/platform/metadata/collect/tasks/" + taskId + "/run-callback";
        return "curl -sf -X POST \"" + url + "\" "
                + "-H \"X-Ds-Callback-Token: " + token + "\" "
                + "-H \"Content-Type: application/json\" "
                + "-d \"{\\\"dsInstanceId\\\":${system.task.instance.id}}\" "
                + "|| exit 1";
    }

    private GovMetaCollectTask requireTask(Long id) {
        GovMetaCollectTask task = taskMapper.selectById(id);
        if (task == null || "DELETED".equals(task.getStatus())) {
            throw new BusinessException(404, "采集任务不存在");
        }
        return task;
    }

    private String safeName(String name) {
        if (name == null) return "task";
        return name.replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5_\\-]", "_");
    }
}
