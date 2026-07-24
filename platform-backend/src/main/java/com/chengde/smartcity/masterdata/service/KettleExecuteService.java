package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.integration.kettle.KettleClient;
import com.chengde.smartcity.masterdata.entity.GovGovernanceNodeLog;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTask;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTaskRun;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceNodeLogMapper;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskRunMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kettle 执行引擎服务
 * 整合转换引擎 + KettleClient API，提供完整的执行/监控能力
 */
@Service
public class KettleExecuteService {

    private static final Logger log = LoggerFactory.getLogger(KettleExecuteService.class);

    /** 与汇聚任务一致：RUNNING 超过该分钟数则停止并标失败 */
    private static final int STALE_RUNNING_MINUTES = 5;

    private final GovGovernanceTaskMapper taskMapper;
    private final GovGovernanceTaskRunMapper runMapper;
    private final GovGovernanceNodeLogMapper nodeLogMapper;
    private final KettleTransConverterService transConverter;
    private final KettleClient kettleClient;
    private final GovernanceLayerTableService layerTableService;

    public KettleExecuteService(GovGovernanceTaskMapper taskMapper,
                                GovGovernanceTaskRunMapper runMapper,
                                GovGovernanceNodeLogMapper nodeLogMapper,
                                KettleTransConverterService transConverter,
                                KettleClient kettleClient,
                                GovernanceLayerTableService layerTableService) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.nodeLogMapper = nodeLogMapper;
        this.transConverter = transConverter;
        this.kettleClient = kettleClient;
        this.layerTableService = layerTableService;
    }

    /**
     * 执行任务：画布→KTR→注册→启动
     */
    @Transactional
    public Map<String, Object> executeTask(Long taskId, Map<String, String> params) {
        if (!kettleClient.isHealthy()) {
            return Map.of("status", "FAILED",
                    "message", "Kettle Carte 不可用，请启动 compose profile etl 并设置 INTEGRATION_ENABLED=true");
        }
        GovGovernanceTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return Map.of("status", "FAILED", "message", "任务不存在");
        }
        // 同任务不可并行；不同任务可同时跑（各有独立 Carte 转换名）
        if ("RUNNING".equals(task.getStatus())) {
            return Map.of("status", "FAILED",
                    "message", "该任务正在运行，请先停止或等待结束后再启动。不同任务可以同时运行。");
        }

        String transName = "GOV_" + taskId + "_" + System.currentTimeMillis();
        String graphJson = task.getGraphJson();

        if (graphJson == null || graphJson.isBlank()) {
            return Map.of("status", "FAILED", "message", "画布为空");
        }

        String graphErr = transConverter.validateGraphOutputRules(graphJson);
        if (graphErr != null) {
            return Map.of("status", "FAILED", "message", graphErr);
        }

        try {
            // 落 DWD/DWS/ADS 前由平台按 ODS 源表结构主动建表（与汇聚 ensureOdsDdl 同思路）
            List<String> ensured = layerTableService.ensureOutputTables(graphJson);
            if (!ensured.isEmpty()) {
                log.info("Task {} auto-created target tables: {}", taskId, ensured);
            }
        } catch (Exception e) {
            log.error("Task {} ensure target tables failed: {}", taskId, e.getMessage());
            return Map.of("status", "FAILED", "message", e.getMessage() == null ? "自动创建目标表失败" : e.getMessage());
        }

        try {
            String ktrXml = transConverter.graphToKtr(graphJson, transName);
            int expectedSteps = Math.max(transConverter.countGraphNodes(graphJson),
                    transConverter.countStepsInKtr(ktrXml));
            log.info("Generated KTR for task {}: {} bytes, steps={}", taskId, ktrXml.length(), expectedSteps);

            transConverter.archiveKtr(transName, ktrXml);

            Map<String, Object> addResult = kettleClient.addTrans(transName, ktrXml);
            if (!"SUCCESS".equals(addResult.get("status"))) {
                return Map.of("status", "FAILED", "message", "注册转换失败: " + addResult.get("message"));
            }
            String carteId = stringVal(addResult.get("carteId"));

            Map<String, Object> startResult = kettleClient.startTrans(transName, params);
            if (!"SUCCESS".equals(startResult.get("status"))) {
                return Map.of("status", "FAILED", "message", "启动转换失败: " + startResult.get("message"));
            }

            GovGovernanceTaskRun run = new GovGovernanceTaskRun();
            run.setTaskId(taskId);
            run.setTransName(transName);
            run.setKettleTransName(transName);
            run.setStatus("RUNNING");
            run.setStartedAt(LocalDateTime.now());
            run.setTriggeredBy("USER");
            run.setTotalNodes(expectedSteps);
            run.setCreatedAt(LocalDateTime.now());
            if (carteId != null && !carteId.isBlank()) {
                run.setMessage("carteId=" + carteId);
            }
            runMapper.insert(run);

            task.setStatus("RUNNING");
            task.setEngineType("KETTLE");
            task.setLastRunAt(LocalDateTime.now());
            taskMapper.updateById(task);

            // 短等待后对账 Carte step 数，防止中文步名损坏导致只剩 Output 空等
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            Map<String, Object> early = kettleClient.getTransStatus(transName, carteId);
            int carteSteps = toInt(early.get("stepCount"));
            if (carteSteps > 0 && expectedSteps > 0 && carteSteps < expectedSteps) {
                String msg = "Carte 仅加载 " + carteSteps + "/" + expectedSteps
                        + " 个步骤，已自动停止（多为步名编码损坏）。请重试或检查 KTR。";
                log.error("Task {} step mismatch: {}", taskId, msg);
                try {
                    kettleClient.stopTrans(transName);
                } catch (Exception ignore) {
                    // ignore
                }
                run.setStatus("FAILED");
                run.setEndedAt(LocalDateTime.now());
                run.setMessage(msg);
                run.setFailedNodes(expectedSteps - carteSteps);
                run.setSuccessNodes(carteSteps);
                runMapper.updateById(run);
                task.setStatus("READY");
                task.setLastMessage(msg);
                taskMapper.updateById(task);
                Map<String, Object> fail = new LinkedHashMap<>();
                fail.put("status", "FAILED");
                fail.put("runId", run.getId());
                fail.put("transName", transName);
                fail.put("message", msg);
                fail.put("stepCount", carteSteps);
                fail.put("expectedSteps", expectedSteps);
                return fail;
            }

            log.info("Task {} started with transName: {}, runId: {}, carteSteps={}",
                    taskId, transName, run.getId(), carteSteps);

            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("status", "SUCCESS");
            ok.put("runId", run.getId());
            ok.put("transName", transName);
            ok.put("message", "任务已启动执行");
            if (carteId != null && !carteId.isBlank()) {
                ok.put("carteId", carteId);
            }
            return ok;
        } catch (Exception e) {
            log.error("Execute task {} failed: {}", taskId, e.getMessage(), e);
            return Map.of("status", "FAILED", "message", "执行失败: " + e.getMessage());
        }
    }

    public boolean isCarteAvailable() {
        try {
            return kettleClient.isHealthy();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 停止任务执行
     */
    @Transactional
    public Map<String, Object> stopTask(Long taskId) {
        List<GovGovernanceTaskRun> runs = runMapper.selectList(
            new LambdaQueryWrapper<GovGovernanceTaskRun>()
                .eq(GovGovernanceTaskRun::getTaskId, taskId)
                .orderByDesc(GovGovernanceTaskRun::getId)
                .last("LIMIT 1")
        );

        if (runs.isEmpty()) {
            return Map.of("status", "FAILED", "message", "没有找到运行记录");
        }

        GovGovernanceTaskRun run = runs.get(0);
        if (!"RUNNING".equals(run.getStatus())) {
            return Map.of("status", "FAILED", "message", "任务当前不在运行状态");
        }

        try {
            kettleClient.stopTrans(run.getTransName());

            run.setStatus("STOPPED");
            run.setEndedAt(LocalDateTime.now());
            run.setMessage("用户手动停止");
            runMapper.updateById(run);

            GovGovernanceTask task = taskMapper.selectById(taskId);
            if (task != null) {
                task.setStatus("READY");
                taskMapper.updateById(task);
            }

            return Map.of("status", "SUCCESS", "message", "任务已停止");
        } catch (Exception e) {
            log.error("Stop task {} failed: {}", taskId, e.getMessage());
            return Map.of("status", "FAILED", "message", "停止失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务执行状态并更新
     */
    @Transactional
    public Map<String, Object> updateExecutionStatus(Long runId) {
        GovGovernanceTaskRun run = runMapper.selectById(runId);
        if (run == null) {
            return Map.of("status", "FAILED", "message", "运行记录不存在");
        }

        // 已终态：只读返回，禁止再次把 endedAt 刷成 now（否则监控时长会一直涨）
        if (isTerminalStatus(run.getStatus())) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "SUCCESS".equals(run.getStatus()) ? "FINISHED" : run.getStatus());
            out.put("runId", run.getId());
            out.put("runStatus", run.getStatus());
            out.put("runMessage", run.getMessage() == null ? "" : run.getMessage());
            out.put("transName", run.getTransName());
            out.put("totalNodes", run.getTotalNodes() == null ? 0 : run.getTotalNodes());
            out.put("stepCount", run.getSuccessNodes() == null ? 0 : run.getSuccessNodes());
            out.put("linesInput", run.getRowCount() == null ? 0 : run.getRowCount());
            out.put("linesOutput", run.getLineCount() == null ? 0 : run.getLineCount());
            out.put("startedAt", run.getStartedAt() == null ? "" : run.getStartedAt().toString());
            out.put("endedAt", run.getEndedAt() == null ? "" : run.getEndedAt().toString());
            try {
                String carteId = extractCarteId(run.getMessage());
                Map<String, Object> live = kettleClient.getTransStatus(run.getTransName(), carteId);
                if (live.get("log") != null) {
                    out.put("log", live.get("log"));
                }
                if (live.get("statusDesc") != null) {
                    out.put("statusDesc", live.get("statusDesc"));
                }
            } catch (Exception e) {
                log.debug("terminal run {} carte refresh skipped: {}", runId, e.getMessage());
            }
            return out;
        }

        if ("RUNNING".equals(run.getStatus()) && isStale(run)) {
            return failStaleRun(run, "执行超时（超过 " + STALE_RUNNING_MINUTES + " 分钟），已自动停止");
        }

        try {
            String carteId = extractCarteId(run.getMessage());
            Map<String, Object> statusResult = kettleClient.getTransStatus(run.getTransName(), carteId);

            String status = stringVal(statusResult.get("status"));
            int linesOut = toInt(statusResult.get("linesOutput"));
            int linesIn = toInt(statusResult.get("linesInput"));
            int stepCount = toInt(statusResult.get("stepCount"));
            String statusDesc = stringVal(statusResult.get("statusDesc"));

            run.setRowCount(linesIn);
            run.setLineCount(linesOut);
            if (stepCount > 0) {
                run.setSuccessNodes(stepCount);
            }
            if (statusDesc != null && !statusDesc.isBlank() && "RUNNING".equals(run.getStatus())) {
                String msg = run.getMessage();
                if (msg == null || msg.isBlank() || msg.startsWith("carteId=")) {
                    String prefix = (msg != null && msg.startsWith("carteId=")) ? msg.split("\\|", 2)[0] + "|" : "";
                    run.setMessage(prefix + "Carte:" + statusDesc
                            + " in=" + linesIn + " out=" + linesOut + " steps=" + stepCount);
                }
            }

            if ("FINISHED".equals(status)) {
                run.setStatus("SUCCESS");
                if (run.getEndedAt() == null) {
                    run.setEndedAt(endAtFromCarteSteps(run.getStartedAt(), statusResult, LocalDateTime.now()));
                }
                run.setMessage(trimMsg("执行完成，输出 " + linesOut + " 行", 900));
                syncNodeLogsFromCarte(run, statusResult);
            } else if ("FAILED".equals(status)) {
                run.setStatus("FAILED");
                if (run.getEndedAt() == null) {
                    run.setEndedAt(endAtFromCarteSteps(run.getStartedAt(), statusResult, LocalDateTime.now()));
                }
                String errLine = firstErrorLine(stringVal(statusResult.get("log")));
                String failMsg = "执行失败" + (statusDesc == null ? "" : ": " + statusDesc);
                if (errLine != null && !errLine.isBlank()) {
                    failMsg = failMsg + " | " + errLine;
                }
                run.setMessage(trimMsg(failMsg, 900));
                syncNodeLogsFromCarte(run, statusResult);
            } else if ("STOPPED".equals(status)) {
                run.setStatus("STOPPED");
                if (run.getEndedAt() == null) {
                    run.setEndedAt(endAtFromCarteSteps(run.getStartedAt(), statusResult, LocalDateTime.now()));
                }
                syncNodeLogsFromCarte(run, statusResult);
            } else if ("UNKNOWN".equals(status)) {
                log.warn("Run {} status UNKNOWN from Carte: {}", runId, statusResult.get("message"));
                syncNodeLogsFromCarte(run, statusResult);
            } else {
                // RUNNING：过程同步
                syncNodeLogsFromCarte(run, statusResult);
            }

            Integer expected = run.getTotalNodes();
            if ("RUNNING".equals(run.getStatus())
                    && expected != null && expected > 1
                    && stepCount > 0 && stepCount < expected
                    && linesIn == 0 && linesOut == 0
                    && run.getStartedAt() != null
                    && run.getStartedAt().isBefore(LocalDateTime.now().minusSeconds(30))) {
                return failStaleRun(run, "Carte 步骤数 " + stepCount + "/" + expected
                        + " 且长时间 0 行，疑似步丢失，已自动停止");
            }

            runMapper.updateById(run);

            if ("FINISHED".equals(status) || "FAILED".equals(status) || "STOPPED".equals(status)) {
                markTaskReady(run.getTaskId());
            }

            statusResult.put("runId", run.getId());
            statusResult.put("runStatus", run.getStatus());
            statusResult.put("runMessage", run.getMessage());
            statusResult.put("totalNodes", run.getTotalNodes());
            statusResult.put("endedAt", run.getEndedAt() == null ? "" : run.getEndedAt().toString());
            return statusResult;
        } catch (Exception e) {
            log.warn("updateExecutionStatus runId={} failed: {}", runId, e.getMessage());
            return Map.of("status", "UNKNOWN", "message", e.getMessage() == null ? "" : e.getMessage());
        }
    }

    /** 用 Carte 各 step 最大 seconds 推算结束时刻，避免用墙钟 now 虚高时长 */
    @SuppressWarnings("unchecked")
    private LocalDateTime endAtFromCarteSteps(LocalDateTime started, Map<String, Object> statusResult, LocalDateTime fallback) {
        if (started == null) {
            return fallback;
        }
        double maxSec = 0;
        Object stepsObj = statusResult.get("steps");
        if (stepsObj instanceof List<?> stepList) {
            for (Object item : stepList) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> step = (Map<String, Object>) raw;
                String seconds = stringVal(step.get("seconds"));
                if (seconds == null || seconds.isBlank()) {
                    continue;
                }
                try {
                    maxSec = Math.max(maxSec, Double.parseDouble(seconds.trim()));
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
        if (maxSec <= 0) {
            return fallback;
        }
        return started.plusNanos(Math.round(maxSec * 1_000_000_000L));
    }

    /** 将 Carte stepstatus 同步到 gov_governance_node_log，供监控页展示 */
    @SuppressWarnings("unchecked")
    private void syncNodeLogsFromCarte(GovGovernanceTaskRun run, Map<String, Object> statusResult) {
        Object stepsObj = statusResult.get("steps");
        if (!(stepsObj instanceof List<?> stepList) || stepList.isEmpty()) {
            return;
        }
        nodeLogMapper.delete(new LambdaQueryWrapper<GovGovernanceNodeLog>()
                .eq(GovGovernanceNodeLog::getRunId, run.getId()));
        LocalDateTime now = LocalDateTime.now();
        boolean runTerminal = isTerminalStatus(run.getStatus());
        LocalDateTime runEnd = run.getEndedAt() != null ? run.getEndedAt() : (runTerminal ? now : null);
        int failed = 0;
        for (Object item : stepList) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> step = (Map<String, Object>) raw;
            String name = stringVal(step.get("stepName"));
            String st = stringVal(step.get("status"));
            if ("FAILED".equals(st)) {
                failed++;
            }
            String nodeStatus = st == null ? "RUNNING" : ("FINISHED".equals(st) ? "SUCCESS" : st);
            // Carte 结束后常报 Stopped；若整次 run 已成功，节点按成功展示
            if (runTerminal && "SUCCESS".equals(run.getStatus()) && "STOPPED".equals(nodeStatus)) {
                nodeStatus = "SUCCESS";
            }
            GovGovernanceNodeLog nl = new GovGovernanceNodeLog();
            nl.setRunId(run.getId());
            nl.setTaskId(run.getTaskId());
            nl.setNodeId(name == null ? "step" : name);
            nl.setNodeName(name);
            nl.setNodeType("STEP");
            nl.setStatus(nodeStatus);
            nl.setInputRows(toInt(step.get("linesInput")) + toInt(step.get("linesRead")));
            nl.setOutputRows(toInt(step.get("linesOutput")) + toInt(step.get("linesWritten")));
            String desc = stringVal(step.get("statusDesc"));
            String speed = stringVal(step.get("speed"));
            String seconds = stringVal(step.get("seconds"));
            nl.setMessage((desc == null ? "" : desc)
                    + (seconds == null || seconds.isBlank() ? "" : " · " + seconds.trim() + "s")
                    + (speed == null || speed.isBlank() ? "" : " · " + speed.trim() + " r/s"));
            nl.setDetailJson("{\"errors\":" + toInt(step.get("errors"))
                    + ",\"linesRejected\":" + toInt(step.get("linesRejected"))
                    + ",\"seconds\":\"" + (seconds == null ? "" : seconds.trim().replace("\"", "")) + "\"}");
            nl.setStartedAt(run.getStartedAt() != null ? run.getStartedAt() : now);
            if (isTerminalStatus(nodeStatus) || runTerminal) {
                LocalDateTime end = runEnd;
                if (end == null) {
                    end = endAtFromSeconds(run.getStartedAt(), seconds, now);
                }
                nl.setEndedAt(end);
            }
            nl.setCreatedAt(now);
            nodeLogMapper.insert(nl);
        }
        run.setFailedNodes(failed);
        if (run.getTotalNodes() == null || run.getTotalNodes() == 0) {
            run.setTotalNodes(stepList.size());
        }
    }

    private static boolean isTerminalStatus(String status) {
        return "SUCCESS".equals(status) || "FAILED".equals(status)
                || "STOPPED".equals(status) || "FINISHED".equals(status);
    }

    private static LocalDateTime endAtFromSeconds(LocalDateTime started, String seconds, LocalDateTime fallback) {
        if (started == null) {
            return fallback;
        }
        if (seconds == null || seconds.isBlank()) {
            return fallback;
        }
        try {
            double sec = Double.parseDouble(seconds.trim());
            if (sec < 0) {
                return fallback;
            }
            long millis = Math.round(sec * 1000);
            return started.plusNanos(millis * 1_000_000L);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 每分钟扫描超时仍 RUNNING 的治理 run */
    @Scheduled(cron = "0 * * * * ?")
    public void reconcileStaleRuns() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STALE_RUNNING_MINUTES);
        List<GovGovernanceTaskRun> stale = runMapper.selectList(
                new LambdaQueryWrapper<GovGovernanceTaskRun>()
                        .eq(GovGovernanceTaskRun::getStatus, "RUNNING")
                        .lt(GovGovernanceTaskRun::getStartedAt, cutoff)
                        .last("LIMIT 50"));
        for (GovGovernanceTaskRun run : stale) {
            try {
                failStaleRun(run, "执行超时（超过 " + STALE_RUNNING_MINUTES + " 分钟），已自动停止");
            } catch (Exception e) {
                log.warn("reconcile stale run {} failed: {}", run.getId(), e.getMessage());
            }
        }
    }

    public Map<String, Object> getExecutionLog(Long runId) {
        GovGovernanceTaskRun run = runMapper.selectById(runId);
        if (run == null) {
            return Map.of("status", "FAILED", "message", "运行记录不存在");
        }
        return kettleClient.getTransLog(run.getTransName(), extractCarteId(run.getMessage()));
    }

    @Transactional
    public Map<String, Object> cleanupExecution(Long runId) {
        GovGovernanceTaskRun run = runMapper.selectById(runId);
        if (run == null) {
            return Map.of("status", "FAILED", "message", "运行记录不存在");
        }

        try {
            kettleClient.removeTrans(run.getTransName());
            return Map.of("status", "SUCCESS", "message", "清理完成");
        } catch (Exception e) {
            return Map.of("status", "FAILED", "message", e.getMessage());
        }
    }

    public Map<String, Object> healthCheck() {
        boolean healthy = kettleClient.isHealthy();
        return Map.of(
            "status", healthy ? "ONLINE" : "OFFLINE",
            "message", healthy ? "Kettle Carte 连接正常" : "Kettle Carte 不可用"
        );
    }

    private Map<String, Object> failStaleRun(GovGovernanceTaskRun run, String message) {
        try {
            kettleClient.stopTrans(run.getTransName());
        } catch (Exception e) {
            log.debug("stop stale trans {}: {}", run.getTransName(), e.getMessage());
        }
        run.setStatus("FAILED");
        run.setEndedAt(LocalDateTime.now());
        run.setMessage(message);
        runMapper.updateById(run);
        markTaskReady(run.getTaskId());
        log.warn("Stale governance run {} marked FAILED: {}", run.getId(), message);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "FAILED");
        out.put("message", message);
        out.put("transName", run.getTransName());
        return out;
    }

    private void markTaskReady(Long taskId) {
        if (taskId == null) {
            return;
        }
        GovGovernanceTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus("READY");
            // 把最近一次 run 摘要写入任务，供列表「最近结果」展示
            List<GovGovernanceTaskRun> latest = runMapper.selectList(
                    new LambdaQueryWrapper<GovGovernanceTaskRun>()
                            .eq(GovGovernanceTaskRun::getTaskId, taskId)
                            .orderByDesc(GovGovernanceTaskRun::getId)
                            .last("LIMIT 1"));
            if (!latest.isEmpty() && latest.get(0).getMessage() != null) {
                task.setLastMessage(trimMsg(latest.get(0).getMessage(), 480));
            }
            taskMapper.updateById(task);
        }
    }

    private static boolean isStale(GovGovernanceTaskRun run) {
        return run.getStartedAt() != null
                && run.getStartedAt().isBefore(LocalDateTime.now().minusMinutes(STALE_RUNNING_MINUTES));
    }

    private static String firstErrorLine(String log) {
        if (log == null || log.isBlank()) {
            return null;
        }
        for (String line : log.split("\r?\n")) {
            if (line != null && line.toUpperCase().contains("ERROR")) {
                return line.trim();
            }
        }
        return null;
    }

    private static String trimMsg(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String extractCarteId(String message) {
        if (message == null || !message.startsWith("carteId=")) {
            return null;
        }
        String rest = message.substring("carteId=".length());
        int cut = rest.indexOf('|');
        return cut >= 0 ? rest.substring(0, cut) : rest;
    }

    private static String stringVal(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int toInt(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
