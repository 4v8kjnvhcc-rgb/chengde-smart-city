package com.chengde.smartcity.analysis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.analysis.entity.AnaIndicator;
import com.chengde.smartcity.analysis.entity.AnaIndicatorGroup;
import com.chengde.smartcity.analysis.entity.AnaIndicatorQuery;
import com.chengde.smartcity.analysis.entity.AnaIndicatorTask;
import com.chengde.smartcity.analysis.entity.AnaIndicatorTaskRun;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorGroupMapper;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorMapper;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorQueryMapper;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorTaskMapper;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorTaskRunMapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
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

    private final AnaIndicatorTaskMapper taskMapper;
    private final AnaIndicatorTaskRunMapper runMapper;
    private final AnaIndicatorGroupMapper groupMapper;
    private final AnaIndicatorMapper indicatorMapper;
    private final AnaIndicatorQueryMapper queryMapper;
    private final IndicatorTaskDsScheduleService dsScheduleService;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public IndicatorTaskService(AnaIndicatorTaskMapper taskMapper,
                                AnaIndicatorTaskRunMapper runMapper,
                                AnaIndicatorGroupMapper groupMapper,
                                AnaIndicatorMapper indicatorMapper,
                                AnaIndicatorQueryMapper queryMapper,
                                IndicatorTaskDsScheduleService dsScheduleService,
                                JdbcTemplate jdbcTemplate,
                                AuditService auditService) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.groupMapper = groupMapper;
        this.indicatorMapper = indicatorMapper;
        this.queryMapper = queryMapper;
        this.dsScheduleService = dsScheduleService;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    public List<AnaIndicatorTask> list(String domain, String taskName, String scheduleStatus,
                                       String execStatus, String calcResult) {
        String d = normalizeDomain(domain);
        LambdaQueryWrapper<AnaIndicatorTask> q = new LambdaQueryWrapper<AnaIndicatorTask>()
                .eq(AnaIndicatorTask::getOwnerDomainCode, d)
                .eq(AnaIndicatorTask::getStatus, "ACTIVE")
                .orderByDesc(AnaIndicatorTask::getId);
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
        if (group == null || group.getId() == null) {
            throw new BusinessException(400, "指标组无效");
        }
        AnaIndicatorTask existing = taskMapper.selectOne(new LambdaQueryWrapper<AnaIndicatorTask>()
                .eq(AnaIndicatorTask::getGroupId, group.getId())
                .eq(AnaIndicatorTask::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            AnaIndicatorTask t = new AnaIndicatorTask();
            t.setOwnerDomainCode(group.getOwnerDomainCode());
            t.setGroupId(group.getId());
            t.setTaskName(group.getGroupName());
            t.setExecCycle("MONTHLY");
            t.setScheduleCron(DEFAULT_CRON_MONTHLY);
            t.setScheduleStatus("STOPPED");
            t.setExecStatus("NONE");
            t.setCalcResult("NONE");
            t.setPublishStatus("PUBLISHED");
            t.setPublishedAt(now);
            t.setStatus("ACTIVE");
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
        existing.setTaskName(group.getGroupName());
        existing.setPublishStatus("PUBLISHED");
        existing.setPublishedAt(now);
        existing.setUpdatedAt(now);
        taskMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public Map<String, Object> execute(UserPrincipal operator, Long id) {
        AnaIndicatorTask task = require(id);
        if ("OFFLINE".equalsIgnoreCase(task.getPublishStatus())) {
            throw new BusinessException(400, "已下线任务不可执行");
        }
        // 优先走 DS 实例；DS 不可用时本地直跑 SQL（诚实降级）
        if (dsScheduleService.isDsAvailable()) {
            try {
                Map<String, Object> ds = dsScheduleService.startOnce(operator, task);
                // 同步执行计算，便于页面立刻看到结果；DS 回调到来时幂等再跑一次也可
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
        String targetTable = group == null ? null : group.getTargetTable();

        List<AnaIndicator> indicators = indicatorMapper.selectList(new LambdaQueryWrapper<AnaIndicator>()
                .eq(AnaIndicator::getGroupId, task.getGroupId())
                .eq(AnaIndicator::getStatus, "ACTIVE"));
        Set<Long> queryIds = new LinkedHashSet<>();
        for (AnaIndicator ind : indicators) {
            if (ind.getQueryId() != null && ind.getQueryId() > 0) {
                queryIds.add(ind.getQueryId());
            }
        }

        StringBuilder logBuf = new StringBuilder();
        logBuf.append("[").append(started).append("] 触发=").append(triggerType)
                .append(" 任务=").append(task.getTaskName())
                .append(" 目标表=").append(targetTable == null ? "-" : targetTable)
                .append(" 指标数=").append(indicators.size())
                .append(" 语句数=").append(queryIds.size()).append('\n');

        int success = 0;
        int failed = 0;
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
                String wrapped = "SELECT * FROM (" + sql + ") _ana_ind_run LIMIT 100";
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(wrapped);
                success++;
                logBuf.append("- ").append(q.getQueryNo()).append(" OK rows=")
                        .append(rows.size()).append(" ds=").append(q.getDatasourceName()).append('\n');
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

        String calc;
        String exec;
        if (failed == 0 && success > 0) {
            calc = "ALL_SUCCESS";
            exec = "SUCCESS";
        } else if (success > 0) {
            calc = "PARTIAL";
            exec = "SUCCESS";
        } else {
            calc = "FAILED";
            exec = "FAILED";
        }
        String message = "成功语句 " + success + "，失败 " + failed;
        LocalDateTime finished = LocalDateTime.now();
        String logText = logBuf.toString();
        if (logText.length() > 60000) {
            logText = logText.substring(0, 60000);
        }

        task.setExecStatus(exec);
        task.setCalcResult(calc);
        task.setLastRunAt(finished);
        task.setLastRunMessage(message);
        task.setLastLog(logText);
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
        run.setLogText(logText);
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
        out.put("runId", run.getId());
        return out;
    }

    private static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) throw new BusinessException(400, "domain required");
        return domain.trim().toLowerCase(Locale.ROOT);
    }
}
