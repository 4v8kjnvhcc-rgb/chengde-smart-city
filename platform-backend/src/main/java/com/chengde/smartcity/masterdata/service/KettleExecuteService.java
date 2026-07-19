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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kettle 执行引擎服务
 * 整合转换引擎 + KettleClient API，提供完整的执行/监控能力
 */
@Service
public class KettleExecuteService {

    private static final Logger log = LoggerFactory.getLogger(KettleExecuteService.class);

    private final GovGovernanceTaskMapper taskMapper;
    private final GovGovernanceTaskRunMapper runMapper;
    private final GovGovernanceNodeLogMapper nodeLogMapper;
    private final KettleTransConverterService transConverter;
    private final KettleClient kettleClient;

    public KettleExecuteService(GovGovernanceTaskMapper taskMapper,
                                GovGovernanceTaskRunMapper runMapper,
                                GovGovernanceNodeLogMapper nodeLogMapper,
                                KettleTransConverterService transConverter,
                                KettleClient kettleClient) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.nodeLogMapper = nodeLogMapper;
        this.transConverter = transConverter;
        this.kettleClient = kettleClient;
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
            // 1. 生成 KTR XML（含真实 connection）
            String ktrXml = transConverter.graphToKtr(graphJson, transName);
            log.info("Generated KTR for task {}: {} bytes", taskId, ktrXml.length());

            // 可选归档到本地仓库目录
            transConverter.archiveKtr(transName, ktrXml);

            // 2. 注册到 Carte
            Map<String, Object> addResult = kettleClient.addTrans(transName, ktrXml);
            if (!"SUCCESS".equals(addResult.get("status"))) {
                return Map.of("status", "FAILED", "message", "注册转换失败: " + addResult.get("message"));
            }

            // 3. 启动执行
            Map<String, Object> startResult = kettleClient.startTrans(transName, params);
            if (!"SUCCESS".equals(startResult.get("status"))) {
                return Map.of("status", "FAILED", "message", "启动转换失败: " + startResult.get("message"));
            }

            // 4. 创建运行记录
            GovGovernanceTaskRun run = new GovGovernanceTaskRun();
            run.setTaskId(taskId);
            run.setTransName(transName);
            run.setKettleTransName(transName);
            run.setStatus("RUNNING");
            run.setStartedAt(LocalDateTime.now());
            run.setTriggeredBy("USER");
            runMapper.insert(run);

            // 5. 更新任务状态
            task.setStatus("RUNNING");
            task.setEngineType("KETTLE");
            task.setLastRunAt(LocalDateTime.now());
            taskMapper.updateById(task);

            log.info("Task {} started with transName: {}, runId: {}", taskId, transName, run.getId());

            return Map.of(
                "status", "SUCCESS",
                "runId", run.getId(),
                "transName", transName,
                "message", "任务已启动执行"
            );
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
        // 获取最后一个运行记录
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
            // 停止 Kettle 转换
            Map<String, Object> stopResult = kettleClient.stopTrans(run.getTransName());

            // 更新状态
            run.setStatus("STOPPED");
            run.setEndedAt(LocalDateTime.now());
            run.setMessage("用户手动停止");
            runMapper.updateById(run);

            // 更新任务状态
            GovGovernanceTask task = taskMapper.selectById(taskId);
            task.setStatus("READY");
            taskMapper.updateById(task);

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

        try {
            Map<String, Object> statusResult = kettleClient.getTransStatus(run.getTransName());

            String status = (String) statusResult.get("status");
            if ("FINISHED".equals(status)) {
                run.setStatus("SUCCESS");
                run.setEndedAt(LocalDateTime.now());
                run.setMessage("执行完成");
                run.setLineCount((int) statusResult.getOrDefault("linesOutput", 0));
            } else if ("FAILED".equals(status)) {
                run.setStatus("FAILED");
                run.setEndedAt(LocalDateTime.now());
                run.setMessage("执行失败");
            }

            runMapper.updateById(run);

            // 如果完成，更新任务状态
            if ("FINISHED".equals(status) || "FAILED".equals(status) || "STOPPED".equals(status)) {
                GovGovernanceTask task = taskMapper.selectById(run.getTaskId());
                if (task != null) {
                    task.setStatus("READY");
                    taskMapper.updateById(task);
                }
            }

            return statusResult;
        } catch (Exception e) {
            return Map.of("status", "UNKNOWN", "message", e.getMessage());
        }
    }

    /**
     * 获取执行日志
     */
    public Map<String, Object> getExecutionLog(Long runId) {
        GovGovernanceTaskRun run = runMapper.selectById(runId);
        if (run == null) {
            return Map.of("status", "FAILED", "message", "运行记录不存在");
        }

        return kettleClient.getTransLog(run.getTransName());
    }

    /**
     * 清理任务执行记录和Carte注册
     */
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

    /**
     * 快速验证Carte连接状态
     */
    public Map<String, Object> healthCheck() {
        boolean healthy = kettleClient.isHealthy();
        return Map.of(
            "status", healthy ? "ONLINE" : "OFFLINE",
            "message", healthy ? "Kettle Carte 连接正常" : "Kettle Carte 不可用"
        );
    }
}
