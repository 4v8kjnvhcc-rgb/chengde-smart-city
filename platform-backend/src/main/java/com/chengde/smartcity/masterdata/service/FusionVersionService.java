package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovCrossPipeline;
import com.chengde.smartcity.masterdata.entity.GovCrossPipelineStep;
import com.chengde.smartcity.masterdata.entity.GovFusionWorkflowVersion;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTask;
import com.chengde.smartcity.masterdata.mapper.GovCrossPipelineMapper;
import com.chengde.smartcity.masterdata.mapper.GovCrossPipelineStepMapper;
import com.chengde.smartcity.masterdata.mapper.GovFusionWorkflowVersionMapper;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 融合「版本管理」：工作流（融合任务 + 跨模块流水线）版本发布/回滚/锁定/环境隔离。
 * 脚本侧能力仍由 {@link FusionScriptService} 提供。
 */
@Service
public class FusionVersionService {

    private static final Logger log = LoggerFactory.getLogger(FusionVersionService.class);
    private static final ObjectMapper OM = new ObjectMapper();
    public static final String TYPE_TASK = "TASK";
    public static final String TYPE_PIPELINE = "PIPELINE";

    private final GovGovernanceTaskMapper taskMapper;
    private final GovCrossPipelineMapper pipelineMapper;
    private final GovCrossPipelineStepMapper stepMapper;
    private final GovFusionWorkflowVersionMapper versionMapper;
    private final CrossModulePipelineService pipelineService;
    private final GovernanceDsScheduleService dsScheduleService;

    public FusionVersionService(GovGovernanceTaskMapper taskMapper,
                                GovCrossPipelineMapper pipelineMapper,
                                GovCrossPipelineStepMapper stepMapper,
                                GovFusionWorkflowVersionMapper versionMapper,
                                CrossModulePipelineService pipelineService,
                                GovernanceDsScheduleService dsScheduleService) {
        this.taskMapper = taskMapper;
        this.pipelineMapper = pipelineMapper;
        this.stepMapper = stepMapper;
        this.versionMapper = versionMapper;
        this.pipelineService = pipelineService;
        this.dsScheduleService = dsScheduleService;
    }

