package com.chengde.smartcity.masterdata.service;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.exchange.service.CollectUploadService;
import com.chengde.smartcity.exchange.service.IngestDsScheduleService;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.ds.DolphinSchedulerClient;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTask;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据融合处理 · 工作流调度1：门户下拉选归集/治理/融合任务，预览脚本并发布到 DS。
 */
@Service
public class FusionDsComposerService {

    private final CollectUploadService collectUploadService;
    private final GovernanceTaskService governanceTaskService;
    private final GovernanceDsScheduleService govDsScheduleService;
    private final IngestDsScheduleService ingestDsScheduleService;
    private final GovGovernanceTaskMapper govTaskMapper;
    private final IngIngestTaskMapper ingestTaskMapper;
    private final IntegrationProperties integrationProperties;
    private final DolphinSchedulerClient dsClient;

    public FusionDsComposerService(CollectUploadService collectUploadService,
                                   GovernanceTaskService governanceTaskService,
                                   GovernanceDsScheduleService govDsScheduleService,
                                   IngestDsScheduleService ingestDsScheduleService,
                                   GovGovernanceTaskMapper govTaskMapper,
                                   IngIngestTaskMapper ingestTaskMapper,
                                   IntegrationProperties integrationProperties,
                                   DolphinSchedulerClient dsClient) {
        this.collectUploadService = collectUploadService;
        this.governanceTaskService = governanceTaskService;
        this.govDsScheduleService = govDsScheduleService;
        this.ingestDsScheduleService = ingestDsScheduleService;
        this.govTaskMapper = govTaskMapper;
        this.ingestTaskMapper = ingestTaskMapper;
        this.integrationProperties = integrationProperties;
        this.dsClient = dsClient;
    }

    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean healthy = integrationProperties.isEnabled() && dsClient.isHealthy();
        out.put("healthy", healthy);
        out.put("uiBase", integrationProperties.getDs().resolveUiBase());
        out.put("dsUrl", integrationProperties.getDs().getUrl());
        return out;
    }

    public List<Map<String, Object>> taskOptions(String domain) {
        String d = normalizeDomain(domain);
        List<Map<String, Object>> out = new ArrayList<>();
        if ("INGEST".equals(d)) {
            for (IngIngestTask t : collectUploadService.listJobs(null)) {
                out.add(toIngestOption(t));
            }
            return out;
        }
        for (Map<String, Object> row : governanceTaskService.list(d)) {
            out.add(toGovOption(row, d));
        }
        return out;
    }

    public Map<String, Object> preview(String domain, Long taskId) {
        if (taskId == null) {
            throw new BusinessException(400, "taskId 必填");
        }
        String d = normalizeDomain(domain);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("domain", d);
        out.put("taskId", taskId);
        if ("INGEST".equals(d)) {
            IngIngestTask task = requireIngest(taskId);
            out.put("taskName", task.getTaskName());
            out.put("taskCode", task.getTaskCode());
            out.put("definitionName", ingestDsScheduleService.suggestedDefinitionName(task));
            out.put("script", ingestDsScheduleService.previewTriggerScript(taskId));
            out.put("scheduleCron", task.getScheduleCron());
            out.put("scheduleEnabled", task.getEnabled() != null && task.getEnabled() == 1
                    && "STARTED".equalsIgnoreCase(nz(task.getLifecycleStatus())));
            out.put("dsProjectCode", task.getDsProjectCode());
            out.put("dsDefinitionCode", task.getDsDefinitionCode());
            out.put("dsScheduleId", task.getDsScheduleId());
            out.put("dsOpenUrl", buildDsOpenUrl(task.getDsProjectCode(), task.getDsDefinitionCode()));
            return out;
        }
        GovGovernanceTask task = requireGov(taskId, d);
        out.put("taskName", task.getTaskName());
        out.put("taskCode", task.getTaskCode());
        out.put("definitionName", govDsScheduleService.suggestedDefinitionName(task));
        out.put("script", govDsScheduleService.previewTriggerScript(taskId));
        out.put("scheduleCron", task.getScheduleCron());
        out.put("scheduleEnabled", task.getScheduleEnabled() != null && task.getScheduleEnabled() == 1);
        out.put("dsProjectCode", task.getDsProjectCode());
        out.put("dsDefinitionCode", task.getDsDefinitionCode());
        out.put("dsScheduleId", task.getDsScheduleId());
        out.put("dsOpenUrl", buildDsOpenUrl(task.getDsProjectCode(), task.getDsDefinitionCode()));
        return out;
    }

    @Transactional
    public Map<String, Object> publish(UserPrincipal operator, Map<String, Object> body) {
        String d = normalizeDomain(str(body.get("domain"), null));
        Long taskId = longVal(body.get("taskId"));
        if (taskId == null) {
            throw new BusinessException(400, "taskId 必填");
        }
        String cron = str(body.get("cron"), null);
        if ("INGEST".equals(d)) {
            IngIngestTask task = requireIngest(taskId);
            if (cron != null && !cron.isBlank()) {
                task.setScheduleCron(cron.trim());
                ingestTaskMapper.updateById(task);
                task = requireIngest(taskId);
            }
            Map<String, Object> result = ingestDsScheduleService.startSchedule(operator, task);
            Map<String, Object> preview = preview(d, taskId);
            preview.putAll(result);
            return preview;
        }
        GovGovernanceTask task = requireGov(taskId, d);
        if (cron != null && !cron.isBlank()) {
            task.setScheduleCron(cron.trim());
            task.setScheduleMode("CRON");
            govTaskMapper.updateById(task);
        }
        Map<String, Object> result = governanceTaskService.startDsSchedule(operator, taskId);
        Map<String, Object> preview = preview(d, taskId);
        preview.putAll(result);
        return preview;
    }

    @Transactional
    public Map<String, Object> stop(UserPrincipal operator, Map<String, Object> body) {
        String d = normalizeDomain(str(body.get("domain"), null));
        Long taskId = longVal(body.get("taskId"));
        if (taskId == null) {
            throw new BusinessException(400, "taskId 必填");
        }
        if ("INGEST".equals(d)) {
            IngIngestTask task = requireIngest(taskId);
            Map<String, Object> result = ingestDsScheduleService.stopSchedule(operator, task);
            Map<String, Object> preview = preview(d, taskId);
            preview.putAll(result);
            return preview;
        }
        Map<String, Object> result = governanceTaskService.stopDsSchedule(operator, taskId);
        Map<String, Object> preview = preview(d, taskId);
        preview.putAll(result);
        return preview;
    }

    private Map<String, Object> toIngestOption(IngIngestTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getTaskName());
        m.put("code", t.getTaskCode());
        m.put("domain", "INGEST");
        m.put("scheduleCron", t.getScheduleCron());
        boolean enabled = t.getEnabled() != null && t.getEnabled() == 1
                && "STARTED".equalsIgnoreCase(nz(t.getLifecycleStatus()));
        m.put("scheduleEnabled", enabled);
        m.put("dsProjectCode", t.getDsProjectCode());
        m.put("dsDefinitionCode", t.getDsDefinitionCode());
        m.put("lifecycleStatus", t.getLifecycleStatus());
        m.put("label", formatLabel("归集", t.getTaskName(), t.getTaskCode(), t.getId()));
        return m;
    }

    private Map<String, Object> toGovOption(Map<String, Object> row, String domain) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("name", row.get("taskName"));
        m.put("code", row.get("taskCode"));
        m.put("domain", domain);
        m.put("scheduleCron", row.get("scheduleCron"));
        m.put("scheduleEnabled", row.get("scheduleEnabled"));
        m.put("dsProjectCode", row.get("dsProjectCode"));
        m.put("dsDefinitionCode", row.get("dsDefinitionCode"));
        String zh = "FUSION".equals(domain) ? "融合" : "治理";
        m.put("label", formatLabel(zh, String.valueOf(row.get("taskName")),
                row.get("taskCode") == null ? null : String.valueOf(row.get("taskCode")),
                longVal(row.get("id"))));
        return m;
    }

    private String formatLabel(String zh, String name, String code, Long id) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(zh).append("] ").append(name == null ? "-" : name);
        if (code != null && !code.isBlank()) {
            sb.append(" (").append(code).append(')');
        }
        if (id != null) {
            sb.append(" #").append(id);
        }
        return sb.toString();
    }

    private String buildDsOpenUrl(Long projectCode, Long definitionCode) {
        if (projectCode == null || definitionCode == null) {
            return null;
        }
        String base = integrationProperties.getDs().resolveUiBase();
        return base + "/ui/#/projects/" + projectCode + "/workflow/definitions/" + definitionCode;
    }

    private IngIngestTask requireIngest(Long id) {
        IngIngestTask task = ingestTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "归集任务不存在");
        }
        return task;
    }

    private GovGovernanceTask requireGov(Long id, String domain) {
        GovGovernanceTask task = govTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "治理/融合任务不存在");
        }
        String actual = task.getTaskDomain() == null ? "GOVERNANCE" : task.getTaskDomain().trim().toUpperCase(Locale.ROOT);
        if (!domain.equals(actual)) {
            throw new BusinessException(400, "任务域不匹配，期望 " + domain + "，实际 " + actual);
        }
        return task;
    }

    private static String normalizeDomain(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(400, "domain 必填：INGEST / GOVERNANCE / FUSION");
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "INGEST", "COLLECT", "归集" -> "INGEST";
            case "GOVERNANCE", "GOV", "治理" -> "GOVERNANCE";
            case "FUSION", "融合" -> "FUSION";
            default -> throw new BusinessException(400, "不支持的 domain: " + raw);
        };
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static Long longVal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
