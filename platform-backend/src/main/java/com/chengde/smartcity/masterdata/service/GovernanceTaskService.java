package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovGovernanceNodeLog;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTask;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTaskRun;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceNodeLogMapper;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskRunMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceTaskService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceTaskService.class);
    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final GovGovernanceTaskMapper taskMapper;
    private final GovGovernanceTaskRunMapper runMapper;
    private final GovGovernanceNodeLogMapper nodeLogMapper;
    private final GovernanceExecuteService executeService;
    private final KettleExecuteService kettleExecuteService;
    private final TaskVariableService variableService;

    public GovernanceTaskService(GovGovernanceTaskMapper taskMapper,
                                 GovGovernanceTaskRunMapper runMapper,
                                 GovGovernanceNodeLogMapper nodeLogMapper,
                                 GovernanceExecuteService executeService,
                                 KettleExecuteService kettleExecuteService,
                                 TaskVariableService variableService) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.nodeLogMapper = nodeLogMapper;
        this.executeService = executeService;
        this.kettleExecuteService = kettleExecuteService;
        this.variableService = variableService;
    }

    public List<Map<String, Object>> list() {
        List<GovGovernanceTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GovGovernanceTask>()
                .orderByDesc(GovGovernanceTask::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovGovernanceTask t : tasks) {
            out.add(toTaskMap(t, false));
        }
        return out;
    }

    public Map<String, Object> get(Long id) {
        return toTaskMap(requireTask(id), true);
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        String name = str(body.get("taskName"), null);
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "taskName 不能为空");
        }
        GovGovernanceTask task = new GovGovernanceTask();
        task.setTaskCode(str(body.get("taskCode"), genCode()));
        task.setTaskName(name.trim());
        task.setDescription(str(body.get("description"), null));
        task.setGraphJson(str(body.get("graphJson"), emptyGraph()));
        task.setStatus(str(body.get("status"), "DRAFT"));
        task.setEngineType(str(body.get("engineType"), "KETTLE"));
        if (operator != null) {
            task.setCreatedBy(operator.getUsername());
        }
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        log.info("governance task created id={} code={}", task.getId(), task.getTaskCode());
        return task.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovGovernanceTask task = requireTask(id);
        assertEditable(task);
        if (body.containsKey("taskName")) {
            String name = str(body.get("taskName"), null);
            if (name == null || name.isBlank()) {
                throw new BusinessException(400, "taskName 不能为空");
            }
            task.setTaskName(name.trim());
        }
        if (body.containsKey("description")) {
            task.setDescription(str(body.get("description"), null));
        }
        if (body.containsKey("status")) {
            task.setStatus(str(body.get("status"), task.getStatus()));
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        GovGovernanceTask task = requireTask(id);
        if ("LOCKED".equals(task.getStatus()) || "RUNNING".equals(task.getStatus())) {
            throw new BusinessException(400, "锁定或运行中的任务不可删除");
        }
        nodeLogMapper.delete(new LambdaQueryWrapper<GovGovernanceNodeLog>()
                .eq(GovGovernanceNodeLog::getTaskId, id));
        runMapper.delete(new LambdaQueryWrapper<GovGovernanceTaskRun>()
                .eq(GovGovernanceTaskRun::getTaskId, id));
        taskMapper.deleteById(id);
    }

    public Map<String, Object> getGraph(Long id) {
        GovGovernanceTask task = requireTask(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", task.getId());
        m.put("taskName", task.getTaskName());
        m.put("status", task.getStatus());
        m.put("lockedBy", task.getLockedBy());
        m.put("graphJson", task.getGraphJson());
        return m;
    }

    @Transactional
    public void saveGraph(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovGovernanceTask task = requireTask(id);
        assertEditable(task);
        if ("LOCKED".equals(task.getStatus())) {
            String user = operator != null ? operator.getUsername() : null;
            if (task.getLockedBy() != null && user != null && !task.getLockedBy().equals(user)) {
                throw new BusinessException(403, "任务已被 " + task.getLockedBy() + " 锁定");
            }
        }
        Object g = body.get("graphJson");
        if (g == null) {
            throw new BusinessException(400, "graphJson 不能为空");
        }
        task.setGraphJson(g instanceof String ? (String) g : String.valueOf(g));
        if ("DRAFT".equals(task.getStatus())) {
            task.setStatus("READY");
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional
    public Map<String, Object> lock(UserPrincipal operator, Long id) {
        GovGovernanceTask task = requireTask(id);
        if ("RUNNING".equals(task.getStatus())) {
            throw new BusinessException(400, "运行中不可锁定");
        }
        String user = operator != null ? operator.getUsername() : "system";
        if ("LOCKED".equals(task.getStatus()) && task.getLockedBy() != null && !task.getLockedBy().equals(user)) {
            throw new BusinessException(403, "任务已被 " + task.getLockedBy() + " 锁定");
        }
        task.setStatus("LOCKED");
        task.setLockedBy(user);
        task.setLockedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return toTaskMap(task, false);
    }

    @Transactional
    public Map<String, Object> unlock(UserPrincipal operator, Long id) {
        GovGovernanceTask task = requireTask(id);
        String user = operator != null ? operator.getUsername() : "system";
        boolean privileged = operator != null && (operator.isSystemAdmin() || isTenantOwner(operator));
        if ("LOCKED".equals(task.getStatus())
                && task.getLockedBy() != null
                && !task.getLockedBy().equals(user)
                && !"sys_admin".equals(user)
                && !privileged) {
            throw new BusinessException(403, "仅锁定人、系统管理员或租户所有者可解锁");
        }
        task.setStatus(task.getGraphJson() != null && !task.getGraphJson().isBlank() ? "READY" : "DRAFT");
        task.setLockedBy(null);
        task.setLockedAt(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return toTaskMap(task, false);
    }

    @Transactional
    public Map<String, Object> publish(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovGovernanceTask task = requireTask(id);
        // 保存画布
        Object graphJson = body.get("graphJson");
        if (graphJson != null) {
            task.setGraphJson(graphJson instanceof String ? (String) graphJson : String.valueOf(graphJson));
        }
        // 状态改为PUBLISHED
        task.setStatus("PUBLISHED");
        // 释放锁
        task.setLockedBy(null);
        task.setLockedAt(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return toTaskMap(task, false);
    }

    @Transactional
    public Map<String, Object> rename(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovGovernanceTask task = requireTask(id);
        assertEditable(task);
        String name = str(body.get("taskName"), null);
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "taskName 不能为空");
        }
        task.setTaskName(name.trim());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return toTaskMap(task, false);
    }

    public Map<String, Object> run(UserPrincipal operator, Long id) {
        return run(operator, id, null);
    }

    public Map<String, Object> run(UserPrincipal operator, Long id, Map<String, String> runtimeVariables) {
        GovGovernanceTask task = requireTask(id);
        if ("IN_MEMORY".equalsIgnoreCase(task.getEngineType())) {
            return executeService.executeTask(operator, id);
        }
        Map<String, String> params = variableService.getVariableParams(id, runtimeVariables);
        return kettleExecuteService.executeTask(id, params);
    }

    public Map<String, Object> stop(UserPrincipal operator, Long id) {
        GovGovernanceTask task = requireTask(id);
        if ("IN_MEMORY".equalsIgnoreCase(task.getEngineType())) {
            return executeService.stopTask(operator, id);
        }
        return kettleExecuteService.stopTask(id);
    }

    @Transactional
    public int batchDelete(UserPrincipal operator, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Long id : ids) {
            GovGovernanceTask task = taskMapper.selectById(id);
            if (task == null) continue;
            if ("LOCKED".equals(task.getStatus()) || "RUNNING".equals(task.getStatus())) {
                throw new BusinessException(400, "锁定或运行中的任务不可删除: " + task.getTaskName());
            }
            nodeLogMapper.delete(new LambdaQueryWrapper<GovGovernanceNodeLog>()
                    .eq(GovGovernanceNodeLog::getTaskId, id));
            runMapper.delete(new LambdaQueryWrapper<GovGovernanceTaskRun>()
                    .eq(GovGovernanceTaskRun::getTaskId, id));
            taskMapper.deleteById(id);
            n++;
        }
        return n;
    }

    @Transactional
    public Map<String, Object> updateSchedule(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovGovernanceTask task = requireTask(id);
        if ("RUNNING".equals(task.getStatus())) {
            throw new BusinessException(400, "运行中不可修改定时");
        }
        boolean enabled = boolVal(body.get("scheduleEnabled"), false);
        String mode = str(body.get("scheduleMode"), "CRON");
        if (enabled) {
            task.setScheduleEnabled(1);
            task.setScheduleMode(mode);
            if ("SIMPLE".equalsIgnoreCase(mode)) {
                String startTimeStr = str(body.get("startTime"), null);
                String timeUnit = str(body.get("timeUnit"), "DAY");
                int interval = 1;
                Object iv = body.get("intervalValue");
                if (iv instanceof Number n) {
                    interval = Math.max(1, n.intValue());
                } else if (iv != null) {
                    try { interval = Math.max(1, Integer.parseInt(String.valueOf(iv))); } catch (Exception ignored) {}
                }
                if (startTimeStr == null || startTimeStr.isBlank()) {
                    throw new BusinessException(400, "简单调度须填写起始时间");
                }
                LocalDateTime start = LocalDateTime.parse(startTimeStr.replace(" ", "T"));
                task.setStartTime(start);
                task.setTimeUnit(timeUnit);
                task.setIntervalValue(interval);
                task.setScheduleCron(null);
                task.setNextRunAt(computeNextSimple(start, timeUnit, interval, LocalDateTime.now()));
            } else {
                String cron = str(body.get("scheduleCron"), null);
                if (cron == null || cron.isBlank()) {
                    throw new BusinessException(400, "启用定时须填写 Cron 表达式");
                }
                try {
                    CronExpression.parse(cron.trim());
                } catch (Exception e) {
                    throw new BusinessException(400, "Cron 表达式无效: " + e.getMessage());
                }
                task.setScheduleCron(cron.trim());
                task.setStartTime(null);
                task.setTimeUnit(null);
                task.setIntervalValue(null);
                task.setNextRunAt(computeNextRun(cron.trim(), LocalDateTime.now()));
            }
        } else {
            task.setScheduleEnabled(0);
            task.setScheduleCron(null);
            task.setScheduleMode(null);
            task.setStartTime(null);
            task.setTimeUnit(null);
            task.setIntervalValue(null);
            task.setNextRunAt(null);
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return toTaskMap(task, false);
    }

    public void refreshNextRunAfterExecute(Long taskId) {
        GovGovernanceTask task = taskMapper.selectById(taskId);
        if (task == null || task.getScheduleEnabled() == null || task.getScheduleEnabled() != 1) {
            return;
        }
        try {
            if ("SIMPLE".equalsIgnoreCase(task.getScheduleMode())) {
                LocalDateTime base = task.getStartTime() != null ? task.getStartTime() : LocalDateTime.now();
                String unit = task.getTimeUnit() != null ? task.getTimeUnit() : "DAY";
                int interval = task.getIntervalValue() != null ? task.getIntervalValue() : 1;
                task.setNextRunAt(computeNextSimple(base, unit, interval, LocalDateTime.now()));
            } else {
                String cron = task.getScheduleCron();
                if (cron == null || cron.isBlank()) {
                    return;
                }
                task.setNextRunAt(computeNextRun(cron, LocalDateTime.now()));
            }
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        } catch (Exception e) {
            log.warn("refreshNextRunAfterExecute failed taskId={}: {}", taskId, e.getMessage());
        }
    }

    public List<GovGovernanceTaskRun> listRuns(Long taskId) {
        LambdaQueryWrapper<GovGovernanceTaskRun> q = new LambdaQueryWrapper<GovGovernanceTaskRun>()
                .orderByDesc(GovGovernanceTaskRun::getId)
                .last("LIMIT 50");
        if (taskId != null) {
            q.eq(GovGovernanceTaskRun::getTaskId, taskId);
        }
        return runMapper.selectList(q);
    }

    public List<GovGovernanceNodeLog> listNodeLogs(Long runId) {
        return nodeLogMapper.selectList(new LambdaQueryWrapper<GovGovernanceNodeLog>()
                .eq(GovGovernanceNodeLog::getRunId, runId)
                .orderByAsc(GovGovernanceNodeLog::getId));
    }

    private GovGovernanceTask requireTask(Long id) {
        GovGovernanceTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "治理任务不存在: " + id);
        }
        return task;
    }

    private void assertEditable(GovGovernanceTask task) {
        if ("RUNNING".equals(task.getStatus())) {
            throw new BusinessException(400, "运行中不可编辑");
        }
    }

    private Map<String, Object> toTaskMap(GovGovernanceTask t, boolean withGraph) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("taskCode", t.getTaskCode());
        m.put("taskName", t.getTaskName());
        m.put("description", t.getDescription());
        m.put("status", t.getStatus());
        m.put("lockedBy", t.getLockedBy());
        m.put("lockedAt", t.getLockedAt());
        m.put("lastRunAt", t.getLastRunAt());
        m.put("lastMessage", t.getLastMessage());
        m.put("scheduleEnabled", t.getScheduleEnabled() != null && t.getScheduleEnabled() == 1);
        m.put("scheduleCron", t.getScheduleCron());
        m.put("scheduleMode", t.getScheduleMode());
        m.put("startTime", t.getStartTime());
        m.put("timeUnit", t.getTimeUnit());
        m.put("intervalValue", t.getIntervalValue());
        m.put("nextRunAt", t.getNextRunAt());
        m.put("engineType", t.getEngineType() != null ? t.getEngineType() : "KETTLE");
        m.put("createdBy", t.getCreatedBy());
        m.put("createdAt", t.getCreatedAt());
        m.put("updatedAt", t.getUpdatedAt());
        if (withGraph) {
            m.put("graphJson", t.getGraphJson());
        }
        return m;
    }

    private static String genCode() {
        return "GT_" + LocalDateTime.now().format(CODE_FMT) + "_"
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    private static LocalDateTime computeNextRun(String cron, LocalDateTime base) {
        CronExpression expr = CronExpression.parse(cron);
        LocalDateTime next = expr.next(base);
        if (next == null) {
            throw new BusinessException(400, "Cron 无法计算下次运行时间");
        }
        return next;
    }

    private static LocalDateTime computeNextSimple(LocalDateTime start, String timeUnit, int interval, LocalDateTime now) {
        LocalDateTime next = start;
        String unit = timeUnit == null ? "DAY" : timeUnit.toUpperCase();
        while (!next.isAfter(now)) {
            next = switch (unit) {
                case "HOUR" -> next.plusHours(interval);
                case "WEEK" -> next.plusWeeks(interval);
                case "MONTH" -> next.plusMonths(interval);
                default -> next.plusDays(interval);
            };
        }
        return next;
    }

    private static boolean boolVal(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim().toLowerCase();
        if ("true".equals(s) || "1".equals(s)) return true;
        if ("false".equals(s) || "0".equals(s)) return false;
        return def;
    }

    private static String emptyGraph() {
        return "{\"nodes\":[],\"edges\":[]}";
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static boolean isTenantOwner(UserPrincipal operator) {
        if (operator == null) return false;
        return operator.getRoleCodes().contains("TENANT_OWNER");
    }
}
