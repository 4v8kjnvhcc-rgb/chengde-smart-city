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
    private final KettleExecuteService kettleExecuteService;
    private final TaskVariableService variableService;
    private final GovernanceLayerTableService layerTableService;

    public GovernanceTaskService(GovGovernanceTaskMapper taskMapper,
                                 GovGovernanceTaskRunMapper runMapper,
                                 GovGovernanceNodeLogMapper nodeLogMapper,
                                 KettleExecuteService kettleExecuteService,
                                 TaskVariableService variableService,
                                 GovernanceLayerTableService layerTableService) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.nodeLogMapper = nodeLogMapper;
        this.kettleExecuteService = kettleExecuteService;
        this.variableService = variableService;
        this.layerTableService = layerTableService;
    }

    public List<Map<String, Object>> list() {
        List<GovGovernanceTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GovGovernanceTask>()
                .orderByDesc(GovGovernanceTask::getId));
        Map<Long, GovGovernanceTaskRun> latestRunByTask = latestRunsByTask();
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovGovernanceTask t : tasks) {
            Map<String, Object> m = toTaskMap(t, false);
            GovGovernanceTaskRun latest = latestRunByTask.get(t.getId());
            if (latest != null) {
                m.put("lastRunStatus", latest.getStatus());
                m.put("lastRunId", latest.getId());
            }
            out.add(m);
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
        String sourceConn = str(body.get("sourceConnection"), null);
        String sourceTable = str(body.get("sourceTable"), null);
        String targetConn = str(body.get("targetConnection"), null);
        String targetTable = str(body.get("targetTable"), null);
        List<String> rules = parseRules(body.get("rules"));

        String graphJson = str(body.get("graphJson"), null);
        if (graphJson == null || graphJson.isBlank() || emptyGraph().equals(graphJson.trim())) {
            if (sourceConn != null || targetConn != null || !rules.isEmpty()) {
                graphJson = buildInitGraph(sourceConn, sourceTable, targetConn, targetTable, rules);
            } else {
                graphJson = emptyGraph();
            }
        }

        boolean configured = hasConfiguredGraph(graphJson)
                || (sourceConn != null && targetConn != null);
        String status = str(body.get("status"), configured ? "CONFIGURED" : "DRAFT");
        if ("READY".equalsIgnoreCase(status)) {
            status = "CONFIGURED";
        }

        GovGovernanceTask task = new GovGovernanceTask();
        task.setTaskCode(str(body.get("taskCode"), genCode()));
        task.setTaskName(name.trim());
        task.setDescription(str(body.get("description"), null));
        task.setGraphJson(graphJson);
        task.setStatus(status);
        task.setEngineType(str(body.get("engineType"), "KETTLE"));
        if (operator != null) {
            task.setCreatedBy(operator.getUsername());
        }
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        if (boolVal(body.get("scheduleEnabled"), false)) {
            applySchedule(task, body);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }

        log.info("governance task created id={} code={} status={}", task.getId(), task.getTaskCode(), task.getStatus());
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
        m.put("status", normalizeLifecycleStatus(task.getStatus()));
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
        if ("DRAFT".equals(task.getStatus()) || "READY".equals(task.getStatus())) {
            task.setStatus("CONFIGURED");
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
        task.setStatus(task.getGraphJson() != null && !task.getGraphJson().isBlank()
                && hasConfiguredGraph(task.getGraphJson()) ? "CONFIGURED" : "DRAFT");
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
        // 产品侧仅 Kettle；历史 IN_MEMORY 强制改走 Kettle
        if (!kettleExecuteService.isCarteAvailable()) {
            throw new BusinessException(503,
                    "Kettle Carte 不可用，请启动 compose profile etl 并设置 INTEGRATION_ENABLED=true");
        }
        if (task.getEngineType() == null || "IN_MEMORY".equalsIgnoreCase(task.getEngineType())) {
            task.setEngineType("KETTLE");
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        Map<String, String> params = variableService.getVariableParams(id, runtimeVariables);
        return kettleExecuteService.executeTask(id, params);
    }

    public Map<String, Object> stop(UserPrincipal operator, Long id) {
        requireTask(id);
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
        applySchedule(task, body);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return toTaskMap(task, false);
    }

    private void applySchedule(GovGovernanceTask task, Map<String, Object> body) {
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

    /**
     * 预览治理任务输出表样例数据（平台分层库）。
     */
    public Map<String, Object> previewOutput(Long id, String table, Integer limit) {
        GovGovernanceTask task = requireTask(id);
        Map<String, Object> out = layerTableService.previewFromGraph(task.getGraphJson(), table, limit);
        out.put("taskId", task.getId());
        out.put("taskName", task.getTaskName());
        return out;
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
        m.put("status", normalizeLifecycleStatus(t.getStatus()));
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

    /** READY 兼容为 CONFIGURED（对外统一「已配置」） */
    private static String normalizeLifecycleStatus(String status) {
        if (status == null) return "DRAFT";
        if ("READY".equalsIgnoreCase(status)) return "CONFIGURED";
        return status;
    }

    private Map<Long, GovGovernanceTaskRun> latestRunsByTask() {
        List<GovGovernanceTaskRun> runs = runMapper.selectList(new LambdaQueryWrapper<GovGovernanceTaskRun>()
                .orderByDesc(GovGovernanceTaskRun::getId)
                .last("LIMIT 500"));
        Map<Long, GovGovernanceTaskRun> map = new LinkedHashMap<>();
        for (GovGovernanceTaskRun r : runs) {
            if (r.getTaskId() != null && !map.containsKey(r.getTaskId())) {
                map.put(r.getTaskId(), r);
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseRules(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                String s = str(o, null);
                if (s != null && isAllowedRule(s)) out.add(s.toUpperCase());
            }
            return out;
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return out;
        for (String part : s.split("[,;\\s]+")) {
            if (isAllowedRule(part)) out.add(part.toUpperCase());
        }
        return out;
    }

    private static boolean isAllowedRule(String s) {
        if (s == null || s.isBlank()) return false;
        String u = s.trim().toUpperCase();
        return "FILTER".equals(u) || "FIELD_PROCESS".equals(u)
                || "DEDUPLICATE".equals(u) || "MASK".equals(u);
    }

    private static boolean hasConfiguredGraph(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) return false;
        String g = graphJson.replace(" ", "");
        return g.contains("\"nodeType\"") || g.contains("\"nodes\":[{");
    }

    private static String buildInitGraph(String sourceConn, String sourceTable,
                                         String targetConn, String targetTable,
                                         List<String> rules) {
        StringBuilder nodes = new StringBuilder("[");
        StringBuilder edges = new StringBuilder("[");
        int x = 80;
        int y = 120;
        String prevId = null;
        int seq = 1;

        String inId = "n_INPUT_" + seq++;
        nodes.append(nodeJson(inId, "INPUT", "输入", "#409eff", x, y,
                inputConfigJson(sourceConn, sourceTable)));
        prevId = inId;
        x += 180;

        for (String rule : rules) {
            String label = switch (rule) {
                case "FILTER" -> "过滤";
                case "FIELD_PROCESS" -> "字段处理";
                case "DEDUPLICATE" -> "去重";
                case "MASK" -> "脱敏";
                default -> rule;
            };
            String color = switch (rule) {
                case "FILTER" -> "#67c23a";
                case "FIELD_PROCESS" -> "#e6a23c";
                case "DEDUPLICATE" -> "#909399";
                case "MASK" -> "#f56c6c";
                default -> "#909399";
            };
            String id = "n_" + rule + "_" + seq++;
            if (nodes.length() > 1) nodes.append(',');
            nodes.append(nodeJson(id, rule, label, color, x, y, ruleDefaultConfig(rule)));
            if (prevId != null) {
                if (edges.length() > 1) edges.append(',');
                edges.append("{\"id\":\"e_").append(prevId).append("_").append(id)
                        .append("\",\"source\":\"").append(prevId)
                        .append("\",\"target\":\"").append(id).append("\"}");
            }
            prevId = id;
            x += 180;
        }

        String outId = "n_OUTPUT_" + seq;
        if (nodes.length() > 1) nodes.append(',');
        nodes.append(nodeJson(outId, "OUTPUT", "输出", "#626aef", x, y,
                outputConfigJson(targetConn, targetTable)));
        if (prevId != null) {
            if (edges.length() > 1) edges.append(',');
            edges.append("{\"id\":\"e_").append(prevId).append("_").append(outId)
                    .append("\",\"source\":\"").append(prevId)
                    .append("\",\"target\":\"").append(outId).append("\"}");
        }
        nodes.append(']');
        edges.append(']');
        return "{\"nodes\":" + nodes + ",\"edges\":" + edges + "}";
    }

    private static String inputConfigJson(String conn, String table) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"inputMode\":\"TABLE\"");
        sb.append(",\"connection\":\"").append(jsonEsc(conn != null ? conn : "")).append("\"");
        sb.append(",\"tableName\":\"").append(jsonEsc(table != null ? table : "")).append("\"");
        sb.append(",\"limit\":0");
        sb.append('}');
        return sb.toString();
    }

    private static String outputConfigJson(String conn, String table) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"connection\":\"").append(jsonEsc(conn != null ? conn : "")).append("\"");
        sb.append(",\"table\":\"").append(jsonEsc(table != null ? table : "")).append("\"");
        sb.append(",\"outputMode\":\"INSERT\"");
        sb.append(",\"commit\":1000");
        sb.append('}');
        return sb.toString();
    }

    private static String ruleDefaultConfig(String rule) {
        return switch (rule) {
            case "FILTER" -> "{\"mode\":\"SIMPLE\",\"field\":\"\",\"op\":\"EQ\",\"value\":\"\"}";
            case "FIELD_PROCESS" -> "{\"mappings\":[{\"from\":\"name\",\"to\":\"name_upper\",\"expr\":\"UPPER\"}]}";
            case "DEDUPLICATE" -> "{\"keys\":[\"id\"],\"keepStrategy\":\"FIRST\"}";
            case "MASK" -> "{\"fields\":[\"phone\",\"idCard\"],\"maskType\":\"BLUR\",\"maskChar\":\"*\"}";
            default -> "{}";
        };
    }

    private static String nodeJson(String id, String type, String label, String color,
                                   int x, int y, String configJson) {
        return "{\"id\":\"" + id + "\",\"type\":\"default\","
                + "\"position\":{\"x\":" + x + ",\"y\":" + y + "},"
                + "\"label\":\"" + jsonEsc(label) + "\","
                + "\"data\":{\"nodeType\":\"" + type + "\",\"label\":\"" + jsonEsc(label)
                + "\",\"config\":" + configJson + "},"
                + "\"style\":{\"border\":\"2px solid " + color
                + "\",\"borderRadius\":\"8px\",\"padding\":\"6px 10px\","
                + "\"fontSize\":\"12px\",\"background\":\"#fff\",\"minWidth\":\"100px\"}}";
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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
