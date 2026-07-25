package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovQualityIssue;
import com.chengde.smartcity.masterdata.entity.GovQualityRule;
import com.chengde.smartcity.masterdata.entity.GovQualityRuleConfig;
import com.chengde.smartcity.masterdata.entity.GovQualityTask;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskDetail;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskRun;
import com.chengde.smartcity.masterdata.mapper.GovQualityIssueMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityRuleConfigMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityRuleMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskDetailMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskRunMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityTaskService {

    private static final Logger log = LoggerFactory.getLogger(QualityTaskService.class);

    private final GovQualityTaskMapper taskMapper;
    private final GovQualityTaskDetailMapper detailMapper;
    private final GovQualityTaskRunMapper runMapper;
    private final GovQualityIssueMapper issueMapper;
    private final GovQualityRuleMapper ruleMapper;
    private final GovQualityRuleConfigMapper configMapper;
    private final QualityExecuteService executeService;

    public QualityTaskService(GovQualityTaskMapper taskMapper,
                              GovQualityTaskDetailMapper detailMapper,
                              GovQualityTaskRunMapper runMapper,
                              GovQualityIssueMapper issueMapper,
                              GovQualityRuleMapper ruleMapper,
                              GovQualityRuleConfigMapper configMapper,
                              QualityExecuteService executeService) {
        this.taskMapper = taskMapper;
        this.detailMapper = detailMapper;
        this.runMapper = runMapper;
        this.issueMapper = issueMapper;
        this.ruleMapper = ruleMapper;
        this.configMapper = configMapper;
        this.executeService = executeService;
    }

    public List<Map<String, Object>> list() {
        List<GovQualityTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GovQualityTask>()
                .orderByDesc(GovQualityTask::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityTask t : tasks) {
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
        if (name == null) {
            throw new BusinessException(400, "taskName 不能为空");
        }
        GovQualityTask task = new GovQualityTask();
        task.setTaskName(name);
        applyBody(task, body, true);
        task.setStatus(str(body.get("status"), "READY"));
        if (operator != null) {
            task.setCreatedBy(operator.getUsername());
        }
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        log.info("quality task created id={} name={}", task.getId(), name);
        return task.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovQualityTask task = requireTask(id);
        if (body.containsKey("taskName")) {
            String name = str(body.get("taskName"), null);
            if (name == null) {
                throw new BusinessException(400, "taskName 不能为空");
            }
            task.setTaskName(name);
        }
        applyBody(task, body, false);
        if (body.containsKey("status")) {
            task.setStatus(str(body.get("status"), task.getStatus()));
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        requireTask(id);
        detailMapper.delete(new LambdaQueryWrapper<GovQualityTaskDetail>()
                .eq(GovQualityTaskDetail::getTaskId, id));
        taskMapper.deleteById(id);
    }

    public List<GovQualityTaskDetail> listDetails(Long taskId) {
        requireTask(taskId);
        return detailMapper.selectList(new LambdaQueryWrapper<GovQualityTaskDetail>()
                .eq(GovQualityTaskDetail::getTaskId, taskId)
                .orderByAsc(GovQualityTaskDetail::getSortOrder)
                .orderByAsc(GovQualityTaskDetail::getId));
    }

    @Transactional
    public GovQualityTaskDetail addDetail(UserPrincipal operator, Long taskId, Map<String, Object> body) {
        requireTask(taskId);
        Long ruleId = toLong(body.get("ruleId"));
        if (ruleId == null) {
            throw new BusinessException(400, "ruleId 不能为空");
        }
        GovQualityRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException(404, "质量规则不存在: " + ruleId);
        }
        GovQualityRuleConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<GovQualityRuleConfig>()
                .eq(GovQualityRuleConfig::getRuleId, ruleId)
                .last("LIMIT 1"));

        GovQualityTaskDetail d = new GovQualityTaskDetail();
        d.setTaskId(taskId);
        d.setRuleId(ruleId);
        d.setTargetTable(str(body.get("targetTable"), cfg != null ? cfg.getTargetTable() : null));
        d.setTargetColumn(str(body.get("targetColumn"), cfg != null ? cfg.getTargetColumn() : null));
        d.setCheckType(str(body.get("checkType"), cfg != null ? cfg.getCheckType() : null));
        if (d.getTargetTable() == null || d.getTargetTable().isBlank()) {
            throw new BusinessException(400, "目标表不能为空，请从登记库选择表");
        }
        String checkType = d.getCheckType() == null ? "NULL_CHECK" : d.getCheckType();
        if (!"RECORD_COUNT".equalsIgnoreCase(checkType)
                && (d.getTargetColumn() == null || d.getTargetColumn().isBlank())) {
            throw new BusinessException(400, "目标字段不能为空");
        }
        d.setSortOrder(body.get("sortOrder") != null ? Integer.valueOf(String.valueOf(body.get("sortOrder"))) : 0);
        d.setStatus(str(body.get("status"), "ENABLED"));
        d.setCreatedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        detailMapper.insert(d);
        return d;
    }

    @Transactional
    public void deleteDetail(UserPrincipal operator, Long taskId, Long detailId) {
        requireTask(taskId);
        GovQualityTaskDetail d = detailMapper.selectById(detailId);
        if (d == null || !taskId.equals(d.getTaskId())) {
            throw new BusinessException(404, "任务明细不存在");
        }
        detailMapper.deleteById(detailId);
    }

    public Map<String, Object> run(UserPrincipal operator, Long id) {
        return executeService.executeTask(operator, id);
    }

    @Transactional
    public Map<String, Object> stop(UserPrincipal operator, Long id) {
        GovQualityTask task = requireTask(id);
        task.setStatus("STOPPED");
        task.setLastMessage("任务已停止");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        List<GovQualityTaskRun> running = runMapper.selectList(new LambdaQueryWrapper<GovQualityTaskRun>()
                .eq(GovQualityTaskRun::getTaskId, id)
                .eq(GovQualityTaskRun::getStatus, "RUNNING"));
        for (GovQualityTaskRun r : running) {
            r.setStatus("STOPPED");
            r.setEndedAt(LocalDateTime.now());
            r.setMessage("手动停止");
            runMapper.updateById(r);
        }
        return Map.of("taskId", id, "status", "STOPPED");
    }

    public List<GovQualityTaskRun> listRuns(Long taskId) {
        LambdaQueryWrapper<GovQualityTaskRun> q = new LambdaQueryWrapper<GovQualityTaskRun>()
                .orderByDesc(GovQualityTaskRun::getId);
        if (taskId != null) {
            q.eq(GovQualityTaskRun::getTaskId, taskId);
        }
        q.last("LIMIT 200");
        return runMapper.selectList(q);
    }

    /** 运行列表附带任务名称，供监控页展示（避免只显示 taskId）。 */
    public List<Map<String, Object>> listRunViews(Long taskId) {
        List<GovQualityTaskRun> runs = listRuns(taskId);
        Map<Long, String> nameCache = new LinkedHashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityTaskRun r : runs) {
            out.add(toRunView(r, nameCache));
        }
        return out;
    }

    public List<GovQualityIssue> listIssues(Long runId) {
        GovQualityTaskRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(404, "运行记录不存在: " + runId);
        }
        return issueMapper.selectList(new LambdaQueryWrapper<GovQualityIssue>()
                .eq(GovQualityIssue::getRunId, runId)
                .orderByDesc(GovQualityIssue::getId));
    }

    public Map<String, Object> rerun(UserPrincipal operator, Long runId) {
        GovQualityTaskRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(404, "运行记录不存在: " + runId);
        }
        return executeService.executeTask(operator, run.getTaskId());
    }

    public Map<String, Object> stats() {
        long taskTotal = taskMapper.selectCount(null);
        long taskReady = taskMapper.selectCount(new LambdaQueryWrapper<GovQualityTask>()
                .eq(GovQualityTask::getStatus, "READY"));
        long taskRunning = taskMapper.selectCount(new LambdaQueryWrapper<GovQualityTask>()
                .eq(GovQualityTask::getStatus, "RUNNING"));
        LocalDateTime dayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        List<GovQualityTaskRun> todayRuns = runMapper.selectList(new LambdaQueryWrapper<GovQualityTaskRun>()
                .ge(GovQualityTaskRun::getStartedAt, dayStart));
        long runToday = todayRuns.size();
        long successToday = todayRuns.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count();
        long failToday = todayRuns.stream().filter(r -> "FAILED".equals(r.getStatus())).count();
        long issueToday = todayRuns.stream().mapToLong(r -> r.getIssueCount() == null ? 0 : r.getIssueCount()).sum();

        BigDecimal avgScore = BigDecimal.ZERO;
        List<GovQualityTaskRun> scored = runMapper.selectList(new LambdaQueryWrapper<GovQualityTaskRun>()
                .isNotNull(GovQualityTaskRun::getScore)
                .orderByDesc(GovQualityTaskRun::getId)
                .last("LIMIT 50"));
        if (!scored.isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (GovQualityTaskRun r : scored) {
                sum = sum.add(r.getScore());
            }
            avgScore = sum.divide(BigDecimal.valueOf(scored.size()), 2, RoundingMode.HALF_UP);
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime from = LocalDateTime.of(day, LocalTime.MIN);
            LocalDateTime to = LocalDateTime.of(day, LocalTime.MAX);
            List<GovQualityTaskRun> dayRuns = runMapper.selectList(new LambdaQueryWrapper<GovQualityTaskRun>()
                    .ge(GovQualityTaskRun::getStartedAt, from)
                    .le(GovQualityTaskRun::getStartedAt, to));
            BigDecimal dayScore = BigDecimal.ZERO;
            int scoreN = 0;
            int issues = 0;
            for (GovQualityTaskRun r : dayRuns) {
                if (r.getScore() != null) {
                    dayScore = dayScore.add(r.getScore());
                    scoreN++;
                }
                issues += r.getIssueCount() == null ? 0 : r.getIssueCount();
            }
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", day.toString());
            point.put("runCount", dayRuns.size());
            point.put("issueCount", issues);
            point.put("avgScore", scoreN == 0 ? null : dayScore.divide(BigDecimal.valueOf(scoreN), 2, RoundingMode.HALF_UP));
            trend.add(point);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskTotal", taskTotal);
        out.put("taskReady", taskReady);
        out.put("taskRunning", taskRunning);
        out.put("runToday", runToday);
        out.put("successToday", successToday);
        out.put("failToday", failToday);
        out.put("issueToday", issueToday);
        out.put("avgScore", scored.isEmpty() ? null : avgScore);
        out.put("trend", trend);
        out.put("recentRuns", listRunViews(null).stream().limit(20).toList());
        return out;
    }

    private Map<String, Object> toRunView(GovQualityTaskRun r, Map<Long, String> nameCache) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getId());
        row.put("taskId", r.getTaskId());
        String taskName = nameCache.computeIfAbsent(r.getTaskId(), id -> {
            GovQualityTask t = taskMapper.selectById(id);
            return t != null ? t.getTaskName() : ("任务#" + id);
        });
        row.put("taskName", taskName);
        row.put("status", r.getStatus());
        row.put("startedAt", r.getStartedAt());
        row.put("endedAt", r.getEndedAt());
        row.put("score", r.getScore());
        row.put("totalChecks", r.getTotalChecks());
        row.put("issueCount", r.getIssueCount());
        row.put("message", r.getMessage());
        row.put("triggeredBy", r.getTriggeredBy());
        return row;
    }

    private void applyBody(GovQualityTask task, Map<String, Object> body, boolean creating) {
        if (body.containsKey("description") || creating) {
            if (body.containsKey("description")) {
                task.setDescription(str(body.get("description"), null));
            }
        }
        if (body.containsKey("ruleId")) {
            task.setRuleId(toLong(body.get("ruleId")));
        }
        if (body.containsKey("scheduleType") || creating) {
            task.setScheduleType(str(body.get("scheduleType"), creating ? "MANUAL" : task.getScheduleType()));
        }
        if (body.containsKey("cronExpr")) {
            task.setCronExpr(str(body.get("cronExpr"), null));
        }
        if (body.containsKey("datasourceId")) {
            task.setDatasourceId(toLong(body.get("datasourceId")));
        }
        if (body.containsKey("metadataEntryCode")) {
            task.setMetadataEntryCode(str(body.get("metadataEntryCode"), null));
        }
    }

    private Map<String, Object> toTaskMap(GovQualityTask t, boolean withDetails) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.getId());
        row.put("taskName", t.getTaskName());
        row.put("description", t.getDescription());
        row.put("ruleId", t.getRuleId());
        row.put("status", t.getStatus());
        row.put("lastRunAt", t.getLastRunAt());
        row.put("lastScore", t.getLastScore());
        row.put("lastMessage", t.getLastMessage());
        row.put("scheduleType", t.getScheduleType());
        row.put("cronExpr", t.getCronExpr());
        row.put("datasourceId", t.getDatasourceId());
        row.put("metadataEntryCode", t.getMetadataEntryCode());
        row.put("createdBy", t.getCreatedBy());
        row.put("createdAt", t.getCreatedAt());
        row.put("updatedAt", t.getUpdatedAt());
        Long detailCount = detailMapper.selectCount(new LambdaQueryWrapper<GovQualityTaskDetail>()
                .eq(GovQualityTaskDetail::getTaskId, t.getId()));
        row.put("detailCount", detailCount);
        if (withDetails) {
            row.put("details", listDetails(t.getId()));
        }
        return row;
    }

    private GovQualityTask requireTask(Long id) {
        GovQualityTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "质量任务不存在: " + id);
        }
        return task;
    }

    private static String str(Object v, String defaultVal) {
        if (v == null) {
            return defaultVal;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? defaultVal : s;
    }

    private static Long toLong(Object v) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(v));
    }
}
