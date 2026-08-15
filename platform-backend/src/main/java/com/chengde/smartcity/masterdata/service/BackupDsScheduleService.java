package com.chengde.smartcity.masterdata.service;

import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.ds.DolphinSchedulerClient;
import com.chengde.smartcity.masterdata.entity.RcStoragePolicy;
import com.chengde.smartcity.masterdata.mapper.RcStoragePolicyMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 备份策略 DolphinScheduler 编排：启停调度、回调触发逻辑备份。
 * DS 不可用时仍可启停本地 Spring 定时（StoragePolicyScheduler）。
 */
@Service
public class BackupDsScheduleService {

    private static final Logger log = LoggerFactory.getLogger(BackupDsScheduleService.class);
    private static final String BACKUP_PROJECT = "chengde_storage_backup";

    private final DolphinSchedulerClient dsClient;
    private final RcStoragePolicyMapper policyMapper;
    private final IntegrationProperties integrationProperties;
    private final AuditService auditService;
    private final ResourceCenterPlatformService platformService;

    public BackupDsScheduleService(DolphinSchedulerClient dsClient,
                                   RcStoragePolicyMapper policyMapper,
                                   IntegrationProperties integrationProperties,
                                   AuditService auditService,
                                   @Lazy ResourceCenterPlatformService platformService) {
        this.dsClient = dsClient;
        this.policyMapper = policyMapper;
        this.integrationProperties = integrationProperties;
        this.auditService = auditService;
        this.platformService = platformService;
    }

    public boolean isDsAvailable() {
        return integrationProperties.isEnabled() && dsClient.isHealthy();
    }

    public String resolveCallbackToken() {
        String token = integrationProperties.getDs().getCallbackToken();
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        String pwd = integrationProperties.getDs().getPassword();
        return pwd == null ? "chengde-backup-callback" : pwd;
    }

    public void assertCallbackToken(String token) {
        String expected = resolveCallbackToken();
        if (token == null || !expected.equals(token.trim())) {
            throw new BusinessException(403, "DS 回调令牌无效");
        }
    }

    @Transactional
    public Map<String, Object> startSchedule(UserPrincipal operator, Long policyId) {
        RcStoragePolicy p = requireBackupPolicy(policyId);
        if (p.getScheduleCron() == null || p.getScheduleCron().isBlank()) {
            throw new BusinessException(400, "请先配置执行周期后再启动调度");
        }
        p.setScheduleEnabled(1);
        p.setStatus("ACTIVE");
        p.setNextRunAt(LocalDateTime.now().plusMinutes(1));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policyId", policyId);
        out.put("scheduleEnabled", 1);
        out.put("scheduleCron", p.getScheduleCron());

        if (isDsAvailable()) {
            offlineQuiet(p);
            long projectCode = dsClient.ensureProject(BACKUP_PROJECT);
            String tenant = dsClient.resolveTenant();
            String defName = "数据备份_" + safeName(p.getPolicyName()) + "_" + policyId;
            String script = buildTriggerScript(policyId);
            long definitionCode = dsClient.createAndReleaseShellChain(
                    projectCode, defName, List.of("逻辑备份执行"), List.of(script), tenant);
            int scheduleId = dsClient.createAndOnlineSchedule(projectCode, definitionCode, p.getScheduleCron());
            p.setDsProjectCode(projectCode);
            p.setDsDefinitionCode(definitionCode);
            p.setDsScheduleId(scheduleId);
            p.setDsPublishStatus("PUBLISHED");
            out.put("dsPublished", true);
            out.put("projectCode", projectCode);
            out.put("definitionCode", definitionCode);
            out.put("scheduleId", scheduleId);
            out.put("message", "已发布到 DolphinScheduler 并上线调度");
        } else {
            p.setDsPublishStatus("DRAFT");
            out.put("dsPublished", false);
            out.put("message", "DolphinScheduler 不可用，已启用应用内定时调度");
        }
        policyMapper.updateById(p);
        auditService.log(operator != null ? operator.getUserId() : null,
                operator != null ? operator.getUsername() : "system",
                operator != null ? operator.getOrgId() : null,
                "RC_BACKUP_DS_START", "rc_storage_policy", String.valueOf(policyId),
                String.valueOf(out.get("message")));
        return out;
    }

    @Transactional
    public Map<String, Object> stopSchedule(UserPrincipal operator, Long policyId) {
        RcStoragePolicy p = requireBackupPolicy(policyId);
        offlineQuiet(p);
        p.setScheduleEnabled(0);
        p.setNextRunAt(null);
        p.setDsPublishStatus(p.getDsScheduleId() != null ? "OFFLINE" : "DRAFT");
        policyMapper.updateById(p);
        auditService.log(operator != null ? operator.getUserId() : null,
                operator != null ? operator.getUsername() : "system",
                operator != null ? operator.getOrgId() : null,
                "RC_BACKUP_DS_STOP", "rc_storage_policy", String.valueOf(policyId), "offline");
        return Map.of("policyId", policyId, "scheduleEnabled", 0, "message", "调度已停止");
    }

    @Transactional
    public Map<String, Object> runFromDsCallback(Long policyId, String token, Long dsInstanceId) {
        assertCallbackToken(token);
        log.info("backup DS callback policyId={} instance={}", policyId, dsInstanceId);
        return platformService.executePolicy(null, policyId);
    }

    private void offlineQuiet(RcStoragePolicy p) {
        if (p.getDsProjectCode() != null && p.getDsScheduleId() != null && isDsAvailable()) {
            try {
                dsClient.offlineSchedule(p.getDsProjectCode(), p.getDsScheduleId());
            } catch (Exception e) {
                log.warn("offline backup DS schedule policyId={}: {}", p.getId(), e.getMessage());
            }
        }
    }

    private RcStoragePolicy requireBackupPolicy(Long id) {
        RcStoragePolicy p = policyMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(404, "策略不存在");
        }
        if (!"BACKUP".equalsIgnoreCase(p.getActionType())) {
            throw new BusinessException(400, "仅备份策略支持 DS 调度启停");
        }
        return p;
    }

    private String buildTriggerScript(Long policyId) {
        String base = integrationProperties.getDs().getCallbackBaseUrl();
        if (base == null || base.isBlank()) {
            throw new BusinessException(500, "未配置 app.integration.ds.callback-base-url");
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String token = resolveCallbackToken();
        String url = base + "/api/v1/resource-center/platform/policies/" + policyId + "/ds-trigger";
        return "curl -sf -X POST \"" + url + "\" "
                + "-H \"X-Ds-Callback-Token: " + token + "\" "
                + "-H \"Content-Type: application/json\" "
                + "-d \"{\\\"dsInstanceId\\\":${system.task.instance.id}}\" "
                + "|| exit 1";
    }

    private static String safeName(String name) {
        if (name == null) return "policy";
        return name.replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5_\\-]", "_");
    }
}
