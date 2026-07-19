package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataColumn;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngIngestChannel;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.mapper.IngDataColumnMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestChannelMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.integration.jdbc.JdbcProbeService;
import com.chengde.smartcity.integration.kettle.KettleClient;
import com.chengde.smartcity.integration.kettle.KettleKtrCompiler;
import com.chengde.smartcity.masterdata.service.MetadataSubsystemService;
import com.chengde.smartcity.masterdata.support.DataLayerSupport;
import com.chengde.smartcity.masterdata.service.DsOrchestrationService;
import com.chengde.smartcity.security.UserPrincipal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 真实汇聚：由 Kettle Carte 把已登记源表抽取到平台 ODS 物理表。
 * 平台侧先按登记字段建 ODS DDL，Carte 完成 Table Input -> Table Output 后回读真实行数并回写台账。
 * 任何 Carte 失败/行数不符都置 FAILED 且抛出真实原因，绝不伪造成功。
 */
@Service
public class KettleCollectService {

    private static final Logger log = LoggerFactory.getLogger(KettleCollectService.class);
    private static final int POLL_MAX = 120;
    private static final long POLL_INTERVAL_MS = 1000L;

    private final IngDataSourceMapper dataSourceMapper;
    private final IngDataTableMapper dataTableMapper;
    private final IngDataColumnMapper dataColumnMapper;
    private final IngIngestTaskMapper ingestTaskMapper;
    private final IngIngestChannelMapper channelMapper;
    private final JdbcProbeService jdbcProbeService;
    private final KettleKtrCompiler ktrCompiler;
    private final KettleClient kettleClient;
    private final DataSource platformDataSource;
    private final AuditService auditService;
    private final DsOrchestrationService dsOrchestrationService;
    private final MetadataSubsystemService metadataSubsystemService;

    public KettleCollectService(IngDataSourceMapper dataSourceMapper, IngDataTableMapper dataTableMapper,
                                IngDataColumnMapper dataColumnMapper, IngIngestTaskMapper ingestTaskMapper,
                                IngIngestChannelMapper channelMapper, JdbcProbeService jdbcProbeService,
                                KettleKtrCompiler ktrCompiler, KettleClient kettleClient,
                                DataSource platformDataSource, AuditService auditService,
                                DsOrchestrationService dsOrchestrationService,
                                MetadataSubsystemService metadataSubsystemService) {
        this.dataSourceMapper = dataSourceMapper;
        this.dataTableMapper = dataTableMapper;
        this.dataColumnMapper = dataColumnMapper;
        this.ingestTaskMapper = ingestTaskMapper;
        this.channelMapper = channelMapper;
        this.jdbcProbeService = jdbcProbeService;
        this.ktrCompiler = ktrCompiler;
        this.kettleClient = kettleClient;
        this.platformDataSource = platformDataSource;
        this.auditService = auditService;
        this.dsOrchestrationService = dsOrchestrationService;
        this.metadataSubsystemService = metadataSubsystemService;
    }

    /** 触发一张已登记源表的真实汇聚（ODS 表名按源表规则自动生成）。 */
    public Map<String, Object> collectTable(UserPrincipal operator, Long tableId) {
        return collectTable(operator, tableId, null);
    }

