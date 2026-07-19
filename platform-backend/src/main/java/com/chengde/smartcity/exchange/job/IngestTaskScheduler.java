package com.chengde.smartcity.exchange.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.exchange.service.TableIngestEngine;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/** 扫描启用的库表接入任务，按 schedule_cron 触发真实 ODS 汇聚。 */
@Component
public class IngestTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestTaskScheduler.class);

    private final IngIngestTaskMapper taskMapper;
    private final TableIngestEngine tableIngestEngine;
    private final IngestTaskRecoveryRunner recoveryRunner;

    public IngestTaskScheduler(IngIngestTaskMapper taskMapper, TableIngestEngine tableIngestEngine,
                               IngestTaskRecoveryRunner recoveryRunner) {
        this.taskMapper = taskMapper;
        this.tableIngestEngine = tableIngestEngine;
        this.recoveryRunner = recoveryRunner;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void scanAndRunDueTasks() {
        int stale = recoveryRunner.resetStaleRunning(5);
        if (stale > 0) {
            log.warn("IngestTaskScheduler reset {} stale RUNNING job(s)", stale);
        }
        List<IngIngestTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<IngIngestTask>()
                .eq(IngIngestTask::getEnabled, 1)
                .isNotNull(IngIngestTask::getScheduleCron)
                .ne(IngIngestTask::getScheduleCron, "")
                .ne(IngIngestTask::getStatus, "RUNNING"));
        LocalDateTime now = LocalDateTime.now();
        for (IngIngestTask task : tasks) {
            try {
                if (isRealtimeManual(task)) {
                    continue;
                }
                String expr = normalizeCron(task.getScheduleCron());
                CronExpression cron = CronExpression.parse(expr);
                LocalDateTime base = task.getLastRunAt() != null ? task.getLastRunAt() : now.minusMinutes(1);
                LocalDateTime next = cron.next(base);
                if (next != null && !next.isAfter(now)) {
                    log.info("IngestTaskScheduler trigger taskId={} cron={}", task.getId(), task.getScheduleCron());
                    tableIngestEngine.runJobBySystem(task.getId());
                }
            } catch (Exception e) {
                log.warn("IngestTaskScheduler skip taskId={}: {}", task.getId(), e.getMessage());
            }
        }
    }

    /** 实时/立即任务不走定时调度，仅手动执行。 */
    private boolean isRealtimeManual(IngIngestTask task) {
        String json = task.getConfigJson();
        if (json == null || json.isBlank()) {
            return false;
        }
        return json.contains("\"syncMode\":\"REALTIME\"") || json.contains("\"syncMode\": \"REALTIME\"");
    }

    /** 兼容 5 段 Unix cron（分 时 日 月 周）→ Spring 6 段。 */
    private String normalizeCron(String cron) {
        String c = cron.trim();
        String[] parts = c.split("\\s+");
        if (parts.length == 5) {
            return "0 " + c;
        }
        return c;
    }
}
