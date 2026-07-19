package com.chengde.smartcity.exchange.job;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时把遗留 RUNNING 任务置为 FAILED，避免进程中断后永久卡死。 */
@Component
public class IngestTaskRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestTaskRecoveryRunner.class);

    private final IngIngestTaskMapper taskMapper;

    public IngestTaskRecoveryRunner(IngIngestTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        int n = taskMapper.update(null, new LambdaUpdateWrapper<IngIngestTask>()
                .eq(IngIngestTask::getStatus, "RUNNING")
                .set(IngIngestTask::getStatus, "FAILED")
                .set(IngIngestTask::getLastRunMessage, "进程中断，已自动重置")
                .set(IngIngestTask::getErrorDetail, "backend restart while RUNNING")
                .set(IngIngestTask::getLastRunAt, LocalDateTime.now()));
        if (n > 0) {
            log.warn("IngestTaskRecoveryRunner reset {} stuck RUNNING job(s)", n);
        }
    }

    /** 超时仍 RUNNING 的任务（调度器调用）。 */
    public int resetStaleRunning(int olderThanMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, olderThanMinutes));
        return taskMapper.update(null, new LambdaUpdateWrapper<IngIngestTask>()
                .eq(IngIngestTask::getStatus, "RUNNING")
                .and(w -> w.isNull(IngIngestTask::getLastRunAt).or().lt(IngIngestTask::getLastRunAt, cutoff))
                .set(IngIngestTask::getStatus, "FAILED")
                .set(IngIngestTask::getLastRunMessage, "执行超时，已自动重置")
                .set(IngIngestTask::getErrorDetail, "RUNNING longer than " + olderThanMinutes + " min")
                .set(IngIngestTask::getLastRunAt, LocalDateTime.now()));
    }

    public void resetOne(Long taskId, String reason) {
        IngIngestTask t = taskMapper.selectById(taskId);
        if (t == null) {
            return;
        }
        if (!"RUNNING".equals(t.getStatus())) {
            return;
        }
        t.setStatus("FAILED");
        t.setLastRunMessage(reason == null || reason.isBlank() ? "已手动重置" : reason);
        t.setErrorDetail("manual reset");
        t.setLastRunAt(LocalDateTime.now());
        taskMapper.updateById(t);
    }
}
