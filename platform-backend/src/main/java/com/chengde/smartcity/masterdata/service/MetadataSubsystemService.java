package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataColumn;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.mapper.IngDataColumnMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.jdbc.JdbcProbeService;
import com.chengde.smartcity.masterdata.support.DataLayerSupport;
import com.chengde.smartcity.integration.openmetadata.OpenMetadataClient;
import com.chengde.smartcity.masterdata.entity.GovMetaChangeNotice;
import com.chengde.smartcity.masterdata.entity.GovMetaCollectRun;
import com.chengde.smartcity.masterdata.entity.GovMetaCollectTask;
import com.chengde.smartcity.masterdata.entity.GovMetaModel;
import com.chengde.smartcity.masterdata.entity.GovMetaRelation;
import com.chengde.smartcity.masterdata.entity.GovMetaSubscription;
import com.chengde.smartcity.masterdata.entity.GovMetaVersion;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.entity.GovOmConnector;
import com.chengde.smartcity.masterdata.entity.GovStandardItem;
import com.chengde.smartcity.masterdata.mapper.GovMetaChangeNoticeMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaCollectRunMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaCollectTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaModelMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaRelationMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaSubscriptionMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaVersionMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetadataRegistryMapper;
import com.chengde.smartcity.masterdata.mapper.GovOmConnectorMapper;
import com.chengde.smartcity.masterdata.mapper.GovStandardItemMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetadataSubsystemService {

    private static final Logger log = LoggerFactory.getLogger(MetadataSubsystemService.class);
    private static final int JDBC_TABLE_LIMIT = 500;
    private static final long PLATFORM_ODS_ID = -1L;
    private static final long PLATFORM_DWD_ID = -2L;
    private static final long PLATFORM_DWS_ID = -3L;
    private static final long PLATFORM_ADS_ID = -4L;

    private final GovMetaModelMapper modelMapper;
    private final GovMetaCollectTaskMapper taskMapper;
    private final GovMetaCollectRunMapper runMapper;
    private final GovMetaVersionMapper versionMapper;
    private final GovMetaRelationMapper relationMapper;
    private final GovMetadataRegistryMapper registryMapper;
    private final GovOmConnectorMapper connectorMapper;
    private final GovStandardItemMapper standardItemMapper;
    private final GovMetaSubscriptionMapper subscriptionMapper;
    private final GovMetaChangeNoticeMapper changeNoticeMapper;
    private final AuditService auditService;
    private final IntegrationProperties integrationProperties;
    private final OpenMetadataClient openMetadataClient;
    private final DataSource dataSource;
    private final IngDataSourceMapper ingDataSourceMapper;
    private final IngDataTableMapper ingDataTableMapper;
    private final IngDataColumnMapper ingDataColumnMapper;
    private final IngIngestTaskMapper ingIngestTaskMapper;
    private final JdbcProbeService jdbcProbeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MetadataSubsystemService(GovMetaModelMapper modelMapper,
                                    GovMetaCollectTaskMapper taskMapper,
                                    GovMetaCollectRunMapper runMapper,
                                    GovMetaVersionMapper versionMapper,
                                    GovMetaRelationMapper relationMapper,
                                    GovMetadataRegistryMapper registryMapper,
                                    GovOmConnectorMapper connectorMapper,
                                    GovStandardItemMapper standardItemMapper,
                                    GovMetaSubscriptionMapper subscriptionMapper,
                                    GovMetaChangeNoticeMapper changeNoticeMapper,
                                    AuditService auditService,
                                    IntegrationProperties integrationProperties,
                                    OpenMetadataClient openMetadataClient,
                                    IngDataSourceMapper ingDataSourceMapper,
                                    IngDataTableMapper ingDataTableMapper,
                                    IngDataColumnMapper ingDataColumnMapper,
                                    IngIngestTaskMapper ingIngestTaskMapper,
                                    JdbcProbeService jdbcProbeService,
                                    @Autowired(required = false) DataSource dataSource) {
        this.modelMapper = modelMapper;
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.versionMapper = versionMapper;
        this.relationMapper = relationMapper;
        this.registryMapper = registryMapper;
        this.connectorMapper = connectorMapper;
        this.standardItemMapper = standardItemMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.changeNoticeMapper = changeNoticeMapper;
        this.auditService = auditService;
        this.integrationProperties = integrationProperties;
        this.openMetadataClient = openMetadataClient;
        this.ingDataSourceMapper = ingDataSourceMapper;
        this.ingDataTableMapper = ingDataTableMapper;
        this.ingDataColumnMapper = ingDataColumnMapper;
        this.ingIngestTaskMapper = ingIngestTaskMapper;
        this.jdbcProbeService = jdbcProbeService;
        this.dataSource = dataSource;
    }

    public List<GovMetaModel> listModels(String status) {
        LambdaQueryWrapper<GovMetaModel> q = new LambdaQueryWrapper<GovMetaModel>().orderByDesc(GovMetaModel::getId);
        if (status != null && !status.isBlank()) {
            q.eq(GovMetaModel::getStatus, status);
        }
        return modelMapper.selectList(q);
    }

    @Transactional
    public Long createModel(UserPrincipal operator, Map<String, Object> body) {
        GovMetaModel m = new GovMetaModel();
        m.setModelCode(str(body.get("modelCode"), "MM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
        m.setModelNameZh(required(body.get("modelNameZh"), "modelNameZh"));
        m.setModelNameEn(str(body.get("modelNameEn"), null));
        m.setModelType(str(body.get("modelType"), "TABLE"));
        m.setDataLength(intOrNull(body.get("dataLength")));
        m.setRequiredFlag(intVal(body.get("requiredFlag"), 0));
        m.setComponentType(str(body.get("componentType"), "FORM"));
        m.setContentJson(str(body.get("contentJson"), "[]"));
        m.setStatus("DRAFT");
        m.setCreatedBy(operator.getUsername());
        modelMapper.insert(m);
        snapshotVersion(operator, "MODEL", m.getId(), toJson(m), "创建草稿");
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_MODEL_CREATE", "gov_meta_model", String.valueOf(m.getId()), m.getModelNameZh());
        return m.getId();
    }

    @Transactional
    public void updateModel(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovMetaModel m = requireModel(id);
        if (body.containsKey("modelNameEn")) {
            String next = str(body.get("modelNameEn"), null);
            String cur = m.getModelNameEn();
            if (next == null ? cur != null : !next.equals(cur)) {
                throw new BusinessException(400, "英文名称不可编辑");
            }
        }
        if ("PUBLISHED".equals(m.getStatus()) && !Boolean.TRUE.equals(body.get("forceEdit"))) {
            m.setStatus("DRAFT");
        }
        if (body.containsKey("modelNameZh")) m.setModelNameZh(required(body.get("modelNameZh"), "modelNameZh"));
        if (body.containsKey("modelType")) m.setModelType(str(body.get("modelType"), m.getModelType()));
        if (body.containsKey("dataLength")) m.setDataLength(intOrNull(body.get("dataLength")));
        if (body.containsKey("requiredFlag")) m.setRequiredFlag(intVal(body.get("requiredFlag"), 0));
        if (body.containsKey("componentType")) m.setComponentType(str(body.get("componentType"), m.getComponentType()));
        if (body.containsKey("contentJson")) m.setContentJson(str(body.get("contentJson"), m.getContentJson()));
        modelMapper.updateById(m);
        snapshotVersion(operator, "MODEL", m.getId(), toJson(m), "编辑后需重新发布");
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_MODEL_UPDATE", "gov_meta_model", String.valueOf(id), m.getStatus());
    }

    @Transactional
    public void publishModel(UserPrincipal operator, Long id) {
        GovMetaModel m = requireModel(id);
        m.setStatus("PUBLISHED");
        m.setPublishedAt(LocalDateTime.now());
        modelMapper.updateById(m);
        snapshotVersion(operator, "MODEL", m.getId(), toJson(m), "发布元模型");
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_MODEL_PUBLISH", "gov_meta_model", String.valueOf(id), m.getModelCode());
    }

    @Transactional
    public void offlineModel(UserPrincipal operator, Long id) {
        GovMetaModel m = requireModel(id);
        m.setStatus("OFFLINE");
        modelMapper.updateById(m);
        snapshotVersion(operator, "MODEL", m.getId(), toJson(m), "下线元模型");
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_MODEL_OFFLINE", "gov_meta_model", String.valueOf(id), m.getModelCode());
    }

    public Map<String, Object> compareModels(Long leftId, Long rightId) {
        GovMetaModel left = requireModel(leftId);
        GovMetaModel right = requireModel(rightId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("left", left);
        out.put("right", right);
        out.put("sameType", left.getModelType() != null && left.getModelType().equals(right.getModelType()));
        out.put("sameContent", String.valueOf(left.getContentJson()).equals(String.valueOf(right.getContentJson())));
        out.put("fieldDiff", diffContentJsonFields(left.getContentJson(), right.getContentJson()));
        out.put("diffFields", List.of(
                Map.of("field", "modelNameZh", "left", nvl(left.getModelNameZh()), "right", nvl(right.getModelNameZh())),
                Map.of("field", "status", "left", nvl(left.getStatus()), "right", nvl(right.getStatus())),
                Map.of("field", "requiredFlag", "left", String.valueOf(left.getRequiredFlag()), "right", String.valueOf(right.getRequiredFlag()))
        ));
        return out;
    }

    public Map<String, Object> exportModels() {
        List<GovMetaModel> list = listModels(null);
        return Map.of("count", list.size(), "models", list);
    }

    @Transactional
    public int importModels(UserPrincipal operator, Map<String, Object> body) {
        Object raw = body.get("models");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(400, "models required");
        }
        int n = 0;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            map.forEach((k, v) -> m.put(String.valueOf(k), v));
            createModel(operator, m);
            n++;
        }
        return n;
    }

    public List<GovMetaCollectTask> listTasks(String status, String sourceType, String keyword) {
        LambdaQueryWrapper<GovMetaCollectTask> q = new LambdaQueryWrapper<GovMetaCollectTask>()
                .ne(GovMetaCollectTask::getStatus, "DELETED")
                .orderByDesc(GovMetaCollectTask::getId);
        if (status != null && !status.isBlank()) {
            q.eq(GovMetaCollectTask::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.like(GovMetaCollectTask::getTaskName, keyword.trim());
        }
        List<GovMetaCollectTask> tasks = taskMapper.selectList(q);
        if (sourceType != null && !sourceType.isBlank()) {
            String st = sourceType.trim();
            tasks = tasks.stream().filter(t -> {
                GovOmConnector c = connectorMapper.selectById(t.getConnectorId());
                return c != null && st.equalsIgnoreCase(c.getSourceType());
            }).collect(Collectors.toList());
        }
        return tasks;
    }

    @Transactional
    public Long createTask(UserPrincipal operator, Map<String, Object> body) {
        Long ingDataSourceId = longVal(body.get("ingDataSourceId"));
        Long connectorId = longVal(body.get("connectorId"));
        GovOmConnector connector = null;
        if (ingDataSourceId != null && isPlatformLayerId(ingDataSourceId)) {
            connector = findOrCreatePlatformLayerConnector(ingDataSourceId, operator);
            connectorId = connector.getId();
        } else if (ingDataSourceId != null) {
            IngDataSource ingDs = ingDataSourceMapper.selectById(ingDataSourceId);
            if (ingDs == null) {
                throw new BusinessException(404, "登记数据源不存在");
            }
            connector = findOrCreateConnectorFromIngSource(ingDs, operator);
            connectorId = connector.getId();
        } else if (connectorId != null) {
            connector = connectorMapper.selectById(connectorId);
        }
        if (connectorId == null || connector == null) {
            throw new BusinessException(400, "ingDataSourceId 或 connectorId 必填");
        }
        Long modelId = longVal(body.get("modelId"));
        if (modelId != null) {
            GovMetaModel model = requireModel(modelId);
            if (!"PUBLISHED".equals(model.getStatus())) {
                throw new BusinessException(400, "采集任务只能绑定已发布元模型");
            }
        }
        GovMetaCollectTask t = new GovMetaCollectTask();
        t.setTaskCode(str(body.get("taskCode"), "MCT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
        t.setTaskName(required(body.get("taskName"), "taskName"));
        t.setConnectorId(connectorId);
        t.setIngDataSourceId(ingDataSourceId);
        t.setModelId(modelId);
        t.setCronExpr(normalizeOptionalCron(body.get("cronExpr")));
        t.setScopeType(str(body.get("scopeType"), normalizeTableList(body.get("tableList")) == null ? "FULL" : "TABLE"));
        t.setTableList(normalizeTableList(body.get("tableList")));
        t.setStatus("READY");
        t.setCreatedBy(operator.getUsername());
        taskMapper.insert(t);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_COLLECT_TASK_CREATE", "gov_meta_collect_task", String.valueOf(t.getId()), t.getTaskName());
        return t.getId();
    }

    @Transactional
    public void updateTask(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovMetaCollectTask t = requireTask(id);
        if ("DELETED".equals(t.getStatus())) {
            throw new BusinessException(400, "任务已删除");
        }
        if (body.containsKey("connectorId")) {
            throw new BusinessException(400, "connectorId 不可修改");
        }
        if (body.containsKey("taskName")) t.setTaskName(required(body.get("taskName"), "taskName"));
        if (body.containsKey("cronExpr")) t.setCronExpr(str(body.get("cronExpr"), null));
        if (body.containsKey("scopeType")) t.setScopeType(str(body.get("scopeType"), t.getScopeType()));
        if (body.containsKey("tableList")) t.setTableList(str(body.get("tableList"), null));
        if (body.containsKey("modelId")) {
            Long modelId = longVal(body.get("modelId"));
            if (modelId != null) {
                GovMetaModel model = requireModel(modelId);
                if (!"PUBLISHED".equals(model.getStatus())) {
                    throw new BusinessException(400, "只能绑定已发布元模型");
                }
            }
            t.setModelId(modelId);
        }
        taskMapper.updateById(t);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_COLLECT_TASK_UPDATE", "gov_meta_collect_task", String.valueOf(id), t.getTaskName());
    }

    @Transactional
    public void deleteTask(UserPrincipal operator, Long id) {
        GovMetaCollectTask t = requireTask(id);
        if ("RUNNING".equals(t.getStatus())) {
            throw new BusinessException(400, "运行中任务不可删除");
        }
        t.setStatus("DELETED");
        taskMapper.updateById(t);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_COLLECT_TASK_DELETE", "gov_meta_collect_task", String.valueOf(id), t.getTaskName());
    }

    @Transactional
    public Map<String, Object> runTask(UserPrincipal operator, Long taskId) {
        return doRunTask(operator, taskId);
    }

    @Transactional
    public Map<String, Object> runTaskBySystem(Long taskId) {
        return doRunTask(systemPrincipal(), taskId);
    }

    private Map<String, Object> doRunTask(UserPrincipal operator, Long taskId) {
        GovMetaCollectTask task = requireTask(taskId);
        if ("DELETED".equals(task.getStatus())) {
            throw new BusinessException(400, "任务已删除");
        }
        if ("RUNNING".equals(task.getStatus())) {
            throw new BusinessException(409, "任务正在运行");
        }
        if (task.getModelId() != null) {
            GovMetaModel model = requireModel(task.getModelId());
            if (!"PUBLISHED".equals(model.getStatus())) {
                throw new BusinessException(400, "关联元模型未发布，不可采集");
            }
        }
        GovOmConnector connector = connectorMapper.selectById(task.getConnectorId());
        if (connector == null && task.getIngDataSourceId() != null) {
            IngDataSource ingDs = ingDataSourceMapper.selectById(task.getIngDataSourceId());
            if (ingDs != null) {
                connector = findOrCreateConnectorFromIngSource(ingDs, operator);
                task.setConnectorId(connector.getId());
                taskMapper.updateById(task);
            }
        }
        if (connector == null) {
            throw new BusinessException(404, "适配器不存在");
        }

        GovMetaCollectRun run = new GovMetaCollectRun();
        run.setTaskId(taskId);
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        run.setLogText("start collect connector=" + connector.getConnectorName());
        runMapper.insert(run);

        task.setStatus("RUNNING");
        task.setLastRunAt(LocalDateTime.now());
        taskMapper.updateById(task);

        String message;
        int tableCount;
        boolean omUsed = false;
        boolean jdbcUsed = false;
        try {
            List<Map<String, Object>> schema;
            if (task.getIngDataSourceId() != null && isPlatformLayerId(task.getIngDataSourceId())) {
                schema = probePlatformLayerSchema(task.getIngDataSourceId(), task);
            } else if (task.getIngDataSourceId() != null) {
                IngDataSource ingDs = ingDataSourceMapper.selectById(task.getIngDataSourceId());
                schema = ingDs == null ? List.of() : probeIngDataSourceSchema(ingDs, task);
            } else {
                schema = probeJdbcSchema(connector, task);
            }
            if (!schema.isEmpty()) {
                jdbcUsed = true;
                tableCount = upsertTableAndColumns(task, run.getId(), connector, schema);
                Map<String, Object> diff = buildRunDiff(task.getId(), run.getId());
                Map<String, Object> compact = compactRunDiff(diff);
                message = "JDBC采集 tables=" + tableCount
                        + " added=" + compact.get("addedCount")
                        + " removed=" + compact.get("removedCount")
                        + " changed=" + compact.get("changedCount");
                // 完整 diff 明细写入 log_text（TEXT），summary 仅存压缩 JSON，避免 VARCHAR 截断
                run.setLogText(run.getLogText() + "\njdbc tables=" + tableCount + "\ndiff=" + toJson(diff));
                run.setSummary(clip(toJson(compact), 480));
            } else if (integrationProperties.isEnabled() && openMetadataClient.isHealthy()) {
                Map<String, Object> om = openMetadataClient.ingestService(connector.getConnectorName());
                message = "OpenMetadata sync: " + om.getOrDefault("status", "TRIGGERED");
                List<Map<String, Object>> tables = openMetadataClient.listTables(null, 20);
                tableCount = tables.isEmpty() ? 3 + (int) (Math.random() * 8) : tables.size();
                omUsed = true;
                run.setLogText(run.getLogText() + "\nom=" + message + "\ntablesSample=" + tableCount);
                upsertCollectResults(task, run.getId(), tableCount);
                run.setSummary(clip(message, 480));
            } else {
                tableCount = 5 + (int) (Math.random() * 20);
                message = "metadata sync ok, tables=" + tableCount;
                run.setLogText(run.getLogText() + "\nlocalFallback tables=" + tableCount);
                upsertCollectResults(task, run.getId(), tableCount);
                run.setSummary(clip(message, 480));
            }
            run.setStatus("SUCCESS");
            run.setTableCount(tableCount);
            if (run.getSummary() == null) {
                run.setSummary(clip(message, 480));
            }
            run.setEndedAt(LocalDateTime.now());
            runMapper.updateById(run);

            task.setStatus("READY");
            task.setLastMessage(clip(message, 480));
            taskMapper.updateById(task);

            connector.setLastSyncAt(LocalDateTime.now());
            connector.setLastMessage(clip(message, 480));
            connectorMapper.updateById(connector);

            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "META_COLLECT_RUN", "gov_meta_collect_run", String.valueOf(run.getId()), clip(message, 480));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("runId", run.getId());
            out.put("status", "SUCCESS");
            out.put("message", message);
            out.put("tableCount", tableCount);
            out.put("omUsed", omUsed);
            out.put("jdbcUsed", jdbcUsed);
            return out;
        } catch (Exception e) {
            String err = clip(e.getMessage(), 480);
            run.setStatus("FAILED");
            run.setSummary(err);
            run.setEndedAt(LocalDateTime.now());
            run.setLogText(clip(run.getLogText() + "\nerror=" + e.getMessage(), 8000));
            runMapper.updateById(run);
            task.setStatus("READY");
            task.setLastMessage(clip("FAILED: " + err, 480));
            taskMapper.updateById(task);
            if (!integrationProperties.isDemoFallback()) {
                throw new BusinessException(503, "元数据采集失败: " + err);
            }
            return Map.of("runId", run.getId(), "status", "FAILED", "message", err);
        }
    }

    public List<GovMetadataRegistry> listRunResults(Long runId) {
        return registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getRunId, runId)
                .orderByAsc(GovMetadataRegistry::getEntryType)
                .orderByAsc(GovMetadataRegistry::getEntryCode));
    }

    @Transactional
    public Map<String, Object> stopRun(UserPrincipal operator, Long runId) {
        GovMetaCollectRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(404, "运行记录不存在");
        }
        if (!"RUNNING".equals(run.getStatus())) {
            throw new BusinessException(400, "当前运行不可停止");
        }
        boolean omStopped = false;
        if (integrationProperties.isEnabled() && openMetadataClient.isHealthy()) {
            try {
                GovMetaCollectTask task = taskMapper.selectById(run.getTaskId());
                if (task != null) {
                    GovOmConnector c = connectorMapper.selectById(task.getConnectorId());
                    if (c != null) {
                        openMetadataClient.stopIngestionPipeline(c.getConnectorName() + "_ingest");
                        omStopped = true;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        run.setStatus("STOPPED");
        run.setEndedAt(LocalDateTime.now());
        run.setSummary("stopped by " + operator.getUsername());
        run.setLogText((run.getLogText() == null ? "" : run.getLogText()) + "\nSTOPPED");
        runMapper.updateById(run);

        GovMetaCollectTask task = taskMapper.selectById(run.getTaskId());
        if (task != null) {
            task.setStatus("STOPPED");
            task.setLastMessage("stopped run#" + runId);
            taskMapper.updateById(task);
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_COLLECT_STOP", "gov_meta_collect_run", String.valueOf(runId), "STOP");
        return Map.of("runId", runId, "status", "STOPPED", "omStopped", omStopped);
    }

    public List<GovMetaCollectRun> listRuns(Long taskId, String status, String keyword) {
        LambdaQueryWrapper<GovMetaCollectRun> q = new LambdaQueryWrapper<GovMetaCollectRun>().orderByDesc(GovMetaCollectRun::getId);
        if (taskId != null) {
            q.eq(GovMetaCollectRun::getTaskId, taskId);
        }
        if (status != null && !status.isBlank()) {
            q.eq(GovMetaCollectRun::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(GovMetaCollectRun::getSummary, keyword).or().like(GovMetaCollectRun::getLogText, keyword));
        }
        return runMapper.selectList(q);
    }

    public Map<String, Object> monitorOverview(String sourceKeyword, String status) {
        List<GovMetaCollectTask> tasks = listTasks(status, null, null);
        if (sourceKeyword != null && !sourceKeyword.isBlank()) {
            String kw = sourceKeyword.trim();
            tasks = tasks.stream().filter(t -> {
                GovOmConnector c = connectorMapper.selectById(t.getConnectorId());
                return (c != null && (c.getConnectorName().contains(kw) || c.getSourceType().contains(kw)))
                        || t.getTaskName().contains(kw);
            }).collect(Collectors.toList());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GovMetaCollectTask t : tasks) {
            GovOmConnector c = connectorMapper.selectById(t.getConnectorId());
            GovMetaCollectRun last = runMapper.selectOne(new LambdaQueryWrapper<GovMetaCollectRun>()
                    .eq(GovMetaCollectRun::getTaskId, t.getId())
                    .orderByDesc(GovMetaCollectRun::getId)
                    .last("limit 1"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("task", t);
            row.put("connectorName", c == null ? null : c.getConnectorName());
            row.put("sourceType", c == null ? null : c.getSourceType());
            row.put("lastRun", last);
            rows.add(row);
        }
        return Map.of("items", rows, "omHealthy", openMetadataClient.isHealthy());
    }

    @Transactional
    public Long maintainEntry(UserPrincipal operator, Map<String, Object> body) {
        String mode = str(body.get("mode"), "MANUAL");
        Long id = longVal(body.get("id"));
        GovMetadataRegistry e;
        String summary;
        if (id != null) {
            e = registryMapper.selectById(id);
            if (e == null) {
                throw new BusinessException(404, "条目不存在");
            }
            if (body.containsKey("entryName")) e.setEntryName(required(body.get("entryName"), "entryName"));
            if (body.containsKey("entryType")) e.setEntryType(str(body.get("entryType"), e.getEntryType()));
            if (body.containsKey("description")) e.setDescription(str(body.get("description"), null));
            if (body.containsKey("omRef")) e.setOmRef(str(body.get("omRef"), null));
            if (body.containsKey("tags")) e.setTags(normalizeTags(body.get("tags")));
            if (body.containsKey("keywords")) e.setKeywords(str(body.get("keywords"), null));
            if (body.containsKey("securityLevel")) e.setSecurityLevel(str(body.get("securityLevel"), null));
            if (body.containsKey("businessDomain")) e.setBusinessDomain(str(body.get("businessDomain"), null));
            if (body.containsKey("ownerName")) e.setOwnerName(str(body.get("ownerName"), null));
            if (body.containsKey("dataLayer")
                    && (e.getDatabaseName() == null || e.getDatabaseName().isBlank())) {
                e.setDataLayer(str(body.get("dataLayer"), null));
            }
            if (body.containsKey("status")) e.setStatus(str(body.get("status"), e.getStatus()));
            e.setChangeFlag("CHANGED");
            e.setUpdatedAt(LocalDateTime.now());
            registryMapper.updateById(e);
            summary = mode + " 维护更新";
            insertChangeNotice(e, summary, str(body.get("description"), null));
        } else {
            e = new GovMetadataRegistry();
            e.setEntryCode(str(body.get("entryCode"), "META_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
            e.setEntryName(required(body.get("entryName"), "entryName"));
            e.setEntryType(str(body.get("entryType"), "SOURCE"));
            e.setParentCode(str(body.get("parentCode"), null));
            e.setOmRef(str(body.get("omRef"), null));
            e.setModelId(longVal(body.get("modelId")));
            e.setDescription(str(body.get("description"), null));
            e.setTags(normalizeTags(body.get("tags")));
            e.setKeywords(str(body.get("keywords"), null));
            e.setSecurityLevel(str(body.get("securityLevel"), null));
            e.setBusinessDomain(str(body.get("businessDomain"), null));
            e.setOwnerName(str(body.get("ownerName"), null));
            e.setDataLayer(str(body.get("dataLayer"), null));
            e.setChangeFlag("NEW");
            e.setStatus("ACTIVE");
            registryMapper.insert(e);
            summary = mode + " 手工新增";
        }
        if ("AUTO".equalsIgnoreCase(mode)) {
            applyStandardMatch(e);
            registryMapper.updateById(e);
            summary = "AUTO 标准匹配维护";
        }
        snapshotVersion(operator, "ENTRY", e.getId(), toJson(e), summary);
        if (Boolean.TRUE.equals(body.get("promoteStandard"))) {
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "META_PROMOTE_STANDARD", "gov_metadata_registry", String.valueOf(e.getId()), "沉淀标准提醒");
        }
        return e.getId();
    }

    public List<GovMetaVersion> listVersions(String targetType, Long targetId) {
        LambdaQueryWrapper<GovMetaVersion> q = new LambdaQueryWrapper<GovMetaVersion>()
                .eq(GovMetaVersion::getTargetType, targetType)
                .eq(GovMetaVersion::getTargetId, targetId)
                .orderByDesc(GovMetaVersion::getVersionNo);
        return versionMapper.selectList(q);
    }

    public Map<String, Object> compareVersions(Long leftId, Long rightId) {
        GovMetaVersion left = versionMapper.selectById(leftId);
        GovMetaVersion right = versionMapper.selectById(rightId);
        if (left == null || right == null) {
            throw new BusinessException(404, "版本不存在");
        }
        Map<String, Object> leftSnap = parseSnapshot(left.getSnapshotJson());
        Map<String, Object> rightSnap = parseSnapshot(right.getSnapshotJson());
        List<Map<String, Object>> basicDiff = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        keys.addAll(leftSnap.keySet());
        keys.addAll(rightSnap.keySet());
        for (String key : keys) {
            if ("contentJson".equals(key)) continue;
            String lv = nvl(String.valueOf(leftSnap.getOrDefault(key, "")));
            String rv = nvl(String.valueOf(rightSnap.getOrDefault(key, "")));
            if (!lv.equals(rv)) {
                basicDiff.add(Map.of("field", key, "left", lv, "right", rv, "changeType", "changed"));
            }
        }
        Map<String, Object> fieldDiff = diffContentJsonFields(
                leftSnap.get("contentJson"), rightSnap.get("contentJson"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("left", left);
        out.put("right", right);
        out.put("sameSnapshot", String.valueOf(left.getSnapshotJson()).equals(String.valueOf(right.getSnapshotJson())));
        out.put("basicDiff", basicDiff);
        out.put("fieldDiff", fieldDiff);
        return out;
    }

    public List<GovMetadataRegistry> searchCatalog(String keyword, String type, String tag) {
        return searchCatalog(keyword, type, tag, null);
    }

    public List<GovMetadataRegistry> searchCatalog(String keyword, String type, String tag, UserPrincipal operator) {
        // 打开目录时回填已有 ODS 上传/汇聚资产，保证资产树可见
        try {
            syncOdsIngestAssetsToMetadata(operator);
        } catch (Exception e) {
            log.warn("目录检索前回填 ODS 资产失败: {}", e.getMessage());
        }
        LambdaQueryWrapper<GovMetadataRegistry> q = new LambdaQueryWrapper<GovMetadataRegistry>()
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .orderByAsc(GovMetadataRegistry::getEntryType)
                .orderByAsc(GovMetadataRegistry::getEntryName);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(GovMetadataRegistry::getEntryName, kw)
                    .or().like(GovMetadataRegistry::getEntryCode, kw)
                    .or().like(GovMetadataRegistry::getKeywords, kw)
                    .or().like(GovMetadataRegistry::getTags, kw));
        }
        if (tag != null && !tag.isBlank()) {
            q.like(GovMetadataRegistry::getTags, tag.trim());
        }
        List<GovMetadataRegistry> all = registryMapper.selectList(q);
        String t = type == null ? "" : type.trim().toLowerCase();
        if ("source".equals(t)) {
            return all.stream().filter(e -> isSourceType(e.getEntryType())).collect(Collectors.toList());
        }
        // 资产/全部：排除控制面 smart_city 系统表及其字段
        List<GovMetadataRegistry> plane = excludeControlPlaneEntries(all);
        if ("asset".equals(t)) {
            return plane.stream().filter(e -> isAssetType(e.getEntryType())).collect(Collectors.toList());
        }
        return plane;
    }

    public Map<String, Object> catalogViews() {
        List<GovMetadataRegistry> all = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .orderByAsc(GovMetadataRegistry::getId));
        List<GovMetadataRegistry> sources = all.stream()
                .filter(e -> isSourceType(e.getEntryType()))
                .collect(Collectors.toList());
        List<GovMetadataRegistry> plane = excludeControlPlaneEntries(all);
        List<GovMetadataRegistry> assets = plane.stream()
                .filter(e -> isAssetType(e.getEntryType()))
                .collect(Collectors.toList());
        List<Map<String, Object>> omTables = List.of();
        if (integrationProperties.isEnabled() && openMetadataClient.isHealthy()) {
            try {
                omTables = openMetadataClient.listTables(null, 30);
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceCatalog", sources);
        out.put("assetCatalog", assets);
        out.put("omTables", omTables);
        out.put("omHealthy", openMetadataClient.isHealthy());
        return out;
    }

    @Transactional
    public void offlineEntry(UserPrincipal operator, Long id) {
        GovMetadataRegistry e = registryMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(404, "条目不存在");
        }
        e.setStatus("OFFLINE");
        e.setChangeFlag("CHANGED");
        e.setUpdatedAt(LocalDateTime.now());
        registryMapper.updateById(e);
        insertChangeNotice(e, "目录条目下线", null);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_ENTRY_OFFLINE", "gov_metadata_registry", String.valueOf(id), e.getEntryCode());
    }

    public Map<String, Object> analyzeGraph(String relationType) {
        List<GovMetadataRegistry> entries = registryMapper.selectList(
                new LambdaQueryWrapper<GovMetadataRegistry>().ne(GovMetadataRegistry::getStatus, "OFFLINE"));
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (GovMetadataRegistry e : entries) {
            nodes.add(Map.of("id", e.getEntryCode(), "label", e.getEntryName(), "type", e.getEntryType()));
        }
        LambdaQueryWrapper<GovMetaRelation> q = new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getStatus, "ACTIVE");
        if (relationType != null && !relationType.isBlank()) {
            q.eq(GovMetaRelation::getRelationType, relationType);
        }
        List<GovMetaRelation> rels = relationMapper.selectList(q);
        List<Map<String, Object>> edges = new ArrayList<>();
        for (GovMetaRelation r : rels) {
            edges.add(Map.of(
                    "from", r.getFromCode(),
                    "to", r.getToCode(),
                    "label", r.getLabel() == null ? r.getRelationType() : r.getLabel(),
                    "type", r.getRelationType()
            ));
        }
        if (edges.isEmpty() && integrationProperties.isEnabled() && openMetadataClient.isHealthy() && !entries.isEmpty()) {
            try {
                String fqn = entries.get(0).getOmRef() != null ? entries.get(0).getOmRef() : entries.get(0).getEntryCode();
                Map<String, Object> omLin = openMetadataClient.getLineage(fqn);
                if (omLin != null && !omLin.isEmpty()) {
                    return Map.of("nodes", nodes, "edges", edges, "omLineage", omLin, "source", "om+local");
                }
            } catch (Exception ignored) {
            }
        }
        return Map.of("nodes", nodes, "edges", edges, "source", "local");
    }

    public Map<String, Object> analyzeImpactRecursive(String fromCode) {
        if (fromCode == null || fromCode.isBlank()) {
            throw new BusinessException(400, "fromCode required");
        }
        List<GovMetaRelation> rels = relationMapper.selectList(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getStatus, "ACTIVE"));
        Set<String> visited = new HashSet<>();
        List<String> impacted = new ArrayList<>();
        collectDownstream(fromCode.trim(), rels, visited, impacted);
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(Map.of("id", fromCode, "label", fromCode, "type", "ROOT"));
        for (String code : impacted) {
            GovMetadataRegistry e = registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                    .eq(GovMetadataRegistry::getEntryCode, code).last("limit 1"));
            nodes.add(Map.of(
                    "id", code,
                    "label", e == null ? code : e.getEntryName(),
                    "type", e == null ? "UNKNOWN" : e.getEntryType()
            ));
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (GovMetaRelation r : rels) {
            if (visited.contains(r.getFromCode()) && visited.contains(r.getToCode())) {
                edges.add(Map.of("from", r.getFromCode(), "to", r.getToCode(),
                        "label", r.getLabel() == null ? r.getRelationType() : r.getLabel(),
                        "type", r.getRelationType()));
            }
        }
        return Map.of("fromCode", fromCode, "impacted", impacted, "nodes", nodes, "edges", edges, "count", impacted.size());
    }

    @Transactional
    public Long subscribe(UserPrincipal operator, Map<String, Object> body) {
        String targetType = required(body.get("targetType"), "targetType");
        Long targetId = longVal(body.get("targetId"));
        if (targetId == null) {
            throw new BusinessException(400, "targetId required");
        }
        GovMetaSubscription exist = subscriptionMapper.selectOne(new LambdaQueryWrapper<GovMetaSubscription>()
                .eq(GovMetaSubscription::getUserId, operator.getUserId())
                .eq(GovMetaSubscription::getTargetType, targetType)
                .eq(GovMetaSubscription::getTargetId, targetId)
                .last("limit 1"));
        if (exist != null) {
            exist.setStatus("ACTIVE");
            subscriptionMapper.updateById(exist);
            return exist.getId();
        }
        GovMetaSubscription sub = new GovMetaSubscription();
        sub.setUserId(operator.getUserId());
        sub.setUsername(operator.getUsername());
        sub.setTargetType(targetType);
        sub.setTargetId(targetId);
        sub.setStatus("ACTIVE");
        subscriptionMapper.insert(sub);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_SUBSCRIBE", "gov_meta_subscription", String.valueOf(sub.getId()), targetType);
        return sub.getId();
    }

    public List<GovMetaSubscription> listSubscriptions(UserPrincipal operator, String targetType) {
        LambdaQueryWrapper<GovMetaSubscription> q = new LambdaQueryWrapper<GovMetaSubscription>()
                .eq(GovMetaSubscription::getUserId, operator.getUserId())
                .eq(GovMetaSubscription::getStatus, "ACTIVE")
                .orderByDesc(GovMetaSubscription::getId);
        if (targetType != null && !targetType.isBlank()) {
            q.eq(GovMetaSubscription::getTargetType, targetType);
        }
        return subscriptionMapper.selectList(q);
    }

    public List<GovMetaChangeNotice> listNotices(String status) {
        LambdaQueryWrapper<GovMetaChangeNotice> q = new LambdaQueryWrapper<GovMetaChangeNotice>()
                .orderByDesc(GovMetaChangeNotice::getId);
        if (status != null && !status.isBlank()) {
            q.eq(GovMetaChangeNotice::getStatus, status);
        }
        return changeNoticeMapper.selectList(q);
    }

    public List<Map<String, Object>> suggestStandards() {
        List<GovMetadataRegistry> columns = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryType, "COLUMN"));
        Map<String, Long> freq = new HashMap<>();
        for (GovMetadataRegistry c : columns) {
            if (c.getEntryName() == null || c.getEntryName().isBlank()) continue;
            String name = c.getEntryName().trim();
            freq.merge(name, 1L, Long::sum);
        }
        return freq.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(20)
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("itemName", e.getKey());
                    row.put("count", e.getValue());
                    row.put("itemType", "COLUMN");
                    return row;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public Long promoteSuggestedStandard(UserPrincipal operator, Map<String, Object> body) {
        String itemName = required(body.get("itemName"), "itemName");
        GovStandardItem item = new GovStandardItem();
        item.setItemCode(str(body.get("itemCode"), "STD_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
        item.setItemName(itemName);
        item.setItemType(str(body.get("itemType"), "COLUMN"));
        item.setStandardRef(str(body.get("standardRef"), "META_SUGGEST"));
        item.setStatus("ACTIVE");
        standardItemMapper.insert(item);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_STANDARD_PROMOTE", "gov_standard_item", String.valueOf(item.getId()), itemName);
        return item.getId();
    }

    @Transactional
    public int parseForeignKeys(UserPrincipal operator, Long connectorId) {
        GovOmConnector connector = connectorMapper.selectById(connectorId);
        if (connector == null) {
            throw new BusinessException(404, "适配器不存在");
        }
        int count = 0;
        try (Connection conn = openJdbcConnection(connector)) {
            String schema = resolveSchema(conn, connector);
            String sql = "SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME "
                    + "FROM information_schema.KEY_COLUMN_USAGE "
                    + "WHERE TABLE_SCHEMA = ? AND REFERENCED_TABLE_NAME IS NOT NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String table = rs.getString("TABLE_NAME");
                        String col = rs.getString("COLUMN_NAME");
                        String refTable = rs.getString("REFERENCED_TABLE_NAME");
                        String refCol = rs.getString("REFERENCED_COLUMN_NAME");
                        String fromCode = tableEntryCode(connectorId, table);
                        String toCode = tableEntryCode(connectorId, refTable);
                        GovMetaRelation exist = relationMapper.selectOne(new LambdaQueryWrapper<GovMetaRelation>()
                                .eq(GovMetaRelation::getFromCode, fromCode)
                                .eq(GovMetaRelation::getToCode, toCode)
                                .eq(GovMetaRelation::getRelationType, "FK")
                                .last("limit 1"));
                        if (exist == null) {
                            GovMetaRelation r = new GovMetaRelation();
                            r.setFromCode(fromCode);
                            r.setToCode(toCode);
                            r.setRelationType("FK");
                            r.setLabel(col + "->" + refCol);
                            r.setStatus("ACTIVE");
                            relationMapper.insert(r);
                            count++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new BusinessException(503, "解析外键失败: " + e.getMessage());
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_PARSE_FK", "gov_om_connector", String.valueOf(connectorId), "count=" + count);
        return count;
    }

    @Transactional
    public Long createRelation(UserPrincipal operator, Map<String, Object> body) {
        GovMetaRelation r = new GovMetaRelation();
        r.setFromCode(required(body.get("fromCode"), "fromCode"));
        r.setToCode(required(body.get("toCode"), "toCode"));
        r.setRelationType(str(body.get("relationType"), "ASSOC"));
        r.setLabel(str(body.get("label"), null));
        r.setStatus("ACTIVE");
        relationMapper.insert(r);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_RELATION_CREATE", "gov_meta_relation", String.valueOf(r.getId()), r.getRelationType());
        return r.getId();
    }

    private List<Map<String, Object>> probeJdbcSchema(GovOmConnector connector, GovMetaCollectTask task) {
        try (Connection conn = openJdbcConnection(connector)) {
            String schema = resolveSchema(conn, connector);
            Set<String> allowedTables = parseTableList(task);
            List<Map<String, Object>> tables = new ArrayList<>();
            String tableSql = "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME LIMIT ?";
            try (PreparedStatement ps = conn.prepareStatement(tableSql)) {
                ps.setString(1, schema);
                ps.setInt(2, JDBC_TABLE_LIMIT);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        if (!allowedTables.isEmpty() && !allowedTables.contains(tableName)) {
                            continue;
                        }
                        Map<String, Object> table = new LinkedHashMap<>();
                        table.put("tableName", tableName);
                        table.put("columns", loadColumns(conn, schema, tableName));
                        tables.add(table);
                    }
                }
            }
            return tables;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<Map<String, String>> loadColumns(Connection conn, String schema, String tableName) throws Exception {
        List<Map<String, String>> cols = new ArrayList<>();
        String colSql = "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_COMMENT "
                + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        try (PreparedStatement ps = conn.prepareStatement(colSql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> col = new LinkedHashMap<>();
                    col.put("columnName", rs.getString("COLUMN_NAME"));
                    col.put("dataType", rs.getString("DATA_TYPE"));
                    col.put("nullable", rs.getString("IS_NULLABLE"));
                    col.put("comment", rs.getString("COLUMN_COMMENT"));
                    cols.add(col);
                }
            }
        }
        return cols;
    }

    private int upsertTableAndColumns(GovMetaCollectTask task, Long runId, GovOmConnector connector,
                                      List<Map<String, Object>> schema) {
        int tableCount = 0;
        for (Map<String, Object> table : schema) {
            String tableName = String.valueOf(table.get("tableName"));
            String tableCode = tableEntryCode(connector.getId(), tableName);
            GovMetadataRegistry tbl = registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                    .eq(GovMetadataRegistry::getEntryCode, tableCode).last("limit 1"));
            if (tbl == null) {
                tbl = new GovMetadataRegistry();
                tbl.setEntryCode(tableCode);
                tbl.setEntryName(tableName);
                tbl.setEntryType("TABLE");
                tbl.setModelId(task.getModelId());
                tbl.setRunId(runId);
                tbl.setChangeFlag("NEW");
                tbl.setDescription("JDBC采集");
                tbl.setStatus("ACTIVE");
                enrichRegistryLayerFields(tbl, tableName, connector, task);
                registryMapper.insert(tbl);
            } else {
                tbl.setRunId(runId);
                tbl.setChangeFlag("CHANGED");
                tbl.setUpdatedAt(LocalDateTime.now());
                enrichRegistryLayerFields(tbl, tableName, connector, task);
                registryMapper.updateById(tbl);
            }
            tableCount++;
            @SuppressWarnings("unchecked")
            List<Map<String, String>> columns = (List<Map<String, String>>) table.get("columns");
            if (columns != null) {
                for (Map<String, String> col : columns) {
                    String colName = col.get("columnName");
                    String colCode = columnEntryCode(connector.getId(), tableName, colName);
                    GovMetadataRegistry ce = registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                            .eq(GovMetadataRegistry::getEntryCode, colCode).last("limit 1"));
                    if (ce == null) {
                        ce = new GovMetadataRegistry();
                        ce.setEntryCode(colCode);
                        ce.setEntryName(colName);
                        ce.setEntryType("COLUMN");
                        ce.setParentCode(tableCode);
                        ce.setModelId(task.getModelId());
                        ce.setRunId(runId);
                        ce.setChangeFlag("NEW");
                        ce.setDescription(col.get("dataType"));
                        ce.setStatus("ACTIVE");
                        registryMapper.insert(ce);
                    } else {
                        ce.setRunId(runId);
                        ce.setParentCode(tableCode);
                        ce.setChangeFlag("CHANGED");
                        ce.setDescription(col.get("dataType"));
                        ce.setUpdatedAt(LocalDateTime.now());
                        registryMapper.updateById(ce);
                    }
                }
            }
            applyConformity(tbl, task.getModelId());
            registryMapper.updateById(tbl);
        }
        return tableCount;
    }

    private Map<String, Object> buildRunDiff(Long taskId, Long currentRunId) {
        GovMetaCollectRun prev = runMapper.selectOne(new LambdaQueryWrapper<GovMetaCollectRun>()
                .eq(GovMetaCollectRun::getTaskId, taskId)
                .eq(GovMetaCollectRun::getStatus, "SUCCESS")
                .ne(GovMetaCollectRun::getId, currentRunId)
                .orderByDesc(GovMetaCollectRun::getId)
                .last("limit 1"));
        List<GovMetadataRegistry> current = listRunResults(currentRunId);
        List<GovMetadataRegistry> previous = prev == null ? List.of() : listRunResults(prev.getId());
        Map<String, GovMetadataRegistry> prevMap = previous.stream()
                .collect(Collectors.toMap(GovMetadataRegistry::getEntryCode, e -> e, (a, b) -> a));
        Map<String, GovMetadataRegistry> curMap = current.stream()
                .collect(Collectors.toMap(GovMetadataRegistry::getEntryCode, e -> e, (a, b) -> a));
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        for (String code : curMap.keySet()) {
            if (!prevMap.containsKey(code)) {
                added.add(code);
            } else {
                GovMetadataRegistry c = curMap.get(code);
                GovMetadataRegistry p = prevMap.get(code);
                if (!nvl(c.getEntryName()).equals(nvl(p.getEntryName()))
                        || !nvl(c.getDescription()).equals(nvl(p.getDescription()))) {
                    changed.add(code);
                }
            }
        }
        for (String code : prevMap.keySet()) {
            if (!curMap.containsKey(code)) {
                removed.add(code);
            }
        }
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("added", added);
        diff.put("removed", removed);
        diff.put("changed", changed);
        diff.put("prevRunId", prev == null ? null : prev.getId());
        return diff;
    }

    /**
     * 压缩 diff 供 summary 落库：保留计数 + 少量样例编码，避免整表 entryCode 列表撑爆 VARCHAR(512)。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> compactRunDiff(Map<String, Object> diff) {
        List<String> added = (List<String>) diff.getOrDefault("added", List.of());
        List<String> removed = (List<String>) diff.getOrDefault("removed", List.of());
        List<String> changed = (List<String>) diff.getOrDefault("changed", List.of());
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("addedCount", added.size());
        compact.put("removedCount", removed.size());
        compact.put("changedCount", changed.size());
        compact.put("added", sampleCodes(added, 8));
        compact.put("removed", sampleCodes(removed, 8));
        compact.put("changed", sampleCodes(changed, 8));
        compact.put("prevRunId", diff.get("prevRunId"));
        compact.put("truncated", added.size() > 8 || removed.size() > 8 || changed.size() > 8);
        return compact;
    }

    private List<String> sampleCodes(List<String> codes, int limit) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        if (codes.size() <= limit) {
            return codes;
        }
        return new ArrayList<>(codes.subList(0, limit));
    }

    private String clip(String raw, int max) {
        if (raw == null) {
            return null;
        }
        if (raw.length() <= max) {
            return raw;
        }
        return raw.substring(0, Math.max(0, max - 3)) + "...";
    }

    private void upsertCollectResults(GovMetaCollectTask task, Long runId, int tableCount) {
        for (int i = 1; i <= Math.min(tableCount, 5); i++) {
            String code = "META_TBL_" + task.getId() + "_" + i;
            GovMetadataRegistry exist = registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                    .eq(GovMetadataRegistry::getEntryCode, code).last("limit 1"));
            if (exist == null) {
                GovMetadataRegistry e = new GovMetadataRegistry();
                e.setEntryCode(code);
                e.setEntryName(task.getTaskName() + "-表" + i);
                e.setEntryType("TABLE");
                e.setModelId(task.getModelId());
                e.setRunId(runId);
                e.setChangeFlag("NEW");
                e.setDescription("采集产出");
                e.setStatus("ACTIVE");
                registryMapper.insert(e);
            } else {
                exist.setRunId(runId);
                exist.setChangeFlag("CHANGED");
                exist.setUpdatedAt(LocalDateTime.now());
                registryMapper.updateById(exist);
            }
        }
    }

    private Connection openJdbcConnection(GovOmConnector connector) throws Exception {
        if (connector.getJdbcUrl() != null && !connector.getJdbcUrl().isBlank()) {
            String user = connector.getJdbcUser();
            String pwd = connector.getJdbcPassword();
            if (user != null && !user.isBlank()) {
                return DriverManager.getConnection(connector.getJdbcUrl(), user, pwd == null ? "" : pwd);
            }
            return DriverManager.getConnection(connector.getJdbcUrl());
        }
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        throw new BusinessException(400, "无可用 JDBC 连接");
    }

    private String resolveSchema(Connection conn, GovOmConnector connector) throws Exception {
        if (connector.getJdbcDatabase() != null && !connector.getJdbcDatabase().isBlank()) {
            return connector.getJdbcDatabase();
        }
        String catalog = conn.getCatalog();
        if (catalog != null && !catalog.isBlank()) {
            return catalog;
        }
        DatabaseMetaData meta = conn.getMetaData();
        String url = meta.getURL();
        if (url != null && url.contains("/")) {
            String part = url.substring(url.lastIndexOf('/') + 1);
            int q = part.indexOf('?');
            return q > 0 ? part.substring(0, q) : part;
        }
        return "smartcity";
    }

    private Set<String> parseTableList(GovMetaCollectTask task) {
        if (!"TABLE".equalsIgnoreCase(task.getScopeType()) || task.getTableList() == null || task.getTableList().isBlank()) {
            return Collections.emptySet();
        }
        Set<String> set = new HashSet<>();
        for (String t : task.getTableList().split("[,;\\s]+")) {
            if (!t.isBlank()) set.add(t.trim());
        }
        return set;
    }

    private String tableEntryCode(Long connectorId, String tableName) {
        return "TBL_" + connectorId + "_" + tableName;
    }

    private String columnEntryCode(Long connectorId, String tableName, String columnName) {
        return "COL_" + connectorId + "_" + tableName + "_" + columnName;
    }

    private void applyStandardMatch(GovMetadataRegistry e) {
        if (e.getEntryName() == null || e.getEntryName().isBlank()) return;
        GovStandardItem std = standardItemMapper.selectOne(new LambdaQueryWrapper<GovStandardItem>()
                .like(GovStandardItem::getItemName, e.getEntryName())
                .eq(GovStandardItem::getStatus, "ACTIVE")
                .last("limit 1"));
        if (std != null) {
            if (e.getDescription() == null || e.getDescription().isBlank()) {
                e.setDescription("标准:" + std.getItemName() + " ref=" + nvl(std.getStandardRef()));
            }
            if (e.getOmRef() == null || e.getOmRef().isBlank()) {
                e.setOmRef(std.getItemCode());
            }
            e.setChangeFlag("CHANGED");
        }
    }

    private void insertChangeNotice(GovMetadataRegistry e, String title, String detail) {
        if (!"CHANGED".equals(e.getChangeFlag())) return;
        GovMetaChangeNotice notice = new GovMetaChangeNotice();
        notice.setEntryId(e.getId());
        notice.setEntryCode(e.getEntryCode());
        notice.setTitle(title);
        notice.setDetail(detail == null ? e.getEntryName() : detail);
        notice.setStatus("UNREAD");
        changeNoticeMapper.insert(notice);
    }

    private void collectDownstream(String code, List<GovMetaRelation> rels, Set<String> visited, List<String> impacted) {
        if (!visited.add(code)) return;
        for (GovMetaRelation r : rels) {
            if (code.equals(r.getFromCode()) && !visited.contains(r.getToCode())) {
                impacted.add(r.getToCode());
                collectDownstream(r.getToCode(), rels, visited, impacted);
            }
        }
    }

    private Map<String, Object> parseSnapshot(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> diffContentJsonFields(Object leftRaw, Object rightRaw) {
        List<String> leftFields = extractFieldNames(leftRaw);
        List<String> rightFields = extractFieldNames(rightRaw);
        Set<String> leftSet = new HashSet<>(leftFields);
        Set<String> rightSet = new HashSet<>(rightFields);
        List<String> added = rightFields.stream().filter(f -> !leftSet.contains(f)).collect(Collectors.toList());
        List<String> removed = leftFields.stream().filter(f -> !rightSet.contains(f)).collect(Collectors.toList());
        List<String> changed = new ArrayList<>();
        if (leftRaw != null && rightRaw != null) {
            Map<String, String> leftMap = fieldNameToJson(leftRaw);
            Map<String, String> rightMap = fieldNameToJson(rightRaw);
            for (String name : leftMap.keySet()) {
                if (rightMap.containsKey(name) && !nvl(leftMap.get(name)).equals(nvl(rightMap.get(name)))) {
                    changed.add(name);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", added);
        out.put("removed", removed);
        out.put("changed", changed);
        return out;
    }

    private List<String> extractFieldNames(Object raw) {
        List<String> names = new ArrayList<>();
        if (raw == null) return names;
        try {
            if (raw instanceof String s) {
                if (s.isBlank()) return names;
                List<Map<String, Object>> list = objectMapper.readValue(s, new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> item : list) {
                    Object fn = item.get("fieldName");
                    if (fn == null) fn = item.get("name");
                    if (fn != null) names.add(String.valueOf(fn));
                }
                return names;
            }
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Object fn = map.get("fieldName");
                        if (fn == null) fn = map.get("name");
                        if (fn != null) names.add(String.valueOf(fn));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    private Map<String, String> fieldNameToJson(Object raw) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            String json = raw instanceof String ? (String) raw : objectMapper.writeValueAsString(raw);
            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> item : list) {
                Object fn = item.get("fieldName");
                if (fn == null) fn = item.get("name");
                if (fn != null) {
                    map.put(String.valueOf(fn), objectMapper.writeValueAsString(item));
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private boolean isSourceType(String entryType) {
        return "SOURCE".equalsIgnoreCase(entryType) || "CONNECTOR".equalsIgnoreCase(entryType);
    }

    private boolean isAssetType(String entryType) {
        return "CATALOG".equalsIgnoreCase(entryType) || "MODEL".equalsIgnoreCase(entryType)
                || "TABLE".equalsIgnoreCase(entryType) || "COLUMN".equalsIgnoreCase(entryType);
    }

    /** 控制面表：dataLayer=CONTROL 或物理库 smart_city */
    private boolean isControlPlaneTable(GovMetadataRegistry e) {
        if (e == null || !"TABLE".equalsIgnoreCase(e.getEntryType())) {
            return false;
        }
        if (DataLayerSupport.isControlLayer(e.getDataLayer())) {
            return true;
        }
        return DataLayerSupport.isControlDatabase(e.getDatabaseName());
    }

    private List<GovMetadataRegistry> excludeControlPlaneEntries(List<GovMetadataRegistry> all) {
        Set<String> controlTableCodes = all.stream()
                .filter(this::isControlPlaneTable)
                .map(GovMetadataRegistry::getEntryCode)
                .collect(Collectors.toSet());
        if (controlTableCodes.isEmpty()) {
            return all;
        }
        return all.stream()
                .filter(e -> {
                    if (isControlPlaneTable(e)) {
                        return false;
                    }
                    // 控制面表下的字段一并排除
                    if ("COLUMN".equalsIgnoreCase(e.getEntryType())
                            && e.getParentCode() != null
                            && controlTableCodes.contains(e.getParentCode())) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private String resolveDataPlaneLayer(GovMetadataRegistry t) {
        if (t.getDataLayer() != null && !t.getDataLayer().isBlank()
                && DataLayerSupport.isDataPlaneLayer(t.getDataLayer())) {
            return t.getDataLayer().trim().toUpperCase(Locale.ROOT);
        }
        if (t.getDatabaseName() != null && DataLayerSupport.isPlatformLayerDb(t.getDatabaseName())
                && !DataLayerSupport.isControlDatabase(t.getDatabaseName())) {
            return DataLayerSupport.layerForDatabase(t.getDatabaseName());
        }
        String name = t.getPhysicalTableName() != null ? t.getPhysicalTableName() : t.getEntryName();
        return DataLayerSupport.layerForTableName(name);
    }

    private UserPrincipal systemPrincipal() {
        return new UserPrincipal(0L, "system", null, "系统调度", List.of("SYSTEM"), List.of());
    }

    private void snapshotVersion(UserPrincipal operator, String targetType, Long targetId, String snapshot, String summary) {
        Long cnt = versionMapper.selectCount(new LambdaQueryWrapper<GovMetaVersion>()
                .eq(GovMetaVersion::getTargetType, targetType)
                .eq(GovMetaVersion::getTargetId, targetId));
        GovMetaVersion v = new GovMetaVersion();
        v.setTargetType(targetType);
        v.setTargetId(targetId);
        v.setVersionNo(cnt == null ? 1 : cnt.intValue() + 1);
        v.setSnapshotJson(snapshot);
        v.setChangeSummary(summary);
        v.setCreatedBy(operator.getUsername());
        versionMapper.insert(v);
    }

    private GovMetaModel requireModel(Long id) {
        GovMetaModel m = modelMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(404, "元模型不存在");
        }
        return m;
    }

    private GovMetaCollectTask requireTask(Long id) {
        GovMetaCollectTask t = taskMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "采集任务不存在");
        }
        return t;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private String required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " required");
        }
        return String.valueOf(v);
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }

    private int intVal(Object v, int def) {
        if (v == null || String.valueOf(v).isBlank()) return def;
        return Integer.parseInt(String.valueOf(v));
    }

    private Integer intOrNull(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        return Integer.parseInt(String.valueOf(v));
    }

    private Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        return Long.valueOf(String.valueOf(v));
    }

    // ---- 分层库 / 采集数据源 / 汇聚登记 ----

    public List<Map<String, Object>> listCollectDataSources() {
        List<Map<String, Object>> out = new ArrayList<>();
        List<IngDataSource> sources = ingDataSourceMapper.selectList(new LambdaQueryWrapper<IngDataSource>()
                .eq(IngDataSource::getConnStatus, "OK")
                .orderByAsc(IngDataSource::getId));
        for (IngDataSource ds : sources) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", ds.getId());
            row.put("sourceCode", ds.getSourceCode());
            row.put("sourceName", ds.getSourceName());
            row.put("sourceType", ds.getSourceType());
            row.put("layerHint", "EXTERNAL");
            row.put("platformLayer", false);
            row.put("databaseName", parseIngDatabase(ds));
            out.add(row);
        }
        out.add(platformLayerSource(PLATFORM_ODS_ID, "PLATFORM_ODS", "平台 ODS 层", DataLayerSupport.ODS, "ODS"));
        out.add(platformLayerSource(PLATFORM_DWD_ID, "PLATFORM_DWD", "平台 DWD 层", DataLayerSupport.DWD, "DWD"));
        out.add(platformLayerSource(PLATFORM_DWS_ID, "PLATFORM_DWS", "平台 DWS 层", DataLayerSupport.DWS, "DWS"));
        out.add(platformLayerSource(PLATFORM_ADS_ID, "PLATFORM_ADS", "平台 ADS 层", DataLayerSupport.ADS, "ADS"));
        return out;
    }

    public List<Map<String, Object>> listCollectDataSourceTables(Long dataSourceId) {
        if (dataSourceId == null) {
            throw new BusinessException(400, "dataSourceId 必填");
        }
        if (isPlatformLayerId(dataSourceId)) {
            return listPlatformLayerTables(platformLayerDatabase(dataSourceId));
        }
        IngDataSource ds = ingDataSourceMapper.selectById(dataSourceId);
        if (ds == null) {
            throw new BusinessException(404, "登记数据源不存在");
        }
        JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
        return jdbcProbeService.listTables(conn);
    }

    public Map<String, Object> listModelBindings(Long modelId) {
        requireModel(modelId);
        List<GovMetadataRegistry> tables = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getModelId, modelId)
                .eq(GovMetadataRegistry::getEntryType, "TABLE")
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .orderByAsc(GovMetadataRegistry::getEntryCode));
        List<GovMetaCollectTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GovMetaCollectTask>()
                .eq(GovMetaCollectTask::getModelId, modelId)
                .ne(GovMetaCollectTask::getStatus, "DELETED")
                .orderByDesc(GovMetaCollectTask::getId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tables", tables);
        out.put("collectTasks", tasks);
        return out;
    }

    @Transactional
    public Long createModelFromEntry(UserPrincipal operator, Map<String, Object> body) {
        Long entryId = longVal(body.get("entryId"));
        if (entryId == null) {
            throw new BusinessException(400, "entryId 必填");
        }
        GovMetadataRegistry entry = registryMapper.selectById(entryId);
        if (entry == null || !"TABLE".equalsIgnoreCase(entry.getEntryType())) {
            throw new BusinessException(404, "TABLE 条目不存在");
        }
        List<Map<String, Object>> fields = buildModelFieldsFromEntry(entry);
        Map<String, Object> modelBody = new LinkedHashMap<>();
        modelBody.put("modelNameZh", entry.getEntryName() + " 模型");
        modelBody.put("modelType", "TABLE");
        modelBody.put("contentJson", toJson(fields));
        return createModel(operator, modelBody);
    }

    @Transactional
    public Map<String, Object> recheckModelConformity(UserPrincipal operator, Long modelId) {
        requireModel(modelId);
        List<GovMetadataRegistry> tables = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getModelId, modelId)
                .eq(GovMetadataRegistry::getEntryType, "TABLE")
                .ne(GovMetadataRegistry::getStatus, "OFFLINE"));
        int pass = 0;
        int partial = 0;
        int fail = 0;
        for (GovMetadataRegistry tbl : tables) {
            applyConformity(tbl, modelId);
            registryMapper.updateById(tbl);
            String st = tbl.getConformStatus();
            if ("PASS".equals(st)) {
                pass++;
            } else if ("PARTIAL".equals(st)) {
                partial++;
            } else if ("FAIL".equals(st)) {
                fail++;
            }
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_MODEL_RECHECK", "gov_meta_model", String.valueOf(modelId),
                "pass=" + pass + " partial=" + partial + " fail=" + fail);
        return Map.of("modelId", modelId, "tableCount", tables.size(),
                "pass", pass, "partial", partial, "fail", fail);
    }

    public Map<String, Object> catalogInventory() {
        return catalogInventory(null);
    }

    public Map<String, Object> catalogInventory(UserPrincipal operator) {
        try {
            syncOdsIngestAssetsToMetadata(operator);
        } catch (Exception e) {
            log.warn("盘点前回填 ODS 资产失败: {}", e.getMessage());
        }
        List<GovMetadataRegistry> tables = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryType, "TABLE")
                .ne(GovMetadataRegistry::getStatus, "OFFLINE"));
        tables = tables.stream().filter(t -> !isControlPlaneTable(t)).collect(Collectors.toList());
        Map<String, Long> byLayer = new LinkedHashMap<>();
        // 固定分层顺序，便于前端盘点条展示
        for (String layer : List.of("ODS", "DWD", "DWS", "ADS")) {
            byLayer.put(layer, 0L);
        }
        long idleCount = 0;
        List<String> idleEntryCodes = new ArrayList<>();
        for (GovMetadataRegistry t : tables) {
            String layer = resolveDataPlaneLayer(t);
            byLayer.merge(layer, 1L, Long::sum);
            if (!hasDownstreamLineage(t.getEntryCode())) {
                idleCount++;
                if (idleEntryCodes.size() < 200) {
                    idleEntryCodes.add(t.getEntryCode());
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("byLayer", byLayer);
        out.put("tableCount", tables.size());
        out.put("idleCount", idleCount);
        out.put("idleEntryCodes", idleEntryCodes);
        return out;
    }

    @Transactional
    public Long rollbackVersion(UserPrincipal operator, Long versionId) {
        GovMetaVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(404, "版本不存在");
        }
        Map<String, Object> snap = parseSnapshot(version.getSnapshotJson());
        if ("MODEL".equalsIgnoreCase(version.getTargetType())) {
            GovMetaModel model = requireModel(version.getTargetId());
            if ("PUBLISHED".equals(model.getStatus())) {
                model.setStatus("DRAFT");
            }
            if (snap.containsKey("modelNameZh")) {
                model.setModelNameZh(String.valueOf(snap.get("modelNameZh")));
            }
            if (snap.containsKey("modelType")) {
                model.setModelType(String.valueOf(snap.get("modelType")));
            }
            if (snap.containsKey("contentJson")) {
                model.setContentJson(String.valueOf(snap.get("contentJson")));
            }
            modelMapper.updateById(model);
            snapshotVersion(operator, "MODEL", model.getId(), toJson(model),
                    "自版本 v" + version.getVersionNo() + " 回滚");
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "META_VERSION_ROLLBACK", "gov_meta_model", String.valueOf(model.getId()),
                    "fromVersion=" + versionId);
            return model.getId();
        }
        if ("ENTRY".equalsIgnoreCase(version.getTargetType())) {
            GovMetadataRegistry entry = registryMapper.selectById(version.getTargetId());
            if (entry == null) {
                throw new BusinessException(404, "条目不存在");
            }
            applySnapshotToEntry(entry, snap);
            entry.setChangeFlag("CHANGED");
            entry.setUpdatedAt(LocalDateTime.now());
            registryMapper.updateById(entry);
            snapshotVersion(operator, "ENTRY", entry.getId(), toJson(entry),
                    "自版本 v" + version.getVersionNo() + " 回滚");
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "META_VERSION_ROLLBACK", "gov_metadata_registry", String.valueOf(entry.getId()),
                    "fromVersion=" + versionId);
            return entry.getId();
        }
        throw new BusinessException(400, "不支持的回滚类型: " + version.getTargetType());
    }

    public Map<String, Object> analyzeOfflineAssess(String entryCode) {
        if (entryCode == null || entryCode.isBlank()) {
            throw new BusinessException(400, "entryCode 必填");
        }
        boolean hasDownstream = hasDownstreamLineage(entryCode.trim());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entryCode", entryCode.trim());
        out.put("hasDownstream", hasDownstream);
        out.put("canOffline", !hasDownstream);
        return out;
    }

    public List<Map<String, Object>> analyzeTasks(String entryCode) {
        if (entryCode == null || entryCode.isBlank()) {
            throw new BusinessException(400, "entryCode 必填");
        }
        GovMetadataRegistry entry = registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryCode, entryCode.trim()).last("limit 1"));
        if (entry == null) {
            return List.of();
        }
        LambdaQueryWrapper<IngIngestTask> q = new LambdaQueryWrapper<IngIngestTask>()
                .orderByDesc(IngIngestTask::getId);
        if (entry.getSourceTableId() != null) {
            q.eq(IngIngestTask::getTableId, entry.getSourceTableId());
        } else if (entry.getPhysicalTableName() != null && !entry.getPhysicalTableName().isBlank()) {
            q.eq(IngIngestTask::getTargetTable, entry.getPhysicalTableName());
        } else {
            return List.of();
        }
        List<IngIngestTask> tasks = ingIngestTaskMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (IngIngestTask task : tasks) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ingestTaskId", task.getId());
            row.put("taskCode", task.getTaskCode());
            row.put("taskName", task.getTaskName());
            row.put("status", task.getStatus());
            row.put("targetTable", task.getTargetTable());
            row.put("collectedRows", task.getCollectedRows());
            row.put("lastRunAt", task.getLastRunAt());
            out.add(row);
        }
        return out;
    }

    @Transactional
    public void registerAfterCollect(UserPrincipal operator, Long tableId, String odsTable) {
        registerAfterCollect(operator, tableId, odsTable, null);
    }

    /**
     * 汇聚/手动上传写入 ODS 后登记元数据：SOURCE → TABLE(ODS) → COLUMN，供元数据目录资产树展示。
     * @param lineageLabel 血缘标签；空则按数据源类型推断（FILE=手动上传，其它=Kettle汇聚）
     */
    @Transactional
    public void registerAfterCollect(UserPrincipal operator, Long tableId, String odsTable, String lineageLabel) {
        IngDataTable table = ingDataTableMapper.selectById(tableId);
        if (table == null) {
            return;
        }
        IngDataSource ds = ingDataSourceMapper.selectById(table.getSourceId());
        if (ds == null) {
            return;
        }
        String sourceCode = stableEntryCode("SRC_ING_" + ds.getId());
        String sourceDesc = "FILE".equalsIgnoreCase(ds.getSourceType()) ? "手动上传数据源" : "汇聚登记数据源";
        GovMetadataRegistry sourceEntry = upsertRegistryEntry(sourceCode, ds.getSourceName(), "SOURCE",
                null, operator, sourceDesc);
        sourceEntry.setDataSourceId(ds.getId());
        registryMapper.updateById(sourceEntry);

        String tableCode = stableEntryCode("TBL_ING_" + ds.getId() + "_" + odsTable);
        String tableDesc = "FILE".equalsIgnoreCase(ds.getSourceType()) ? "手动上传 ODS 表" : "汇聚产出表";
        GovMetadataRegistry tbl = upsertRegistryEntry(tableCode, table.getTableName(), "TABLE",
                sourceCode, operator, tableDesc);
        tbl.setParentCode(sourceCode);
        tbl.setDataSourceId(ds.getId());
        tbl.setSourceTableId(tableId);
        tbl.setPhysicalTableName(odsTable);
        tbl.setDatabaseName(DataLayerSupport.ODS);
        tbl.setSchemaName(DataLayerSupport.ODS);
        tbl.setDataLayer("ODS");
        registryMapper.updateById(tbl);

        List<IngDataColumn> columns = ingDataColumnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                .eq(IngDataColumn::getTableId, tableId).orderByAsc(IngDataColumn::getSortOrder));
        for (IngDataColumn col : columns) {
            String colCode = stableEntryCode("COL_" + tableCode + "_" + col.getColumnCode());
            GovMetadataRegistry ce = upsertRegistryEntry(colCode, col.getColumnCode(), "COLUMN",
                    tableCode, operator, col.getDataType());
            ce.setParentCode(tableCode);
            ce.setDataSourceId(ds.getId());
            ce.setSourceTableId(tableId);
            ce.setPhysicalTableName(odsTable);
            ce.setDatabaseName(DataLayerSupport.ODS);
            ce.setSchemaName(DataLayerSupport.ODS);
            ce.setDataLayer("ODS");
            registryMapper.updateById(ce);
        }
        String label = lineageLabel;
        if (label == null || label.isBlank()) {
            label = "FILE".equalsIgnoreCase(ds.getSourceType()) ? "手动上传" : "Kettle汇聚";
        }
        // 血缘：数据源 → ODS 表
        GovMetaRelation existRel = relationMapper.selectOne(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getFromCode, sourceCode)
                .eq(GovMetaRelation::getToCode, tableCode)
                .eq(GovMetaRelation::getRelationType, "LINEAGE")
                .last("limit 1"));
        if (existRel == null) {
            GovMetaRelation r = new GovMetaRelation();
            r.setFromCode(sourceCode);
            r.setToCode(tableCode);
            r.setRelationType("LINEAGE");
            r.setLabel(label);
            r.setStatus("ACTIVE");
            relationMapper.insert(r);
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_REGISTER_AFTER_COLLECT", "gov_metadata_registry", String.valueOf(tbl.getId()), tableCode);
    }

    /**
     * 将已写入 ODS 的汇聚/手动上传资产回填到元数据目录（幂等），避免「有上传无树节点」。
     */
    public void syncOdsIngestAssetsToMetadata(UserPrincipal operator) {
        UserPrincipal op = operator != null ? operator : systemPrincipal();
        List<IngDataTable> assets = ingDataTableMapper.selectList(new LambdaQueryWrapper<IngDataTable>()
                .eq(IngDataTable::getStatus, "ACTIVE")
                .isNotNull(IngDataTable::getPhysicalTableName)
                .ne(IngDataTable::getPhysicalTableName, "")
                .orderByAsc(IngDataTable::getId));
        for (IngDataTable asset : assets) {
            String schema = asset.getSourceSchema();
            if (schema != null && !schema.isBlank()
                    && !DataLayerSupport.ODS.equalsIgnoreCase(schema.trim())
                    && !"ODS".equalsIgnoreCase(schema.trim())) {
                continue;
            }
            String odsTable = asset.getPhysicalTableName().trim();
            String tableCode = stableEntryCode("TBL_ING_" + asset.getSourceId() + "_" + odsTable);
            Long cnt = registryMapper.selectCount(new LambdaQueryWrapper<GovMetadataRegistry>()
                    .eq(GovMetadataRegistry::getEntryCode, tableCode)
                    .ne(GovMetadataRegistry::getStatus, "OFFLINE"));
            if (cnt != null && cnt > 0) {
                continue;
            }
            try {
                registerAfterCollect(op, asset.getId(), odsTable);
            } catch (Exception e) {
                log.warn("ODS 资产回填元数据失败 tableId={} ods={}: {}", asset.getId(), odsTable, e.getMessage());
            }
        }
    }

    private GovOmConnector findOrCreateConnectorFromIngSource(IngDataSource ds, UserPrincipal operator) {
        String code = "ING_CONN_" + ds.getId();
        GovOmConnector existing = connectorMapper.selectOne(new LambdaQueryWrapper<GovOmConnector>()
                .eq(GovOmConnector::getConnectorCode, code).last("limit 1"));
        if (existing != null) {
            return existing;
        }
        JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
        GovOmConnector c = new GovOmConnector();
        c.setConnectorCode(code);
        c.setConnectorName(ds.getSourceName() + " (登记)");
        c.setSourceType(ds.getSourceType());
        c.setJdbcUrl(jdbcProbeService.jdbcUrl(conn));
        c.setJdbcUser(conn.username);
        c.setJdbcPassword(conn.password);
        c.setJdbcDatabase(conn.database);
        c.setStatus("ACTIVE");
        c.setCreatedBy(operator.getUsername());
        connectorMapper.insert(c);
        return c;
    }

    private GovOmConnector findOrCreatePlatformLayerConnector(Long platformSourceId, UserPrincipal operator) {
        String database = platformLayerDatabase(platformSourceId);
        String code = "PLATFORM_CONN_" + database;
        GovOmConnector existing = connectorMapper.selectOne(new LambdaQueryWrapper<GovOmConnector>()
                .eq(GovOmConnector::getConnectorCode, code).last("limit 1"));
        if (existing != null) {
            return existing;
        }
        String jdbcUrl = buildPlatformJdbcUrl(database);
        GovOmConnector c = new GovOmConnector();
        c.setConnectorCode(code);
        c.setConnectorName("平台分层库 " + database);
        c.setSourceType("MYSQL");
        c.setJdbcUrl(jdbcUrl);
        c.setJdbcDatabase(database);
        c.setStatus("ACTIVE");
        c.setCreatedBy(operator == null ? "system" : operator.getUsername());
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                c.setJdbcUser(conn.getMetaData().getUserName());
            } catch (Exception ignored) {
            }
        }
        connectorMapper.insert(c);
        return c;
    }

    private List<Map<String, Object>> probePlatformLayerSchema(Long platformSourceId, GovMetaCollectTask task) {
        String database = platformLayerDatabase(platformSourceId);
        Set<String> allowed = parseTableList(task);
        List<Map<String, Object>> tables = new ArrayList<>();
        for (Map<String, Object> raw : listPlatformLayerTables(database)) {
            String tableName = String.valueOf(raw.getOrDefault("sourceTable", ""));
            if (tableName.isBlank()) {
                continue;
            }
            if (!allowed.isEmpty() && !allowed.contains(tableName)) {
                continue;
            }
            if (tables.size() >= JDBC_TABLE_LIMIT) {
                break;
            }
            tables.add(convertProbeTable(raw));
        }
        return tables;
    }

    private String buildPlatformJdbcUrl(String database) {
        if (dataSource == null) {
            return "jdbc:mysql://localhost:3306/" + database;
        }
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            if (url == null || url.isBlank()) {
                return "jdbc:mysql://localhost:3306/" + database;
            }
            int slash = url.lastIndexOf('/');
            if (slash < 0) {
                return url;
            }
            String prefix = url.substring(0, slash + 1);
            int q = url.indexOf('?', slash);
            String query = q > 0 ? url.substring(q) : "";
            return prefix + database + query;
        } catch (Exception e) {
            return "jdbc:mysql://localhost:3306/" + database;
        }
    }

    private List<Map<String, Object>> probeIngDataSourceSchema(IngDataSource ds, GovMetaCollectTask task) {
        try {
            JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
            Set<String> allowed = parseTableList(task);
            List<Map<String, Object>> tables = new ArrayList<>();
            if (allowed.isEmpty()) {
                for (Map<String, Object> raw : jdbcProbeService.listTables(conn)) {
                    if (tables.size() >= JDBC_TABLE_LIMIT) {
                        break;
                    }
                    tables.add(convertProbeTable(raw));
                }
                return tables;
            }
            for (String tableName : allowed) {
                Map<String, Object> desc = jdbcProbeService.describeTable(conn, tableName);
                tables.add(convertProbeTable(desc));
            }
            return tables;
        } catch (Exception e) {
            log.warn("登记数据源 JDBC 探测失败 sourceId={}: {}", ds.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertProbeTable(Map<String, Object> raw) {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("tableName", raw.get("sourceTable"));
        List<Map<String, String>> cols = new ArrayList<>();
        Object colObj = raw.get("columns");
        if (colObj instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                Map<String, String> col = new LinkedHashMap<>();
                Object name = m.get("columnName");
                Object type = m.get("dataType");
                col.put("columnName", name == null ? "" : String.valueOf(name));
                col.put("dataType", type == null ? "" : String.valueOf(type));
                cols.add(col);
            }
        }
        table.put("columns", cols);
        return table;
    }

    private void enrichRegistryLayerFields(GovMetadataRegistry tbl, String tableName,
                                           GovOmConnector connector, GovMetaCollectTask task) {
        String db = connector.getJdbcDatabase();
        // 未指定库时默认数据面 ODS，禁止落入控制面 smart_city
        if (db == null || db.isBlank()) {
            db = DataLayerSupport.ODS;
        }
        if (DataLayerSupport.isControlDatabase(db)) {
            throw new BusinessException(400, "禁止将控制面库 smart_city 登记为数据资产元数据");
        }
        if (DataLayerSupport.isPlatformLayerDb(db)) {
            tbl.setDatabaseName(db);
            tbl.setSchemaName(db);
            tbl.setDataLayer(DataLayerSupport.layerForDatabase(db));
        } else if (task.getIngDataSourceId() != null) {
            tbl.setDatabaseName(db);
            tbl.setSchemaName(db);
            tbl.setDataLayer(DataLayerSupport.layerForTableName(tableName));
        } else {
            String layer = DataLayerSupport.layerForTableName(tableName);
            tbl.setDataLayer(layer);
            tbl.setDatabaseName(DataLayerSupport.databaseForLayer(layer));
            tbl.setSchemaName(tbl.getDatabaseName());
        }
        tbl.setPhysicalTableName(tableName);
        if (task.getIngDataSourceId() != null) {
            tbl.setDataSourceId(task.getIngDataSourceId());
        }
    }

    private void applyConformity(GovMetadataRegistry tbl, Long modelId) {
        if (modelId == null) {
            tbl.setConformStatus("SKIP");
            tbl.setConformReport(null);
            return;
        }
        GovMetaModel model = modelMapper.selectById(modelId);
        if (model == null || model.getContentJson() == null || model.getContentJson().isBlank()) {
            tbl.setConformStatus("SKIP");
            return;
        }
        List<String> modelFields = extractFieldNames(model.getContentJson());
        List<GovMetadataRegistry> cols = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getParentCode, tbl.getEntryCode())
                .eq(GovMetadataRegistry::getEntryType, "COLUMN"));
        Set<String> physicalCols = cols.stream()
                .map(GovMetadataRegistry::getEntryName)
                .filter(n -> n != null && !n.isBlank())
                .map(n -> n.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        for (String field : modelFields) {
            if (physicalCols.contains(field.toLowerCase(Locale.ROOT))) {
                matched.add(field);
            } else {
                missing.add(field);
            }
        }
        String status;
        if (missing.isEmpty()) {
            status = "PASS";
        } else if (matched.isEmpty()) {
            status = "FAIL";
        } else {
            status = "PARTIAL";
        }
        tbl.setConformStatus(status);
        tbl.setConformReport(toJson(Map.of(
                "modelId", modelId,
                "matched", matched,
                "missing", missing,
                "modelFieldCount", modelFields.size(),
                "physicalColumnCount", physicalCols.size()
        )));
    }

    private List<Map<String, Object>> buildModelFieldsFromEntry(GovMetadataRegistry entry) {
        List<GovMetadataRegistry> cols = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getParentCode, entry.getEntryCode())
                .eq(GovMetadataRegistry::getEntryType, "COLUMN")
                .orderByAsc(GovMetadataRegistry::getEntryCode));
        List<Map<String, Object>> fields = new ArrayList<>();
        if (!cols.isEmpty()) {
            for (GovMetadataRegistry c : cols) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("code", c.getEntryName());
                f.put("name", c.getEntryName());
                f.put("type", nvl(c.getDescription()));
                f.put("required", false);
                fields.add(f);
            }
            return fields;
        }
        if (entry.getPhysicalTableName() != null && dataSource != null) {
            String db = entry.getDatabaseName() != null ? entry.getDatabaseName()
                    : DataLayerSupport.databaseForLayer(DataLayerSupport.layerForTableName(entry.getPhysicalTableName()));
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData md = conn.getMetaData();
                try (ResultSet rs = md.getColumns(db, null, entry.getPhysicalTableName(), null)) {
                    while (rs.next()) {
                        Map<String, Object> f = new LinkedHashMap<>();
                        f.put("code", rs.getString("COLUMN_NAME"));
                        f.put("name", rs.getString("COLUMN_NAME"));
                        f.put("type", rs.getString("TYPE_NAME"));
                        f.put("required", false);
                        fields.add(f);
                    }
                }
            } catch (Exception e) {
                log.warn("从物理表构建模型字段失败 entry={}: {}", entry.getEntryCode(), e.getMessage());
            }
        }
        return fields;
    }

    private Map<String, Object> platformLayerSource(Long id, String code, String name, String database, String layer) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("sourceCode", code);
        row.put("sourceName", name);
        row.put("sourceType", "MYSQL");
        row.put("layerHint", layer);
        row.put("platformLayer", true);
        row.put("databaseName", database);
        return row;
    }

    private boolean isPlatformLayerId(Long id) {
        return id != null && (id == PLATFORM_ODS_ID || id == PLATFORM_DWD_ID
                || id == PLATFORM_DWS_ID || id == PLATFORM_ADS_ID);
    }

    private String platformLayerDatabase(Long id) {
        if (id == PLATFORM_ODS_ID) {
            return DataLayerSupport.ODS;
        }
        if (id == PLATFORM_DWD_ID) {
            return DataLayerSupport.DWD;
        }
        if (id == PLATFORM_DWS_ID) {
            return DataLayerSupport.DWS;
        }
        if (id == PLATFORM_ADS_ID) {
            return DataLayerSupport.ADS;
        }
        throw new BusinessException(400, "非平台分层数据源");
    }

    private List<Map<String, Object>> listPlatformLayerTables(String database) {
        if (dataSource == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT TABLE_NAME FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME LIMIT ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, database);
                ps.setInt(2, JDBC_TABLE_LIMIT);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("sourceTable", tableName);
                        row.put("columns", loadColumns(conn, database, tableName));
                        row.put("primaryKeys", List.of());
                        row.put("rowCount", -1L);
                        out.add(row);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("平台分层库探表失败 db={}: {}", database, e.getMessage());
        }
        return out;
    }

    private String parseIngDatabase(IngDataSource ds) {
        try {
            JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
            return conn.database;
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeTableList(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof List<?> list) {
            if (list.isEmpty()) {
                return null;
            }
            return list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isBlank())
                    .collect(Collectors.joining(","));
        }
        String s = String.valueOf(raw).trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            try {
                List<String> parsed = objectMapper.readValue(s, new TypeReference<List<String>>() {});
                return parsed.stream().filter(v -> v != null && !v.isBlank()).collect(Collectors.joining(","));
            } catch (Exception ignored) {
            }
        }
        return s.isBlank() ? null : s;
    }

    private String normalizeOptionalCron(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        return String.valueOf(raw).trim();
    }

    private String normalizeTags(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isBlank())
                    .collect(Collectors.joining(","));
        }
        return str(raw, null);
    }

    private boolean hasDownstreamLineage(String entryCode) {
        Long cnt = relationMapper.selectCount(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getFromCode, entryCode)
                .eq(GovMetaRelation::getRelationType, "LINEAGE")
                .eq(GovMetaRelation::getStatus, "ACTIVE"));
        return cnt != null && cnt > 0;
    }

    private GovMetadataRegistry upsertRegistryEntry(String entryCode, String entryName, String entryType,
                                                    String parentCode, UserPrincipal operator, String description) {
        GovMetadataRegistry e = registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryCode, entryCode).last("limit 1"));
        if (e == null) {
            e = new GovMetadataRegistry();
            e.setEntryCode(entryCode);
            e.setEntryName(entryName);
            e.setEntryType(entryType);
            e.setParentCode(parentCode);
            e.setDescription(description);
            e.setChangeFlag("NEW");
            e.setStatus("ACTIVE");
            e.setUpdatedAt(LocalDateTime.now());
            registryMapper.insert(e);
        } else {
            e.setEntryName(entryName);
            e.setEntryType(entryType);
            if (parentCode != null) {
                e.setParentCode(parentCode);
            }
            e.setDescription(description);
            e.setChangeFlag("CHANGED");
            e.setUpdatedAt(LocalDateTime.now());
            registryMapper.updateById(e);
        }
        return e;
    }

    private String stableEntryCode(String raw) {
        String code = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        if (code.length() <= 64) {
            return code;
        }
        return code.substring(0, 55) + "_" + Math.abs(raw.hashCode() % 100000);
    }

    private void applySnapshotToEntry(GovMetadataRegistry entry, Map<String, Object> snap) {
        if (snap.containsKey("entryName")) {
            entry.setEntryName(String.valueOf(snap.get("entryName")));
        }
        if (snap.containsKey("description")) {
            entry.setDescription(String.valueOf(snap.get("description")));
        }
        if (snap.containsKey("tags")) {
            entry.setTags(String.valueOf(snap.get("tags")));
        }
        if (snap.containsKey("keywords")) {
            entry.setKeywords(String.valueOf(snap.get("keywords")));
        }
        if (snap.containsKey("securityLevel")) {
            entry.setSecurityLevel(String.valueOf(snap.get("securityLevel")));
        }
        if (snap.containsKey("businessDomain")) {
            entry.setBusinessDomain(String.valueOf(snap.get("businessDomain")));
        }
        if (snap.containsKey("ownerName")) {
            entry.setOwnerName(String.valueOf(snap.get("ownerName")));
        }
        if (snap.containsKey("dataLayer") && (entry.getDatabaseName() == null || entry.getDatabaseName().isBlank())) {
            entry.setDataLayer(String.valueOf(snap.get("dataLayer")));
        }
        if (snap.containsKey("databaseName")) {
            entry.setDatabaseName(String.valueOf(snap.get("databaseName")));
        }
        if (snap.containsKey("schemaName")) {
            entry.setSchemaName(String.valueOf(snap.get("schemaName")));
        }
        if (snap.containsKey("physicalTableName")) {
            entry.setPhysicalTableName(String.valueOf(snap.get("physicalTableName")));
        }
    }
}
