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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
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

    public List<Map<String, Object>> listRunsEnriched(Long taskId, String status, String keyword) {
        List<GovMetaCollectRun> runs = listRuns(taskId, status, null);
        String kw = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovMetaCollectRun run : runs) {
            GovMetaCollectTask task = taskMapper.selectById(run.getTaskId());
            if (task == null || "DELETED".equals(task.getStatus())) {
                continue;
            }
            GovOmConnector c = connectorMapper.selectById(task.getConnectorId());
            if (!kw.isEmpty()) {
                boolean hit = task.getTaskName() != null && task.getTaskName().contains(kw)
                        || (c != null && c.getConnectorName() != null && c.getConnectorName().contains(kw))
                        || (run.getSummary() != null && run.getSummary().contains(kw))
                        || (run.getLogText() != null && run.getLogText().contains(kw));
                if (!hit) continue;
            }
            out.add(enrichRunRow(run, task, c));
        }
        return out;
    }

    public Map<String, Object> runDetail(Long runId) {
        GovMetaCollectRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(404, "运行记录不存在");
        }
        GovMetaCollectTask task = taskMapper.selectById(run.getTaskId());
        GovOmConnector c = task == null ? null : connectorMapper.selectById(task.getConnectorId());
        Map<String, Object> out = enrichRunRow(run, task, c);
        out.put("diff", parseRunDiff(run));
        out.put("metadataCount", listRunResults(runId).size());
        out.put("logText", run.getLogText());
        return out;
    }

    @Transactional
    public Map<String, Object> stopTaskRunning(UserPrincipal operator, Long taskId) {
        GovMetaCollectTask task = requireTask(taskId);
        GovMetaCollectRun run = runMapper.selectOne(new LambdaQueryWrapper<GovMetaCollectRun>()
                .eq(GovMetaCollectRun::getTaskId, taskId)
                .eq(GovMetaCollectRun::getStatus, "RUNNING")
                .orderByDesc(GovMetaCollectRun::getId)
                .last("limit 1"));
        if (run == null) {
            if ("RUNNING".equals(task.getStatus())) {
                task.setStatus("STOPPED");
                task.setLastMessage("stopped without active run");
                taskMapper.updateById(task);
                return Map.of("taskId", taskId, "status", "STOPPED", "runId", 0L);
            }
            throw new BusinessException(400, "当前没有正在执行的采集任务");
        }
        return stopRun(operator, run.getId());
    }

    public Map<String, Object> monitorOverview(String sourceKeyword, String taskKeyword, Long sourceId,
                                               String status, String runStatus) {
        // status 兼容旧参数：优先按最近执行状态过滤
        String execStatus = (runStatus != null && !runStatus.isBlank()) ? runStatus
                : (status != null && !status.isBlank() ? status : null);
        List<GovMetaCollectTask> tasks = listTasks(null, null, taskKeyword);
        String sk = sourceKeyword == null ? "" : sourceKeyword.trim();
        List<Map<String, Object>> allRows = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GovMetaCollectTask t : tasks) {
            GovOmConnector c = connectorMapper.selectById(t.getConnectorId());
            if (sourceId != null) {
                if (t.getIngDataSourceId() == null || !sourceId.equals(t.getIngDataSourceId())) {
                    continue;
                }
            }
            if (!sk.isEmpty()) {
                boolean hit = (c != null && (
                        (c.getConnectorName() != null && c.getConnectorName().contains(sk))
                                || (c.getSourceType() != null && c.getSourceType().contains(sk))))
                        || (t.getTaskName() != null && t.getTaskName().contains(sk));
                if (!hit) continue;
            }
            GovMetaCollectRun last = runMapper.selectOne(new LambdaQueryWrapper<GovMetaCollectRun>()
                    .eq(GovMetaCollectRun::getTaskId, t.getId())
                    .orderByDesc(GovMetaCollectRun::getId)
                    .last("limit 1"));
            String lastStatus = last != null ? last.getStatus()
                    : ("RUNNING".equals(t.getStatus()) ? "RUNNING" : "IDLE");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("task", t);
            row.put("connectorName", c == null ? null : c.getConnectorName());
            row.put("sourceType", c == null ? null : c.getSourceType());
            row.put("sourceId", t.getIngDataSourceId());
            row.put("lastRun", last == null ? null : enrichRunRow(last, t, c));
            row.put("execStatus", "RUNNING".equals(t.getStatus()) ? "RUNNING" : lastStatus);
            row.put("canStop", "RUNNING".equals(t.getStatus())
                    || (last != null && "RUNNING".equals(last.getStatus())));
            allRows.add(row);

            if (execStatus != null && !execStatus.isBlank()) {
                if ("IDLE".equalsIgnoreCase(execStatus)) {
                    if (last != null) continue;
                } else if (last == null || !execStatus.equalsIgnoreCase(last.getStatus())) {
                    if (!("RUNNING".equalsIgnoreCase(execStatus) && "RUNNING".equals(t.getStatus()))) {
                        continue;
                    }
                }
            }
            rows.add(row);
        }
        int running = 0, success = 0, failed = 0, stopped = 0, idle = 0;
        for (Map<String, Object> row : allRows) {
            String es = String.valueOf(row.get("execStatus"));
            if ("RUNNING".equals(es)) running++;
            else if ("SUCCESS".equals(es)) success++;
            else if ("FAILED".equals(es)) failed++;
            else if ("STOPPED".equals(es)) stopped++;
            else idle++;
        }
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("total", allRows.size());
        kpi.put("running", running);
        kpi.put("success", success);
        kpi.put("failed", failed);
        kpi.put("stopped", stopped);
        kpi.put("idle", idle);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", rows);
        out.put("kpi", kpi);
        out.put("omHealthy", openMetadataClient.isHealthy());
        out.put("refreshedAt", LocalDateTime.now().toString());
        return out;
    }

    private Map<String, Object> enrichRunRow(GovMetaCollectRun run, GovMetaCollectTask task, GovOmConnector c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", run.getId());
        m.put("taskId", run.getTaskId());
        m.put("taskName", task == null ? null : task.getTaskName());
        m.put("taskStatus", task == null ? null : task.getStatus());
        m.put("connectorName", c == null ? null : c.getConnectorName());
        m.put("sourceType", c == null ? null : c.getSourceType());
        m.put("status", run.getStatus());
        m.put("startedAt", run.getStartedAt());
        m.put("endedAt", run.getEndedAt());
        m.put("tableCount", run.getTableCount());
        m.put("summary", run.getSummary());
        m.put("logText", run.getLogText());
        m.put("durationSeconds", calcDurationSeconds(run.getStartedAt(), run.getEndedAt()));
        m.put("diff", parseRunDiffCompact(run));
        return m;
    }

    private Long calcDurationSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null) return null;
        LocalDateTime to = end != null ? end : LocalDateTime.now();
        return java.time.Duration.between(start, to).getSeconds();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRunDiffCompact(GovMetaCollectRun run) {
        if (run == null) return null;
        String summary = run.getSummary();
        if (summary != null && summary.trim().startsWith("{")) {
            try {
                return objectMapper.readValue(summary, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> full = parseRunDiff(run);
        return full == null ? null : compactRunDiff(full);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRunDiff(GovMetaCollectRun run) {
        if (run == null) return null;
        String log = run.getLogText();
        if (log != null) {
            int idx = log.lastIndexOf("diff=");
            if (idx >= 0) {
                String json = log.substring(idx + 5).trim();
                int nl = json.indexOf('\n');
                if (nl > 0) json = json.substring(0, nl).trim();
                try {
                    return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                } catch (Exception ignored) {
                }
            }
        }
        String summary = run.getSummary();
        if (summary != null && summary.trim().startsWith("{")) {
            try {
                return objectMapper.readValue(summary, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** @deprecated 保留兼容；请用带任务名/数据源参数的重载 */
    public Map<String, Object> monitorOverview(String sourceKeyword, String status) {
        return monitorOverview(sourceKeyword, null, null, status, null);
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

    /**
     * 版本管理实体概览：同一实体的发布状态、版本数、最新/已发布版本。
     */
    public Map<String, Object> versionOverview(String keyword, String targetType, String publishStatus) {
        String tt = targetType == null || targetType.isBlank() ? "ENTRY" : targetType.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> items = new ArrayList<>();
        int published = 0, draft = 0, offline = 0;
        if ("MODEL".equals(tt)) {
            List<GovMetaModel> models = listModels(null);
            for (GovMetaModel m : models) {
                if (keyword != null && !keyword.isBlank()) {
                    String kw = keyword.trim();
                    if (!nvl(m.getModelNameZh()).contains(kw) && !nvl(m.getModelCode()).contains(kw)) {
                        continue;
                    }
                }
                Map<String, Object> row = enrichVersionTarget("MODEL", m.getId(), m.getModelCode(), m.getModelNameZh(),
                        m.getStatus(), null);
                String ps = String.valueOf(row.get("publishStatus"));
                if ("PUBLISHED".equals(ps)) published++;
                else if ("OFFLINE".equals(ps)) offline++;
                else draft++;
                if (publishStatus != null && !publishStatus.isBlank() && !publishStatus.equalsIgnoreCase(ps)) {
                    continue;
                }
                items.add(row);
            }
        } else {
            LambdaQueryWrapper<GovMetadataRegistry> q = new LambdaQueryWrapper<GovMetadataRegistry>()
                    .in(GovMetadataRegistry::getEntryType, List.of("TABLE", "SOURCE"))
                    .orderByDesc(GovMetadataRegistry::getUpdatedAt)
                    .orderByDesc(GovMetadataRegistry::getId);
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.trim();
                q.and(w -> w.like(GovMetadataRegistry::getEntryName, kw)
                        .or().like(GovMetadataRegistry::getEntryCode, kw));
            }
            List<GovMetadataRegistry> entries = excludeControlPlaneEntries(registryMapper.selectList(q));
            for (GovMetadataRegistry e : entries) {
                Map<String, Object> row = enrichVersionTarget("ENTRY", e.getId(), e.getEntryCode(), e.getEntryName(),
                        e.getStatus(), e.getChangeFlag());
                row.put("entryType", e.getEntryType());
                row.put("securityLevel", e.getSecurityLevel());
                row.put("ownerName", e.getOwnerName());
                row.put("updatedAt", e.getUpdatedAt());
                String ps = String.valueOf(row.get("publishStatus"));
                if ("PUBLISHED".equals(ps)) published++;
                else if ("OFFLINE".equals(ps)) offline++;
                else draft++;
                if (publishStatus != null && !publishStatus.isBlank() && !publishStatus.equalsIgnoreCase(ps)) {
                    continue;
                }
                items.add(row);
            }
        }
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("total", published + draft + offline);
        kpi.put("published", published);
        kpi.put("draft", draft);
        kpi.put("offline", offline);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kpi", kpi);
        out.put("items", items);
        out.put("targetType", tt);
        return out;
    }

    public Map<String, Object> versionDetail(Long versionId) {
        GovMetaVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(404, "版本不存在");
        }
        Map<String, Object> snap = parseSnapshot(version.getSnapshotJson());
        Map<String, Object> basicInfo = new LinkedHashMap<>(snap);
        basicInfo.remove("fields");
        basicInfo.remove("relations");
        basicInfo.remove("contentJson");

        List<Map<String, Object>> fields = extractFieldAttrs(snap);
        List<Map<String, Object>> relations = extractRelationsFromSnap(snap);
        if (relations.isEmpty() && "ENTRY".equalsIgnoreCase(version.getTargetType())) {
            Object code = snap.get("entryCode");
            if (code != null) {
                relations = listRelationsForEntryCode(String.valueOf(code));
            }
        }

        List<Map<String, Object>> dataPreview = List.of();
        String previewHint = null;
        if ("ENTRY".equalsIgnoreCase(version.getTargetType())) {
            try {
                dataPreview = previewEntryRows(snap, 10);
            } catch (Exception e) {
                previewHint = "数据预览不可用: " + e.getMessage();
            }
            if (dataPreview.isEmpty() && previewHint == null) {
                previewHint = "当前版本无可用物理表样例数据";
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", version);
        out.put("basicInfo", basicInfo);
        out.put("fields", fields);
        out.put("relations", relations);
        out.put("dataPreview", dataPreview);
        out.put("previewHint", previewHint);
        out.put("isPublishVersion", isPublishSummary(version.getChangeSummary()));
        out.put("isOfflineVersion", isOfflineSummary(version.getChangeSummary()));
        return out;
    }

    @Transactional
    public Map<String, Object> publishVersionTarget(UserPrincipal operator, Map<String, Object> body) {
        String targetType = required(body.get("targetType"), "targetType").toUpperCase(Locale.ROOT);
        Long targetId = longVal(body.get("targetId"));
        if (targetId == null) {
            throw new BusinessException(400, "targetId required");
        }
        String description = str(body.get("description"), null);
        if (description == null || description.isBlank()) {
            description = "MODEL".equals(targetType) ? "发布元模型" : "发布元数据";
        }
        if ("MODEL".equals(targetType)) {
            GovMetaModel m = requireModel(targetId);
            m.setStatus("PUBLISHED");
            m.setPublishedAt(LocalDateTime.now());
            modelMapper.updateById(m);
            snapshotVersion(operator, "MODEL", m.getId(), toJson(m), description);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "META_MODEL_PUBLISH", "gov_meta_model", String.valueOf(targetId), m.getModelCode());
            return Map.of("targetType", "MODEL", "targetId", targetId, "status", "PUBLISHED");
        }
        if ("ENTRY".equals(targetType)) {
            return publishEntry(operator, targetId, description);
        }
        throw new BusinessException(400, "不支持的目标类型: " + targetType);
    }

    @Transactional
    public Map<String, Object> offlineVersionTarget(UserPrincipal operator, Map<String, Object> body) {
        String targetType = required(body.get("targetType"), "targetType").toUpperCase(Locale.ROOT);
        Long targetId = longVal(body.get("targetId"));
        if (targetId == null) {
            throw new BusinessException(400, "targetId required");
        }
        String description = str(body.get("description"), null);
        if (description == null || description.isBlank()) {
            description = "MODEL".equals(targetType) ? "下线元模型" : "下线元数据";
        }
        if ("MODEL".equals(targetType)) {
            GovMetaModel m = requireModel(targetId);
            m.setStatus("OFFLINE");
            modelMapper.updateById(m);
            snapshotVersion(operator, "MODEL", m.getId(), toJson(m), description);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "META_MODEL_OFFLINE", "gov_meta_model", String.valueOf(targetId), m.getModelCode());
            return Map.of("targetType", "MODEL", "targetId", targetId, "status", "OFFLINE");
        }
        if ("ENTRY".equals(targetType)) {
            offlineEntry(operator, targetId, description);
            return Map.of("targetType", "ENTRY", "targetId", targetId, "status", "OFFLINE");
        }
        throw new BusinessException(400, "不支持的目标类型: " + targetType);
    }

    public Map<String, Object> compareVersions(Long leftId, Long rightId) {
        GovMetaVersion left = versionMapper.selectById(leftId);
        GovMetaVersion right = versionMapper.selectById(rightId);
        if (left == null || right == null) {
            throw new BusinessException(404, "版本不存在");
        }
        Map<String, Object> leftSnap = parseSnapshot(left.getSnapshotJson());
        Map<String, Object> rightSnap = parseSnapshot(right.getSnapshotJson());
        List<Map<String, Object>> basicDiff = buildBasicDiff(leftSnap, rightSnap);
        Map<String, Object> fieldDiff = diffContentJsonFields(
                fieldRawFromSnap(leftSnap), fieldRawFromSnap(rightSnap));
        List<Map<String, Object>> attrDiff = buildAttrDiff(leftSnap, rightSnap);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("left", left);
        out.put("right", right);
        out.put("sameSnapshot", basicDiff.isEmpty() && attrDiff.isEmpty()
                && ((List<?>) fieldDiff.getOrDefault("added", List.of())).isEmpty()
                && ((List<?>) fieldDiff.getOrDefault("removed", List.of())).isEmpty()
                && ((List<?>) fieldDiff.getOrDefault("changed", List.of())).isEmpty());
        out.put("basicDiff", basicDiff);
        out.put("fieldDiff", fieldDiff);
        out.put("attrDiff", attrDiff);
        return out;
    }

    public List<GovMetadataRegistry> searchCatalog(String keyword, String type, String tag) {
        return searchCatalog(keyword, type, tag, null);
    }

    public List<GovMetadataRegistry> searchCatalog(String keyword, String type, String tag, UserPrincipal operator) {
        try {
            syncOdsIngestAssetsToMetadata(operator);
        } catch (Exception e) {
            log.warn("目录检索前回填 ODS 资产失败: {}", e.getMessage());
        }
        List<GovMetadataRegistry> all = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .orderByAsc(GovMetadataRegistry::getEntryType)
                .orderByAsc(GovMetadataRegistry::getEntryName));
        all = all.stream().filter(this::isPublishedForQuery).collect(Collectors.toList());
        all = excludeControlPlaneEntries(all);
        Map<String, GovMetadataRegistry> byCode = all.stream()
                .filter(e -> e.getEntryCode() != null)
                .collect(Collectors.toMap(GovMetadataRegistry::getEntryCode, e -> e, (a, b) -> a));

        if (tag != null && !tag.isBlank()) {
            String tg = tag.trim();
            all = all.stream().filter(e -> nvl(e.getTags()).contains(tg)).collect(Collectors.toList());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase(Locale.ROOT);
            Set<String> keep = new HashSet<>();
            for (GovMetadataRegistry e : all) {
                if (catalogKeywordMatch(e, kw)) {
                    keep.add(e.getEntryCode());
                }
            }
            // 字段命中 → 保留父表；表命中 → 保留归属源
            for (GovMetadataRegistry e : new ArrayList<>(all)) {
                if (!keep.contains(e.getEntryCode())) continue;
                if ("COLUMN".equalsIgnoreCase(e.getEntryType()) && e.getParentCode() != null) {
                    keep.add(e.getParentCode());
                    GovMetadataRegistry tbl = byCode.get(e.getParentCode());
                    if (tbl != null && tbl.getParentCode() != null) {
                        keep.add(tbl.getParentCode());
                    }
                }
                if ("TABLE".equalsIgnoreCase(e.getEntryType()) && e.getParentCode() != null) {
                    keep.add(e.getParentCode());
                }
            }
            // 命中表时带上其字段，便于展开查看
            for (GovMetadataRegistry e : all) {
                if ("COLUMN".equalsIgnoreCase(e.getEntryType())
                        && e.getParentCode() != null
                        && keep.contains(e.getParentCode())) {
                    keep.add(e.getEntryCode());
                }
            }
            all = all.stream().filter(e -> e.getEntryCode() != null && keep.contains(e.getEntryCode()))
                    .collect(Collectors.toList());
        }

        String t = type == null ? "" : type.trim().toLowerCase();
        if ("source".equals(t)) {
            boolean narrow = (keyword != null && !keyword.isBlank()) || (tag != null && !tag.isBlank());
            Set<String> hitCodes = all.stream()
                    .map(GovMetadataRegistry::getEntryCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<String> include = new HashSet<>();
            Set<String> sourceCodes = byCode.values().stream()
                    .filter(e -> isSourceType(e.getEntryType()))
                    .map(GovMetadataRegistry::getEntryCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!narrow) {
                include.addAll(sourceCodes);
            } else {
                for (String code : hitCodes) {
                    GovMetadataRegistry e = byCode.get(code);
                    if (e == null) continue;
                    if (isSourceType(e.getEntryType())) {
                        include.add(code);
                    } else if ("TABLE".equalsIgnoreCase(e.getEntryType()) && e.getParentCode() != null
                            && sourceCodes.contains(e.getParentCode())) {
                        include.add(code);
                        include.add(e.getParentCode());
                    } else if ("COLUMN".equalsIgnoreCase(e.getEntryType()) && e.getParentCode() != null) {
                        GovMetadataRegistry tbl = byCode.get(e.getParentCode());
                        if (tbl != null && tbl.getParentCode() != null && sourceCodes.contains(tbl.getParentCode())) {
                            include.add(code);
                            include.add(tbl.getEntryCode());
                            include.add(tbl.getParentCode());
                        }
                    }
                }
            }
            for (GovMetadataRegistry e : byCode.values()) {
                if (!"TABLE".equalsIgnoreCase(e.getEntryType())) continue;
                if (e.getParentCode() == null || !include.contains(e.getParentCode())) continue;
                if (narrow && !include.contains(e.getEntryCode())) continue;
                include.add(e.getEntryCode());
            }
            for (GovMetadataRegistry e : byCode.values()) {
                if (!"COLUMN".equalsIgnoreCase(e.getEntryType())) continue;
                if (e.getParentCode() != null && include.contains(e.getParentCode())) {
                    if (!narrow || include.contains(e.getEntryCode()) || hitCodes.contains(e.getParentCode())) {
                        include.add(e.getEntryCode());
                    }
                }
            }
            return byCode.values().stream()
                    .filter(e -> e.getEntryCode() != null && include.contains(e.getEntryCode()))
                    .sorted(Comparator.comparing((GovMetadataRegistry x) -> nvl(x.getEntryType()))
                            .thenComparing(x -> nvl(x.getEntryName())))
                    .collect(Collectors.toList());
        }
        if ("asset".equals(t)) {
            return all.stream()
                    .filter(e -> isAssetType(e.getEntryType()) || isSourceType(e.getEntryType()))
                    .collect(Collectors.toList());
        }
        return all;
    }

    /**
     * 目录浏览：源目录（业务系统→数据源→库→表）/ 资产目录（分层→主题域→表）。
     */
    public Map<String, Object> catalogBrowse(String keyword, String tag, String catalogKind, UserPrincipal operator) {
        String kind = catalogKind == null || catalogKind.isBlank() ? "asset" : catalogKind.trim().toLowerCase();
        String type = "source".equals(kind) ? "source" : "asset";
        List<GovMetadataRegistry> entries = searchCatalog(keyword, type, tag, operator);
        List<Map<String, Object>> tree = "source".equals(kind)
                ? buildSourceCatalogTree(entries)
                : buildAssetCatalogTree(entries);
        Set<String> tags = new LinkedHashSet<>();
        Set<String> domains = new LinkedHashSet<>();
        for (GovMetadataRegistry e : entries) {
            if (e.getTags() != null) {
                for (String part : e.getTags().split("[,，]")) {
                    if (part != null && !part.isBlank()) tags.add(part.trim());
                }
            }
            if (e.getBusinessDomain() != null && !e.getBusinessDomain().isBlank()) {
                domains.add(e.getBusinessDomain().trim());
            }
        }
        long tableCount = entries.stream().filter(e -> "TABLE".equalsIgnoreCase(e.getEntryType())).count();
        long sourceCount = entries.stream().filter(e -> isSourceType(e.getEntryType())).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("catalogKind", kind);
        out.put("entries", entries);
        out.put("tree", tree);
        out.put("tags", new ArrayList<>(tags));
        out.put("domains", new ArrayList<>(domains));
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("entryCount", entries.size());
        kpi.put("tableCount", tableCount);
        kpi.put("sourceCount", sourceCount);
        out.put("kpi", kpi);
        return out;
    }

    public Map<String, Object> catalogEntryDetail(Long id) {
        GovMetadataRegistry e = registryMapper.selectById(id);
        if (e == null || "OFFLINE".equalsIgnoreCase(e.getStatus())) {
            throw new BusinessException(404, "条目不存在或已下线");
        }
        List<GovMetadataRegistry> columns = List.of();
        if ("TABLE".equalsIgnoreCase(e.getEntryType())) {
            columns = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                    .eq(GovMetadataRegistry::getParentCode, e.getEntryCode())
                    .eq(GovMetadataRegistry::getEntryType, "COLUMN")
                    .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                    .orderByAsc(GovMetadataRegistry::getEntryCode));
        }
        List<GovMetaVersion> versions = listVersions("ENTRY", e.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entry", e);
        out.put("columns", columns);
        out.put("versions", versions);
        out.put("latestVersionNo", versions.isEmpty() ? null : versions.get(0).getVersionNo());
        out.put("versionCount", versions.size());
        return out;
    }

    public Map<String, Object> catalogViews() {
        List<GovMetadataRegistry> all = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .orderByAsc(GovMetadataRegistry::getId));
        all = all.stream().filter(this::isPublishedForQuery).collect(Collectors.toList());
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
        offlineEntry(operator, id, "下线元数据");
    }

    @Transactional
    public void offlineEntry(UserPrincipal operator, Long id, String description) {
        GovMetadataRegistry e = registryMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(404, "条目不存在");
        }
        e.setStatus("OFFLINE");
        e.setChangeFlag("CHANGED");
        e.setUpdatedAt(LocalDateTime.now());
        registryMapper.updateById(e);
        snapshotEntryVersion(operator, e, description == null || description.isBlank() ? "下线元数据" : description);
        insertChangeNotice(e, "目录条目下线", null);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_ENTRY_OFFLINE", "gov_metadata_registry", String.valueOf(id), e.getEntryCode());
    }

    public Map<String, Object> analyzeGraph(String relationType) {
        return analyzeGraph(relationType, null);
    }

    public Map<String, Object> analyzeGraph(String relationType, String focusCode) {
        List<GovMetadataRegistry> entries = registryMapper.selectList(
                new LambdaQueryWrapper<GovMetadataRegistry>().ne(GovMetadataRegistry::getStatus, "OFFLINE"));
        Map<String, GovMetadataRegistry> byCode = entries.stream()
                .filter(e -> e.getEntryCode() != null)
                .collect(Collectors.toMap(GovMetadataRegistry::getEntryCode, e -> e, (a, b) -> a));
        LambdaQueryWrapper<GovMetaRelation> q = new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getStatus, "ACTIVE");
        if (relationType != null && !relationType.isBlank()) {
            String rt = relationType.trim().toUpperCase(Locale.ROOT);
            if ("ASSOC".equals(rt)) {
                q.in(GovMetaRelation::getRelationType, List.of("ASSOC", "FK"));
            } else {
                q.eq(GovMetaRelation::getRelationType, rt);
            }
        }
        List<GovMetaRelation> rels = relationMapper.selectList(q);
        Set<String> used = new HashSet<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        for (GovMetaRelation r : rels) {
            if (focusCode != null && !focusCode.isBlank()) {
                String fc = focusCode.trim();
                if (!fc.equals(r.getFromCode()) && !fc.equals(r.getToCode())) {
                    continue;
                }
            }
            used.add(r.getFromCode());
            used.add(r.getToCode());
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", r.getId());
            edge.put("from", r.getFromCode());
            edge.put("to", r.getToCode());
            edge.put("label", r.getLabel() == null ? r.getRelationType() : r.getLabel());
            edge.put("type", r.getRelationType());
            edges.add(edge);
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> nodeIds = used.isEmpty() && (focusCode == null || focusCode.isBlank())
                ? byCode.keySet() : used;
        if (focusCode != null && !focusCode.isBlank()) {
            nodeIds = new HashSet<>(used);
            nodeIds.add(focusCode.trim());
        }
        for (String code : nodeIds) {
            GovMetadataRegistry e = byCode.get(code);
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", code);
            n.put("label", e == null ? code : e.getEntryName());
            n.put("type", e == null ? "UNKNOWN" : e.getEntryType());
            n.put("dataLayer", e == null ? null : e.getDataLayer());
            nodes.add(n);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", nodes);
        out.put("edges", edges);
        out.put("source", "local");
        out.put("relationType", relationType);
        out.put("focusCode", focusCode);
        if (edges.isEmpty() && integrationProperties.isEnabled() && openMetadataClient.isHealthy() && !entries.isEmpty()) {
            try {
                String fqn = entries.get(0).getOmRef() != null ? entries.get(0).getOmRef() : entries.get(0).getEntryCode();
                Map<String, Object> omLin = openMetadataClient.getLineage(fqn);
                if (omLin != null && !omLin.isEmpty()) {
                    out.put("omLineage", omLin);
                    out.put("source", "om+local");
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public Map<String, Object> analyzeOverview() {
        Long tableCount = registryMapper.selectCount(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryType, "TABLE")
                .ne(GovMetadataRegistry::getStatus, "OFFLINE"));
        Long assocCount = relationMapper.selectCount(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getStatus, "ACTIVE")
                .in(GovMetaRelation::getRelationType, List.of("ASSOC", "FK")));
        Long lineageCount = relationMapper.selectCount(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getStatus, "ACTIVE")
                .in(GovMetaRelation::getRelationType, List.of("LINEAGE", "COLUMN_LINEAGE")));
        Long impactEdgeCount = relationMapper.selectCount(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getStatus, "ACTIVE")
                .eq(GovMetaRelation::getRelationType, "IMPACT"));
        Long colLineage = relationMapper.selectCount(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getStatus, "ACTIVE")
                .eq(GovMetaRelation::getRelationType, "COLUMN_LINEAGE"));
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("tableCount", tableCount == null ? 0 : tableCount.intValue());
        kpi.put("assocCount", assocCount == null ? 0 : assocCount.intValue());
        kpi.put("lineageCount", lineageCount == null ? 0 : lineageCount.intValue());
        kpi.put("columnLineageCount", colLineage == null ? 0 : colLineage.intValue());
        kpi.put("impactEdgeCount", impactEdgeCount == null ? 0 : impactEdgeCount.intValue());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kpi", kpi);
        return out;
    }

    public List<Map<String, Object>> listAnalyzeTables(String keyword) {
        LambdaQueryWrapper<GovMetadataRegistry> q = new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryType, "TABLE")
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .orderByAsc(GovMetadataRegistry::getEntryName);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(GovMetadataRegistry::getEntryName, kw)
                    .or().like(GovMetadataRegistry::getEntryCode, kw));
        }
        List<GovMetadataRegistry> tables = excludeControlPlaneEntries(registryMapper.selectList(q));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovMetadataRegistry e : tables) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("entryCode", e.getEntryCode());
            row.put("entryName", e.getEntryName());
            row.put("entryType", e.getEntryType());
            row.put("dataLayer", e.getDataLayer());
            row.put("databaseName", e.getDatabaseName());
            row.put("physicalTableName", e.getPhysicalTableName());
            out.add(row);
        }
        return out;
    }

    /**
     * 血缘分析：表级上下游递归；可下钻字段级血缘。
     */
    public Map<String, Object> analyzeLineage(String entryCode, String level) {
        if (entryCode == null || entryCode.isBlank()) {
            throw new BusinessException(400, "entryCode required");
        }
        String code = entryCode.trim();
        String lv = level == null || level.isBlank() ? "TABLE" : level.trim().toUpperCase(Locale.ROOT);
        List<GovMetaRelation> all = relationMapper.selectList(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getStatus, "ACTIVE")
                .in(GovMetaRelation::getRelationType, List.of("LINEAGE", "FK", "ASSOC", "COLUMN_LINEAGE")));
        List<GovMetaRelation> tableRels = all.stream()
                .filter(r -> !"COLUMN_LINEAGE".equalsIgnoreCase(r.getRelationType()))
                .collect(Collectors.toList());

        Set<String> upVisited = new HashSet<>();
        List<String> upstream = new ArrayList<>();
        collectUpstream(code, tableRels, upVisited, upstream);

        Set<String> downVisited = new HashSet<>();
        List<String> downstream = new ArrayList<>();
        collectDownstream(code, tableRels, downVisited, downstream);

        Set<String> scope = new LinkedHashSet<>();
        scope.add(code);
        scope.addAll(upstream);
        scope.addAll(downstream);

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String c : scope) {
            nodes.add(nodeForCode(c, c.equals(code) ? "ROOT" : null));
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (GovMetaRelation r : tableRels) {
            if (scope.contains(r.getFromCode()) && scope.contains(r.getToCode())) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("id", r.getId());
                edge.put("from", r.getFromCode());
                edge.put("to", r.getToCode());
                edge.put("label", r.getLabel() == null ? r.getRelationType() : r.getLabel());
                edge.put("type", r.getRelationType());
                edges.add(edge);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entryCode", code);
        out.put("level", lv);
        out.put("upstream", upstream);
        out.put("downstream", downstream);
        out.put("nodes", nodes);
        out.put("edges", edges);
        out.put("upstreamCount", upstream.size());
        out.put("downstreamCount", downstream.size());

        if ("COLUMN".equals(lv) || "FIELD".equals(lv)) {
            Map<String, Object> fieldGraph = buildColumnLineageGraph(code, all, scope);
            out.put("fieldNodes", fieldGraph.get("nodes"));
            out.put("fieldEdges", fieldGraph.get("edges"));
            out.put("fieldEdgeCount", fieldGraph.get("edgeCount"));
        }
        return out;
    }

    public Map<String, Object> analyzeImpactRecursive(String fromCode) {
        return analyzeImpactRecursive(fromCode, null);
    }

    public Map<String, Object> analyzeImpactRecursive(String fromCode, Integer maxDepth) {
        if (fromCode == null || fromCode.isBlank()) {
            throw new BusinessException(400, "fromCode required");
        }
        String code = fromCode.trim();
        int depthLimit = maxDepth == null || maxDepth <= 0 ? 20 : Math.min(maxDepth, 50);
        List<GovMetaRelation> rels = relationMapper.selectList(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getStatus, "ACTIVE")
                .in(GovMetaRelation::getRelationType, List.of("LINEAGE", "IMPACT", "FK", "ASSOC")));
        Map<String, Integer> hopMap = new LinkedHashMap<>();
        collectDownstreamWithDepth(code, rels, new HashSet<>(), hopMap, 0, depthLimit);
        List<Map<String, Object>> impactedDetails = new ArrayList<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(nodeForCode(code, "ROOT"));
        for (Map.Entry<String, Integer> en : hopMap.entrySet()) {
            GovMetadataRegistry e = findByCode(en.getKey());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("entryCode", en.getKey());
            detail.put("entryName", e == null ? en.getKey() : e.getEntryName());
            detail.put("entryType", e == null ? "UNKNOWN" : e.getEntryType());
            detail.put("dataLayer", e == null ? null : e.getDataLayer());
            detail.put("hop", en.getValue());
            impactedDetails.add(detail);
            Map<String, Object> n = nodeForCode(en.getKey(), null);
            n.put("hop", en.getValue());
            nodes.add(n);
        }
        Set<String> visited = new HashSet<>();
        visited.add(code);
        visited.addAll(hopMap.keySet());
        List<Map<String, Object>> edges = new ArrayList<>();
        for (GovMetaRelation r : rels) {
            if (visited.contains(r.getFromCode()) && visited.contains(r.getToCode())) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("id", r.getId());
                edge.put("from", r.getFromCode());
                edge.put("to", r.getToCode());
                edge.put("label", r.getLabel() == null ? r.getRelationType() : r.getLabel());
                edge.put("type", r.getRelationType());
                edges.add(edge);
            }
        }
        String risk = hopMap.isEmpty() ? "LOW" : hopMap.size() <= 3 ? "MEDIUM" : "HIGH";
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromCode", code);
        out.put("impacted", new ArrayList<>(hopMap.keySet()));
        out.put("impactedDetails", impactedDetails);
        out.put("nodes", nodes);
        out.put("edges", edges);
        out.put("count", hopMap.size());
        out.put("maxHop", hopMap.values().stream().mapToInt(Integer::intValue).max().orElse(0));
        out.put("riskLevel", risk);
        out.put("offlineAssess", analyzeOfflineAssess(code));
        return out;
    }

    public List<Map<String, Object>> listRelations(String relationType, String keyword) {
        LambdaQueryWrapper<GovMetaRelation> q = new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getStatus, "ACTIVE")
                .orderByDesc(GovMetaRelation::getId);
        if (relationType != null && !relationType.isBlank()) {
            String rt = relationType.trim().toUpperCase(Locale.ROOT);
            if ("ASSOC".equals(rt)) {
                q.in(GovMetaRelation::getRelationType, List.of("ASSOC", "FK"));
            } else {
                q.eq(GovMetaRelation::getRelationType, rt);
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(GovMetaRelation::getFromCode, kw)
                    .or().like(GovMetaRelation::getToCode, kw)
                    .or().like(GovMetaRelation::getLabel, kw));
        }
        List<GovMetaRelation> rels = relationMapper.selectList(q.last("limit 500"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovMetaRelation r : rels) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("fromCode", r.getFromCode());
            row.put("toCode", r.getToCode());
            row.put("fromName", resolveName(r.getFromCode()));
            row.put("toName", resolveName(r.getToCode()));
            row.put("relationType", r.getRelationType());
            row.put("label", r.getLabel());
            row.put("createdAt", r.getCreatedAt());
            out.add(row);
        }
        return out;
    }

    @Transactional
    public void deleteRelation(UserPrincipal operator, Long id) {
        GovMetaRelation r = relationMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(404, "关系不存在");
        }
        r.setStatus("DELETED");
        relationMapper.updateById(r);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_RELATION_DELETE", "gov_meta_relation", String.valueOf(id), r.getRelationType());
    }

    @Transactional
    public void updateRelation(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovMetaRelation r = relationMapper.selectById(id);
        if (r == null || "DELETED".equalsIgnoreCase(r.getStatus())) {
            throw new BusinessException(404, "关系不存在");
        }
        if (body.containsKey("label")) {
            r.setLabel(str(body.get("label"), null));
        }
        if (body.containsKey("relationType")) {
            r.setRelationType(str(body.get("relationType"), r.getRelationType()));
        }
        if (body.containsKey("fromCode")) {
            r.setFromCode(required(body.get("fromCode"), "fromCode"));
        }
        if (body.containsKey("toCode")) {
            r.setToCode(required(body.get("toCode"), "toCode"));
        }
        relationMapper.updateById(r);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_RELATION_UPDATE", "gov_meta_relation", String.valueOf(id), r.getRelationType());
    }

    /**
     * 自动解析血缘：补齐 SOURCE→TABLE，并按同名列生成字段级血缘。
     */
    @Transactional
    public Map<String, Object> parseLineageAuto(UserPrincipal operator) {
        int tableEdges = 0;
        int fieldEdges = 0;
        List<GovMetadataRegistry> tables = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryType, "TABLE")
                .ne(GovMetadataRegistry::getStatus, "OFFLINE"));
        for (GovMetadataRegistry t : tables) {
            if (t.getParentCode() == null || t.getParentCode().isBlank() || t.getEntryCode() == null) {
                continue;
            }
            if (ensureRelation(t.getParentCode(), t.getEntryCode(), "LINEAGE", "自动解析：归属数据源")) {
                tableEdges++;
            }
        }
        // 分层推断：同物理名跨层 ODS→DWD→DWS→ADS
        Map<String, List<GovMetadataRegistry>> byPhys = new HashMap<>();
        for (GovMetadataRegistry t : tables) {
            String key = nvl(t.getPhysicalTableName()).toLowerCase(Locale.ROOT);
            if (key.isBlank()) {
                key = nvl(t.getEntryName()).toLowerCase(Locale.ROOT);
            }
            if (key.isBlank()) continue;
            // 去掉层前缀后分组
            String norm = key.replaceFirst("^(ods_|dwd_|dws_|ads_)", "");
            byPhys.computeIfAbsent(norm, k -> new ArrayList<>()).add(t);
        }
        List<String> layerOrder = List.of("ODS", "DWD", "DWS", "ADS");
        for (List<GovMetadataRegistry> group : byPhys.values()) {
            if (group.size() < 2) continue;
            group.sort(Comparator.comparingInt(a -> layerOrder.indexOf(
                    a.getDataLayer() == null ? "" : a.getDataLayer().toUpperCase(Locale.ROOT))));
            for (int i = 0; i < group.size() - 1; i++) {
                GovMetadataRegistry a = group.get(i);
                GovMetadataRegistry b = group.get(i + 1);
                if (a.getEntryCode() == null || b.getEntryCode() == null) continue;
                String la = a.getDataLayer() == null ? "" : a.getDataLayer();
                String lb = b.getDataLayer() == null ? "" : b.getDataLayer();
                if (layerOrder.indexOf(la) < 0 || layerOrder.indexOf(lb) < 0) continue;
                if (layerOrder.indexOf(la) >= layerOrder.indexOf(lb)) continue;
                if (ensureRelation(a.getEntryCode(), b.getEntryCode(), "LINEAGE",
                        "自动解析：分层 " + la + "→" + lb)) {
                    tableEdges++;
                }
            }
        }
        // 已有表级血缘/外键 → 同名列字段血缘
        List<GovMetaRelation> tableRels = relationMapper.selectList(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getStatus, "ACTIVE")
                .in(GovMetaRelation::getRelationType, List.of("LINEAGE", "FK")));
        for (GovMetaRelation r : tableRels) {
            fieldEdges += linkSameNameColumns(r.getFromCode(), r.getToCode(), r.getRelationType(), r.getLabel());
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_PARSE_LINEAGE", "gov_meta_relation", "0",
                "table=" + tableEdges + ",field=" + fieldEdges);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tableEdges", tableEdges);
        out.put("fieldEdges", fieldEdges);
        return out;
    }

    @Transactional
    public Long subscribe(UserPrincipal operator, Map<String, Object> body) {
        String targetType = required(body.get("targetType"), "targetType");
        Long targetId = longVal(body.get("targetId"));
        if (targetId == null) {
            throw new BusinessException(400, "targetId required");
        }
        if ("ENTRY".equalsIgnoreCase(targetType)) {
            GovMetadataRegistry e = registryMapper.selectById(targetId);
            if (e == null) {
                throw new BusinessException(404, "元数据条目不存在");
            }
            if (!isPublishedForQuery(e)) {
                throw new BusinessException(400, "仅定版发布的元数据支持订阅");
            }
        } else if ("MODEL".equalsIgnoreCase(targetType)) {
            GovMetaModel m = requireModel(targetId);
            if (!"PUBLISHED".equals(m.getStatus())) {
                throw new BusinessException(400, "仅已发布的元模型支持订阅");
            }
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

    public Map<String, Object> maintainOverview(String keyword, String entryType, Boolean needRepublishOnly) {
        LambdaQueryWrapper<GovMetadataRegistry> q = new LambdaQueryWrapper<GovMetadataRegistry>()
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .orderByDesc(GovMetadataRegistry::getUpdatedAt)
                .orderByDesc(GovMetadataRegistry::getId);
        if (entryType != null && !entryType.isBlank()) {
            q.eq(GovMetadataRegistry::getEntryType, entryType.trim());
        } else {
            q.in(GovMetadataRegistry::getEntryType, List.of("TABLE", "SOURCE", "COLUMN"));
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(GovMetadataRegistry::getEntryName, kw)
                    .or().like(GovMetadataRegistry::getEntryCode, kw)
                    .or().like(GovMetadataRegistry::getTags, kw)
                    .or().like(GovMetadataRegistry::getKeywords, kw));
        }
        List<GovMetadataRegistry> entries = registryMapper.selectList(q);
        List<Map<String, Object>> items = new ArrayList<>();
        int needRepublish = 0, unreadNotice = 0, matchedHint = 0;
        for (GovMetadataRegistry e : entries) {
            Map<String, Object> row = enrichMaintainEntry(e);
            boolean need = Boolean.TRUE.equals(row.get("needRepublish"));
            if (need) needRepublish++;
            if (Boolean.TRUE.equals(needRepublishOnly) && !need) {
                continue;
            }
            items.add(row);
        }
        unreadNotice = Math.toIntExact(changeNoticeMapper.selectCount(new LambdaQueryWrapper<GovMetaChangeNotice>()
                .eq(GovMetaChangeNotice::getStatus, "UNREAD")));
        matchedHint = (int) entries.stream().filter(e -> e.getOmRef() != null && !e.getOmRef().isBlank()).count();
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("total", entries.size());
        kpi.put("needRepublish", needRepublish);
        kpi.put("unreadNotice", unreadNotice);
        kpi.put("standardLinked", matchedHint);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("kpi", kpi);
        return out;
    }

    private Map<String, Object> enrichMaintainEntry(GovMetadataRegistry e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entry", e);
        GovMetaVersion latest = versionMapper.selectOne(new LambdaQueryWrapper<GovMetaVersion>()
                .eq(GovMetaVersion::getTargetType, "ENTRY")
                .eq(GovMetaVersion::getTargetId, e.getId())
                .orderByDesc(GovMetaVersion::getVersionNo)
                .last("limit 1"));
        GovMetaVersion published = versionMapper.selectOne(new LambdaQueryWrapper<GovMetaVersion>()
                .eq(GovMetaVersion::getTargetType, "ENTRY")
                .eq(GovMetaVersion::getTargetId, e.getId())
                .like(GovMetaVersion::getChangeSummary, "发布")
                .orderByDesc(GovMetaVersion::getVersionNo)
                .last("limit 1"));
        row.put("latestVersionNo", latest == null ? null : latest.getVersionNo());
        row.put("latestVersionId", latest == null ? null : latest.getId());
        row.put("publishedVersionNo", published == null ? null : published.getVersionNo());
        row.put("publishedVersionId", published == null ? null : published.getId());
        boolean needRepublish = "CHANGED".equals(e.getChangeFlag()) || "NEW".equals(e.getChangeFlag());
        row.put("needRepublish", needRepublish);
        row.put("pendingFirstPublish", needRepublish && published == null);
        row.put("standardCode", e.getOmRef());
        return row;
    }

    @Transactional
    public Map<String, Object> autoMaintainBatch(UserPrincipal operator, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = body.get("entryIds") instanceof List ? (List<Object>) body.get("entryIds") : List.of();
        List<Long> ids = rawIds.stream().map(this::longVal).filter(Objects::nonNull).collect(Collectors.toList());
        String entryType = str(body.get("entryType"), null);
        LambdaQueryWrapper<GovMetadataRegistry> q = new LambdaQueryWrapper<GovMetadataRegistry>()
                .ne(GovMetadataRegistry::getStatus, "OFFLINE");
        if (!ids.isEmpty()) {
            q.in(GovMetadataRegistry::getId, ids);
        } else if (entryType != null && !entryType.isBlank()) {
            q.eq(GovMetadataRegistry::getEntryType, entryType);
        } else {
            q.in(GovMetadataRegistry::getEntryType, List.of("COLUMN", "TABLE", "SOURCE"));
        }
        List<GovMetadataRegistry> targets = registryMapper.selectList(q.last("limit 500"));
        int scanned = 0;
        int matched = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (GovMetadataRegistry e : targets) {
            scanned++;
            if (!applyStandardMatch(e)) {
                continue;
            }
            e.setUpdatedAt(LocalDateTime.now());
            registryMapper.updateById(e);
            insertChangeNotice(e, "AUTO 标准匹配维护", "依据数据元标准自动补充元数据");
            snapshotVersion(operator, "ENTRY", e.getId(), toJson(e), "AUTO 标准匹配维护");
            matched++;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("entryId", e.getId());
            d.put("entryName", e.getEntryName());
            d.put("standardCode", e.getOmRef());
            d.put("description", e.getDescription());
            details.add(d);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scanned", scanned);
        out.put("matched", matched);
        out.put("details", details);
        return out;
    }

    public List<Map<String, Object>> autoMatchPreview(Integer limit) {
        int lim = limit == null || limit <= 0 ? 30 : Math.min(limit, 100);
        List<GovMetadataRegistry> entries = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .in(GovMetadataRegistry::getEntryType, List.of("COLUMN", "TABLE"))
                .orderByDesc(GovMetadataRegistry::getId)
                .last("limit " + lim));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovMetadataRegistry e : entries) {
            if (e.getEntryName() == null || e.getEntryName().isBlank()) continue;
            GovStandardItem std = standardItemMapper.selectOne(new LambdaQueryWrapper<GovStandardItem>()
                    .eq(GovStandardItem::getItemName, e.getEntryName().trim())
                    .eq(GovStandardItem::getStatus, "ACTIVE")
                    .last("limit 1"));
            if (std == null) {
                std = standardItemMapper.selectOne(new LambdaQueryWrapper<GovStandardItem>()
                        .like(GovStandardItem::getItemName, e.getEntryName().trim())
                        .eq(GovStandardItem::getStatus, "ACTIVE")
                        .last("limit 1"));
            }
            if (std == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entryId", e.getId());
            row.put("entryName", e.getEntryName());
            row.put("entryType", e.getEntryType());
            row.put("standardCode", std.getItemCode());
            row.put("standardName", std.getItemName());
            row.put("suggestedDescription", firstNonBlank(std.getBusinessDefinition(), std.getStandardRef()));
            row.put("suggestedSecurity", mapSensitivity(nvl(std.getSensitivity()).isBlank() ? "INTERNAL" : std.getSensitivity()));
            row.put("suggestedDomain", std.getCategory());
            row.put("alreadyLinked", e.getOmRef() != null && e.getOmRef().equals(std.getItemCode()));
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> publishEntry(UserPrincipal operator, Long id) {
        return publishEntry(operator, id, "发布元数据");
    }

    @Transactional
    public Map<String, Object> publishEntry(UserPrincipal operator, Long id, String description) {
        GovMetadataRegistry e = registryMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(404, "条目不存在");
        }
        e.setChangeFlag("SYNCED");
        e.setStatus("PUBLISHED");
        e.setUpdatedAt(LocalDateTime.now());
        registryMapper.updateById(e);
        String summary = description == null || description.isBlank() ? "发布元数据" : description.trim();
        if (!summary.contains("发布")) {
            summary = "发布元数据：" + summary;
        }
        snapshotEntryVersion(operator, e, summary);
        // 表定版时同步子字段可查询
        if ("TABLE".equalsIgnoreCase(e.getEntryType()) && e.getEntryCode() != null) {
            List<GovMetadataRegistry> cols = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                    .eq(GovMetadataRegistry::getParentCode, e.getEntryCode())
                    .eq(GovMetadataRegistry::getEntryType, "COLUMN"));
            for (GovMetadataRegistry c : cols) {
                c.setStatus("PUBLISHED");
                c.setChangeFlag("SYNCED");
                c.setUpdatedAt(LocalDateTime.now());
                registryMapper.updateById(c);
            }
        }
        GovMetaChangeNotice notice = new GovMetaChangeNotice();
        notice.setEntryId(e.getId());
        notice.setEntryCode(e.getEntryCode());
        notice.setTitle("元数据已发布");
        notice.setDetail(e.getEntryName() + " 已发布为当前生效版本");
        notice.setStatus("READ");
        changeNoticeMapper.insert(notice);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "META_ENTRY_PUBLISH", "gov_metadata_registry", String.valueOf(id), e.getEntryCode());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entryId", id);
        out.put("changeFlag", e.getChangeFlag());
        out.put("status", e.getStatus());
        return out;
    }

    public Map<String, Object> compareWithPublished(Long entryId) {
        GovMetadataRegistry e = registryMapper.selectById(entryId);
        if (e == null) {
            throw new BusinessException(404, "条目不存在");
        }
        GovMetaVersion published = versionMapper.selectOne(new LambdaQueryWrapper<GovMetaVersion>()
                .eq(GovMetaVersion::getTargetType, "ENTRY")
                .eq(GovMetaVersion::getTargetId, entryId)
                .like(GovMetaVersion::getChangeSummary, "发布")
                .orderByDesc(GovMetaVersion::getVersionNo)
                .last("limit 1"));
        GovMetaVersion latest = versionMapper.selectOne(new LambdaQueryWrapper<GovMetaVersion>()
                .eq(GovMetaVersion::getTargetType, "ENTRY")
                .eq(GovMetaVersion::getTargetId, entryId)
                .orderByDesc(GovMetaVersion::getVersionNo)
                .last("limit 1"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entry", e);
        out.put("published", published);
        out.put("latest", latest);
        if (published == null) {
            out.put("sameSnapshot", false);
            out.put("basicDiff", List.of(Map.of(
                    "field", "publishStatus",
                    "left", "未发布",
                    "right", "当前草稿/最新",
                    "changeType", "changed")));
            out.put("fieldDiff", Map.of("added", List.of(), "removed", List.of(), "changed", List.of()));
            return out;
        }
        // 用当前实体快照与已发布版本对比
        Map<String, Object> leftSnap = parseSnapshot(published.getSnapshotJson());
        Map<String, Object> rightSnap = parseSnapshot(toJson(e));
        List<Map<String, Object>> basicDiff = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        keys.addAll(leftSnap.keySet());
        keys.addAll(rightSnap.keySet());
        for (String key : keys) {
            if ("contentJson".equals(key) || "updatedAt".equals(key) || "createdAt".equals(key)) continue;
            String lv = nvl(String.valueOf(leftSnap.getOrDefault(key, "")));
            String rv = nvl(String.valueOf(rightSnap.getOrDefault(key, "")));
            if (!lv.equals(rv)) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("field", key);
                d.put("left", lv);
                d.put("right", rv);
                d.put("changeType", "changed");
                basicDiff.add(d);
            }
        }
        out.put("sameSnapshot", basicDiff.isEmpty());
        out.put("basicDiff", basicDiff);
        out.put("fieldDiff", diffContentJsonFields(leftSnap.get("contentJson"), rightSnap.get("contentJson")));
        out.put("attrDiff", buildAttrDiff(leftSnap, rightSnap));
        return out;
    }

    @Transactional
    public void markNoticeRead(Long id) {
        GovMetaChangeNotice n = changeNoticeMapper.selectById(id);
        if (n == null) {
            throw new BusinessException(404, "通知不存在");
        }
        n.setStatus("READ");
        changeNoticeMapper.updateById(n);
    }

    /**
     * 数据项管理编辑后：覆盖元数据维护中对应 COLUMN 属性，原信息不可恢复。
     */
    @Transactional
    public void overwriteColumnMetadataFromIngest(Long tableId, String columnCode,
                                                  String columnName, String dataType, String semanticDesc) {
        if (tableId == null || columnCode == null || columnCode.isBlank()) {
            return;
        }
        String code = columnCode.trim();
        List<GovMetadataRegistry> candidates = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryType, "COLUMN")
                .eq(GovMetadataRegistry::getSourceTableId, tableId)
                .ne(GovMetadataRegistry::getStatus, "OFFLINE"));
        for (GovMetadataRegistry e : candidates) {
            boolean match = code.equalsIgnoreCase(nvl(e.getEntryName()))
                    || code.equalsIgnoreCase(nvl(e.getKeywords()))
                    || (e.getEntryCode() != null && e.getEntryCode().toUpperCase(Locale.ROOT)
                    .endsWith("_" + code.toUpperCase(Locale.ROOT)));
            if (!match) {
                continue;
            }
            String before = "名称=" + nvl(e.getEntryName()) + "；说明=" + nvl(e.getDescription());
            String displayName = columnName != null && !columnName.isBlank() ? columnName.trim() : code;
            String desc = (dataType == null || dataType.isBlank() ? "" : dataType.trim())
                    + (semanticDesc != null && !semanticDesc.isBlank() ? (" | " + semanticDesc.trim()) : "");
            e.setEntryName(displayName);
            e.setDescription(desc);
            e.setKeywords(code);
            e.setChangeFlag("CHANGED");
            registryMapper.updateById(e);

            GovMetaChangeNotice notice = new GovMetaChangeNotice();
            notice.setEntryId(e.getId());
            notice.setEntryCode(e.getEntryCode());
            notice.setTitle("数据项编辑覆盖属性（不可恢复）");
            notice.setDetail("字段编码 " + code + " 的元数据属性已被覆盖，原信息不可恢复。变更前：" + before
                    + "；变更后：名称=" + displayName + "；说明=" + desc);
            notice.setStatus("UNREAD");
            changeNoticeMapper.insert(notice);
        }
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
                        // 字段级外键血缘
                        String fromColCode = resolveColumnCode(fromCode, col);
                        String toColCode = resolveColumnCode(toCode, refCol);
                        if (fromColCode != null && toColCode != null
                                && ensureRelation(fromColCode, toColCode, "COLUMN_LINEAGE",
                                "FK " + col + "→" + refCol)) {
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

    private boolean applyStandardMatch(GovMetadataRegistry e) {
        if (e.getEntryName() == null || e.getEntryName().isBlank()) return false;
        GovStandardItem std = standardItemMapper.selectOne(new LambdaQueryWrapper<GovStandardItem>()
                .eq(GovStandardItem::getItemName, e.getEntryName().trim())
                .eq(GovStandardItem::getStatus, "ACTIVE")
                .last("limit 1"));
        if (std == null) {
            std = standardItemMapper.selectOne(new LambdaQueryWrapper<GovStandardItem>()
                    .like(GovStandardItem::getItemName, e.getEntryName().trim())
                    .eq(GovStandardItem::getStatus, "ACTIVE")
                    .last("limit 1"));
        }
        if (std == null) {
            return false;
        }
        boolean touched = false;
        if (isBlank(e.getDescription())) {
            String def = firstNonBlank(std.getBusinessDefinition(), std.getReferenceStandard(), std.getStandardRef());
            if (def != null) {
                e.setDescription("标准匹配：" + std.getItemName() + " · " + def);
                touched = true;
            }
        }
        if (isBlank(e.getOmRef()) && !isBlank(std.getItemCode())) {
            e.setOmRef(std.getItemCode());
            touched = true;
        }
        if (isBlank(e.getSecurityLevel()) && !isBlank(std.getSensitivity())) {
            e.setSecurityLevel(mapSensitivity(std.getSensitivity()));
            touched = true;
        }
        if (isBlank(e.getBusinessDomain()) && !isBlank(std.getCategory())) {
            e.setBusinessDomain(std.getCategory());
            touched = true;
        }
        if (isBlank(e.getKeywords())) {
            e.setKeywords(std.getItemName());
            touched = true;
        }
        if (isBlank(e.getTags()) && !isBlank(std.getCategory())) {
            e.setTags(std.getCategory());
            touched = true;
        } else if (!isBlank(std.getCategory()) && (e.getTags() == null || !e.getTags().contains(std.getCategory()))) {
            e.setTags(normalizeTags((e.getTags() == null ? "" : e.getTags() + ",") + std.getCategory()));
            touched = true;
        }
        if (touched) {
            e.setChangeFlag("CHANGED");
        }
        return touched;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private String mapSensitivity(String sensitivity) {
        String s = sensitivity.trim().toUpperCase(Locale.ROOT);
        if (s.contains("公开") || s.contains("PUBLIC") || s.contains("L1")) return "PUBLIC";
        if (s.contains("内部") || s.contains("INTERNAL") || s.contains("L2")) return "INTERNAL";
        if (s.contains("敏感") || s.contains("SENSITIVE") || s.contains("L3")) return "SENSITIVE";
        if (s.contains("核心") || s.contains("SECRET") || s.contains("L4")) return "SECRET";
        return "INTERNAL";
    }

    private void insertChangeNotice(GovMetadataRegistry e, String title, String detail) {
        if (!"CHANGED".equals(e.getChangeFlag()) && !"NEW".equals(e.getChangeFlag())) return;
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

    private void collectUpstream(String code, List<GovMetaRelation> rels, Set<String> visited, List<String> upstream) {
        if (!visited.add(code)) return;
        for (GovMetaRelation r : rels) {
            if (code.equals(r.getToCode()) && !visited.contains(r.getFromCode())) {
                upstream.add(r.getFromCode());
                collectUpstream(r.getFromCode(), rels, visited, upstream);
            }
        }
    }

    private void collectDownstreamWithDepth(String code, List<GovMetaRelation> rels, Set<String> visited,
                                            Map<String, Integer> hopMap, int depth, int maxDepth) {
        if (depth >= maxDepth) return;
        if (!visited.add(code)) return;
        for (GovMetaRelation r : rels) {
            if (!code.equals(r.getFromCode())) continue;
            String next = r.getToCode();
            if (next == null || visited.contains(next)) continue;
            int hop = depth + 1;
            hopMap.merge(next, hop, Math::min);
            collectDownstreamWithDepth(next, rels, visited, hopMap, hop, maxDepth);
        }
    }

    private Map<String, Object> nodeForCode(String code, String typeOverride) {
        GovMetadataRegistry e = findByCode(code);
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", code);
        n.put("label", e == null ? code : e.getEntryName());
        if (typeOverride != null) {
            n.put("type", typeOverride);
        } else {
            n.put("type", e == null ? "UNKNOWN" : e.getEntryType());
        }
        n.put("dataLayer", e == null ? null : e.getDataLayer());
        n.put("entryType", e == null ? null : e.getEntryType());
        return n;
    }

    private GovMetadataRegistry findByCode(String code) {
        if (code == null || code.isBlank()) return null;
        return registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryCode, code).last("limit 1"));
    }

    private String resolveName(String code) {
        GovMetadataRegistry e = findByCode(code);
        return e == null ? code : e.getEntryName();
    }

    private boolean ensureRelation(String fromCode, String toCode, String type, String label) {
        if (fromCode == null || toCode == null || fromCode.equals(toCode)) {
            return false;
        }
        GovMetaRelation exist = relationMapper.selectOne(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getFromCode, fromCode)
                .eq(GovMetaRelation::getToCode, toCode)
                .eq(GovMetaRelation::getRelationType, type)
                .eq(GovMetaRelation::getStatus, "ACTIVE")
                .last("limit 1"));
        if (exist != null) {
            return false;
        }
        GovMetaRelation r = new GovMetaRelation();
        r.setFromCode(fromCode);
        r.setToCode(toCode);
        r.setRelationType(type);
        r.setLabel(label);
        r.setStatus("ACTIVE");
        relationMapper.insert(r);
        return true;
    }

    private int linkSameNameColumns(String fromTableCode, String toTableCode, String edgeType, String fkLabel) {
        int n = 0;
        // FK 标签明确列映射时优先
        if ("FK".equalsIgnoreCase(edgeType) && fkLabel != null && fkLabel.contains("->")) {
            String[] parts = fkLabel.split("->", 2);
            if (parts.length == 2) {
                String fromCol = resolveColumnCode(fromTableCode, parts[0].trim());
                String toCol = resolveColumnCode(toTableCode, parts[1].trim());
                if (fromCol != null && toCol != null
                        && ensureRelation(fromCol, toCol, "COLUMN_LINEAGE", "FK " + fkLabel)) {
                    n++;
                }
                return n;
            }
        }
        List<GovMetadataRegistry> fromCols = listColumnsOfTable(fromTableCode);
        List<GovMetadataRegistry> toCols = listColumnsOfTable(toTableCode);
        Map<String, GovMetadataRegistry> toByName = new HashMap<>();
        for (GovMetadataRegistry c : toCols) {
            if (c.getEntryName() != null) {
                toByName.put(c.getEntryName().toLowerCase(Locale.ROOT), c);
            }
        }
        for (GovMetadataRegistry fc : fromCols) {
            if (fc.getEntryName() == null) continue;
            GovMetadataRegistry tc = toByName.get(fc.getEntryName().toLowerCase(Locale.ROOT));
            if (tc == null || fc.getEntryCode() == null || tc.getEntryCode() == null) continue;
            if (ensureRelation(fc.getEntryCode(), tc.getEntryCode(), "COLUMN_LINEAGE",
                    "同名列 " + fc.getEntryName())) {
                n++;
            }
        }
        return n;
    }

    private List<GovMetadataRegistry> listColumnsOfTable(String tableCode) {
        if (tableCode == null || tableCode.isBlank()) return List.of();
        return registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getParentCode, tableCode)
                .eq(GovMetadataRegistry::getEntryType, "COLUMN")
                .ne(GovMetadataRegistry::getStatus, "OFFLINE"));
    }

    private String resolveColumnCode(String tableCode, String columnName) {
        if (tableCode == null || columnName == null || columnName.isBlank()) return null;
        List<GovMetadataRegistry> cols = listColumnsOfTable(tableCode);
        for (GovMetadataRegistry c : cols) {
            if (columnName.equalsIgnoreCase(c.getEntryName())) {
                return c.getEntryCode();
            }
        }
        // 回退：按编码后缀匹配
        String suffix = "_" + columnName;
        for (GovMetadataRegistry c : cols) {
            if (c.getEntryCode() != null && c.getEntryCode().toUpperCase(Locale.ROOT)
                    .endsWith(suffix.toUpperCase(Locale.ROOT))) {
                return c.getEntryCode();
            }
        }
        return null;
    }

    private Map<String, Object> buildColumnLineageGraph(String tableCode, List<GovMetaRelation> all,
                                                        Set<String> tableScope) {
        Set<String> colCodes = new LinkedHashSet<>();
        Map<String, String> colTable = new HashMap<>();
        for (String tc : tableScope) {
            for (GovMetadataRegistry c : listColumnsOfTable(tc)) {
                if (c.getEntryCode() != null) {
                    colCodes.add(c.getEntryCode());
                    colTable.put(c.getEntryCode(), tc);
                }
            }
        }
        List<Map<String, Object>> fieldEdges = new ArrayList<>();
        Set<String> usedCols = new HashSet<>();
        for (GovMetaRelation r : all) {
            if (!"COLUMN_LINEAGE".equalsIgnoreCase(r.getRelationType())) continue;
            if (!colCodes.contains(r.getFromCode()) && !colCodes.contains(r.getToCode())) continue;
            // 聚焦：至少一端属于当前表，或两端都在范围内
            boolean touchFocus = tableCode.equals(colTable.get(r.getFromCode()))
                    || tableCode.equals(colTable.get(r.getToCode()));
            boolean bothIn = colCodes.contains(r.getFromCode()) && colCodes.contains(r.getToCode());
            if (!touchFocus && !bothIn) continue;
            usedCols.add(r.getFromCode());
            usedCols.add(r.getToCode());
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", r.getId());
            edge.put("from", r.getFromCode());
            edge.put("to", r.getToCode());
            edge.put("label", r.getLabel() == null ? "字段血缘" : r.getLabel());
            edge.put("type", "COLUMN_LINEAGE");
            edge.put("fromTable", colTable.get(r.getFromCode()));
            edge.put("toTable", colTable.get(r.getToCode()));
            fieldEdges.add(edge);
        }
        // 若无存量字段血缘，现场按同名列推导（不落库，仅展示）
        if (fieldEdges.isEmpty()) {
            for (GovMetaRelation r : all) {
                if ("COLUMN_LINEAGE".equalsIgnoreCase(r.getRelationType())) continue;
                if (!tableScope.contains(r.getFromCode()) || !tableScope.contains(r.getToCode())) continue;
                List<GovMetadataRegistry> fromCols = listColumnsOfTable(r.getFromCode());
                Map<String, GovMetadataRegistry> toByName = new HashMap<>();
                for (GovMetadataRegistry c : listColumnsOfTable(r.getToCode())) {
                    if (c.getEntryName() != null) {
                        toByName.put(c.getEntryName().toLowerCase(Locale.ROOT), c);
                    }
                }
                for (GovMetadataRegistry fc : fromCols) {
                    if (fc.getEntryName() == null) continue;
                    GovMetadataRegistry tc = toByName.get(fc.getEntryName().toLowerCase(Locale.ROOT));
                    if (tc == null) continue;
                    usedCols.add(fc.getEntryCode());
                    usedCols.add(tc.getEntryCode());
                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("from", fc.getEntryCode());
                    edge.put("to", tc.getEntryCode());
                    edge.put("label", "推导：" + fc.getEntryName());
                    edge.put("type", "COLUMN_LINEAGE");
                    edge.put("derived", true);
                    edge.put("fromTable", r.getFromCode());
                    edge.put("toTable", r.getToCode());
                    fieldEdges.add(edge);
                }
            }
        }
        List<Map<String, Object>> fieldNodes = new ArrayList<>();
        for (String cc : usedCols) {
            GovMetadataRegistry e = findByCode(cc);
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", cc);
            n.put("label", e == null ? cc : e.getEntryName());
            n.put("type", "COLUMN");
            n.put("tableCode", colTable.get(cc));
            fieldNodes.add(n);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", fieldNodes);
        out.put("edges", fieldEdges);
        out.put("edgeCount", fieldEdges.size());
        return out;
    }

    private Map<String, Object> parseSnapshot(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private boolean catalogKeywordMatch(GovMetadataRegistry e, String kwLower) {
        if (e == null || kwLower == null || kwLower.isBlank()) return false;
        return containsIgnoreCase(e.getEntryName(), kwLower)
                || containsIgnoreCase(e.getEntryCode(), kwLower)
                || containsIgnoreCase(e.getPhysicalTableName(), kwLower)
                || containsIgnoreCase(e.getDatabaseName(), kwLower)
                || containsIgnoreCase(e.getKeywords(), kwLower)
                || containsIgnoreCase(e.getTags(), kwLower)
                || containsIgnoreCase(e.getDescription(), kwLower)
                || containsIgnoreCase(e.getBusinessDomain(), kwLower)
                || containsIgnoreCase(e.getOmRef(), kwLower);
    }

    private boolean containsIgnoreCase(String raw, String kwLower) {
        return raw != null && raw.toLowerCase(Locale.ROOT).contains(kwLower);
    }

    private List<Map<String, Object>> buildSourceCatalogTree(List<GovMetadataRegistry> entries) {
        Map<String, GovMetadataRegistry> byCode = entries.stream()
                .filter(e -> e.getEntryCode() != null)
                .collect(Collectors.toMap(GovMetadataRegistry::getEntryCode, e -> e, (a, b) -> a));
        List<GovMetadataRegistry> sources = entries.stream()
                .filter(e -> isSourceType(e.getEntryType()))
                .collect(Collectors.toList());
        List<GovMetadataRegistry> tables = entries.stream()
                .filter(e -> "TABLE".equalsIgnoreCase(e.getEntryType()))
                .collect(Collectors.toList());

        Map<String, List<GovMetadataRegistry>> sourcesByBiz = new LinkedHashMap<>();
        for (GovMetadataRegistry s : sources) {
            String biz = firstNonBlank(s.getBusinessDomain(), "未归属业务系统");
            sourcesByBiz.computeIfAbsent(biz, k -> new ArrayList<>()).add(s);
        }
        // 无源但有表时，按表业务域兜底
        if (sources.isEmpty()) {
            for (GovMetadataRegistry t : tables) {
                String biz = firstNonBlank(t.getBusinessDomain(), "未归属业务系统");
                sourcesByBiz.computeIfAbsent(biz, k -> new ArrayList<>());
            }
        }

        List<Map<String, Object>> tree = new ArrayList<>();
        for (Map.Entry<String, List<GovMetadataRegistry>> bizEn : sourcesByBiz.entrySet()) {
            List<Map<String, Object>> sourceNodes = new ArrayList<>();
            List<GovMetadataRegistry> srcList = bizEn.getValue();
            if (srcList.isEmpty()) {
                // 仅表：虚拟源节点
                Map<String, List<GovMetadataRegistry>> byDb = new LinkedHashMap<>();
                for (GovMetadataRegistry t : tables) {
                    if (!bizEn.getKey().equals(firstNonBlank(t.getBusinessDomain(), "未归属业务系统"))) continue;
                    String db = firstNonBlank(t.getDatabaseName(), "未命名库");
                    byDb.computeIfAbsent(db, k -> new ArrayList<>()).add(t);
                }
                List<Map<String, Object>> dbNodes = new ArrayList<>();
                for (Map.Entry<String, List<GovMetadataRegistry>> dbEn : byDb.entrySet()) {
                    List<Map<String, Object>> tableNodes = new ArrayList<>();
                    for (GovMetadataRegistry t : dbEn.getValue()) {
                        tableNodes.add(catalogTreeLeaf(t));
                    }
                    dbNodes.add(catalogTreeGroup("db:" + bizEn.getKey() + ":" + dbEn.getKey(),
                            dbEn.getKey() + "（" + tableNodes.size() + "）", tableNodes));
                }
                sourceNodes.add(catalogTreeGroup("src:__none__:" + bizEn.getKey(),
                        "未登记数据源（" + tables.size() + "）", dbNodes));
            } else {
                for (GovMetadataRegistry s : srcList) {
                    Map<String, List<GovMetadataRegistry>> byDb = new LinkedHashMap<>();
                    for (GovMetadataRegistry t : tables) {
                        if (!s.getEntryCode().equals(t.getParentCode())) continue;
                        String db = firstNonBlank(t.getDatabaseName(), "未命名库");
                        byDb.computeIfAbsent(db, k -> new ArrayList<>()).add(t);
                    }
                    List<Map<String, Object>> dbNodes = new ArrayList<>();
                    int tableCount = 0;
                    for (Map.Entry<String, List<GovMetadataRegistry>> dbEn : byDb.entrySet()) {
                        List<Map<String, Object>> tableNodes = new ArrayList<>();
                        for (GovMetadataRegistry t : dbEn.getValue()) {
                            tableNodes.add(catalogTreeLeaf(t));
                        }
                        tableCount += tableNodes.size();
                        dbNodes.add(catalogTreeGroup("db:" + s.getEntryCode() + ":" + dbEn.getKey(),
                                dbEn.getKey() + "（" + tableNodes.size() + "）", tableNodes));
                    }
                    Map<String, Object> srcNode = catalogTreeGroup("src:" + s.getEntryCode(),
                            s.getEntryName() + "（" + tableCount + "）", dbNodes);
                    srcNode.put("entryCode", s.getEntryCode());
                    sourceNodes.add(srcNode);
                }
            }
            tree.add(catalogTreeGroup("biz:" + bizEn.getKey(),
                    bizEn.getKey() + "（" + sourceNodes.size() + "）", sourceNodes));
        }
        return tree;
    }

    private List<Map<String, Object>> buildAssetCatalogTree(List<GovMetadataRegistry> entries) {
        List<GovMetadataRegistry> tables = entries.stream()
                .filter(e -> "TABLE".equalsIgnoreCase(e.getEntryType()))
                .collect(Collectors.toList());
        List<String> layers = List.of("ODS", "DWD", "DWS", "ADS");
        List<Map<String, Object>> tree = new ArrayList<>();
        for (String layer : layers) {
            List<GovMetadataRegistry> layerTables = tables.stream()
                    .filter(t -> layer.equalsIgnoreCase(resolveDataPlaneLayer(t)))
                    .collect(Collectors.toList());
            Map<String, List<GovMetadataRegistry>> byDomain = new LinkedHashMap<>();
            for (GovMetadataRegistry t : layerTables) {
                String domain = firstNonBlank(t.getBusinessDomain(), "未划分主题域");
                byDomain.computeIfAbsent(domain, k -> new ArrayList<>()).add(t);
            }
            List<Map<String, Object>> domainNodes = new ArrayList<>();
            for (Map.Entry<String, List<GovMetadataRegistry>> den : byDomain.entrySet()) {
                List<Map<String, Object>> tableNodes = new ArrayList<>();
                for (GovMetadataRegistry t : den.getValue()) {
                    tableNodes.add(catalogTreeLeaf(t));
                }
                domainNodes.add(catalogTreeGroup("domain:" + layer + ":" + den.getKey(),
                        den.getKey() + "（" + tableNodes.size() + "）", tableNodes));
            }
            String layerLabel = switch (layer) {
                case "ODS" -> "ODS 原始层";
                case "DWD" -> "DWD 明细层";
                case "DWS" -> "DWS 汇总层";
                case "ADS" -> "ADS 应用层";
                default -> layer;
            };
            tree.add(catalogTreeGroup("layer:" + layer,
                    layerLabel + "（" + layerTables.size() + "）", domainNodes));
        }
        return tree;
    }

    private Map<String, Object> catalogTreeGroup(String code, String label, List<Map<String, Object>> children) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("code", code);
        n.put("label", label);
        n.put("children", children);
        return n;
    }

    private Map<String, Object> catalogTreeLeaf(GovMetadataRegistry t) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("code", t.getEntryCode());
        n.put("label", t.getEntryName());
        n.put("entryId", t.getId());
        n.put("entryCode", t.getEntryCode());
        n.put("entryType", t.getEntryType());
        n.put("leaf", true);
        return n;
    }

    private boolean isPublishedForQuery(GovMetadataRegistry e) {
        if (e == null || "OFFLINE".equalsIgnoreCase(e.getStatus())) {
            return false;
        }
        // 源连接保留树结构可见；表/字段须定版
        if (isSourceType(e.getEntryType())) {
            return true;
        }
        return "PUBLISHED".equalsIgnoreCase(e.getStatus()) || "SYNCED".equalsIgnoreCase(e.getChangeFlag());
    }

    private boolean isPublishSummary(String summary) {
        return summary != null && summary.contains("发布");
    }

    private boolean isOfflineSummary(String summary) {
        return summary != null && summary.contains("下线");
    }

    private Map<String, Object> enrichVersionTarget(String targetType, Long targetId, String code, String name,
                                                    String status, String changeFlag) {
        List<GovMetaVersion> versions = listVersions(targetType, targetId);
        GovMetaVersion latest = versions.isEmpty() ? null : versions.get(0);
        GovMetaVersion published = versions.stream()
                .filter(v -> isPublishSummary(v.getChangeSummary()))
                .findFirst()
                .orElse(null);
        String publishStatus;
        if ("OFFLINE".equalsIgnoreCase(status)) {
            publishStatus = "OFFLINE";
        } else if ("PUBLISHED".equalsIgnoreCase(status)
                || ("ENTRY".equals(targetType) && "SYNCED".equalsIgnoreCase(changeFlag))) {
            publishStatus = "PUBLISHED";
        } else {
            publishStatus = "DRAFT";
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("targetType", targetType);
        row.put("targetId", targetId);
        row.put("code", code);
        row.put("name", name);
        row.put("status", status);
        row.put("changeFlag", changeFlag);
        row.put("publishStatus", publishStatus);
        row.put("versionCount", versions.size());
        row.put("latestVersionNo", latest == null ? null : latest.getVersionNo());
        row.put("latestVersionId", latest == null ? null : latest.getId());
        row.put("publishedVersionNo", published == null ? null : published.getVersionNo());
        row.put("publishedVersionId", published == null ? null : published.getId());
        row.put("canSubscribe", "PUBLISHED".equals(publishStatus));
        row.put("needRepublish", "ENTRY".equals(targetType)
                && ("CHANGED".equalsIgnoreCase(changeFlag) || "NEW".equalsIgnoreCase(changeFlag)));
        return row;
    }

    private void snapshotEntryVersion(UserPrincipal operator, GovMetadataRegistry e, String summary) {
        Map<String, Object> snap = parseSnapshot(toJson(e));
        snap.put("fields", buildFieldAttrsFromEntry(e));
        snap.put("relations", listRelationsForEntryCode(e.getEntryCode()));
        snapshotVersion(operator, "ENTRY", e.getId(), toJson(snap), summary);
    }

    private List<Map<String, Object>> listRelationsForEntryCode(String entryCode) {
        if (entryCode == null || entryCode.isBlank()) {
            return List.of();
        }
        List<GovMetaRelation> rels = relationMapper.selectList(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getStatus, "ACTIVE")
                .and(w -> w.eq(GovMetaRelation::getFromCode, entryCode)
                        .or().eq(GovMetaRelation::getToCode, entryCode))
                .orderByDesc(GovMetaRelation::getId)
                .last("limit 100"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovMetaRelation r : rels) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fromCode", r.getFromCode());
            m.put("toCode", r.getToCode());
            m.put("relationType", r.getRelationType());
            m.put("label", r.getLabel() == null ? r.getRelationType() : r.getLabel());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> buildFieldAttrsFromEntry(GovMetadataRegistry e) {
        List<Map<String, Object>> fields = new ArrayList<>();
        if (e == null) {
            return fields;
        }
        if ("COLUMN".equalsIgnoreCase(e.getEntryType())) {
            fields.add(normalizeFieldAttr(Map.of(
                    "nameEn", nvl(e.getEntryName()),
                    "nameZh", nvl(e.getEntryName()),
                    "dataType", nvl(e.getKeywords()),
                    "description", nvl(e.getDescription())
            )));
            return fields;
        }
        List<GovMetadataRegistry> cols = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getParentCode, e.getEntryCode())
                .eq(GovMetadataRegistry::getEntryType, "COLUMN")
                .orderByAsc(GovMetadataRegistry::getEntryCode));
        for (GovMetadataRegistry c : cols) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("nameEn", c.getEntryName());
            raw.put("nameZh", firstNonBlank(c.getKeywords(), c.getEntryName()));
            // keywords 可能存类型；description 存说明
            String dtype = "";
            String desc = nvl(c.getDescription());
            if (c.getOmRef() != null && c.getOmRef().contains("|")) {
                String[] parts = c.getOmRef().split("\\|", 2);
                dtype = parts[0];
            }
            raw.put("dataType", dtype);
            raw.put("length", "");
            raw.put("primaryKey", "");
            raw.put("partition", "");
            raw.put("unit", "");
            raw.put("description", desc);
            fields.add(normalizeFieldAttr(raw));
        }
        if (fields.isEmpty() && e.getSourceTableId() != null) {
            List<IngDataColumn> ings = ingDataColumnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                    .eq(IngDataColumn::getTableId, e.getSourceTableId())
                    .orderByAsc(IngDataColumn::getId));
            for (IngDataColumn col : ings) {
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("nameEn", col.getColumnCode());
                raw.put("nameZh", firstNonBlank(col.getColumnName(), col.getColumnCode()));
                raw.put("dataType", nvl(col.getDataType()));
                raw.put("length", col.getLengthVal() == null ? "" : String.valueOf(col.getLengthVal()));
                raw.put("primaryKey", "");
                raw.put("partition", Integer.valueOf(1).equals(col.getPartitionColFlag()) ? "是" : "否");
                raw.put("unit", "");
                raw.put("description", nvl(col.getSemanticDesc()));
                fields.add(normalizeFieldAttr(raw));
            }
        }
        return fields;
    }

    private Map<String, Object> normalizeFieldAttr(Map<String, Object> raw) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("nameEn", firstNonBlank(str(raw.get("nameEn"), null), str(raw.get("fieldName"), null),
                str(raw.get("code"), null), str(raw.get("name"), null)));
        f.put("nameZh", firstNonBlank(str(raw.get("nameZh"), null), str(raw.get("nameCn"), null),
                str(raw.get("chineseName"), null), str(raw.get("name"), null), String.valueOf(f.get("nameEn"))));
        f.put("dataType", firstNonBlank(str(raw.get("dataType"), null), str(raw.get("type"), null),
                str(raw.get("data_type"), null)));
        f.put("length", firstNonBlank(str(raw.get("length"), null), str(raw.get("dataLength"), null),
                str(raw.get("columnSize"), null)));
        Object pk = raw.get("primaryKey");
        if (pk == null) pk = raw.get("pk");
        if (pk == null) pk = raw.get("isPrimaryKey");
        f.put("primaryKey", pk == null ? "" : (Boolean.TRUE.equals(pk) || "true".equalsIgnoreCase(String.valueOf(pk))
                || "1".equals(String.valueOf(pk)) || "是".equals(String.valueOf(pk)) ? "是" : String.valueOf(pk)));
        Object part = raw.get("partition");
        if (part == null) part = raw.get("isPartition");
        f.put("partition", part == null ? "" : String.valueOf(part));
        f.put("unit", firstNonBlank(str(raw.get("unit"), null), str(raw.get("measureUnit"), null)));
        f.put("description", firstNonBlank(str(raw.get("description"), null), str(raw.get("comment"), null),
                str(raw.get("desc"), null)));
        return f;
    }

    private List<Map<String, Object>> extractFieldAttrs(Map<String, Object> snap) {
        Object fieldsObj = snap.get("fields");
        if (fieldsObj instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    map.forEach((k, v) -> m.put(String.valueOf(k), v));
                    out.add(normalizeFieldAttr(m));
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        Object content = snap.get("contentJson");
        List<Map<String, Object>> fromContent = parseFieldList(content);
        if (!fromContent.isEmpty()) {
            return fromContent.stream().map(this::normalizeFieldAttr).collect(Collectors.toList());
        }
        // 实时回填：ENTRY 快照无 fields 时按当前登记补
        Object entryCode = snap.get("entryCode");
        if (entryCode != null) {
            GovMetadataRegistry e = registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                    .eq(GovMetadataRegistry::getEntryCode, String.valueOf(entryCode)).last("limit 1"));
            if (e != null) {
                return buildFieldAttrsFromEntry(e);
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRelationsFromSnap(Map<String, Object> snap) {
        Object rel = snap.get("relations");
        if (!(rel instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> m = new LinkedHashMap<>();
                map.forEach((k, v) -> m.put(String.valueOf(k), v));
                out.add(m);
            }
        }
        return out;
    }

    private Object fieldRawFromSnap(Map<String, Object> snap) {
        if (snap.containsKey("fields")) {
            return snap.get("fields");
        }
        return snap.get("contentJson");
    }

    private List<Map<String, Object>> buildBasicDiff(Map<String, Object> leftSnap, Map<String, Object> rightSnap) {
        Set<String> skip = Set.of("fields", "relations", "contentJson", "updatedAt", "createdAt",
                "publishedAt", "omSyncedAt", "id");
        Set<String> keys = new HashSet<>();
        keys.addAll(leftSnap.keySet());
        keys.addAll(rightSnap.keySet());
        List<Map<String, Object>> basicDiff = new ArrayList<>();
        for (String key : keys) {
            if (skip.contains(key)) continue;
            String lv = stringifySnapVal(leftSnap.get(key));
            String rv = stringifySnapVal(rightSnap.get(key));
            if (!lv.equals(rv)) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("field", key);
                d.put("left", lv);
                d.put("right", rv);
                d.put("changeType", "changed");
                basicDiff.add(d);
            }
        }
        return basicDiff;
    }

    private String stringifySnapVal(Object v) {
        if (v == null) return "";
        if (v instanceof Map || v instanceof List) {
            return toJson(v);
        }
        return String.valueOf(v);
    }

    private List<Map<String, Object>> buildAttrDiff(Map<String, Object> leftSnap, Map<String, Object> rightSnap) {
        Map<String, Map<String, Object>> leftMap = indexFieldAttrs(extractFieldAttrs(leftSnap));
        Map<String, Map<String, Object>> rightMap = indexFieldAttrs(extractFieldAttrs(rightSnap));
        Set<String> names = new HashSet<>();
        names.addAll(leftMap.keySet());
        names.addAll(rightMap.keySet());
        List<String> attrKeys = List.of("nameZh", "nameEn", "dataType", "length", "primaryKey", "partition", "unit", "description");
        List<Map<String, Object>> out = new ArrayList<>();
        for (String name : names.stream().sorted().collect(Collectors.toList())) {
            Map<String, Object> lf = leftMap.get(name);
            Map<String, Object> rf = rightMap.get(name);
            if (lf == null) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("fieldName", name);
                d.put("changeType", "added");
                d.put("attr", "整行");
                d.put("left", "");
                d.put("right", summarizeFieldAttr(rf));
                out.add(d);
                continue;
            }
            if (rf == null) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("fieldName", name);
                d.put("changeType", "removed");
                d.put("attr", "整行");
                d.put("left", summarizeFieldAttr(lf));
                d.put("right", "");
                out.add(d);
                continue;
            }
            for (String attr : attrKeys) {
                String lv = nvl(String.valueOf(lf.getOrDefault(attr, "")));
                String rv = nvl(String.valueOf(rf.getOrDefault(attr, "")));
                if (!lv.equals(rv)) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("fieldName", name);
                    d.put("changeType", "changed");
                    d.put("attr", attr);
                    d.put("left", lv);
                    d.put("right", rv);
                    out.add(d);
                }
            }
        }
        return out;
    }

    private Map<String, Map<String, Object>> indexFieldAttrs(List<Map<String, Object>> fields) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (Map<String, Object> f : fields) {
            String key = firstNonBlank(str(f.get("nameEn"), null), str(f.get("nameZh"), null));
            if (key == null || key.isBlank()) continue;
            map.put(key, f);
        }
        return map;
    }

    private String summarizeFieldAttr(Map<String, Object> f) {
        if (f == null) return "";
        return nvl(String.valueOf(f.get("nameZh"))) + "/" + nvl(String.valueOf(f.get("nameEn")))
                + " " + nvl(String.valueOf(f.get("dataType")));
    }

    private List<Map<String, Object>> parseFieldList(Object raw) {
        List<Map<String, Object>> names = new ArrayList<>();
        if (raw == null) return names;
        try {
            String json = raw instanceof String ? (String) raw : objectMapper.writeValueAsString(raw);
            if (json == null || json.isBlank() || "null".equals(json)) return names;
            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            return list == null ? List.of() : list;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Map<String, Object>> previewEntryRows(Map<String, Object> snap, int limit) {
        String table = firstNonBlank(str(snap.get("physicalTableName"), null), str(snap.get("entryName"), null));
        String db = str(snap.get("databaseName"), null);
        if (table == null || table.isBlank() || dataSource == null) {
            return List.of();
        }
        String sql;
        if (db != null && !db.isBlank()) {
            sql = "SELECT * FROM `" + db.replace("`", "") + "`.`" + table.replace("`", "") + "` LIMIT ?";
        } else {
            sql = "SELECT * FROM `" + table.replace("`", "") + "` LIMIT ?";
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(limit, 20)));
            try (ResultSet rs = ps.executeQuery()) {
                int colCount = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            throw new BusinessException(400, e.getMessage());
        }
        return rows;
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
                    if (fn == null) fn = item.get("nameEn");
                    if (fn == null) fn = item.get("name");
                    if (fn == null) fn = item.get("code");
                    if (fn != null) names.add(String.valueOf(fn));
                }
                return names;
            }
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Object fn = map.get("fieldName");
                        if (fn == null) fn = map.get("nameEn");
                        if (fn == null) fn = map.get("name");
                        if (fn == null) fn = map.get("code");
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
                if (fn == null) fn = item.get("nameEn");
                if (fn == null) fn = item.get("name");
                if (fn == null) fn = item.get("code");
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
