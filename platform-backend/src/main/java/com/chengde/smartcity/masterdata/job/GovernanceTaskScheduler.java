package com.chengde.smartcity.masterdata.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTask;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskMapper;
import com.chengde.smartcity.masterdata.service.GovernanceTaskService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GovernanceTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(GovernanceTaskScheduler.class);

    private final GovGovernanceTaskMapper taskMapper;
    private final GovernanceTaskService taskService;

    public GovernanceTaskScheduler(GovGovernanceTaskMapper taskMapper,
                                   GovernanceTaskService taskService) {
        this.taskMapper = taskMapper;
        this.taskService = taskService;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void pollDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<GovGovernanceTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GovGovernanceTask>()
                .eq(GovGovernanceTask::getScheduleEnabled, 1)
                .isNotNull(GovGovernanceTask::getNextRunAt)
                .le(GovGovernanceTask::getNextRunAt, now)
                .ne(GovGovernanceTask::getStatus, "RUNNING"));
        for (GovGovernanceTask task : tasks) {
            try {
                log.info("GovernanceTaskScheduler trigger taskId={} engine={} cron={}",
                        task.getId(), task.getEngineType(), task.getScheduleCron());
                // 调度触发：统一走 Kettle（taskService.run）
                taskService.run(null, task.getId());
                taskService.refreshNextRunAfterExecute(task.getId());
            } catch (Exception e) {
                log.warn("GovernanceTaskScheduler skip taskId={}: {}", task.getId(), e.getMessage());
            }
        }
    }
}
