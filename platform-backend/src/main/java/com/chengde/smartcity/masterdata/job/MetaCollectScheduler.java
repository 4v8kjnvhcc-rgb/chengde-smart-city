package com.chengde.smartcity.masterdata.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.masterdata.entity.GovMetaCollectTask;
import com.chengde.smartcity.masterdata.mapper.GovMetaCollectTaskMapper;
import com.chengde.smartcity.masterdata.service.MetadataSubsystemService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class MetaCollectScheduler {

    private static final Logger log = LoggerFactory.getLogger(MetaCollectScheduler.class);

    private final GovMetaCollectTaskMapper taskMapper;
    private final MetadataSubsystemService metadataService;

    public MetaCollectScheduler(GovMetaCollectTaskMapper taskMapper, MetadataSubsystemService metadataService) {
        this.taskMapper = taskMapper;
        this.metadataService = metadataService;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void scanAndRunDueTasks() {
        List<GovMetaCollectTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GovMetaCollectTask>()
                .eq(GovMetaCollectTask::getStatus, "READY")
                .isNotNull(GovMetaCollectTask::getCronExpr)
                .ne(GovMetaCollectTask::getCronExpr, ""));
        LocalDateTime now = LocalDateTime.now();
        for (GovMetaCollectTask task : tasks) {
            try {
                CronExpression cron = CronExpression.parse(task.getCronExpr());
                LocalDateTime base = task.getLastRunAt() != null ? task.getLastRunAt() : now.minusMinutes(1);
                LocalDateTime next = cron.next(base);
                if (next != null && !next.isAfter(now)) {
                    log.info("MetaCollectScheduler trigger taskId={} cron={}", task.getId(), task.getCronExpr());
                    metadataService.runTaskBySystem(task.getId());
                }
            } catch (Exception e) {
                log.warn("MetaCollectScheduler skip taskId={}: {}", task.getId(), e.getMessage());
            }
        }
    }
}