    public List<Map<String, Object>> listWorkflows(String keyword, String envScope, String objectType) {
        List<Map<String, Object>> out = new ArrayList<>();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String env = envScope == null || envScope.isBlank() ? null : envScope.trim().toUpperCase(Locale.ROOT);
        String type = objectType == null || objectType.isBlank() ? null : objectType.trim().toUpperCase(Locale.ROOT);

        if (type == null || TYPE_TASK.equals(type)) {
            List<GovGovernanceTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GovGovernanceTask>()
                    .eq(GovGovernanceTask::getTaskDomain, "FUSION")
                    .orderByDesc(GovGovernanceTask::getId));
            for (GovGovernanceTask t : tasks) {
                if (env != null && !env.equalsIgnoreCase(nz(t.getEnvScope(), "DEV"))) {
                    continue;
                }
                if (!kw.isEmpty()) {
                    String hit = ((t.getTaskCode() == null ? "" : t.getTaskCode()) + " "
                            + (t.getTaskName() == null ? "" : t.getTaskName())).toLowerCase(Locale.ROOT);
                    if (!hit.contains(kw)) continue;
                }
                out.add(toTaskRow(t));
            }
        }
        if (type == null || TYPE_PIPELINE.equals(type)) {
            List<GovCrossPipeline> pipelines = pipelineMapper.selectList(new LambdaQueryWrapper<GovCrossPipeline>()
                    .orderByDesc(GovCrossPipeline::getId));
            for (GovCrossPipeline p : pipelines) {
                if (env != null && !env.equalsIgnoreCase(nz(p.getEnvScope(), "DEV"))) {
                    continue;
                }
                if (!kw.isEmpty()) {
                    String hit = (p.getPipelineName() == null ? "" : p.getPipelineName()).toLowerCase(Locale.ROOT);
                    if (!hit.contains(kw)) continue;
                }
                out.add(toPipelineRow(p));
            }
        }
        return out;
    }

    @Transactional
    public Map<String, Object> publish(UserPrincipal operator, String objectType, Long objectId, Map<String, Object> body) {
        String type = normalizeType(objectType);
        String summary = body == null ? null : str(body.get("changeSummary"));
        if (summary == null || summary.isBlank()) {
            summary = "发布版本";
        }
        String env = body != null && body.get("envScope") != null
                ? normalizeEnv(String.valueOf(body.get("envScope")))
                : null;

        if (TYPE_TASK.equals(type)) {
            GovGovernanceTask t = requireTask(objectId);
            assertTaskEditable(operator, t);
            int next = (t.getVersionNo() == null ? 0 : t.getVersionNo()) + 1;
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("taskCode", t.getTaskCode());
            snap.put("taskName", t.getTaskName());
            snap.put("description", t.getDescription());
            snap.put("graphJson", t.getGraphJson());
            snap.put("fusionSpecJson", t.getFusionSpecJson());
            snap.put("scheduleCron", t.getScheduleCron());
            snap.put("scheduleMode", t.getScheduleMode());
            snap.put("status", t.getStatus());
            insertVersion(type, objectId, next, toJson(snap), summary, nz(env, nz(t.getEnvScope(), "DEV")), operator);
            t.setVersionNo(next);
            if (env != null) t.setEnvScope(env);
            t.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(t);
            return Map.of("objectType", type, "objectId", objectId, "versionNo", next, "envScope", nz(t.getEnvScope(), "DEV"));
        }

        GovCrossPipeline p = requirePipeline(objectId);
        assertPipelineEditable(operator, p);
        int next = (p.getVersionNo() == null ? 0 : p.getVersionNo()) + 1;
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("pipelineName", p.getPipelineName());
        snap.put("description", p.getDescription());
        snap.put("scheduleCron", p.getScheduleCron());
        snap.put("priority", p.getPriority());
        snap.put("steps", listStepMaps(objectId));
        insertVersion(type, objectId, next, toJson(snap), summary, nz(env, nz(p.getEnvScope(), "DEV")), operator);
        p.setVersionNo(next);
        if (env != null) p.setEnvScope(env);
        p.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(p);
        return Map.of("objectType", type, "objectId", objectId, "versionNo", next, "envScope", nz(p.getEnvScope(), "DEV"));
    }

    public List<Map<String, Object>> listVersions(String objectType, Long objectId) {
        String type = normalizeType(objectType);
        if (TYPE_TASK.equals(type)) {
            requireTask(objectId);
        } else {
            requirePipeline(objectId);
        }
        List<GovFusionWorkflowVersion> rows = versionMapper.selectList(new LambdaQueryWrapper<GovFusionWorkflowVersion>()
                .eq(GovFusionWorkflowVersion::getObjectType, type)
                .eq(GovFusionWorkflowVersion::getObjectId, objectId)
                .orderByDesc(GovFusionWorkflowVersion::getVersionNo));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovFusionWorkflowVersion v : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("objectType", v.getObjectType());
            m.put("objectId", v.getObjectId());
            m.put("versionNo", v.getVersionNo());
            m.put("changeSummary", v.getChangeSummary());
            m.put("envScope", v.getEnvScope());
            m.put("publishedBy", v.getPublishedBy());
            m.put("publishedAt", v.getPublishedAt());
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> rollback(UserPrincipal operator, String objectType, Long objectId, Integer versionNo) {
        String type = normalizeType(objectType);
        GovFusionWorkflowVersion ver = versionMapper.selectOne(new LambdaQueryWrapper<GovFusionWorkflowVersion>()
                .eq(GovFusionWorkflowVersion::getObjectType, type)
                .eq(GovFusionWorkflowVersion::getObjectId, objectId)
                .eq(GovFusionWorkflowVersion::getVersionNo, versionNo));
        if (ver == null) {
            throw new BusinessException(404, "版本不存在: v" + versionNo);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> snap = parseJson(ver.getSnapshotJson());

        if (TYPE_TASK.equals(type)) {
            GovGovernanceTask t = requireTask(objectId);
            assertTaskEditable(operator, t);
            if (snap.get("graphJson") != null) t.setGraphJson(String.valueOf(snap.get("graphJson")));
            if (snap.get("fusionSpecJson") != null) t.setFusionSpecJson(String.valueOf(snap.get("fusionSpecJson")));
            if (snap.get("description") != null) t.setDescription(str(snap.get("description")));
            if (snap.get("scheduleCron") != null) t.setScheduleCron(str(snap.get("scheduleCron")));
            if (snap.get("scheduleMode") != null) t.setScheduleMode(str(snap.get("scheduleMode")));
            t.setEnvScope("DEV");
            t.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(t);
            return Map.of("objectType", type, "objectId", objectId, "versionNo", versionNo, "envScope", "DEV");
        }

        GovCrossPipeline p = requirePipeline(objectId);
        assertPipelineEditable(operator, p);
        if ("RUNNING".equals(p.getScheduleStatus())) {
            throw new BusinessException(400, "请先停止定时再回滚");
        }
        if (snap.get("pipelineName") != null) p.setPipelineName(String.valueOf(snap.get("pipelineName")));
        if (snap.containsKey("description")) p.setDescription(str(snap.get("description")));
        if (snap.containsKey("scheduleCron")) p.setScheduleCron(str(snap.get("scheduleCron")));
        if (snap.get("priority") != null) p.setPriority(String.valueOf(snap.get("priority")));
        Object stepsObj = snap.get("steps");
        if (stepsObj instanceof List<?> list) {
            stepMapper.delete(new LambdaQueryWrapper<GovCrossPipelineStep>()
                    .eq(GovCrossPipelineStep::getPipelineId, objectId));
            int i = 0;
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> raw)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) raw;
                GovCrossPipelineStep s = new GovCrossPipelineStep();
                s.setPipelineId(objectId);
                s.setSortNo(row.get("sortNo") instanceof Number n ? n.intValue() : i);
                s.setStepType(String.valueOf(row.get("stepType")));
                s.setRefId(row.get("refId") instanceof Number n ? n.longValue() : null);
                s.setRefName(str(row.get("refName")));
                s.setCreatedAt(LocalDateTime.now());
                stepMapper.insert(s);
                i++;
            }
            p.setPublishStatus("NONE");
            p.setLastMessage("已回滚到 v" + versionNo + "，请重新发布到调度系统");
        }
        p.setEnvScope("DEV");
        p.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(p);
        return Map.of("objectType", type, "objectId", objectId, "versionNo", versionNo, "envScope", "DEV");
    }

    @Transactional
    public Map<String, Object> lock(UserPrincipal operator, String objectType, Long objectId) {
        String type = normalizeType(objectType);
        String user = operator != null ? operator.getUsername() : "system";
        if (TYPE_TASK.equals(type)) {
            GovGovernanceTask t = requireTask(objectId);
            if ("RUNNING".equals(t.getStatus())) {
                throw new BusinessException(400, "运行中不可锁定");
            }
            if (t.getLockedBy() != null && !t.getLockedBy().isBlank() && !t.getLockedBy().equals(user)) {
                throw new BusinessException(403, "任务已被 " + t.getLockedBy() + " 锁定");
            }
            t.setStatus("LOCKED");
            t.setLockedBy(user);
            t.setLockedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(t);
            return toTaskRow(t);
        }
        GovCrossPipeline p = requirePipeline(objectId);
        if (p.getLockedBy() != null && !p.getLockedBy().isBlank() && !p.getLockedBy().equals(user)) {
            throw new BusinessException(403, "流水线已被 " + p.getLockedBy() + " 锁定");
        }
        p.setLockedBy(user);
        p.setLockedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(p);
        return toPipelineRow(p);
    }

    @Transactional
    public Map<String, Object> unlock(UserPrincipal operator, String objectType, Long objectId) {
        String type = normalizeType(objectType);
        String user = operator != null ? operator.getUsername() : "system";
        boolean privileged = operator != null && operator.isSystemAdmin();
        if (TYPE_TASK.equals(type)) {
            GovGovernanceTask t = requireTask(objectId);
            if (t.getLockedBy() != null && !t.getLockedBy().isBlank()
                    && !t.getLockedBy().equals(user) && !privileged) {
                throw new BusinessException(403, "仅锁定人或系统管理员可解锁");
            }
            t.setStatus(t.getGraphJson() != null && !t.getGraphJson().isBlank() ? "CONFIGURED" : "DRAFT");
            t.setLockedBy(null);
            t.setLockedAt(null);
            t.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(t);
            return toTaskRow(t);
        }
        GovCrossPipeline p = requirePipeline(objectId);
        if (p.getLockedBy() != null && !p.getLockedBy().isBlank()
                && !p.getLockedBy().equals(user) && !privileged) {
            throw new BusinessException(403, "仅锁定人或系统管理员可解锁");
        }
        p.setLockedBy(null);
        p.setLockedAt(null);
        p.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(p);
        return toPipelineRow(p);
    }

    @Transactional
    public Map<String, Object> setEnv(UserPrincipal operator, String objectType, Long objectId, String envScope) {
        String type = normalizeType(objectType);
        String env = normalizeEnv(envScope);
        if (TYPE_TASK.equals(type)) {
            GovGovernanceTask t = requireTask(objectId);
            assertTaskEditable(operator, t);
            t.setEnvScope(env);
            t.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(t);
            return toTaskRow(t);
        }
        GovCrossPipeline p = requirePipeline(objectId);
        assertPipelineEditable(operator, p);
        p.setEnvScope(env);
        p.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(p);
        return toPipelineRow(p);
    }

    /**
     * 工作流一键发布到生产调度：落版本后，流水线走 DS 发布；融合任务走 DS 定时流程定义。
     */
    @Transactional
    public Map<String, Object> deployToProduction(UserPrincipal operator, String objectType, Long objectId,
                                                  Map<String, Object> body) {
        String type = normalizeType(objectType);
        Map<String, Object> pubBody = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        pubBody.putIfAbsent("changeSummary", "一键发布到生产调度");
        pubBody.put("envScope", "PROD");
        Map<String, Object> published = publish(operator, type, objectId, pubBody);

        if (TYPE_PIPELINE.equals(type)) {
            Map<String, Object> ds = pipelineService.publish(operator, objectId);
            Map<String, Object> out = new LinkedHashMap<>(published);
            out.putAll(ds);
            out.put("message", "流水线已版本化并部署到生产调度");
            return out;
        }

        GovGovernanceTask t = requireTask(objectId);
        if (t.getScheduleCron() == null || t.getScheduleCron().isBlank()) {
            if (!"SIMPLE".equalsIgnoreCase(t.getScheduleMode()) || t.getStartTime() == null) {
                throw new BusinessException(400, "融合任务请先配置执行周期（Cron）后再一键发布到生产调度");
            }
        }
        Map<String, Object> ds = dsScheduleService.startSchedule(operator, t);
        Map<String, Object> out = new LinkedHashMap<>(published);
        out.putAll(ds);
        out.put("message", "融合任务已版本化并部署到生产调度");
        return out;
    }

    private void insertVersion(String type, Long objectId, int versionNo, String snapshot,
                               String summary, String env, UserPrincipal operator) {
        GovFusionWorkflowVersion v = new GovFusionWorkflowVersion();
        v.setObjectType(type);
        v.setObjectId(objectId);
        v.setVersionNo(versionNo);
        v.setSnapshotJson(snapshot);
        v.setChangeSummary(summary);
        v.setEnvScope(env);
        v.setPublishedBy(operator != null ? operator.getUsername() : "system");
        v.setPublishedAt(LocalDateTime.now());
        versionMapper.insert(v);
    }

    private List<Map<String, Object>> listStepMaps(Long pipelineId) {
        List<GovCrossPipelineStep> steps = stepMapper.selectList(new LambdaQueryWrapper<GovCrossPipelineStep>()
                .eq(GovCrossPipelineStep::getPipelineId, pipelineId)
                .orderByAsc(GovCrossPipelineStep::getSortNo)
                .orderByAsc(GovCrossPipelineStep::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovCrossPipelineStep s : steps) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sortNo", s.getSortNo());
            row.put("stepType", s.getStepType());
            row.put("refId", s.getRefId());
            row.put("refName", s.getRefName());
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> toTaskRow(GovGovernanceTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("objectType", TYPE_TASK);
        m.put("objectTypeLabel", "融合任务");
        m.put("id", t.getId());
        m.put("code", t.getTaskCode());
        m.put("name", t.getTaskName());
        m.put("status", t.getStatus());
        m.put("versionNo", t.getVersionNo() == null ? 1 : t.getVersionNo());
        m.put("envScope", nz(t.getEnvScope(), "DEV"));
        m.put("lockedBy", t.getLockedBy());
        m.put("lockedAt", t.getLockedAt());
        m.put("dsDefinitionCode", t.getDsDefinitionCode());
        m.put("lastMessage", t.getLastMessage());
        m.put("updatedAt", t.getUpdatedAt());
        return m;
    }

    private Map<String, Object> toPipelineRow(GovCrossPipeline p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("objectType", TYPE_PIPELINE);
        m.put("objectTypeLabel", "跨模块流水线");
        m.put("id", p.getId());
        m.put("code", "PL-" + p.getId());
        m.put("name", p.getPipelineName());
        m.put("status", p.getPublishStatus());
        m.put("scheduleStatus", p.getScheduleStatus());
        m.put("versionNo", p.getVersionNo() == null ? 1 : p.getVersionNo());
        m.put("envScope", nz(p.getEnvScope(), "DEV"));
        m.put("lockedBy", p.getLockedBy());
        m.put("lockedAt", p.getLockedAt());
        m.put("dsDefinitionCode", p.getDsDefinitionCode());
        m.put("lastMessage", p.getLastMessage());
        m.put("updatedAt", p.getUpdatedAt());
        return m;
    }

    private void assertTaskEditable(UserPrincipal operator, GovGovernanceTask t) {
        if ("RUNNING".equals(t.getStatus())) {
            throw new BusinessException(400, "运行中不可编辑");
        }
        if (isLockedByOther(operator, t.getLockedBy())) {
            throw new BusinessException(403, "任务已被 " + t.getLockedBy() + " 锁定");
        }
    }

    private void assertPipelineEditable(UserPrincipal operator, GovCrossPipeline p) {
        if (isLockedByOther(operator, p.getLockedBy())) {
            throw new BusinessException(403, "流水线已被 " + p.getLockedBy() + " 锁定");
        }
    }

    private boolean isLockedByOther(UserPrincipal operator, String lockedBy) {
        if (lockedBy == null || lockedBy.isBlank()) return false;
        String user = operator != null ? operator.getUsername() : null;
        if (user != null && user.equals(lockedBy)) return false;
        return operator == null || !operator.isSystemAdmin();
    }

    private GovGovernanceTask requireTask(Long id) {
        GovGovernanceTask t = taskMapper.selectById(id);
        if (t == null || !"FUSION".equalsIgnoreCase(t.getTaskDomain())) {
            throw new BusinessException(404, "融合任务不存在: " + id);
        }
        return t;
    }

    private GovCrossPipeline requirePipeline(Long id) {
        GovCrossPipeline p = pipelineMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(404, "流水线不存在: " + id);
        }
        return p;
    }

    private static String normalizeType(String objectType) {
        if (objectType == null || objectType.isBlank()) {
            throw new BusinessException(400, "objectType 必填");
        }
        String t = objectType.trim().toUpperCase(Locale.ROOT);
        if (!TYPE_TASK.equals(t) && !TYPE_PIPELINE.equals(t)) {
            throw new BusinessException(400, "objectType 仅支持 TASK/PIPELINE");
        }
        return t;
    }

    private static String normalizeEnv(String env) {
        if (env == null || env.isBlank()) return "DEV";
        String e = env.trim().toUpperCase(Locale.ROOT);
        if (!"DEV".equals(e) && !"PROD".equals(e)) {
            throw new BusinessException(400, "envScope 仅支持 DEV/PROD");
        }
        return e;
    }

    private String toJson(Object o) {
        try {
            return OM.writeValueAsString(o);
        } catch (Exception e) {
            throw new BusinessException(500, "快照序列化失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return OM.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("parse workflow snapshot failed: {}", e.getMessage());
            throw new BusinessException(500, "版本快照解析失败");
        }
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String nz(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }
}
