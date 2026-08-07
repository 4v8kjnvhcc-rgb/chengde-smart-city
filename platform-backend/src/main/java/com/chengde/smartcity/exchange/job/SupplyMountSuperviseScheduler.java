package com.chengde.smartcity.exchange.job;

import com.chengde.smartcity.exchange.service.SupplyDemandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 供需对接：同意提供后超时未挂载门户目录 → 自动督办 */
@Component
public class SupplyMountSuperviseScheduler {

    private static final Logger log = LoggerFactory.getLogger(SupplyMountSuperviseScheduler.class);

    private final SupplyDemandService supplyDemandService;

    public SupplyMountSuperviseScheduler(SupplyDemandService supplyDemandService) {
        this.supplyDemandService = supplyDemandService;
    }

    /** 每小时扫描一次挂载超时 */
    @Scheduled(cron = "0 15 * * * ?")
    public void scanMountOverdue() {
        try {
            int n = supplyDemandService.autoSuperviseMountOverdue();
            if (n > 0) {
                log.info("SupplyMountSuperviseScheduler auto-supervised {} demands", n);
            }
        } catch (Exception e) {
            log.warn("SupplyMountSuperviseScheduler failed: {}", e.getMessage());
        }
    }
}
