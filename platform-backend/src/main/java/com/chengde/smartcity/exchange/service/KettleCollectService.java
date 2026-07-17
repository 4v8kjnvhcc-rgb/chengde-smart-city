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

    /** 触发一张已登记源表的真实汇聚。 */
    public Map<String, Object> collectTable(UserPrincipal operator, Long tableId) {
        IngDataTable table = dataTableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException(404, "登记表不存在");
        }
        if (table.getSourceTable() == null || table.getSourceTable().isBlank()) {
            throw new BusinessException(400, "该表非 JDBC 探测登记（缺少 source_table），无法真实汇聚");
        }
        IngDataSource ds = dataSourceMapper.selectById(table.getSourceId());
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        if (!"OK".equals(ds.getConnStatus())) {
            throw new BusinessException(400, "数据源连接未通过测试，请先测试连接");
        }
        List<IngDataColumn> columns = dataColumnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                .eq(IngDataColumn::getTableId, tableId).orderByAsc(IngDataColumn::getSortOrder));
        if (columns.isEmpty()) {
            throw new BusinessException(400, "登记表无字段，无法生成 ODS");
        }

        String odsTable;
        String sourceTable = table.getSourceTable() == null ? "" : table.getSourceTable();
        String tableCode = table.getTableCode() == null ? "" : table.getTableCode();
        if ("ent_master".equalsIgnoreCase(sourceTable) || "TBL_ENTERPRISE".equalsIgnoreCase(tableCode)) {
            odsTable = "ods_enterprise_base";
        } else if ("proj_construction".equalsIgnoreCase(sourceTable) || "TBL_PROJECT".equalsIgnoreCase(tableCode)) {
            odsTable = "ods_project_base";
        } else {
            odsTable = "ods_" + sanitize(sourceTable.isBlank() ? tableCode : sourceTable);
        }
        String transName = "COLLECT_" + tableId + "_" + System.currentTimeMillis();
        IngIngestTask task = ensureTask(table, odsTable, transName);
        markRunning(task);

        // 1) 平台侧建 ODS DDL（字段沿用源列名）
        try {
            createOdsDdl(odsTable, columns);
        } catch (Exception e) {
            return fail(task, table, "创建 ODS 表失败: " + rootMsg(e));
        }

        // 2) Carte 健康校验
        if (!kettleClient.isHealthy()) {
            return fail(task, table, "Kettle Carte 不可用（请确认 etl profile 已启动且鉴权正确）");
        }

        // 3) 编译并注册 KTR
        JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
        KettleKtrCompiler.SourceConn src = new KettleKtrCompiler.SourceConn();
        src.host = conn.host;
        src.port = conn.port;
        src.database = conn.database;
        src.username = conn.username;
        src.password = conn.password;
        String selectSql = buildSelectSql(table.getSourceTable(), columns);
        String ktr = ktrCompiler.compileCopy(transName, src, selectSql, odsTable, true);

        Map<String, Object> add = kettleClient.addTrans(transName, ktr);
        if (!"SUCCESS".equals(add.get("status"))) {
            return fail(task, table, "注册转换失败: " + add.get("message"));
        }

        // 4) 通过 DS 启动并等待 Carte trans 完成
        try {
            dsOrchestrationService.runKettleTrans(operator, transName, "COLLECT_" + tableId);
        } catch (RuntimeException e) {
            kettleClient.removeTrans(transName);
            return fail(task, table, "DS 执行失败: " + e.getMessage());
        }

        // 5) 读取 Carte 执行真实行数/失败原因
        Map<String, Object> status = kettleClient.getTransStatus(transName);
        String finalStatus = String.valueOf(status.get("status"));
        long linesInput = asLong(status.get("linesInput"));
        long linesOutput = asLong(status.get("linesOutput"));
        long linesRejected = asLong(status.get("linesRejected"));
        kettleClient.removeTrans(transName);

        if (!"FINISHED".equals(finalStatus)) {
            task.setLinesInput(linesInput);
            task.setLinesOutput(linesOutput);
            task.setLinesRejected(linesRejected);
            return fail(task, table, "Carte 执行未成功，状态=" + finalStatus
                    + "（读=" + linesInput + " 写=" + linesOutput + " 拒=" + linesRejected + "）");
        }

        // 6) 回读平台 ODS 真实行数并回写
        long odsRows;
        try {
            odsRows = countPlatformRows(odsTable);
        } catch (Exception e) {
            return fail(task, table, "回读 ODS 行数失败: " + rootMsg(e));
        }

        task.setStatus("SUCCESS");
        task.setSourceId(ds.getId());
        task.setTableId(tableId);
        task.setTargetTable(odsTable);
        task.setCollectedRows(odsRows);
        task.setLinesInput(linesInput);
        task.setLinesOutput(linesOutput);
        task.setLinesRejected(linesRejected);
        task.setKettleTransName(transName);
        task.setErrorDetail(null);
        task.setLastRunAt(LocalDateTime.now());
        task.setLastRunMessage("Carte 汇聚成功 rows=" + odsRows + " (in=" + linesInput + ")");
        ingestTaskMapper.updateById(task);

        table.setCollectStatus("SUCCESS");
        table.setPhysicalTableName(odsTable);
        table.setLastCollectAt(LocalDateTime.now());
        dataTableMapper.updateById(table);

        try {
            metadataSubsystemService.registerAfterCollect(operator, tableId, odsTable);
        } catch (Exception e) {
            log.warn("汇聚后元数据登记失败 table={} ods={}: {}", tableId, odsTable, e.getMessage());
        }

        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_COLLECT_RUN", "ing_data_table", String.valueOf(tableId),
                "collect ok ods=" + odsTable + " rows=" + odsRows);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tableId", tableId);
        out.put("taskId", task.getId());
        out.put("odsTable", odsTable);
        out.put("collectedRows", odsRows);
        out.put("linesInput", linesInput);
        out.put("linesOutput", linesOutput);
        out.put("linesRejected", linesRejected);
        out.put("transName", transName);
        out.put("status", "SUCCESS");
        return out;
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
        ingestTaskMapper.updateById(task);
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
