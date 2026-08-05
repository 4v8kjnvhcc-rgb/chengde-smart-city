package com.chengde.smartcity.masterdata.job;

import com.chengde.smartcity.masterdata.entity.RcStoragePolicy;
import com.chengde.smartcity.masterdata.service.ResourceCenterPlatformService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StoragePolicyScheduler {

    private static final Logger log = LoggerFactory.getLogger(StoragePolicyScheduler.class);

    private final ResourceCenterPlatformService service;

    public StoragePolicyScheduler(ResourceCenterPlatformService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void pollDuePolicies() {
        LocalDateTime now = LocalDateTime.now();
        List<RcStoragePolicy> due = service.listDuePolicies(now);
        for (RcStoragePolicy policy : due) {
            try {
                log.info("StoragePolicyScheduler trigger policyId={} action={} cron={}",
                        policy.getId(), policy.getActionType(), policy.getScheduleCron());
                service.executePolicy(null, policy.getId());
            } catch (Exception e) {
                log.warn("StoragePolicyScheduler skip policyId={}: {}", policy.getId(), e.getMessage());
                try {
                    service.refreshNextRunAfterExecute(policy.getId());
                } catch (Exception ignored) {
                    // keep polling healthy
                }
            }
        }
    }
}
