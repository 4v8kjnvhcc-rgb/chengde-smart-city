package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.ds.DolphinSchedulerClient;
import com.chengde.smartcity.masterdata.entity.GovCrossPipeline;
import com.chengde.smartcity.masterdata.entity.GovCrossPipelineStep;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTask;
import com.chengde.smartcity.masterdata.entity.GovQualityScheme;
import com.chengde.smartcity.masterdata.mapper.GovCrossPipelineMapper;
import com.chengde.smartcity.masterdata.mapper.GovCrossPipelineStepMapper;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualitySchemeMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 跨模块流水线：步骤类型/数量/顺序可配，发布为 DS N 节点串行 SHELL。
 */
@Service
public class CrossModulePipelineService {

    private static final Logger log = LoggerFactory.getLogger(CrossModulePipelineService.class);
    private static final String DS_PROJECT = "chengde_cross_pipeline";
    private static final int MAX_STEPS = 20;

    private final GovCrossPipelineMapper pipelineMapper;
    private final GovCrossPipelineStepMapper stepMapper;
    private final IngIngestTaskMapper ingestTaskMapper;
    private final GovGovernanceTaskMapper govTaskMapper;
    private final GovQualitySchemeMapper qualitySchemeMapper;
    private final DolphinSchedulerClient dsClient;
    private final IntegrationProperties integrationProperties;
    private final AuditService auditService;