    /**
     * 触发一张已登记源表的真实汇聚。
     * @param preferredOdsTable 通道配置中的目标表；为空则按源表规则自动命名
     */
    public Map<String, Object> collectTable(UserPrincipal operator, Long tableId, String preferredOdsTable) {
        IngDataTable table = dataTableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException(404, "登记表不存在");
        }
        if (table.getSourceTable() == null || table.getSourceTable().isBlank()) {
            throw new BusinessException(400, "该表非 JDBC 探测登记（缺少 source_table），无法真实汇聚");
        }
        List<IngDataColumn> columns = dataColumnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                .eq(IngDataColumn::getTableId, tableId).orderByAsc(IngDataColumn::getSortOrder));
        if (columns.isEmpty()) {
            throw new BusinessException(400, "登记表无字段，无法生成 ODS");
        }
        CollectCopyRequest req = new CollectCopyRequest();
        req.setSourceId(table.getSourceId());
        req.setTableId(tableId);
        req.setPhysicalSourceTable(table.getSourceTable());
        req.setOdsTable(resolveOdsTable(table, preferredOdsTable));
        req.setTruncate(true);
        for (IngDataColumn c : columns) {
            req.getFields().add(new CollectCopyRequest.FieldPair(
                    c.getColumnCode(), c.getColumnCode(), c.getDataType(), c.getLengthVal()));
        }
        return executeCopy(operator, req);
    }

    /**
     * 通用抽数：自定义 SELECT / 字段映射 / 全量重建或追加写入。
     */
    public Map<String, Object> executeCopy(UserPrincipal operator, CollectCopyRequest req) {
        if (req.getSourceId() == null) {
            throw new BusinessException(400, "sourceId required");
        }
        if (req.getOdsTable() == null || req.getOdsTable().isBlank()) {
            throw new BusinessException(400, "odsTable required");
        }
        if (req.getFields() == null || req.getFields().isEmpty()) {
            throw new BusinessException(400, "字段映射为空，无法生成 ODS");
        }
        IngDataSource ds = dataSourceMapper.selectById(req.getSourceId());
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        if (!"OK".equals(ds.getConnStatus())) {
            throw new BusinessException(400, "数据源连接未通过测试，请先测试连接");
        }
        String odsTable = sanitizeOdsName(req.getOdsTable());
        String selectSql = req.getSelectSql();
        if (selectSql == null || selectSql.isBlank()) {
            if (req.getPhysicalSourceTable() == null || req.getPhysicalSourceTable().isBlank()) {
                throw new BusinessException(400, "缺少源表或 SELECT SQL");
            }
            selectSql = buildMappedSelectSql(req.getPhysicalSourceTable(), req.getFields(), null);
        }
        validateSelectSql(selectSql);

        IngIngestTask task = resolveLedgerTask(req, odsTable);
        markRunning(task);

        IngDataTable table = req.getTableId() != null ? dataTableMapper.selectById(req.getTableId()) : null;

        try {
            ensureOdsDdl(odsTable, req.getFields(), req.isTruncate());
        } catch (Exception e) {
            return failTask(task, table, "创建/准备 ODS 表失败: " + rootMsg(e));
        }

        if (!kettleClient.isHealthy()) {
            return failTask(task, table, "Kettle Carte 不可用（请确认 etl profile 已启动且鉴权正确）");
        }

        JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
        KettleKtrCompiler.SourceConn src = new KettleKtrCompiler.SourceConn();
        src.host = conn.host;
        src.port = conn.port;
        src.database = conn.database;
        src.username = conn.username;
        src.password = conn.password;

        String transName = "COLLECT_" + (req.getTableId() != null ? req.getTableId() : "SQL")
                + "_" + System.currentTimeMillis();
        task.setKettleTransName(transName);
        ingestTaskMapper.updateById(task);

        String ktr = ktrCompiler.compileCopy(transName, src, selectSql, odsTable, req.isTruncate());
        Map<String, Object> add = kettleClient.addTrans(transName, ktr);
        if (!"SUCCESS".equals(add.get("status"))) {
            return failTask(task, table, "注册转换失败: " + add.get("message"));
        }

        // 直接经 Carte 启动并轮询（避免 DS 长等待把任务卡在 RUNNING）
        Map<String, Object> start = kettleClient.startTrans(transName, null);
        if (!"SUCCESS".equals(start.get("status"))) {
            kettleClient.removeTrans(transName);
            return failTask(task, table, "启动转换失败: " + start.get("message"));
        }

        Map<String, Object> status = pollUntilDone(transName);
        String finalStatus = String.valueOf(status.get("status"));
        long linesInput = asLong(status.get("linesInput"));
        long linesOutput = asLong(status.get("linesOutput"));
        long linesRejected = asLong(status.get("linesRejected"));
        kettleClient.removeTrans(transName);

        if (!stillRunning(task.getId())) {
            log.warn("任务已被重置，丢弃本次执行结果 taskId={}", task.getId());
            Map<String, Object> aborted = new LinkedHashMap<>();
            aborted.put("taskId", task.getId());
            aborted.put("status", "ABORTED");
            aborted.put("message", "任务已重置，本次执行结果已丢弃");
            return aborted;
        }

        if (!"FINISHED".equals(finalStatus)) {
            task.setLinesInput(linesInput);
            task.setLinesOutput(linesOutput);
            task.setLinesRejected(linesRejected);
            return failTask(task, table, "Carte 执行未成功，状态=" + finalStatus
                    + "（读=" + linesInput + " 写=" + linesOutput + " 拒=" + linesRejected + "）");
        }

        long odsRows;
        try {
            odsRows = countPlatformRows(odsTable);
        } catch (Exception e) {
            return failTask(task, table, "回读 ODS 行数失败: " + rootMsg(e));
        }

        task.setStatus("SUCCESS");
        task.setSourceId(ds.getId());
        if (req.getTableId() != null) {
            task.setTableId(req.getTableId());
        }
        task.setTargetTable(odsTable);
        task.setCollectedRows(odsRows);
        task.setLinesInput(linesInput);
        task.setLinesOutput(linesOutput);
        task.setLinesRejected(linesRejected);
        task.setKettleTransName(transName);
        task.setErrorDetail(null);
        task.setLastRunAt(LocalDateTime.now());
        task.setLastRunMessage("Carte 汇聚成功 rows=" + odsRows + " (in=" + linesInput + ")");
        if (req.getWatermarkAfterSuccess() != null && !req.getWatermarkAfterSuccess().isBlank()) {
            task.setWatermarkValue(req.getWatermarkAfterSuccess());
        }
        ingestTaskMapper.updateById(task);

        if (table != null) {
            table.setCollectStatus("SUCCESS");
            table.setPhysicalTableName(odsTable);
            table.setLastCollectAt(LocalDateTime.now());
            dataTableMapper.updateById(table);
            if (operator != null) {
                try {
                    metadataSubsystemService.registerAfterCollect(operator, table.getId(), odsTable);
                } catch (Exception e) {
                    log.warn("汇聚后元数据登记失败 table={} ods={}: {}", table.getId(), odsTable, e.getMessage());
                }
            }
        }

        if (operator != null) {
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "ING_COLLECT_RUN", "ing_ingest_task", String.valueOf(task.getId()),
                    "collect ok ods=" + odsTable + " rows=" + odsRows);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tableId", req.getTableId());
        out.put("taskId", task.getId());
        out.put("odsTable", odsTable);
        out.put("collectedRows", odsRows);
        out.put("linesInput", linesInput);
        out.put("linesOutput", linesOutput);
        out.put("linesRejected", linesRejected);
        out.put("transName", transName);
        out.put("selectSql", selectSql);
        out.put("status", "SUCCESS");
        return out;
    }

    public String buildMappedSelectSql(String sourceTable, List<CollectCopyRequest.FieldPair> fields, String whereClause) {
        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            CollectCopyRequest.FieldPair f = fields.get(i);
            String src = sanitize(f.getSource());
            String tgt = sanitize(f.getTarget() == null || f.getTarget().isBlank() ? f.getSource() : f.getTarget());
            if (src.equals(tgt)) {
                sb.append('`').append(src).append('`');
            } else {
                sb.append('`').append(src).append("` AS `").append(tgt).append('`');
            }
        }
        sb.append(" FROM `").append(sanitize(sourceTable)).append('`');
        if (whereClause != null && !whereClause.isBlank()) {
            sb.append(" WHERE ").append(whereClause.trim());
        }
        return sb.toString();
    }

    public void validateSelectSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException(400, "SQL 为空");
        }
        String norm = sql.trim().replaceAll("\\s+", " ");
        String upper = norm.toUpperCase();
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            throw new BusinessException(400, "仅允许 SELECT / WITH 查询");
        }
        if (norm.contains(";")) {
            throw new BusinessException(400, "不允许多语句 SQL");
        }
        for (String bad : List.of(" INSERT ", " UPDATE ", " DELETE ", " DROP ", " ALTER ", " TRUNCATE ", " CREATE ")) {
            if ((" " + upper + " ").contains(bad)) {
                throw new BusinessException(400, "SQL 含非法关键字");
            }
        }
    }

    public String sanitizeOdsName(String preferred) {
        String name = sanitize(preferred == null ? "" : preferred.trim());
        if (name.isBlank()) {
            throw new BusinessException(400, "目标表名无效");
        }
        if (!name.toLowerCase().startsWith("ods_")) {
            name = "ods_" + name;
        }
        return name;
    }

    private IngIngestTask resolveLedgerTask(CollectCopyRequest req, String odsTable) {
        if (req.getLedger() != null && req.getLedger().getTaskId() != null) {
            IngIngestTask task = ingestTaskMapper.selectById(req.getLedger().getTaskId());
            if (task == null) {
                throw new BusinessException(404, "汇聚任务不存在");
            }
            task.setTargetTable(odsTable);
            return task;
        }
        if (req.getTableId() != null) {
            IngDataTable table = dataTableMapper.selectById(req.getTableId());
            if (table != null) {
                return ensureTask(table, odsTable, "PENDING");
            }
        }
        IngIngestTask task = new IngIngestTask();
        task.setTaskCode("TASK_ADHOC_" + System.currentTimeMillis());
        task.setTaskName("临时汇聚");
        task.setAccessMode("SINGLE");
        task.setWriteMode(req.isTruncate() ? "FULL" : "INCREMENTAL");
        task.setEnabled(0);
        task.setSourceId(req.getSourceId());
        task.setTableId(req.getTableId());
        task.setTargetTable(odsTable);
        task.setScheduleCron("");
        task.setStatus("PENDING");
        IngIngestChannel channel = channelMapper.selectOne(new LambdaQueryWrapper<IngIngestChannel>()
                .eq(IngIngestChannel::getChannelType, "TABLE").last("LIMIT 1"));
        task.setChannelId(channel == null ? null : channel.getId());
        ingestTaskMapper.insert(task);
        return task;
    }

    private void ensureOdsDdl(String odsTable, List<CollectCopyRequest.FieldPair> fields, boolean recreate) throws Exception {
        String odsDb = DataLayerSupport.ODS;
        String qualified = DataLayerSupport.qualify(odsDb, odsTable);
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(qualified).append(" (\n");
        for (int i = 0; i < fields.size(); i++) {
            CollectCopyRequest.FieldPair f = fields.get(i);
            String col = sanitize(f.getTarget() == null || f.getTarget().isBlank() ? f.getSource() : f.getTarget());
            ddl.append("  `").append(col).append("` ").append(mapType(f.getDataType(), f.getLength()));
            if (i < fields.size() - 1) {
                ddl.append(',');
            }
            ddl.append('\n');
        }
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='真实汇聚 ODS 落地表'");
        try (Connection conn = platformDataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS `" + odsDb + "`");
            if (recreate) {
                st.execute("DROP TABLE IF EXISTS " + qualified);
                st.execute(ddl.toString());
            } else {
                // 追加模式：表不存在则建；已存在则保留
                st.execute(ddl.toString().replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS "));
            }
        }
    }

    private Map<String, Object> failTask(IngIngestTask task, IngDataTable table, String reason) {
        log.warn("汇聚失败 task={} reason={}", task.getId(), reason);
        task.setStatus("FAILED");
        task.setErrorDetail(reason.length() > 1000 ? reason.substring(0, 1000) : reason);
        task.setLastRunAt(LocalDateTime.now());
        task.setLastRunMessage("汇聚失败");
        ingestTaskMapper.updateById(task);
        if (table != null) {
            table.setCollectStatus("FAILED");
            dataTableMapper.updateById(table);
        }
        throw new BusinessException(502, reason);
    }

    private Map<String, Object> pollUntilDone(String transName) {
        Map<String, Object> status = Map.of("status", "UNKNOWN");
        for (int i = 0; i < POLL_MAX; i++) {
            status = kettleClient.getTransStatus(transName);
            String s = String.valueOf(status.get("status"));
            if ("FINISHED".equals(s) || "FAILED".equals(s) || "STOPPED".equals(s)) {
                return status;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return status;
    }

    private IngIngestTask ensureTask(IngDataTable table, String odsTable, String transName) {
        IngIngestTask task = ingestTaskMapper.selectOne(new LambdaQueryWrapper<IngIngestTask>()
                .eq(IngIngestTask::getTableId, table.getId()).last("LIMIT 1"));
        if (task != null) {
            task.setKettleTransName(transName);
            return task;
        }
        task = new IngIngestTask();
        task.setTaskCode("TASK_COLLECT_" + table.getId() + "_" + System.currentTimeMillis());
        task.setTaskName(table.getTableName() + " 汇聚");
        IngIngestChannel channel = channelMapper.selectOne(new LambdaQueryWrapper<IngIngestChannel>()
                .eq(IngIngestChannel::getChannelType, "TABLE").last("LIMIT 1"));
        if (channel == null) {
            channel = channelMapper.selectOne(new LambdaQueryWrapper<IngIngestChannel>().last("LIMIT 1"));
        }
        task.setChannelId(channel == null ? null : channel.getId());
        task.setAccessMode("SINGLE");
        task.setWriteMode("FULL");
        task.setEnabled(0);
        task.setSourceId(table.getSourceId());
        task.setTableId(table.getId());
        task.setTargetTable(odsTable);
        task.setScheduleCron("");
        task.setStatus("PENDING");
        task.setKettleTransName(transName);
        ingestTaskMapper.insert(task);
        return task;
    }

    private void markRunning(IngIngestTask task) {
        task.setStatus("RUNNING");
        task.setLastRunAt(LocalDateTime.now());
        task.setLastRunMessage("Carte 汇聚执行中");
        task.setErrorDetail(null);
        ingestTaskMapper.updateById(task);
    }

    private boolean stillRunning(Long taskId) {
        if (taskId == null) {
            return true;
        }
        IngIngestTask latest = ingestTaskMapper.selectById(taskId);
        return latest != null && "RUNNING".equals(latest.getStatus());
    }

    private Map<String, Object> fail(IngIngestTask task, IngDataTable table, String reason) {
        log.warn("汇聚失败 table={} reason={}", table.getId(), reason);
        task.setStatus("FAILED");
        task.setErrorDetail(reason.length() > 1000 ? reason.substring(0, 1000) : reason);
        task.setLastRunAt(LocalDateTime.now());
        task.setLastRunMessage("汇聚失败");
        ingestTaskMapper.updateById(task);
        table.setCollectStatus("FAILED");
        dataTableMapper.updateById(table);
        throw new BusinessException(502, reason);
    }

    private void createOdsDdl(String odsTable, List<IngDataColumn> columns) throws Exception {
        String odsDb = DataLayerSupport.ODS;
        String qualified = DataLayerSupport.qualify(odsDb, odsTable);
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(qualified).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            IngDataColumn c = columns.get(i);
            ddl.append("  `").append(c.getColumnCode()).append("` ")
                    .append(mapType(c.getDataType(), c.getLengthVal()));
            if (i < columns.size() - 1) {
                ddl.append(',');
            }
            ddl.append('\n');
        }
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='真实汇聚 ODS 落地表'");
        try (Connection conn = platformDataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS `" + odsDb + "`");
            st.execute("DROP TABLE IF EXISTS " + qualified);
            st.execute(ddl.toString());
        }
    }

    private String buildSelectSql(String sourceTable, List<IngDataColumn> columns) {
        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('`').append(columns.get(i).getColumnCode()).append('`');
        }
        sb.append(" FROM `").append(sanitize(sourceTable)).append('`');
        return sb.toString();
    }

    private long countPlatformRows(String table) throws Exception {
        String qualified = DataLayerSupport.qualify(DataLayerSupport.ODS, sanitize(table));
        try (Connection conn = platformDataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + qualified)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private String mapType(String dataType, Integer length) {
        String t = dataType == null ? "" : dataType.toUpperCase();
        int len = length == null || length <= 0 ? 255 : Math.min(length, 4000);
        if (t.contains("TEXT") || t.contains("BLOB") || t.contains("CLOB") || t.contains("JSON")) {
            return "TEXT";
        }
        if (t.contains("CHAR")) {
            return "VARCHAR(" + len + ")";
        }
        if (t.contains("BIGINT")) return "BIGINT";
        if (t.contains("TINYINT")) return "TINYINT";
        if (t.contains("SMALLINT")) return "SMALLINT";
        if (t.contains("MEDIUMINT")) return "MEDIUMINT";
        if (t.contains("INT")) return "INT";
        if (t.contains("DECIMAL") || t.contains("NUMERIC") || t.equals("DEC")) return "DECIMAL(18,2)";
        if (t.contains("DOUBLE") || t.contains("FLOAT") || t.contains("REAL")) return "DOUBLE";
        if (t.contains("DATETIME") || t.contains("TIMESTAMP")) return "DATETIME";
        if (t.equals("DATE")) return "DATE";
        if (t.contains("TIME")) return "TIME";
        return "VARCHAR(" + len + ")";
    }

    private String resolveOdsTable(IngDataTable table, String preferredOdsTable) {
        if (preferredOdsTable != null && !preferredOdsTable.isBlank()) {
            String name = sanitize(preferredOdsTable.trim());
            if (name.isBlank()) {
                throw new BusinessException(400, "目标表名无效");
            }
            if (!name.toLowerCase().startsWith("ods_")) {
                name = "ods_" + name;
            }
            return name;
        }
        String sourceTable = table.getSourceTable() == null ? "" : table.getSourceTable();
        String tableCode = table.getTableCode() == null ? "" : table.getTableCode();
        if ("ent_master".equalsIgnoreCase(sourceTable) || "TBL_ENTERPRISE".equalsIgnoreCase(tableCode)) {
            return "ods_enterprise_base";
        }
        if ("proj_construction".equalsIgnoreCase(sourceTable) || "TBL_PROJECT".equalsIgnoreCase(tableCode)) {
            return "ods_project_base";
        }
        String base = sanitize(sourceTable.isBlank() ? tableCode : sourceTable);
        if (base.isBlank()) {
            throw new BusinessException(400, "无法推导 ODS 表名，请填写目标表");
        }
        return "ods_" + base;
    }

    private String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_]", "");
    }

    private long asLong(Object v) {
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String rootMsg(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }
}
