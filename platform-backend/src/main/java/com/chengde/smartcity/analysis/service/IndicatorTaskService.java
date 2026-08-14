package com.chengde.smartcity.analysis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.analysis.entity.AnaIndicator;
import com.chengde.smartcity.analysis.entity.AnaIndicatorDomain;
import com.chengde.smartcity.analysis.entity.AnaIndicatorGroup;
import com.chengde.smartcity.analysis.entity.AnaIndicatorQuery;
import com.chengde.smartcity.analysis.entity.AnaIndicatorTask;
import com.chengde.smartcity.analysis.entity.AnaIndicatorTaskRun;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorDomainMapper;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorGroupMapper;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorMapper;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorQueryMapper;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorTaskMapper;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorTaskRunMapper;
import com.chengde.smartcity.analysis.support.IndicatorJdbcSupport;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.support.TaskConnectionResolver;
import com.chengde.smartcity.security.UserPrincipal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndicatorTaskService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorTaskService.class);
    private static final String DEFAULT_CRON_MONTHLY = "0 0 2 1 * ?";
    private static final String DEFAULT_CRON_HOURLY = "0 0 * * * ?";
    private static final String DEFAULT_CRON_DAILY = "0 0 2 * * ?";
    private static final String DEFAULT_CRON_WEEKLY = "0 0 2 ? * MON";
    private static final String DEFAULT_CRON_QUARTERLY = "0 0 2 1 1,4,7,10 ?";
    private static final String DEFAULT_CRON_YEARLY = "0 0 2 1 1 ?";

    private final AnaIndicatorTaskMapper taskMapper;
    private final AnaIndicatorTaskRunMapper runMapper;
    private final AnaIndicatorGroupMapper groupMapper;
    private final AnaIndicatorDomainMapper domainMapper;
    private final AnaIndicatorMapper indicatorMapper;
    private final AnaIndicatorQueryMapper queryMapper;
    private final IndicatorTaskDsScheduleService dsScheduleService;
    private final IndicatorJdbcSupport indicatorJdbcSupport;
    private final TaskConnectionResolver connectionResolver;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public IndicatorTaskService(AnaIndicatorTaskMapper taskMapper,
                                AnaIndicatorTaskRunMapper runMapper,
                                AnaIndicatorGroupMapper groupMapper,
                                AnaIndicatorDomainMapper domainMapper,
                                AnaIndicatorMapper indicatorMapper,
                                AnaIndicatorQueryMapper queryMapper,
                                IndicatorTaskDsScheduleService dsScheduleService,
                                IndicatorJdbcSupport indicatorJdbcSupport,
                                TaskConnectionResolver connectionResolver,
                                JdbcTemplate jdbcTemplate,
                                AuditService auditService) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.groupMapper = groupMapper;
        this.domainMapper = domainMapper;
        this.indicatorMapper = indicatorMapper;
        this.queryMapper = queryMapper;
        this.dsScheduleService = dsScheduleService;
        this.indicatorJdbcSupport = indicatorJdbcSupport;
        this.connectionResolver = connectionResolver;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    public List<AnaIndicatorTask> list(String domain, String taskName, String scheduleStatus,
                                       String execStatus, String calcResult) {
        String d = normalizeDomain(domain);
        LambdaQueryWrapper<AnaIndicatorTask> q = new LambdaQueryWrapper<AnaIndicatorTask>()
                .eq(AnaIndicatorTask::getStatus, "ACTIVE")
                .orderByDesc(AnaIndicatorTask::getId);
        if (!"all".equals(d) && !"gov".equals(d)) {
            q.eq(AnaIndicatorTask::getOwnerDomainCode, d);
        }
        if (taskName != null && !taskName.isBlank()) {
            q.like(AnaIndicatorTask::getTaskName, taskName.trim());
        }
        if (scheduleStatus != null && !scheduleStatus.isBlank()) {
            q.eq(AnaIndicatorTask::getScheduleStatus, scheduleStatus.trim().toUpperCase(Locale.ROOT));
        }
        if (execStatus != null && !execStatus.isBlank()) {
            q.eq(AnaIndicatorTask::getExecStatus, execStatus.trim().toUpperCase(Locale.ROOT));
        }
        if (calcResult != null && !calcResult.isBlank()) {
            q.eq(AnaIndicatorTask::getCalcResult, calcResult.trim().toUpperCase(Locale.ROOT));
        }
        return taskMapper.selectList(q);
    }

    public AnaIndicatorTask require(Long id) {
        AnaIndicatorTask t = taskMapper.selectById(id);
        if (t == null || !"ACTIVE".equalsIgnoreCase(t.getStatus())) {
            throw new BusinessException(404, "指标任务不存在");
        }
        return t;
    }

    /** 指标组发布时：生成或刷新指标任务（默认定时停止，发布状态=已发布）。 */
    @Transactional
    public AnaIndicatorTask ensureFromPublishedGroup(UserPrincipal operator, AnaIndicatorGroup group) {
        return ensureFromPublishedGroup(operator, group, null, null, null, null, null);
    }

    @Transactional
    public AnaIndicatorTask ensureFromPublishedGroup(UserPrincipal operator, AnaIndicatorGroup group,
                                                     String taskName, String execCycle, String cronExpr,
                                                     String remark, String executorAddress) {
        if (group == null || group.getId() == null) {
            throw new BusinessException(400, "指标组无效");
        }
        String cycle = (execCycle == null || execCycle.isBlank())
                ? "MONTHLY"
                : execCycle.trim();
        String cron = (cronExpr != null && !cronExpr.isBlank())
                ? cronExpr.trim()
                : cronOfCycle(cycle.toUpperCase(Locale.ROOT));
        String name = (taskName != null && !taskName.isBlank()) ? taskName.trim() : group.getGroupName();
        String note = buildPublishNote(remark, executorAddress);

        AnaIndicatorTask existing = taskMapper.selectOne(new LambdaQueryWrapper<AnaIndicatorTask>()
                .eq(AnaIndicatorTask::getGroupId, group.getId())
                .eq(AnaIndicatorTask::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            AnaIndicatorTask t = new AnaIndicatorTask();
            t.setOwnerDomainCode(group.getOwnerDomainCode());
            t.setGroupId(group.getId());
            t.setTaskName(name);
            t.setExecCycle(cycle);
            t.setScheduleCron(cron);
            t.setScheduleStatus("STOPPED");
            t.setExecStatus("NONE");
            t.setCalcResult("NONE");
            t.setPublishStatus("PUBLISHED");
            t.setPublishedAt(now);
            t.setStatus("ACTIVE");
            t.setLastRunMessage(note);
            t.setCreatedBy(operator != null ? operator.getUsername() : "system");
            t.setCreatedAt(now);
            t.setUpdatedAt(now);
            taskMapper.insert(t);
            auditService.log(operator != null ? operator.getUserId() : null,
                    operator != null ? operator.getUsername() : "system",
                    operator != null ? operator.getOrgId() : null,
                    "ANA_IND_TASK_CREATE", group.getOwnerDomainCode(),
                    String.valueOf(t.getId()), t.getTaskName());
            return t;
        }
        existing.setTaskName(name);
        existing.setExecCycle(cycle);
        existing.setScheduleCron(cron);
        existing.setPublishStatus("PUBLISHED");
        existing.setPublishedAt(now);
        if (note != null) {
            existing.setLastRunMessage(note);
        }
        existing.setUpdatedAt(now);
        taskMapper.updateById(existing);
        return existing;
    }

    private static String cronOfCycle(String cycle) {
        return switch (cycle) {
            case "HOURLY" -> DEFAULT_CRON_HOURLY;
            case "DAILY" -> DEFAULT_CRON_DAILY;
            case "WEEKLY" -> DEFAULT_CRON_WEEKLY;
            case "QUARTERLY", "QUARTER" -> DEFAULT_CRON_QUARTERLY;
            case "YEARLY" -> DEFAULT_CRON_YEARLY;
            default -> DEFAULT_CRON_MONTHLY;
        };
    }

    private static String buildPublishNote(String remark, String executorAddress) {
        StringBuilder sb = new StringBuilder();
        if (executorAddress != null && !executorAddress.isBlank()) {
            sb.append("executor=").append(executorAddress.trim());
        }
        if (remark != null && !remark.isBlank()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(remark.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    @Transactional
    public Map<String, Object> execute(UserPrincipal operator, Long id) {
        AnaIndicatorTask task = require(id);
        if ("OFFLINE".equalsIgnoreCase(task.getPublishStatus())) {
            throw new BusinessException(400, "已下线任务不可执行");
        }
        // 「执行」与 DS 定时回调走同一套增量建库/建表/落数逻辑
        if (dsScheduleService.isDsAvailable()) {
            try {
                Map<String, Object> ds = dsScheduleService.startOnce(operator, task);
                Map<String, Object> calc = runCalculation(task, "MANUAL",
                        ds.get("dsInstanceId") instanceof Number n ? n.longValue() : null);
                calc.putAll(ds);
                return calc;
            } catch (BusinessException e) {
                if (e.getCode() == 502) {
                    return runCalculation(task, "MANUAL", null);
                }
                throw e;
            }
        }
        return runCalculation(task, "MANUAL", null);
    }

    @Transactional
    public Map<String, Object> start(UserPrincipal operator, Long id) {
        return dsScheduleService.startSchedule(operator, require(id));
    }

    @Transactional
    public Map<String, Object> stop(UserPrincipal operator, Long id) {
        return dsScheduleService.stopSchedule(operator, require(id));
    }

    @Transactional
    public Map<String, Object> offline(UserPrincipal operator, Long id) {
        AnaIndicatorTask task = require(id);
        dsScheduleService.stopSchedule(operator, task);
        task = require(id);
        task.setPublishStatus("OFFLINE");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_TASK_OFFLINE", task.getOwnerDomainCode(), String.valueOf(id), task.getTaskName());
        return Map.of("taskId", id, "publishStatus", "OFFLINE", "scheduleStatus", "STOPPED");
    }

    @Transactional
    public Map<String, Object> runFromDsCallback(Long taskId, String token, Long dsInstanceId) {
        dsScheduleService.assertCallbackToken(token);
        AnaIndicatorTask task = require(taskId);
        if (dsInstanceId != null) {
            task.setDsInstanceId(dsInstanceId);
            taskMapper.updateById(task);
        }
        return runCalculation(task, "DS_CALLBACK", dsInstanceId);
    }

    public Map<String, Object> logDetail(Long id) {
        AnaIndicatorTask task = require(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", task.getId());
        out.put("taskName", task.getTaskName());
        out.put("lastLog", task.getLastLog());
        out.put("lastRunAt", task.getLastRunAt());
        out.put("lastRunMessage", task.getLastRunMessage());
        out.put("execStatus", task.getExecStatus());
        out.put("calcResult", task.getCalcResult());
        List<AnaIndicatorTaskRun> runs = runMapper.selectList(new LambdaQueryWrapper<AnaIndicatorTaskRun>()
                .eq(AnaIndicatorTaskRun::getTaskId, id)
                .orderByDesc(AnaIndicatorTaskRun::getId)
                .last("LIMIT 20"));
        out.put("runs", runs);
        return out;
    }

    public List<AnaIndicator> indicatorsOfTask(Long id) {
        AnaIndicatorTask task = require(id);
        return indicatorMapper.selectList(new LambdaQueryWrapper<AnaIndicator>()
                .eq(AnaIndicator::getGroupId, task.getGroupId())
                .eq(AnaIndicator::getStatus, "ACTIVE")
                .orderByAsc(AnaIndicator::getId));
    }

    public Map<String, Object> batch(UserPrincipal operator, String action, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请先勾选任务");
        }
        String act = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> results = new ArrayList<>();
        int ok = 0;
        int fail = 0;
        for (Long id : ids) {
            try {
                Map<String, Object> one = switch (act) {
                    case "EXECUTE" -> execute(operator, id);
                    case "START" -> start(operator, id);
                    case "STOP" -> stop(operator, id);
                    default -> throw new BusinessException(400, "action 须为 EXECUTE|START|STOP");
                };
                results.add(Map.of("taskId", id, "ok", true, "result", one));
                ok++;
            } catch (Exception e) {
                results.add(Map.of("taskId", id, "ok", false, "message", e.getMessage() == null ? "失败" : e.getMessage()));
                fail++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", act);
        out.put("ok", ok);
        out.put("fail", fail);
        out.put("results", results);
        return out;
    }

    private Map<String, Object> runCalculation(AnaIndicatorTask task, String triggerType, Long dsInstanceId) {
        LocalDateTime started = LocalDateTime.now();
        task.setExecStatus("RUNNING");
        task.setUpdatedAt(started);
        taskMapper.updateById(task);

        AnaIndicatorGroup group = groupMapper.selectById(task.getGroupId());
        if (group == null) {
            return finishFailed(task, triggerType, dsInstanceId, started, "指标组不存在");
        }
        AnaIndicatorDomain domain = group.getIndicatorDomainId() == null
                ? null : domainMapper.selectById(group.getIndicatorDomainId());
        String domainDb = domain == null ? null : domain.getDomainDbName();
        String targetTable = group.getTargetTable();

        List<AnaIndicator> indicators = indicatorMapper.selectList(new LambdaQueryWrapper<AnaIndicator>()
                .eq(AnaIndicator::getGroupId, task.getGroupId())
                .eq(AnaIndicator::getStatus, "ACTIVE")
                .orderByAsc(AnaIndicator::getId));
        Set<Long> queryIds = new LinkedHashSet<>();
        for (AnaIndicator ind : indicators) {
            if (ind.getQueryId() != null && ind.getQueryId() > 0) {
                queryIds.add(ind.getQueryId());
            }
        }

        StringBuilder logBuf = new StringBuilder();
        logBuf.append("[").append(started).append("] 触发=").append(triggerType)
                .append(" 任务=").append(task.getTaskName())
                .append(" 指标库主机=").append(indicatorJdbcSupport.endpointLabel())
                .append(" 结果库=").append(domainDb == null ? "-" : domainDb)
                .append(" 目标表=").append(targetTable == null ? "-" : targetTable)
                .append(" 指标数=").append(indicators.size())
                .append(" 语句数=").append(queryIds.size()).append('\n');

        if (domainDb == null || domainDb.isBlank() || targetTable == null || targetTable.isBlank()) {
            return finishFailed(task, triggerType, dsInstanceId, started,
                    logBuf + "- 缺少指标域库名或结果表名，无法落库\n");
        }

        try {
            indicatorJdbcSupport.ensureResultTable(domainDb, targetTable, indicators);
            logBuf.append("- 物理库/表增量就绪 ").append(domainDb).append('.').append(targetTable)
                    .append(" @").append(indicatorJdbcSupport.endpointLabel()).append('\n');
        } catch (Exception e) {
            log.warn("indicator ensure ddl failed taskId={}: {}", task.getId(), e.getMessage());
            return finishFailed(task, triggerType, dsInstanceId, started,
                    logBuf + "- 建库/建表失败: " + e.getMessage() + '\n');
        }

        int success = 0;
        int failed = 0;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Long qid : queryIds) {
            AnaIndicatorQuery q = queryMapper.selectById(qid);
            if (q == null || q.getSqlText() == null || q.getSqlText().isBlank()) {
                failed++;
                logBuf.append("- query#").append(qid).append(" 缺失SQL\n");
                continue;
            }
            String sql = q.getSqlText().trim().replaceAll(";\\s*$", "");
            if (!sql.toLowerCase(Locale.ROOT).startsWith("select")) {
                failed++;
                logBuf.append("- ").append(q.getQueryNo()).append(" 非SELECT，已跳过\n");
                continue;
            }
            try {
                List<Map<String, Object>> rows = querySourceRows(q, sql);
                List<AnaIndicator> ofQuery = indicators.stream()
                        .filter(i -> qid.equals(i.getQueryId()))
                        .toList();
                Map<String, Object> first = rows.isEmpty() ? Map.of() : rows.get(0);
                for (AnaIndicator ind : ofQuery) {
                    String col = ind.getFieldName() == null || ind.getFieldName().isBlank()
                            ? ind.getResultField() : ind.getFieldName();
                    if (col == null || col.isBlank()) continue;
                    Object val = pickValue(first, ind.getResultField(), col);
                    snapshot.put(col.trim().toLowerCase(Locale.ROOT), val);
                }
                success++;
                logBuf.append("- ").append(q.getQueryNo()).append(" OK rows=")
                        .append(rows.size()).append(" ds=")
                        .append(q.getDatasourceName() == null ? q.getDatasourceKey() : q.getDatasourceName())
                        .append('\n');
            } catch (Exception e) {
                failed++;
                logBuf.append("- ").append(q.getQueryNo()).append(" FAIL ")
                        .append(e.getMessage()).append('\n');
                log.warn("indicator task sql failed taskId={} query={}: {}", task.getId(), q.getQueryNo(), e.getMessage());
            }
        }
        if (queryIds.isEmpty()) {
            logBuf.append("- 无关联指标语句，跳过计算\n");
            failed++;
        }

        boolean wrote = false;
        if (success > 0) {
            try {
                indicatorJdbcSupport.insertSnapshot(domainDb, targetTable, task.getId(), triggerType, snapshot);
                wrote = true;
                logBuf.append("- 已写入结果行 cols=").append(snapshot.size()).append('\n');
            } catch (Exception e) {
                failed++;
                logBuf.append("- 写入结果失败: ").append(e.getMessage()).append('\n');
                log.warn("indicator write failed taskId={}: {}", task.getId(), e.getMessage());
            }
        }

        String calc;
        String exec;
        if (failed == 0 && success > 0 && wrote) {
            calc = "ALL_SUCCESS";
            exec = "SUCCESS";
        } else if (success > 0 && wrote) {
            calc = "PARTIAL";
            exec = "SUCCESS";
        } else {
            calc = "FAILED";
            exec = "FAILED";
        }
        String message = "成功语句 " + success + "，失败 " + failed
                + (wrote ? "；已落库 " + domainDb + "." + targetTable : "；未落库");
        return finishRun(task, triggerType, dsInstanceId, started, exec, calc, message, logBuf.toString(),
                success, failed, wrote, domainDb, targetTable);
    }

    private List<Map<String, Object>> querySourceRows(AnaIndicatorQuery q, String sql) throws Exception {
        String wrapped = "SELECT * FROM (" + sql + ") _ana_ind_run LIMIT 100";
        String dsKey = q.getDatasourceKey() == null ? "" : q.getDatasourceKey().trim();
        if (dsKey.isBlank() || "platform".equalsIgnoreCase(dsKey) || "control".equalsIgnoreCase(dsKey)) {
            return jdbcTemplate.queryForList(wrapped);
        }
        try {
            var ep = connectionResolver.resolve(dsKey);
            try (Connection conn = java.sql.DriverManager.getConnection(ep.jdbcUrl(), ep.username(), ep.password());
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(wrapped)) {
                return resultSetToMaps(rs);
            }
        } catch (BusinessException e) {
            // 连接键无法解析时回落平台库（兼容历史 platform / 库名）
            log.debug("indicator source resolve fallback dsKey={}: {}", dsKey, e.getMessage());
            return jdbcTemplate.queryForList(wrapped);
        }
    }

    private static List<Map<String, Object>> resultSetToMaps(ResultSet rs) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= n; i++) {
                String label = md.getColumnLabel(i);
                if (label == null || label.isBlank()) {
                    label = md.getColumnName(i);
                }
                row.put(label, rs.getObject(i));
            }
            out.add(row);
        }
        return out;
    }

    private static Object pickValue(Map<String, Object> row, String resultField, String fieldName) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        if (resultField != null && !resultField.isBlank()) {
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(resultField.trim())) {
                    return e.getValue();
                }
            }
        }
        if (fieldName != null && !fieldName.isBlank()) {
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(fieldName.trim())) {
                    return e.getValue();
                }
            }
        }
        // 单列结果时直接取第一列
        if (row.size() == 1) {
            return row.values().iterator().next();
        }
        return null;
    }

    private Map<String, Object> finishFailed(AnaIndicatorTask task, String triggerType, Long dsInstanceId,
                                             LocalDateTime started, String logText) {
        return finishRun(task, triggerType, dsInstanceId, started, "FAILED", "FAILED",
                "执行失败", logText, 0, 1, false, null, null);
    }

    private Map<String, Object> finishRun(AnaIndicatorTask task, String triggerType, Long dsInstanceId,
                                          LocalDateTime started, String exec, String calc, String message,
                                          String logText, int success, int failed, boolean wrote,
                                          String domainDb, String targetTable) {
        String text = logText == null ? "" : logText;
        if (text.length() > 60000) {
            text = text.substring(0, 60000);
        }
        LocalDateTime finished = LocalDateTime.now();
        task.setExecStatus(exec);
        task.setCalcResult(calc);
        task.setLastRunAt(finished);
        task.setLastRunMessage(message);
        task.setLastLog(text);
        if (dsInstanceId != null) {
            task.setDsInstanceId(dsInstanceId);
        }
        task.setUpdatedAt(finished);
        taskMapper.updateById(task);

        AnaIndicatorTaskRun run = new AnaIndicatorTaskRun();
        run.setTaskId(task.getId());
        run.setTriggerType(triggerType);
        run.setExecStatus(exec);
        run.setCalcResult(calc);
        run.setDsInstanceId(dsInstanceId);
        run.setMessage(message);
        run.setLogText(text);
        run.setStartedAt(started);
        run.setFinishedAt(finished);
        runMapper.insert(run);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", task.getId());
        out.put("execStatus", exec);
        out.put("calcResult", calc);
        out.put("message", message);
        out.put("successQueries", success);
        out.put("failedQueries", failed);
        out.put("wroteResult", wrote);
        out.put("physicalPersist", true);
        out.put("domainDb", domainDb);
        out.put("targetTable", targetTable);
        out.put("indicatorHost", indicatorJdbcSupport.endpointLabel());
        out.put("runId", run.getId());
        return out;
    }

    private static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) throw new BusinessException(400, "domain required");
        return domain.trim().toLowerCase(Locale.ROOT);
    }
}