    public CrossModulePipelineService(GovCrossPipelineMapper pipelineMapper,
                                      GovCrossPipelineStepMapper stepMapper,
                                      IngIngestTaskMapper ingestTaskMapper,
                                      GovGovernanceTaskMapper govTaskMapper,
                                      GovQualitySchemeMapper qualitySchemeMapper,
                                      DolphinSchedulerClient dsClient,
                                      IntegrationProperties integrationProperties,
                                      AuditService auditService) {
        this.pipelineMapper = pipelineMapper;
        this.stepMapper = stepMapper;
        this.ingestTaskMapper = ingestTaskMapper;
        this.govTaskMapper = govTaskMapper;
        this.qualitySchemeMapper = qualitySchemeMapper;
        this.dsClient = dsClient;
        this.integrationProperties = integrationProperties;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> list(String keyword, String publishStatus, String scheduleStatus) {
        LambdaQueryWrapper<GovCrossPipeline> q = new LambdaQueryWrapper<GovCrossPipeline>()
                .orderByDesc(GovCrossPipeline::getId);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(GovCrossPipeline::getPipelineName, kw)
                    .or().like(GovCrossPipeline::getDescription, kw));
        }
        if (publishStatus != null && !publishStatus.isBlank()) {
            q.eq(GovCrossPipeline::getPublishStatus, publishStatus.trim());
        }
        if (scheduleStatus != null && !scheduleStatus.isBlank()) {
            q.eq(GovCrossPipeline::getScheduleStatus, scheduleStatus.trim());
        }
        List<GovCrossPipeline> rows = pipelineMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovCrossPipeline p : rows) {
            out.add(toMap(p, true));
        }
        return out;
    }

    public Map<String, Object> get(Long id) {
        return toMap(require(id), true);
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        String name = str(body.get("pipelineName"), null);
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "流水线名称不能为空");
        }
        List<Map<String, Object>> steps = parseSteps(body.get("steps"));
        if (steps.isEmpty()) {
            throw new BusinessException(400, "请至少配置一个步骤");
        }
        GovCrossPipeline p = new GovCrossPipeline();
        p.setPipelineName(name.trim());
        p.setDescription(str(body.get("description"), null));
        p.setScheduleCron(str(body.get("scheduleCron"), null));
        p.setScheduleEnabled(0);
        p.setPublishStatus("NONE");
        p.setScheduleStatus("STOPPED");
        p.setCreatedBy(operator == null ? null : operator.getUsername());
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.insert(p);
        replaceSteps(p.getId(), steps);
        audit(operator, "CROSS_PIPELINE_CREATE", p.getId(), name);
        return p.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovCrossPipeline p = require(id);
        if ("RUNNING".equals(p.getScheduleStatus())) {
            throw new BusinessException(400, "请先停止定时再编辑");
        }
        if (body.containsKey("pipelineName")) {
            String name = str(body.get("pipelineName"), null);
            if (name == null || name.isBlank()) {
                throw new BusinessException(400, "流水线名称不能为空");
            }
            p.setPipelineName(name.trim());
        }
        if (body.containsKey("description")) {
            p.setDescription(str(body.get("description"), null));
        }
        if (body.containsKey("scheduleCron")) {
            p.setScheduleCron(str(body.get("scheduleCron"), null));
        }
        if (body.containsKey("steps")) {
            List<Map<String, Object>> steps = parseSteps(body.get("steps"));
            if (steps.isEmpty()) {
                throw new BusinessException(400, "请至少配置一个步骤");
            }
            replaceSteps(id, steps);
            p.setPublishStatus("NONE");
            p.setLastMessage("步骤已变更，请重新发布到 DolphinScheduler");
        }
        p.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(p);
        audit(operator, "CROSS_PIPELINE_UPDATE", id, p.getPipelineName());
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        GovCrossPipeline p = require(id);
        if ("RUNNING".equals(p.getScheduleStatus())
                && p.getDsProjectCode() != null
                && p.getDsScheduleId() != null
                && isDsAvailable()) {
            try {
                dsClient.offlineSchedule(p.getDsProjectCode(), p.getDsScheduleId());
            } catch (Exception e) {
                throw new BusinessException(502, "停止 DS 定时失败，无法删除: " + e.getMessage());
            }
            p.setScheduleStatus("STOPPED");
            p.setScheduleEnabled(0);
        }
        removeDsArtifacts(p);
        stepMapper.delete(new LambdaQueryWrapper<GovCrossPipelineStep>()
                .eq(GovCrossPipelineStep::getPipelineId, id));
        pipelineMapper.deleteById(id);
        audit(operator, "CROSS_PIPELINE_DELETE", id, p.getPipelineName());
    }

    /**
     * 清理 DS 项目中无门户记录的「跨模块流水线_*」流程定义（补救历史只删库未删 DS 的残留）。
     */
    public Map<String, Object> purgeDsOrphans(UserPrincipal operator) {
        if (!isDsAvailable()) {
            throw new BusinessException(502, "DolphinScheduler 不可用");
        }
        long projectCode = dsClient.ensureProject(DS_PROJECT);
        Set<Long> keep = new HashSet<>();
        for (GovCrossPipeline row : pipelineMapper.selectList(new LambdaQueryWrapper<>())) {
            if (row.getDsDefinitionCode() != null) {
                keep.add(row.getDsDefinitionCode());
            }
        }
        int removed = 0;
        int failed = 0;
        List<String> messages = new ArrayList<>();
        for (Map<String, Object> def : dsClient.listProcessDefinitions(projectCode)) {
            String name = def.get("name") == null ? "" : String.valueOf(def.get("name"));
            long code = Long.parseLong(String.valueOf(def.get("code")));
            if (!name.startsWith("跨模块流水线_")) {
                continue;
            }
            if (keep.contains(code)) {
                continue;
            }
            try {
                dsClient.releaseDefinition(projectCode, code, "OFFLINE", name);
            } catch (Exception e) {
                log.warn("orphan offline def={} {}: {}", code, name, e.getMessage());
            }
            try {
                dsClient.deleteDefinition(projectCode, code);
                removed++;
                messages.add("已删除 " + name);
            } catch (Exception e) {
                failed++;
                messages.add("删除失败 " + name + ": " + e.getMessage());
                log.warn("orphan delete def={} {}: {}", code, name, e.getMessage());
            }
        }
        audit(operator, "CROSS_PIPELINE_PURGE_DS", 0L, "removed=" + removed + " failed=" + failed);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectCode", projectCode);
        out.put("removed", removed);
        out.put("failed", failed);
        out.put("messages", messages);
        return out;
    }

    @Transactional
    public Map<String, Object> publish(UserPrincipal operator, Long id) {
        GovCrossPipeline p = require(id);
        List<GovCrossPipelineStep> steps = listSteps(id);
        if (steps.isEmpty()) {
            throw new BusinessException(400, "流水线无步骤，无法发布");
        }
        if (!isDsAvailable()) {
            throw new BusinessException(502, "DolphinScheduler 不可用，无法发布");
        }
        try {
            removeDsArtifacts(p);
            long projectCode = dsClient.ensureProject(DS_PROJECT);
            String tenant = dsClient.resolveTenant();
            String defName = "跨模块流水线_" + safeName(p.getPipelineName()) + "_" + id;
            List<String> stepNames = new ArrayList<>();
            List<String> scripts = new ArrayList<>();
            for (GovCrossPipelineStep s : steps) {
                stepNames.add((s.getSortNo() == null ? 0 : s.getSortNo()) + "_"
                        + stepTypeLabel(s.getStepType()) + "_" + safeName(s.getRefName()));
                scripts.add(buildStepScript(s));
            }
            long definitionCode = dsClient.createAndReleaseShellChain(
                    projectCode, defName, stepNames, scripts, tenant);

            Integer scheduleId = null;
            String cron = p.getScheduleCron();
            if (cron != null && !cron.isBlank()) {
                scheduleId = dsClient.createAndOnlineSchedule(projectCode, definitionCode, cron.trim());
                dsClient.offlineSchedule(projectCode, scheduleId);
            }

            p.setDsProjectCode(projectCode);
            p.setDsDefinitionCode(definitionCode);
            p.setDsScheduleId(scheduleId);
            p.setPublishStatus("SUCCESS");
            p.setScheduleStatus("STOPPED");
            p.setScheduleEnabled(0);
            p.setLastMessage(scheduleId == null
                    ? "已发布流程定义（未配置 Cron，可立即执行）"
                    : "已发布流程与定时（默认停止，需启动）");
            p.setUpdatedAt(LocalDateTime.now());
            pipelineMapper.updateById(p);
            audit(operator, "CROSS_PIPELINE_PUBLISH", id,
                    "project=" + projectCode + " def=" + definitionCode + " steps=" + steps.size());

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("projectCode", projectCode);
            out.put("definitionCode", definitionCode);
            out.put("scheduleId", scheduleId);
            out.put("publishStatus", "SUCCESS");
            out.put("stepCount", steps.size());
            return out;
        } catch (BusinessException e) {
            p.setPublishStatus("FAILED");
            p.setLastMessage(e.getMessage());
            p.setUpdatedAt(LocalDateTime.now());
            pipelineMapper.updateById(p);
            throw e;
        } catch (Exception e) {
            p.setPublishStatus("FAILED");
            p.setLastMessage(e.getMessage());
            p.setUpdatedAt(LocalDateTime.now());
            pipelineMapper.updateById(p);
            throw new BusinessException(502, "发布失败: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> start(UserPrincipal operator, Long id) {
        GovCrossPipeline p = require(id);
        if (!"SUCCESS".equals(p.getPublishStatus()) || p.getDsProjectCode() == null) {
            throw new BusinessException(400, "请先发布流水线");
        }
        if (p.getDsScheduleId() == null) {
            throw new BusinessException(400, "未配置 Cron，无法启动定时；请使用立即执行或先填写周期后重新发布");
        }
        if ("RUNNING".equals(p.getScheduleStatus())) {
            throw new BusinessException(400, "定时已在运行中");
        }
        if (!isDsAvailable()) {
            throw new BusinessException(502, "DolphinScheduler 不可用");
        }
        dsClient.onlineSchedule(p.getDsProjectCode(), p.getDsScheduleId());
        p.setScheduleStatus("RUNNING");
        p.setScheduleEnabled(1);
        p.setLastMessage("定时已启动");
        p.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(p);
        audit(operator, "CROSS_PIPELINE_START", id, "schedule=" + p.getDsScheduleId());
        return Map.of("id", id, "scheduleStatus", "RUNNING");
    }

    @Transactional
    public Map<String, Object> stop(UserPrincipal operator, Long id) {
        GovCrossPipeline p = require(id);
        if (p.getDsProjectCode() != null && p.getDsScheduleId() != null && isDsAvailable()) {
            try {
                dsClient.offlineSchedule(p.getDsProjectCode(), p.getDsScheduleId());
            } catch (Exception e) {
                log.warn("offline cross pipeline schedule id={}: {}", id, e.getMessage());
            }
        }
        p.setScheduleStatus("STOPPED");
        p.setScheduleEnabled(0);
        p.setLastMessage("定时已停止");
        p.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(p);
        audit(operator, "CROSS_PIPELINE_STOP", id, "offline");
        return Map.of("id", id, "scheduleStatus", "STOPPED");
    }

    @Transactional
    public Map<String, Object> runOnce(UserPrincipal operator, Long id) {
        GovCrossPipeline p = require(id);
        if (!"SUCCESS".equals(p.getPublishStatus())
                || p.getDsProjectCode() == null
                || p.getDsDefinitionCode() == null) {
            throw new BusinessException(400, "请先发布流水线");
        }
        if (!isDsAvailable()) {
            throw new BusinessException(502, "DolphinScheduler 不可用");
        }
        long instanceId = dsClient.startInstance(p.getDsProjectCode(), p.getDsDefinitionCode());
        p.setLastRunAt(LocalDateTime.now());
        p.setLastMessage("已触发立即执行 instance=" + instanceId);
        p.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(p);
        audit(operator, "CROSS_PIPELINE_RUN_ONCE", id, "instance=" + instanceId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("instanceId", instanceId);
        return out;
    }

    private void replaceSteps(Long pipelineId, List<Map<String, Object>> steps) {
        stepMapper.delete(new LambdaQueryWrapper<GovCrossPipelineStep>()
                .eq(GovCrossPipelineStep::getPipelineId, pipelineId));
        int i = 0;
        for (Map<String, Object> raw : steps) {
            String type = normalizeType(str(raw.get("stepType"), null));
            Long refId = toLong(raw.get("refId"));
            if (refId == null) {
                throw new BusinessException(400, "步骤缺少对象 ID");
            }
            String refName = resolveAndValidate(type, refId);
            GovCrossPipelineStep s = new GovCrossPipelineStep();
            s.setPipelineId(pipelineId);
            s.setSortNo(i + 1);
            s.setStepType(type);
            s.setRefId(refId);
            s.setRefName(refName);
            s.setCreatedAt(LocalDateTime.now());
            stepMapper.insert(s);
            i++;
        }
    }

    private String resolveAndValidate(String type, Long refId) {
        return switch (type) {
            case "INGEST" -> {
                IngIngestTask t = ingestTaskMapper.selectById(refId);
                if (t == null) {
                    throw new BusinessException(404, "归集作业不存在: " + refId);
                }
                yield t.getTaskName() == null ? ("作业#" + refId) : t.getTaskName();
            }
            case "GOVERNANCE" -> {
                GovGovernanceTask t = govTaskMapper.selectById(refId);
                if (t == null) {
                    throw new BusinessException(404, "治理任务不存在: " + refId);
                }
                if (t.getTaskDomain() != null && !"GOVERNANCE".equalsIgnoreCase(t.getTaskDomain())) {
                    throw new BusinessException(400, "请选择治理域任务（taskDomain=GOVERNANCE）: " + refId);
                }
                yield t.getTaskName() == null ? ("治理#" + refId) : t.getTaskName();
            }
            case "FUSION" -> {
                GovGovernanceTask t = govTaskMapper.selectById(refId);
                if (t == null) {
                    throw new BusinessException(404, "融合任务不存在: " + refId);
                }
                if (!"FUSION".equalsIgnoreCase(t.getTaskDomain())) {
                    throw new BusinessException(400, "请选择融合域任务（taskDomain=FUSION）: " + refId);
                }
                yield t.getTaskName() == null ? ("融合#" + refId) : t.getTaskName();
            }
            case "QUALITY" -> {
                GovQualityScheme s = qualitySchemeMapper.selectById(refId);
                if (s == null) {
                    throw new BusinessException(404, "质量方案不存在: " + refId);
                }
                yield s.getSchemeName() == null ? ("方案#" + refId) : s.getSchemeName();
            }
            default -> throw new BusinessException(400, "未知步骤类型: " + type);
        };
    }

    private String buildStepScript(GovCrossPipelineStep step) {
        String base = integrationProperties.getDs().getCallbackBaseUrl();
        if (base == null || base.isBlank()) {
            throw new BusinessException(500, "未配置 app.integration.ds.callback-base-url");
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String token = resolveCallbackToken();
        String type = step.getStepType();
        Long refId = step.getRefId();
        String url;
        String body;
        if ("INGEST".equals(type)) {
            url = base + "/api/v1/exchange/ingestion/collect/jobs/" + refId + "/ds-trigger";
            body = "{\\\"dsInstanceId\\\":${system.task.instance.id}}";
        } else if ("GOVERNANCE".equals(type) || "FUSION".equals(type)) {
            url = base + "/api/v1/governance/gov-tasks/" + refId + "/ds-trigger";
            body = "{\\\"dsInstanceId\\\":${system.task.instance.id}}";
        } else if ("QUALITY".equals(type)) {
            url = base + "/api/v1/governance/quality/schemes/" + refId + "/run-callback";
            body = "{}";
        } else {
            throw new BusinessException(400, "未知步骤类型: " + type);
        }
        // 校验业务 code=0（平台失败时常仍 HTTP 200）
        return "RESP=$(curl -sS -X POST \"" + url + "\" "
                + "-H \"X-Ds-Callback-Token: " + token + "\" "
                + "-H \"Content-Type: application/json\" "
                + "-d \"" + body + "\") || exit 1; "
                + "echo \"$RESP\"; "
                + "echo \"$RESP\" | grep -Eq '\"code\"[[:space:]]*:[[:space:]]*0' || exit 1";
    }

    /**
     * 下线定时 + 下线并删除 DS 流程定义，避免门户删除后 DS 残留「上线」定义。
     */
    private void removeDsArtifacts(GovCrossPipeline p) {
        if (p.getDsProjectCode() == null) {
            return;
        }
        if (!isDsAvailable()) {
            log.warn("DS 不可用，跳过清理跨模块流水线 id={} 的 DS 资源", p.getId());
            return;
        }
        long projectCode = p.getDsProjectCode();
        if (p.getDsScheduleId() != null) {
            try {
                dsClient.offlineSchedule(projectCode, p.getDsScheduleId());
            } catch (Exception e) {
                log.warn("offline cross pipeline schedule id={}: {}", p.getId(), e.getMessage());
            }
        }
        if (p.getDsDefinitionCode() == null) {
            return;
        }
        long defCode = p.getDsDefinitionCode();
        String defName = "跨模块流水线_" + safeName(p.getPipelineName())
                + (p.getId() == null ? "" : "_" + p.getId());
        try {
            dsClient.releaseDefinition(projectCode, defCode, "OFFLINE", defName);
        } catch (Exception e) {
            log.warn("offline cross pipeline definition id={} def={}: {}", p.getId(), defCode, e.getMessage());
        }
        try {
            dsClient.deleteDefinition(projectCode, defCode);
        } catch (Exception e) {
            log.warn("delete cross pipeline definition id={} def={}: {}", p.getId(), defCode, e.getMessage());
        }
    }

    private List<GovCrossPipelineStep> listSteps(Long pipelineId) {
        return stepMapper.selectList(new LambdaQueryWrapper<GovCrossPipelineStep>()
                .eq(GovCrossPipelineStep::getPipelineId, pipelineId)
                .orderByAsc(GovCrossPipelineStep::getSortNo)
                .orderByAsc(GovCrossPipelineStep::getId));
    }

    private Map<String, Object> toMap(GovCrossPipeline p, boolean withSteps) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("pipelineName", p.getPipelineName());
        m.put("description", p.getDescription());
        m.put("scheduleCron", p.getScheduleCron());
        m.put("scheduleEnabled", p.getScheduleEnabled() != null && p.getScheduleEnabled() == 1);
        m.put("publishStatus", p.getPublishStatus());
        m.put("scheduleStatus", p.getScheduleStatus());
        m.put("dsProjectCode", p.getDsProjectCode());
        m.put("dsDefinitionCode", p.getDsDefinitionCode());
        m.put("dsScheduleId", p.getDsScheduleId());
        m.put("lastRunAt", p.getLastRunAt());
        m.put("lastMessage", p.getLastMessage());
        m.put("createdBy", p.getCreatedBy());
        m.put("createdAt", p.getCreatedAt());
        m.put("updatedAt", p.getUpdatedAt());
        if (withSteps) {
            List<Map<String, Object>> steps = new ArrayList<>();
            for (GovCrossPipelineStep s : listSteps(p.getId())) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", s.getId());
                row.put("sortNo", s.getSortNo());
                row.put("stepType", s.getStepType());
                row.put("stepTypeLabel", stepTypeLabel(s.getStepType()));
                row.put("refId", s.getRefId());
                row.put("refName", s.getRefName());
                steps.add(row);
            }
            m.put("steps", steps);
            m.put("stepCount", steps.size());
        }
        return m;
    }

    private GovCrossPipeline require(Long id) {
        GovCrossPipeline p = pipelineMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(404, "流水线不存在");
        }
        return p;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseSteps(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        if (list.size() > MAX_STEPS) {
            throw new BusinessException(400, "步骤最多 " + MAX_STEPS + " 个");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException(400, "步骤类型不能为空");
        }
        String t = type.trim().toUpperCase(Locale.ROOT);
        if (!List.of("INGEST", "GOVERNANCE", "QUALITY", "FUSION").contains(t)) {
            throw new BusinessException(400, "步骤类型无效: " + type);
        }
        return t;
    }

    private static String stepTypeLabel(String type) {
        if (type == null) return "步骤";
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "INGEST" -> "归集";
            case "GOVERNANCE" -> "治理";
            case "QUALITY" -> "质量";
            case "FUSION" -> "融合";
            default -> type;
        };
    }

    private boolean isDsAvailable() {
        return integrationProperties.isEnabled() && dsClient.isHealthy();
    }

    private String resolveCallbackToken() {
        String token = integrationProperties.getDs().getCallbackToken();
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        String pwd = integrationProperties.getDs().getPassword();
        return pwd == null ? "chengde-cross-pipeline-callback" : pwd;
    }

    private void audit(UserPrincipal operator, String action, Long id, String detail) {
        Long uid = operator == null ? null : operator.getUserId();
        String uname = operator == null ? null : operator.getUsername();
        Long orgId = operator == null ? null : operator.getOrgId();
        auditService.log(uid, uname, orgId, action, "gov_cross_pipeline", String.valueOf(id), detail);
    }

    private static String str(Object v, String dft) {
        if (v == null) return dft;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? dft : s;
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeName(String name) {
        if (name == null) return "step";
        return name.replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5_\\-]", "_");
    }
}
