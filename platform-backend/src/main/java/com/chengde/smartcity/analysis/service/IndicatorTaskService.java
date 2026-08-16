package com.chengde.smartcity.analysis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.analysis.entity.IndArea;
import com.chengde.smartcity.analysis.entity.IndField;
import com.chengde.smartcity.analysis.entity.IndGroup;
import com.chengde.smartcity.analysis.entity.IndJob;
import com.chengde.smartcity.analysis.entity.IndJobLog;
import com.chengde.smartcity.analysis.entity.IndSql;
import com.chengde.smartcity.analysis.mapper.IndAreaMapper;
import com.chengde.smartcity.analysis.mapper.IndFieldMapper;
import com.chengde.smartcity.analysis.mapper.IndGroupMapper;
import com.chengde.smartcity.analysis.mapper.IndJobLogMapper;
import com.chengde.smartcity.analysis.mapper.IndJobMapper;
import com.chengde.smartcity.analysis.mapper.IndSqlMapper;
import com.chengde.smartcity.analysis.support.IndicatorJdbcSupport;
import com.chengde.smartcity.analysis.support.IndicatorOwnerCodes;
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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndicatorTaskService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorTaskService.class);
    private static final String DEFAULT_CRON_MONTHLY = "0 0 2 1 * ?";
    private static final String DEFAULT_CRON_DAILY = "0 0 2 * * ?";

    private final IndJobMapper jobMapper;
    private final IndJobLogMapper logMapper;
    private final IndGroupMapper groupMapper;
    private final IndAreaMapper areaMapper;
    private final IndFieldMapper fieldMapper;
    private final IndSqlMapper sqlMapper;
    private final IndicatorTaskDsScheduleService dsScheduleService;
    private final IndicatorJdbcSupport indicatorJdbcSupport;
    private final TaskConnectionResolver connectionResolver;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public IndicatorTaskService(IndJobMapper jobMapper,
                                IndJobLogMapper logMapper,
                                IndGroupMapper groupMapper,
                                IndAreaMapper areaMapper,
                                IndFieldMapper fieldMapper,
                                IndSqlMapper sqlMapper,
                                IndicatorTaskDsScheduleService dsScheduleService,
                                IndicatorJdbcSupport indicatorJdbcSupport,
                                TaskConnectionResolver connectionResolver,
                                JdbcTemplate jdbcTemplate,
                                AuditService auditService) {
        this.jobMapper = jobMapper;
        this.logMapper = logMapper;
        this.groupMapper = groupMapper;
        this.areaMapper = areaMapper;
        this.fieldMapper = fieldMapper;
        this.sqlMapper = sqlMapper;
        this.dsScheduleService = dsScheduleService;
        this.indicatorJdbcSupport = indicatorJdbcSupport;
        this.connectionResolver = connectionResolver;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    public List<IndJob> list(String domain, String taskName, String scheduleStatus,
                             String execStatus, String calcResult) {
        String d = normalizeDomain(domain);
        List<IndJob> all = jobMapper.selectList(new LambdaQueryWrapper<IndJob>()
                .orderByDesc(IndJob::getCreateTime)
                .orderByDesc(IndJob::getUuid));
        List<IndJob> out = new ArrayList<>();
        for (IndJob job : all) {
            decorateOwner(job);
            if (!IndicatorOwnerCodes.matchesOwner(d, ownerNameOf(job), ownerSchemaOf(job))) {
                continue;
            }
            if (taskName != null && !taskName.isBlank()
                    && (job.getName() == null || !job.getName().contains(taskName.trim()))) {
                continue;
            }
            if (scheduleStatus != null && !scheduleStatus.isBlank()
                    && !scheduleStatus.trim().equalsIgnoreCase(job.getScheduleStatus())) {
                continue;
            }
            if (execStatus != null && !execStatus.isBlank()
                    && !execStatus.trim().equalsIgnoreCase(job.getExecStatus())) {
                continue;
            }
            if (calcResult != null && !calcResult.isBlank()
                    && !calcResult.trim().equalsIgnoreCase(job.getCalcResult())) {
                continue;
            }
            out.add(job);
        }
        return out;
    }

    public IndJob require(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException(400, "任务 id 无效");
        }
        IndJob t = jobMapper.selectById(id.trim());
        if (t == null) {
            throw new BusinessException(404, "指标任务不存在");
        }
        decorateOwner(t);
        return t;
    }

    @Transactional
    public IndJob ensureFromPublishedGroup(UserPrincipal operator, IndGroup group) {
        return ensureFromPublishedGroup(operator, group, null, null, null, null, null);
    }

    @Transactional
    public IndJob ensureFromPublishedGroup(UserPrincipal operator, IndGroup group,
                                           String taskName, String execCycle, String cronExpr,
                                           String remark, String executorAddress) {
        if (group == null || group.getUuid() == null) {
            throw new BusinessException(400, "指标组无效");
        }
        String cycle = (execCycle == null || execCycle.isBlank()) ? "MONTHLY" : execCycle.trim();
        String cron = (cronExpr != null && !cronExpr.isBlank())
                ? cronExpr.trim()
                : cronOfCycle(cycle.toUpperCase(Locale.ROOT));
        String name = (taskName != null && !taskName.isBlank()) ? taskName.trim() : group.getName();
        String note = buildPublishNote(remark, executorAddress);
        LocalDateTime now = LocalDateTime.now();

        IndJob existing = jobMapper.selectOne(new LambdaQueryWrapper<IndJob>()
                .eq(IndJob::getGroupId, group.getUuid())
                .apply("publish_status <> {0}", 2)
                .last("LIMIT 1"));
        int cronCode = "DAILY".equalsIgnoreCase(cycle) ? 1 : 2;
        if (existing == null) {
            IndJob t = new IndJob();
            t.setUuid(UUID.randomUUID().toString());
            t.setGroupId(group.getUuid());
            t.setName(name);
            t.setCronCodeId(cronCode);
            t.setScheduleCron(cron);
            t.setRunStatus(1);
            t.setCalcResult(0);
            t.setPublishStatus(1);
            t.setPublishTime(now);
            t.setPublishBy(operator != null ? operator.getUsername() : "system");
            t.setStatus(2);
            t.setRemark(note);
            t.setExecutorAddress(executorAddress);
            t.setCreateTime(now);
            t.setCreateBy(operator != null ? operator.getUsername() : "system");
            t.setUpdateTime(now);
            jobMapper.insert(t);
            auditService.log(operator != null ? operator.getUserId() : null,
                    operator != null ? operator.getUsername() : "system",
                    operator != null ? operator.getOrgId() : null,
                    "ANA_IND_TASK_CREATE", group.getOwnerDomainCode(),
                    t.getUuid(), t.getName());
            decorateOwner(t);
            return t;
        }
        existing.setName(name);
        existing.setCronCodeId(cronCode);
        existing.setScheduleCron(cron);
        existing.setPublishStatus(1);
        existing.setPublishTime(now);
        if (note != null) {
            existing.setRemark(note);
        }
        if (executorAddress != null && !executorAddress.isBlank()) {
            existing.setExecutorAddress(executorAddress);
        }
        existing.setUpdateTime(now);
        jobMapper.updateById(existing);
        decorateOwner(existing);
        return existing;
    }

    private static String cronOfCycle(String cycle) {
        return "DAILY".equals(cycle) ? DEFAULT_CRON_DAILY : DEFAULT_CRON_MONTHLY;
    }

    private static String buildPublishNote(String remark, String executorAddress) {
        StringBuilder sb = new StringBuilder();
        if (executorAddress != null && !executorAddress.isBlank()) {
            sb.append("executor=").append(executorAddress.trim());
        }
        if (remark != null && !remark.isBlank()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(remark.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    @Transactional
    public Map<String, Object> execute(UserPrincipal operator, String id) {
        IndJob task = require(id);
        if ("OFFLINE".equalsIgnoreCase(task.getPublishStatus())) {
            throw new BusinessException(400, "已下线任务不可执行");
        }
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
    public Map<String, Object> start(UserPrincipal operator, String id) {
        return dsScheduleService.startSchedule(operator, require(id));
    }

    @Transactional
    public Map<String, Object> stop(UserPrincipal operator, String id) {
        return dsScheduleService.stopSchedule(operator, require(id));
    }

    @Transactional
    public Map<String, Object> offline(UserPrincipal operator, String id) {
        IndJob task = require(id);
        dsScheduleService.stopSchedule(operator, task);
        task = require(id);
        task.setPublishStatus(2);
        task.setStatus(2);
        task.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(task);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_TASK_OFFLINE", task.getOwnerDomainCode(), id, task.getTaskName());
        return Map.of("taskId", id, "publishStatus", "OFFLINE", "scheduleStatus", "STOPPED");
    }

    @Transactional
    public Map<String, Object> runFromDsCallback(String taskId, String token, Long dsInstanceId) {
        dsScheduleService.assertCallbackToken(token);
        IndJob task = require(taskId);
        if (dsInstanceId != null) {
            task.setDsInstanceId(dsInstanceId);
            jobMapper.updateById(task);
        }
        return runCalculation(task, "DS_CALLBACK", dsInstanceId);
    }

    public Map<String, Object> logDetail(String id) {
        IndJob task = require(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", task.getId());
        out.put("taskName", task.getTaskName());
        out.put("lastLog", task.getLastLog());
        out.put("lastRunAt", task.getLastRunAt());
        out.put("lastRunMessage", task.getLastRunMessage());
        out.put("execStatus", task.getExecStatus());
        out.put("calcResult", task.getCalcResult());
        List<IndJobLog> runs = logMapper.selectList(new LambdaQueryWrapper<IndJobLog>()
                .eq(IndJobLog::getJobId, id)
                .orderByDesc(IndJobLog::getId)
                .last("LIMIT 20"));
        out.put("runs", runs);
        return out;
    }

    public List<IndField> indicatorsOfTask(String id) {
        IndJob task = require(id);
        return fieldMapper.selectList(new LambdaQueryWrapper<IndField>()
                .eq(IndField::getGroupId, task.getGroupId())
                .orderByAsc(IndField::getFieldPosition)
                .orderByAsc(IndField::getUuid));
    }

    public Map<String, Object> batch(UserPrincipal operator, String action, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请先勾选任务");
        }
        String act = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> results = new ArrayList<>();
        int ok = 0;
        int fail = 0;
        for (String id : ids) {
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

    private Map<String, Object> runCalculation(IndJob task, String triggerType, Long dsInstanceId) {
        LocalDateTime started = LocalDateTime.now();
        task.setRunStatus(2);
        task.setUpdateTime(started);
        jobMapper.updateById(task);

        IndGroup group = groupMapper.selectById(task.getGroupId());
        if (group == null) {
            return finishFailed(task, triggerType, dsInstanceId, started, "指标组不存在");
        }
        IndArea domain = group.getAreaId() == null ? null : areaMapper.selectById(group.getAreaId());
        String domainDb = domain == null ? null : domain.getDbSchema();
        String targetTable = group.getTableName();

        List<IndField> indicators = fieldMapper.selectList(new LambdaQueryWrapper<IndField>()
                .eq(IndField::getGroupId, task.getGroupId())
                .orderByAsc(IndField::getFieldPosition)
                .orderByAsc(IndField::getUuid));
        List<IndSql> sqls = sqlMapper.selectList(new LambdaQueryWrapper<IndSql>()
                .eq(IndSql::getGroupId, task.getGroupId())
                .orderByAsc(IndSql::getSerial));
        Set<String> queryIds = new LinkedHashSet<>();
        for (IndSql s : sqls) {
            if (s.getUuid() != null) queryIds.add(s.getUuid());
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
            logBuf.append("- 物理库/表已就绪 ").append(domainDb).append('.').append(targetTable)
                    .append(" @").append(indicatorJdbcSupport.endpointLabel()).append('\n');
        } catch (Exception e) {
            log.warn("indicator ensure ddl failed taskId={}: {}", task.getId(), e.getMessage());
            return finishFailed(task, triggerType, dsInstanceId, started,
                    logBuf + "- 建库/建表失败: " + e.getMessage() + '\n');
        }

        int success = 0;
        int failed = 0;
        boolean singleQuery = queryIds.size() == 1;
        List<Map<String, Object>> resultRows = new ArrayList<>();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (IndSql q : sqls) {
            if (q.getContent() == null || q.getContent().isBlank()) {
                failed++;
                logBuf.append("- sql#").append(q.getUuid()).append(" 缺失SQL\n");
                continue;
            }
            String sql = q.getContent().trim().replaceAll(";\\s*$", "");
            if (!sql.toLowerCase(Locale.ROOT).startsWith("select")) {
                failed++;
                logBuf.append("- ").append(q.getSerial()).append(" 非SELECT，已跳过\n");
                continue;
            }
            String dsKey = q.getDbMarkId() == null ? "" : q.getDbMarkId().trim();
            try {
                List<Map<String, Object>> rows = querySourceRows(dsKey, q.getTimeout(), sql, singleQuery ? 10000 : 100, true);
                List<IndField> ofQuery = indicators.stream()
                        .filter(i -> q.getUuid() != null && q.getUuid().equals(i.getSqlId()))
                        .toList();
                if (singleQuery) {
                    resultRows.addAll(rows);
                } else {
                    Map<String, Object> first = rows.isEmpty() ? Map.of() : rows.get(0);
                    for (IndField ind : ofQuery) {
                        String col = ind.getFieldName() == null || ind.getFieldName().isBlank()
                                ? ind.getResultField() : ind.getFieldName();
                        if (col == null || col.isBlank()) continue;
                        Object val = pickValue(first, ind.getResultField(), col);
                        snapshot.put(col.trim().toLowerCase(Locale.ROOT), val);
                    }
                }
                success++;
                logBuf.append("- ").append(q.getSerial() == null ? q.getUuid() : q.getSerial()).append(" OK rows=")
                        .append(rows.size()).append(" ds=")
                        .append(q.getDbName() == null ? dsKey : q.getDbName())
                        .append('\n');
            } catch (Exception e) {
                failed++;
                logBuf.append("- ").append(q.getSerial() == null ? q.getUuid() : q.getSerial()).append(" FAIL ")
                        .append(e.getMessage()).append('\n');
                log.warn("indicator task sql failed taskId={} sql={}: {}", task.getId(), q.getUuid(), e.getMessage());
            }
        }
        if (queryIds.isEmpty()) {
            logBuf.append("- 无关联指标语句，跳过计算\n");
            failed++;
        }
        if (!singleQuery && success > 0) {
            resultRows = snapshot.isEmpty() ? List.of() : List.of(snapshot);
        }

        boolean wrote = false;
        if (success > 0) {
            try {
                int n = indicatorJdbcSupport.replaceResultRows(domainDb, targetTable, indicators,
                        resultRows, task.getId(), triggerType);
                wrote = true;
                logBuf.append("- 已写入本批结果 rows=").append(n)
                        .append("；主表仅保留本批，上批（若有）在 ").append(targetTable).append("_history")
                        .append('\n');
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

    public List<Map<String, Object>> runSelect(String datasourceKey, String sql, int timeoutSec, int limit) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException(400, "请填写查询语句");
        }
        String text = sql.trim();
        if (!text.toLowerCase(Locale.ROOT).startsWith("select")) {
            throw new BusinessException(400, "仅支持 SELECT 查询语句");
        }
        try {
            return querySourceRows(datasourceKey, timeoutSec, text.replaceAll(";\\s*$", ""), limit, true);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "SQL 执行失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private List<Map<String, Object>> querySourceRows(String dsKeyRaw, Integer timeoutSec, String sql, int limit,
                                                      boolean failIfUnresolved) throws Exception {
        int timeout = timeoutSec == null ? 60 : Math.max(5, timeoutSec);
        String wrapped = limit > 0
                ? "SELECT * FROM (" + sql + ") _ana_ind_run LIMIT " + limit
                : "SELECT * FROM (" + sql + ") _ana_ind_run";
        String dsKey = dsKeyRaw == null ? "" : dsKeyRaw.trim();
        if (dsKey.isBlank() || "platform".equalsIgnoreCase(dsKey) || "control".equalsIgnoreCase(dsKey)) {
            Integer old = null;
            try {
                old = jdbcTemplate.getQueryTimeout();
                jdbcTemplate.setQueryTimeout(timeout);
                return jdbcTemplate.queryForList(wrapped);
            } finally {
                if (old != null) {
                    try {
                        jdbcTemplate.setQueryTimeout(old);
                    } catch (Exception ignored) { /* ignore */ }
                }
            }
        }
        try {
            var ep = connectionResolver.resolve(dsKey);
            try (Connection conn = java.sql.DriverManager.getConnection(ep.jdbcUrl(), ep.username(), ep.password());
                 Statement st = conn.createStatement()) {
                st.setQueryTimeout(timeout);
                try (ResultSet rs = st.executeQuery(wrapped)) {
                    return resultSetToMaps(rs);
                }
            }
        } catch (BusinessException e) {
            if (failIfUnresolved) {
                throw new BusinessException(400, "数据源未在本系统登记（" + dsKey + "），无法执行，请先绑定已登记数据源");
            }
            throw e;
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
        if (row == null || row.isEmpty()) return null;
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
        if (row.size() == 1) {
            return row.values().iterator().next();
        }
        return null;
    }

    private Map<String, Object> finishFailed(IndJob task, String triggerType, Long dsInstanceId,
                                             LocalDateTime started, String logText) {
        return finishRun(task, triggerType, dsInstanceId, started, "FAILED", "FAILED",
                "执行失败", logText, 0, 1, false, null, null);
    }

    private Map<String, Object> finishRun(IndJob task, String triggerType, Long dsInstanceId,
                                          LocalDateTime started, String exec, String calc, String message,
                                          String logText, int success, int failed, boolean wrote,
                                          String domainDb, String targetTable) {
        String text = logText == null ? "" : logText;
        if (text.length() > 60000) {
            text = text.substring(0, 60000);
        }
        LocalDateTime finished = LocalDateTime.now();
        if ("RUNNING".equals(exec)) {
            task.setRunStatus(2);
        } else if ("SUCCESS".equals(exec)) {
            task.setRunStatus(3);
        } else if ("FAILED".equals(exec)) {
            task.setRunStatus(4);
        } else {
            task.setRunStatus(1);
        }
        if ("ALL_SUCCESS".equals(calc)) task.setCalcResult(1);
        else if ("PARTIAL".equals(calc)) task.setCalcResult(2);
        else if ("FAILED".equals(calc)) task.setCalcResult(3);
        else task.setCalcResult(0);
        task.setRunTime(finished);
        task.setRemark(message);
        task.setLastLog(text);
        if (dsInstanceId != null) {
            task.setDsInstanceId(dsInstanceId);
        }
        task.setUpdateTime(finished);
        jobMapper.updateById(task);

        IndJobLog run = new IndJobLog();
        run.setJobId(task.getId());
        run.setJobName(task.getTaskName());
        run.setGroupId(task.getGroupId());
        run.setRunStatus(task.getRunStatus());
        run.setRunTime(started);
        run.setSuccessCnt(success);
        run.setFailCnt(failed);
        logMapper.insert(run);

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
        out.put("triggerType", triggerType);
        return out;
    }

    private void decorateOwner(IndJob job) {
        if (job == null) return;
        IndGroup g = job.getGroupId() == null ? null : groupMapper.selectById(job.getGroupId());
        IndArea a = g == null || g.getAreaId() == null ? null : areaMapper.selectById(g.getAreaId());
        String name = a == null ? job.getName() : a.getName();
        String schema = a == null ? "" : a.getDbSchema();
        job.setOwnerDomainCode(IndicatorOwnerCodes.derive(name, schema));
    }

    private String ownerNameOf(IndJob job) {
        IndGroup g = job.getGroupId() == null ? null : groupMapper.selectById(job.getGroupId());
        IndArea a = g == null || g.getAreaId() == null ? null : areaMapper.selectById(g.getAreaId());
        return a == null ? job.getName() : a.getName();
    }

    private String ownerSchemaOf(IndJob job) {
        IndGroup g = job.getGroupId() == null ? null : groupMapper.selectById(job.getGroupId());
        IndArea a = g == null || g.getAreaId() == null ? null : areaMapper.selectById(g.getAreaId());
        return a == null ? "" : a.getDbSchema();
    }

    private static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) throw new BusinessException(400, "domain required");
        return domain.trim().toLowerCase(Locale.ROOT);
    }
}
