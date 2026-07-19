package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngAssetChangeLog;
import com.chengde.smartcity.exchange.entity.IngAssetMetricDaily;
import com.chengde.smartcity.exchange.entity.IngDataColumn;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.entity.IngLineageEdge;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.entity.IngTablePartition;
import com.chengde.smartcity.exchange.mapper.IngAssetChangeLogMapper;
import com.chengde.smartcity.exchange.mapper.IngAssetMetricDailyMapper;
import com.chengde.smartcity.exchange.mapper.IngDataColumnMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.exchange.mapper.IngLineageEdgeMapper;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.exchange.mapper.IngTablePartitionMapper;
import com.chengde.smartcity.masterdata.entity.GovFusionScript;
import com.chengde.smartcity.masterdata.entity.GovFusionScriptRun;
import com.chengde.smartcity.masterdata.entity.GovFusionScriptVersion;
import com.chengde.smartcity.masterdata.entity.GovGovernanceNodeLog;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTask;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTaskRun;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskDetail;
import com.chengde.smartcity.masterdata.entity.GovTaskScriptRel;
import com.chengde.smartcity.masterdata.mapper.GovFusionScriptMapper;
import com.chengde.smartcity.masterdata.mapper.GovFusionScriptRunMapper;
import com.chengde.smartcity.masterdata.mapper.GovFusionScriptVersionMapper;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceNodeLogMapper;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskRunMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskDetailMapper;
import com.chengde.smartcity.masterdata.mapper.GovTaskScriptRelMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AssetReportService {

    private static final Logger log = LoggerFactory.getLogger(AssetReportService.class);
    private static final double BYTES_PER_GB = 1073741824.0;

    private final IngProjectMapper projectMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final IngDataTableMapper tableMapper;
    private final IngDataColumnMapper columnMapper;
    private final IngTablePartitionMapper partitionMapper;
    private final IngAssetMetricDailyMapper metricMapper;
    private final IngAssetChangeLogMapper changeLogMapper;
    private final IngLineageEdgeMapper lineageMapper;
    private final IngIngestTaskMapper ingestTaskMapper;
    private final LineageService lineageService;
    private final GovFusionScriptMapper scriptMapper;
    private final GovFusionScriptVersionMapper scriptVersionMapper;
    private final GovFusionScriptRunMapper scriptRunMapper;
    private final GovGovernanceTaskMapper workflowMapper;
    private final GovGovernanceTaskRunMapper workflowRunMapper;
    private final GovGovernanceNodeLogMapper nodeLogMapper;
    private final GovTaskScriptRelMapper taskScriptRelMapper;
    private final GovQualityTaskDetailMapper qualityDetailMapper;

    public AssetReportService(IngProjectMapper projectMapper,
                              IngDataSourceMapper dataSourceMapper,
                              IngDataTableMapper tableMapper,
                              IngDataColumnMapper columnMapper,
                              IngTablePartitionMapper partitionMapper,
                              IngAssetMetricDailyMapper metricMapper,
                              IngAssetChangeLogMapper changeLogMapper,
                              IngLineageEdgeMapper lineageMapper,
                              IngIngestTaskMapper ingestTaskMapper,
                              LineageService lineageService,
                              GovFusionScriptMapper scriptMapper,
                              GovFusionScriptVersionMapper scriptVersionMapper,
                              GovFusionScriptRunMapper scriptRunMapper,
                              GovGovernanceTaskMapper workflowMapper,
                              GovGovernanceTaskRunMapper workflowRunMapper,
                              GovGovernanceNodeLogMapper nodeLogMapper,
                              GovTaskScriptRelMapper taskScriptRelMapper,
                              GovQualityTaskDetailMapper qualityDetailMapper) {
        this.projectMapper = projectMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.tableMapper = tableMapper;
        this.columnMapper = columnMapper;
        this.partitionMapper = partitionMapper;
        this.metricMapper = metricMapper;
        this.changeLogMapper = changeLogMapper;
        this.lineageMapper = lineageMapper;
        this.ingestTaskMapper = ingestTaskMapper;
        this.lineageService = lineageService;
        this.scriptMapper = scriptMapper;
        this.scriptVersionMapper = scriptVersionMapper;
        this.scriptRunMapper = scriptRunMapper;
        this.workflowMapper = workflowMapper;
        this.workflowRunMapper = workflowRunMapper;
        this.nodeLogMapper = nodeLogMapper;
        this.taskScriptRelMapper = taskScriptRelMapper;
        this.qualityDetailMapper = qualityDetailMapper;
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<IngDataTable> tables = tableMapper.selectList(null);
        List<IngDataSource> sources = dataSourceMapper.selectList(null);
        List<IngProject> projects = projectMapper.selectList(null);
        List<GovFusionScript> scripts = scriptMapper.selectList(null);
        List<GovGovernanceTask> workflows = workflowMapper.selectList(null);

        long storageSum = tables.stream().mapToLong(t -> t.getStorageBytes() == null ? 0L : t.getStorageBytes()).sum();
        long linkedScriptIds = taskScriptRelMapper.selectList(null).stream()
                .map(GovTaskScriptRel::getScriptId).filter(Objects::nonNull).distinct().count();
        long scheduledWorkflows = workflows.stream()
                .filter(w -> w.getScheduleEnabled() != null && w.getScheduleEnabled() == 1).count();

        out.put("projectCount", projects.size());
        out.put("tableCount", tables.size());
        out.put("databaseCount", sources.size());
        out.put("storageGb", roundGb(storageSum));
        out.put("hotTableCount", Math.min(5, tables.size()));
        out.put("auditTableCount", countAuditTables(tables));
        out.put("scriptCount", scripts.size());
        out.put("scriptLinkedWorkflowCount", linkedScriptIds);
        out.put("workflowCount", workflows.size());
        out.put("workflowScheduledCount", scheduledWorkflows);
        out.put("taskCount", ingestTaskMapper.selectCount(null));

        out.put("tableTrend", metricSeries("TABLE_NEW", null, 30));
        out.put("storageTrend", metricSeries("STORAGE_GB", null, 30));
        out.put("topProjects", topProjects(projects, sources, tables));
        out.put("topScriptsByDuration", topScriptsByDuration(scripts));
        out.put("topTablesByStorage", topTablesByStorage(tables));
        out.put("topTasks", topIngestTasks());
        out.put("workflows", workflows.stream().limit(10).map(this::workflowBrief).collect(Collectors.toList()));
        return out;
    }

    public List<Map<String, Object>> projectTables(Long projectId) {
        if (projectId == null) throw new BusinessException(400, "projectId required");
        IngProject project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(404, "项目不存在");
        List<Long> sourceIds = dataSourceMapper.selectList(new LambdaQueryWrapper<IngDataSource>()
                        .eq(IngDataSource::getProjectId, projectId))
                .stream().map(IngDataSource::getId).collect(Collectors.toList());
        if (sourceIds.isEmpty()) return List.of();
        return tableMapper.selectList(new LambdaQueryWrapper<IngDataTable>().in(IngDataTable::getSourceId, sourceIds)
                        .orderByDesc(IngDataTable::getStorageBytes))
                .stream().map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("tableCode", t.getTableCode());
                    m.put("tableName", t.getTableName());
                    m.put("columnCount", t.getColumnCount());
                    m.put("sourceRowCount", t.getSourceRowCount());
                    m.put("storageBytes", t.getStorageBytes());
                    m.put("storageGb", roundGb(t.getStorageBytes()));
                    m.put("status", t.getStatus());
                    return m;
                }).collect(Collectors.toList());
    }

    public Map<String, Object> tableDetail(Long tableId) {
        IngDataTable table = requireTable(tableId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", table.getId());
        out.put("tableCode", table.getTableCode());
        out.put("tableName", table.getTableName());
        out.put("physicalTableName", table.getPhysicalTableName());
        out.put("partitionFlag", table.getPartitionFlag() != null && table.getPartitionFlag() == 1);
        out.put("status", table.getStatus());

        List<IngDataColumn> cols = columnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                .eq(IngDataColumn::getTableId, tableId).orderByAsc(IngDataColumn::getSortOrder));
        out.put("columns", cols.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("columnCode", c.getColumnCode());
            m.put("columnName", c.getColumnName());
            m.put("dataType", c.getDataType());
            m.put("partitionCol", c.getPartitionColFlag() != null && c.getPartitionColFlag() == 1);
            m.put("semanticDesc", c.getSemanticDesc());
            return m;
        }).collect(Collectors.toList()));

        List<IngTablePartition> parts = partitionMapper.selectList(new LambdaQueryWrapper<IngTablePartition>()
                .eq(IngTablePartition::getTableId, tableId).orderByAsc(IngTablePartition::getPartitionName));
        out.put("partitions", parts.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("partitionName", p.getPartitionName());
            m.put("storageBytes", p.getStorageBytes());
            m.put("fileCount", p.getFileCount());
            return m;
        }).collect(Collectors.toList()));

        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("storageBytes", table.getStorageBytes());
        storage.put("storageGb", roundGb(table.getStorageBytes()));
        storage.put("storageBytesToday", table.getStorageBytesToday());
        storage.put("dataChangedAt", table.getDataChangedAt());
        out.put("storage", storage);

        out.put("lineage", lineageService.lineageForTable(table));
        out.put("outputTrend", dualMetricSeries(tableId, 7));
        out.put("changes", recentChanges("TABLE", tableId, 7));
        out.put("ddlSql", table.getDdlSql());
        out.put("advancedSettings", parseJsonSafe(table.getAdvancedSettingsJson()));
        return out;
    }

    public Map<String, Object> scriptDetail(Long scriptId) {
        GovFusionScript script = scriptMapper.selectById(scriptId);
        if (script == null) throw new BusinessException(404, "脚本不存在");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", script.getId());
        out.put("scriptCode", script.getScriptCode());
        out.put("scriptName", script.getScriptName());
        out.put("scriptType", script.getScriptType());
        out.put("publishStatus", script.getPublishStatus());
        out.put("versionNo", script.getVersionNo());
        out.put("status", script.getStatus());
        out.put("createdBy", script.getCreatedBy());

        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<GovFusionScriptRun> runs = scriptRunMapper.selectList(new LambdaQueryWrapper<GovFusionScriptRun>()
                .eq(GovFusionScriptRun::getScriptId, scriptId)
                .ge(GovFusionScriptRun::getStartedAt, since)
                .orderByAsc(GovFusionScriptRun::getStartedAt));
        out.put("runtimeTrend", runs.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("startedAt", r.getStartedAt());
            m.put("durationMs", r.getDurationMs());
            m.put("durationSec", r.getDurationMs() == null ? 0 : r.getDurationMs() / 1000.0);
            m.put("status", r.getStatus());
            return m;
        }).collect(Collectors.toList()));

        String publishedCode = script.getScriptContent();
        if ("PUBLISHED".equalsIgnoreCase(script.getPublishStatus())) {
            GovFusionScriptVersion ver = scriptVersionMapper.selectOne(new LambdaQueryWrapper<GovFusionScriptVersion>()
                    .eq(GovFusionScriptVersion::getScriptId, scriptId)
                    .orderByDesc(GovFusionScriptVersion::getVersionNo)
                    .last("LIMIT 1"));
            if (ver != null && ver.getScriptContent() != null) {
                publishedCode = ver.getScriptContent();
            }
        }
        out.put("publishedCode", publishedCode);

        List<GovFusionScriptVersion> versions = scriptVersionMapper.selectList(new LambdaQueryWrapper<GovFusionScriptVersion>()
                .eq(GovFusionScriptVersion::getScriptId, scriptId)
                .ge(GovFusionScriptVersion::getPublishedAt, since)
                .orderByDesc(GovFusionScriptVersion::getPublishedAt));
        List<Map<String, Object>> changes = new ArrayList<>(recentChanges("SCRIPT", scriptId, 7));
        for (GovFusionScriptVersion v : versions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("changeAt", v.getPublishedAt());
            m.put("changeType", "PUBLISH");
            m.put("summary", v.getChangeSummary() == null ? "发布版本" : v.getChangeSummary());
            m.put("versionLabel", "v" + v.getVersionNo());
            m.put("publishedBy", v.getPublishedBy());
            changes.add(m);
        }
        changes.sort(Comparator.comparing((Map<String, Object> m) -> (LocalDateTime) m.get("changeAt"),
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        out.put("changes", changes);
        return out;
    }

    public Map<String, Object> workflowDetail(Long workflowId) {
        GovGovernanceTask task = workflowMapper.selectById(workflowId);
        if (task == null) throw new BusinessException(404, "工作流不存在");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", task.getId());
        out.put("taskCode", task.getTaskCode());
        out.put("taskName", task.getTaskName());
        out.put("status", task.getStatus());
        out.put("createdBy", task.getCreatedBy());
        out.put("scheduleEnabled", task.getScheduleEnabled() != null && task.getScheduleEnabled() == 1);
        out.put("scheduleCron", task.getScheduleCron());
        out.put("projectName", "平台治理");
        out.put("description", task.getDescription());

        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<GovGovernanceTaskRun> runs = workflowRunMapper.selectList(new LambdaQueryWrapper<GovGovernanceTaskRun>()
                .eq(GovGovernanceTaskRun::getTaskId, workflowId)
                .ge(GovGovernanceTaskRun::getStartedAt, since)
                .orderByDesc(GovGovernanceTaskRun::getStartedAt));

        out.put("runtimeTrend", runs.stream().sorted(Comparator.comparing(GovGovernanceTaskRun::getStartedAt))
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("startedAt", r.getStartedAt());
                    long ms = durationMs(r.getStartedAt(), r.getEndedAt());
                    m.put("durationMs", ms);
                    m.put("durationSec", ms / 1000.0);
                    m.put("status", r.getStatus());
                    return m;
                }).collect(Collectors.toList()));

        out.put("instances", runs.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("startedAt", r.getStartedAt());
            m.put("endedAt", r.getEndedAt());
            m.put("durationMs", durationMs(r.getStartedAt(), r.getEndedAt()));
            m.put("status", r.getStatus());
            m.put("message", r.getMessage());
            m.put("successNodes", r.getSuccessNodes());
            m.put("failedNodes", r.getFailedNodes());
            return m;
        }).collect(Collectors.toList()));

        out.put("changes", recentChanges("WORKFLOW", workflowId, 7));
        return out;
    }

    public Map<String, Object> workflowRunMonitor(Long runId) {
        GovGovernanceTaskRun run = workflowRunMapper.selectById(runId);
        if (run == null) throw new BusinessException(404, "运行实例不存在");
        List<GovGovernanceNodeLog> nodes = nodeLogMapper.selectList(new LambdaQueryWrapper<GovGovernanceNodeLog>()
                .eq(GovGovernanceNodeLog::getRunId, runId)
                .orderByAsc(GovGovernanceNodeLog::getId));
        if (nodes.isEmpty()) {
            // 演示兜底：无节点日志时返回摘要节点
            Map<String, Object> synthetic = new LinkedHashMap<>();
            synthetic.put("nodeId", "summary");
            synthetic.put("nodeName", "运行摘要");
            synthetic.put("nodeType", "SUMMARY");
            synthetic.put("status", run.getStatus());
            synthetic.put("startedAt", run.getStartedAt());
            synthetic.put("endedAt", run.getEndedAt());
            synthetic.put("inputRows", 0);
            synthetic.put("outputRows", run.getRowCount());
            synthetic.put("message", run.getMessage());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("runId", runId);
            out.put("taskId", run.getTaskId());
            out.put("status", run.getStatus());
            out.put("nodes", List.of(synthetic));
            return out;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("taskId", run.getTaskId());
        out.put("status", run.getStatus());
        out.put("nodes", nodes.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeId", n.getNodeId());
            m.put("nodeName", n.getNodeName());
            m.put("nodeType", n.getNodeType());
            m.put("status", n.getStatus());
            m.put("startedAt", n.getStartedAt());
            m.put("endedAt", n.getEndedAt());
            m.put("inputRows", n.getInputRows());
            m.put("outputRows", n.getOutputRows());
            m.put("message", n.getMessage());
            return m;
        }).collect(Collectors.toList()));
        return out;
    }

    private IngDataTable requireTable(Long id) {
        if (id == null) throw new BusinessException(400, "tableId required");
        IngDataTable t = tableMapper.selectById(id);
        if (t == null) throw new BusinessException(404, "表不存在");
        return t;
    }

    private int countAuditTables(List<IngDataTable> tables) {
        Set<String> names = tables.stream()
                .flatMap(t -> List.of(safe(t.getTableName()), safe(t.getTableCode()), safe(t.getPhysicalTableName())).stream())
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<GovQualityTaskDetail> details = qualityDetailMapper.selectList(new LambdaQueryWrapper<GovQualityTaskDetail>()
                .isNotNull(GovQualityTaskDetail::getTargetTable));
        Set<String> hit = new HashSet<>();
        for (GovQualityTaskDetail d : details) {
            String tt = safe(d.getTargetTable()).toLowerCase(Locale.ROOT);
            if (tt.isEmpty()) continue;
            if (names.contains(tt)) hit.add(tt);
            for (String n : names) {
                if (n.contains(tt) || tt.contains(n)) hit.add(n);
            }
        }
        return hit.size();
    }

    private List<Map<String, Object>> topProjects(List<IngProject> projects, List<IngDataSource> sources, List<IngDataTable> tables) {
        Map<Long, Long> sourceToProject = sources.stream()
                .filter(s -> s.getProjectId() != null)
                .collect(Collectors.toMap(IngDataSource::getId, IngDataSource::getProjectId, (a, b) -> a));
        Map<Long, Long> projectStorage = new HashMap<>();
        Map<Long, Integer> projectTableCount = new HashMap<>();
        for (IngDataTable t : tables) {
            Long pid = sourceToProject.get(t.getSourceId());
            if (pid == null) continue;
            long bytes = t.getStorageBytes() == null
                    ? (t.getSourceRowCount() == null ? 0L : t.getSourceRowCount())
                    : t.getStorageBytes();
            projectStorage.merge(pid, bytes, Long::sum);
            projectTableCount.merge(pid, 1, Integer::sum);
        }
        return projects.stream()
                .sorted(Comparator.comparing((IngProject p) -> projectStorage.getOrDefault(p.getId(), 0L)).reversed())
                .limit(5)
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("projectId", p.getId());
                    m.put("projectName", p.getProjectName());
                    m.put("tableCount", projectTableCount.getOrDefault(p.getId(), 0));
                    m.put("storageBytes", projectStorage.getOrDefault(p.getId(), 0L));
                    m.put("storageGb", roundGb(projectStorage.getOrDefault(p.getId(), 0L)));
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> topScriptsByDuration(List<GovFusionScript> scripts) {
        Map<Long, String> nameById = scripts.stream()
                .collect(Collectors.toMap(GovFusionScript::getId, GovFusionScript::getScriptName, (a, b) -> a));
        List<GovFusionScriptRun> runs = scriptRunMapper.selectList(new LambdaQueryWrapper<GovFusionScriptRun>()
                .orderByDesc(GovFusionScriptRun::getDurationMs).last("LIMIT 50"));
        Map<Long, GovFusionScriptRun> best = new LinkedHashMap<>();
        for (GovFusionScriptRun r : runs) {
            best.merge(r.getScriptId(), r, (a, b) ->
                    (a.getDurationMs() == null ? 0 : a.getDurationMs()) >= (b.getDurationMs() == null ? 0 : b.getDurationMs()) ? a : b);
        }
        return best.values().stream()
                .sorted(Comparator.comparing((GovFusionScriptRun r) -> r.getDurationMs() == null ? 0L : r.getDurationMs()).reversed())
                .limit(5)
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("scriptId", r.getScriptId());
                    m.put("scriptName", nameById.getOrDefault(r.getScriptId(), "脚本#" + r.getScriptId()));
                    m.put("durationMs", r.getDurationMs());
                    m.put("durationSec", r.getDurationMs() == null ? 0 : r.getDurationMs() / 1000.0);
                    m.put("status", r.getStatus());
                    m.put("startedAt", r.getStartedAt());
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> topTablesByStorage(List<IngDataTable> tables) {
        return tables.stream()
                .sorted(Comparator.comparing((IngDataTable t) -> t.getStorageBytes() == null ? 0L : t.getStorageBytes()).reversed())
                .limit(5)
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("tableId", t.getId());
                    m.put("tableName", t.getTableName());
                    m.put("tableCode", t.getTableCode());
                    m.put("storageBytes", t.getStorageBytes());
                    m.put("storageGb", roundGb(t.getStorageBytes()));
                    m.put("columnCount", t.getColumnCount());
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> topIngestTasks() {
        return ingestTaskMapper.selectList(new LambdaQueryWrapper<IngIngestTask>().last("LIMIT 5")).stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("taskName", t.getTaskName());
                    m.put("status", t.getStatus());
                    return m;
                }).collect(Collectors.toList());
    }

    private Map<String, Object> workflowBrief(GovGovernanceTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("taskName", t.getTaskName());
        m.put("status", t.getStatus());
        m.put("scheduleEnabled", t.getScheduleEnabled() != null && t.getScheduleEnabled() == 1);
        return m;
    }

    private List<Map<String, Object>> metricSeries(String type, Long refId, int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        LambdaQueryWrapper<IngAssetMetricDaily> q = new LambdaQueryWrapper<IngAssetMetricDaily>()
                .eq(IngAssetMetricDaily::getMetricType, type)
                .ge(IngAssetMetricDaily::getMetricDate, from)
                .orderByAsc(IngAssetMetricDaily::getMetricDate);
        if (refId == null) {
            q.isNull(IngAssetMetricDaily::getRefId);
        } else {
            q.eq(IngAssetMetricDaily::getRefId, refId);
        }
        return metricMapper.selectList(q).stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", m.getMetricDate() == null ? null : m.getMetricDate().toString());
            row.put("month", m.getMetricDate() == null ? "" : String.format("%02d月%02d", m.getMetricDate().getMonthValue(), m.getMetricDate().getDayOfMonth()));
            row.put("value", m.getValueNum() == null ? 0 : m.getValueNum().doubleValue());
            row.put("count", m.getValueNum() == null ? 0 : m.getValueNum().intValue());
            row.put("gb", m.getValueNum() == null ? 0 : m.getValueNum().doubleValue());
            return row;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> dualMetricSeries(Long tableId, int days) {
        List<Map<String, Object>> growth = metricSeries("TABLE_GROWTH", tableId, days);
        List<Map<String, Object>> total = metricSeries("TABLE_TOTAL", tableId, days);
        Map<String, Double> totalByDate = total.stream()
                .collect(Collectors.toMap(m -> String.valueOf(m.get("date")), m -> ((Number) m.get("value")).doubleValue(), (a, b) -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> g : growth) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", g.get("date"));
            row.put("increment", g.get("value"));
            row.put("total", totalByDate.getOrDefault(String.valueOf(g.get("date")), 0.0));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> recentChanges(String assetType, Long assetId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return changeLogMapper.selectList(new LambdaQueryWrapper<IngAssetChangeLog>()
                        .eq(IngAssetChangeLog::getAssetType, assetType)
                        .eq(IngAssetChangeLog::getAssetId, assetId)
                        .ge(IngAssetChangeLog::getChangeAt, since)
                        .orderByDesc(IngAssetChangeLog::getChangeAt))
                .stream().map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("changeAt", c.getChangeAt());
                    m.put("changeType", c.getChangeType());
                    m.put("summary", c.getSummary());
                    m.put("detailJson", c.getDetailJson());
                    m.put("versionLabel", c.getVersionLabel());
                    return m;
                }).collect(Collectors.toList());
    }

    private static long durationMs(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0L;
        return Math.max(0L, Duration.between(start, end).toMillis());
    }

    private static double roundGb(Long bytes) {
        if (bytes == null || bytes <= 0) return 0.0;
        return BigDecimal.valueOf(bytes / BYTES_PER_GB).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static boolean containsIgnore(String hay, String needle) {
        if (hay == null || needle == null || needle.isEmpty()) return false;
        return hay.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonSafe(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            log.debug("advanced_settings_json parse failed: {}", e.getMessage());
            return Map.of("raw", json);
        }
    }
}
