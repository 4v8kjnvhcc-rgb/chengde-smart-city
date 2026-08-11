package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.entity.IngIngestTaskRun;
import com.chengde.smartcity.exchange.entity.IngIngestTaskVersion;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskRunMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskVersionMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库表接入任务生命周期：上线/下线/启停、版本快照、运行史查询。
 */
@Service
public class IngestJobLifecycleService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IngIngestTaskMapper taskMapper;
    private final IngIngestTaskVersionMapper versionMapper;
    private final IngIngestTaskRunMapper runMapper;
    private final IngestDsScheduleService dsScheduleService;
    private final TableIngestEngine tableIngestEngine;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IngestJobLifecycleService(IngIngestTaskMapper taskMapper,
                                     IngIngestTaskVersionMapper versionMapper,
                                     IngIngestTaskRunMapper runMapper,
                                     IngestDsScheduleService dsScheduleService,
                                     @Lazy TableIngestEngine tableIngestEngine,
                                     AuditService auditService) {
        this.taskMapper = taskMapper;
        this.versionMapper = versionMapper;
        this.runMapper = runMapper;
        this.dsScheduleService = dsScheduleService;
        this.tableIngestEngine = tableIngestEngine;
        this.auditService = auditService;
    }

    public IngIngestTask require(Long id) {
        IngIngestTask t = taskMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "接入任务不存在");
        }
        return t;
    }

    private void assertNotRunning(IngIngestTask task) {
        if ("RUNNING".equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(409, "任务运行中，请稍候或先重置");
        }
    }

    private String life(IngIngestTask task) {
        String s = task.getLifecycleStatus();
        return s == null || s.isBlank() ? "DRAFT" : s.toUpperCase();
    }

    @Transactional
    public IngIngestTask publish(UserPrincipal operator, Long id) {
        IngIngestTask task = require(id);
        assertNotRunning(task);
        String lc = life(task);
        if (!"DRAFT".equals(lc) && !"OFFLINE".equals(lc)) {
            throw new BusinessException(400, "仅草稿或已下线任务可上线，当前状态：" + lc);
        }
        if (task.getTaskName() == null || task.getTaskName().isBlank()) {
            throw new BusinessException(400, "任务名称不能为空");
        }
        if (task.getConfigJson() == null || task.getConfigJson().isBlank()) {
            throw new BusinessException(400, "请先完善接入配置后再上线");
        }
        int next = (task.getVersionNo() == null || task.getVersionNo() < 1) ? 1 : task.getVersionNo() + 1;
        task.setVersionNo(next);
        task.setLifecycleStatus("ONLINE");
        task.setEnabled(0);
        task.setPublishedBy(operator != null ? operator.getUsername() : null);
        task.setPublishedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        IngIngestTaskVersion ver = new IngIngestTaskVersion();
        ver.setTaskId(task.getId());
        ver.setVersionNo(next);
        ver.setSnapshotJson(toSnapshotJson(task));
        ver.setChangeSummary("上线 v" + next);
        ver.setPublishedBy(task.getPublishedBy());
        ver.setPublishedAt(task.getPublishedAt());
        versionMapper.insert(ver);

        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_JOB_PUBLISH", "ing_ingest_task", String.valueOf(id), "v" + next);
        return task;
    }

    @Transactional
    public IngIngestTask offline(UserPrincipal operator, Long id) {
        IngIngestTask task = require(id);
        assertNotRunning(task);
        String lc = life(task);
        if ("STARTED".equals(lc)) {
            throw new BusinessException(400, "已启动任务请先停止后再下线");
        }
        if (!"ONLINE".equals(lc) && !"STOPPED".equals(lc)) {
            throw new BusinessException(400, "仅已上线或已停止任务可下线，当前状态：" + lc);
        }
        dsScheduleService.offlineScheduleQuiet(task);
        task.setLifecycleStatus("OFFLINE");
        task.setEnabled(0);
        taskMapper.updateById(task);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_JOB_OFFLINE", "ing_ingest_task", String.valueOf(id), task.getTaskName());
        return task;
    }

    @Transactional
    public Map<String, Object> start(UserPrincipal operator, Long id) {
        IngIngestTask task = require(id);
        assertNotRunning(task);
        String lc = life(task);
        if (!"ONLINE".equals(lc) && !"STOPPED".equals(lc)) {
            throw new BusinessException(400, "仅已上线或已停止任务可启动，当前状态：" + lc);
        }
        return dsScheduleService.startSchedule(operator, task);
    }

    @Transactional
    public Map<String, Object> stop(UserPrincipal operator, Long id) {
        IngIngestTask task = require(id);
        assertNotRunning(task);
        String lc = life(task);
        if (!"STARTED".equals(lc) && !"ONLINE".equals(lc)) {
            throw new BusinessException(400, "仅已启动或已上线任务可停止，当前状态：" + lc);
        }
        return dsScheduleService.stopSchedule(operator, task);
    }

    public Map<String, Object> batch(UserPrincipal operator, String action, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请选择任务");
        }
        String act = action == null ? "" : action.toLowerCase();
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                switch (act) {
                    case "run" -> tableIngestEngine.runJob(operator, id);
                    case "start" -> start(operator, id);
                    case "stop" -> stop(operator, id);
                    default -> throw new BusinessException(400, "不支持的批量操作: " + action);
                }
                ok++;
            } catch (Exception e) {
                errors.add(id + ": " + e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", ok);
        out.put("failed", errors.size());
        out.put("errors", errors);
        return out;
    }

    public List<IngIngestTaskVersion> listVersions(Long id) {
        require(id);
        return versionMapper.selectList(new LambdaQueryWrapper<IngIngestTaskVersion>()
                .eq(IngIngestTaskVersion::getTaskId, id)
                .orderByDesc(IngIngestTaskVersion::getVersionNo));
    }

    public IngIngestTaskVersion getVersion(Long id, Integer versionNo) {
        require(id);
        IngIngestTaskVersion ver = versionMapper.selectOne(new LambdaQueryWrapper<IngIngestTaskVersion>()
                .eq(IngIngestTaskVersion::getTaskId, id)
                .eq(IngIngestTaskVersion::getVersionNo, versionNo));
        if (ver == null) {
            throw new BusinessException(404, "版本不存在: " + versionNo);
        }
        return ver;
    }

    public List<IngIngestTaskRun> listRuns(Long taskId, String runStatus, LocalDateTime from, LocalDateTime to) {
        require(taskId);
        LambdaQueryWrapper<IngIngestTaskRun> q = new LambdaQueryWrapper<IngIngestTaskRun>()
                .eq(IngIngestTaskRun::getTaskId, taskId)
                .orderByDesc(IngIngestTaskRun::getStartedAt)
                .orderByDesc(IngIngestTaskRun::getId);
        if (runStatus != null && !runStatus.isBlank() && !"ALL".equalsIgnoreCase(runStatus)) {
            q.eq(IngIngestTaskRun::getRunStatus, runStatus.toUpperCase());
        }
        if (from != null) {
            q.ge(IngIngestTaskRun::getStartedAt, from);
        }
        if (to != null) {
            q.le(IngIngestTaskRun::getStartedAt, to);
        }
        return runMapper.selectList(q);
    }

    public IngIngestTaskRun getRun(Long runId) {
        IngIngestTaskRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(404, "运行记录不存在");
        }
        return run;
    }

    public Map<String, Object> getRunDetail(Long runId) {
        IngIngestTaskRun run = getRun(runId);
        IngIngestTask task = require(run.getTaskId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", run.getId());
        out.put("taskId", task.getId());
        out.put("taskName", task.getTaskName());
        out.put("triggerType", run.getTriggerType());
        out.put("runStatus", run.getRunStatus());
        out.put("scheduleResult", run.getScheduleResult());
        out.put("collectedRows", run.getCollectedRows());
        out.put("insertRows", run.getInsertRows());
        out.put("updateRows", run.getUpdateRows());
        out.put("tableCount", run.getTableCount());
        out.put("startedAt", run.getStartedAt());
        out.put("finishedAt", run.getFinishedAt());
        out.put("durationMs", run.getDurationMs());
        out.put("scheduleTime", run.getScheduleTime());
        out.put("dsInstanceId", run.getDsInstanceId());
        out.put("message", run.getMessage());
        out.put("errorDetail", run.getErrorDetail());
        out.put("logText", run.getLogText());
        out.put("detailJson", run.getDetailJson());
        out.put("accessMode", task.getAccessMode());
        out.put("targetTable", task.getTargetTable());
        out.put("sourceId", task.getSourceId());
        // 简易增删改汇总
        out.put("added", run.getInsertRows() != null ? run.getInsertRows() : 0);
        out.put("deleted", 0);
        out.put("changed", run.getUpdateRows() != null ? run.getUpdateRows() : 0);
        return out;
    }

    /** 执行开始时创建运行记录 */
    public Long beginRun(Long taskId, String triggerType, Long dsInstanceId) {
        IngIngestTaskRun run = new IngIngestTaskRun();
        run.setTaskId(taskId);
        run.setTriggerType(triggerType == null ? "MANUAL" : triggerType.toUpperCase());
        run.setRunStatus("RUNNING");
        run.setScheduleResult("SCHEDULE".equalsIgnoreCase(triggerType) ? "SUCCESS" : null);
        run.setStartedAt(LocalDateTime.now());
        if ("SCHEDULE".equalsIgnoreCase(triggerType)) {
            run.setScheduleTime(LocalDateTime.now());
        }
        run.setDsInstanceId(dsInstanceId);
        run.setCreatedAt(LocalDateTime.now());
        runMapper.insert(run);
        return run.getId();
    }

    public void finishRun(Long runId, String runStatus, Map<String, Object> result, String errorDetail) {
        if (runId == null) {
            return;
        }
        IngIngestTaskRun run = runMapper.selectById(runId);
        if (run == null) {
            return;
        }
        run.setRunStatus(runStatus);
        run.setFinishedAt(LocalDateTime.now());
        if (run.getStartedAt() != null) {
            run.setDurationMs(java.time.Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis());
        }
        if (result != null) {
            Object rows = result.get("collectedRows");
            if (rows == null) rows = result.get("linesOutput");
            if (rows == null) rows = result.get("rows");
            Long collected = longVal(rows);
            run.setCollectedRows(collected);
            run.setInsertRows(collected);
            run.setUpdateRows(0L);
            if (result.get("tableCount") != null) {
                run.setTableCount(intVal(result.get("tableCount")));
            } else if (result.get("tables") instanceof List<?> list) {
                run.setTableCount(list.size());
            } else {
                run.setTableCount(1);
            }
            Object msg = result.get("message");
            if (msg == null) msg = result.get("lastRunMessage");
            if (msg != null) {
                run.setMessage(String.valueOf(msg));
            }
            if (result.get("durationMs") != null) {
                run.setDurationMs(longVal(result.get("durationMs")));
            }
            try {
                run.setDetailJson(objectMapper.writeValueAsString(result));
                run.setLogText(buildLogText(run, result, errorDetail));
            } catch (Exception ignored) {
            }
        }
        if (errorDetail != null && !errorDetail.isBlank()) {
            run.setErrorDetail(errorDetail.length() > 2000 ? errorDetail.substring(0, 2000) : errorDetail);
            if (run.getMessage() == null) {
                run.setMessage("汇聚失败");
            }
            if ("SCHEDULE".equalsIgnoreCase(run.getTriggerType())) {
                run.setScheduleResult("FAILED");
            }
        } else if ("SCHEDULE".equalsIgnoreCase(run.getTriggerType())
                && ("SUCCESS".equalsIgnoreCase(runStatus) || "PARTIAL".equalsIgnoreCase(runStatus))) {
            run.setScheduleResult("SUCCESS");
        }
        runMapper.updateById(run);
    }

    private String buildLogText(IngIngestTaskRun run, Map<String, Object> result, String error) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(LocalDateTime.now().format(DT)).append("] trigger=").append(run.getTriggerType());
        if (run.getDsInstanceId() != null) {
            sb.append(" dsInstance=").append(run.getDsInstanceId());
        }
        sb.append('\n');
        if (result != null) {
            sb.append("result=").append(result).append('\n');
        }
        if (error != null) {
            sb.append("error=").append(error).append('\n');
        }
        return sb.toString();
    }

    private String toSnapshotJson(IngIngestTask task) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("taskName", task.getTaskName());
            snap.put("accessMode", task.getAccessMode());
            snap.put("sourceId", task.getSourceId());
            snap.put("tableId", task.getTableId());
            snap.put("targetTable", task.getTargetTable());
            snap.put("writeMode", task.getWriteMode());
            snap.put("scheduleCron", task.getScheduleCron());
            snap.put("configJson", task.getConfigJson());
            snap.put("versionNo", task.getVersionNo());
            snap.put("lifecycleStatus", task.getLifecycleStatus());
            snap.put("publishedBy", task.getPublishedBy());
            snap.put("publishedAt", task.getPublishedAt() == null ? null : task.getPublishedAt().toString());
            return objectMapper.writeValueAsString(snap);
        } catch (Exception e) {
            throw new BusinessException(500, "版本快照序列化失败");
        }
    }

    private static Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private static Integer intVal(Object v) {
        Long l = longVal(v);
        return l == null ? null : l.intValue();
    }
}
