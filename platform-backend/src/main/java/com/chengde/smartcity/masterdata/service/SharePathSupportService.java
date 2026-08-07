package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.masterdata.support.DataLayerSupport;
import com.chengde.smartcity.masterdata.support.LayerJdbcSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 直通/加工黄金路径共享：候选表资格、统一对象编码、物理表探测。
 */
@Service
public class SharePathSupportService {

    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final IngDataSourceMapper dataSourceMapper;
    private final IngDataTableMapper dataTableMapper;
    private final IngIngestTaskMapper ingestTaskMapper;
    private final LayerJdbcSupport layerJdbc;

    public SharePathSupportService(IngDataSourceMapper dataSourceMapper,
                                   IngDataTableMapper dataTableMapper,
                                   IngIngestTaskMapper ingestTaskMapper,
                                   LayerJdbcSupport layerJdbc) {
        this.dataSourceMapper = dataSourceMapper;
        this.dataTableMapper = dataTableMapper;
        this.ingestTaskMapper = ingestTaskMapper;
        this.layerJdbc = layerJdbc;
    }

    public List<Map<String, Object>> listEligibleTables() {
        List<IngDataTable> tables = dataTableMapper.selectList(new LambdaQueryWrapper<IngDataTable>()
                .eq(IngDataTable::getStatus, "ACTIVE")
                .isNotNull(IngDataTable::getPhysicalTableName)
                .ne(IngDataTable::getPhysicalTableName, "")
                .orderByAsc(IngDataTable::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (IngDataTable table : tables) {
            IngIngestTask task = findSuccessTask(table.getId());
            if (task == null) {
                continue;
            }
            if (!tableExists(null, table.getPhysicalTableName())) {
                continue;
            }
            IngDataSource source = dataSourceMapper.selectById(table.getSourceId());
            if (source == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tableId", table.getId());
            row.put("dataSourceId", source.getId());
            row.put("sourceCode", source.getSourceCode());
            row.put("sourceName", source.getSourceName());
            row.put("tableCode", table.getTableCode());
            row.put("tableName", table.getTableName());
            row.put("physicalTableName", table.getPhysicalTableName());
            row.put("collectStatus", table.getCollectStatus());
            row.put("ingestTaskId", task.getId());
            row.put("ingestTaskStatus", task.getStatus());
            row.put("collectedRows", task.getCollectedRows());
            row.put("physicalRows", countRowsQuiet(null, table.getPhysicalTableName()));
            row.put("entryCode", sourceEntryCode(source, table));
            out.add(row);
        }
        return out;
    }

    public EligibleTable requireEligible(Long tableId) {
        if (tableId == null) {
            throw new BusinessException(400, "tableId 必填");
        }
        IngDataTable table = dataTableMapper.selectById(tableId);
        if (table == null || !"ACTIVE".equalsIgnoreCase(table.getStatus())) {
            throw new BusinessException(404, "登记表不存在或未激活: " + tableId);
        }
        if (table.getPhysicalTableName() == null || table.getPhysicalTableName().isBlank()) {
            throw new BusinessException(409, "登记表缺少物理表映射");
        }
        requireIdentifier(table.getPhysicalTableName(), "physicalTableName");
        IngDataSource source = dataSourceMapper.selectById(table.getSourceId());
        if (source == null) {
            throw new BusinessException(404, "登记数据源不存在");
        }
        IngIngestTask task = findSuccessTask(table.getId());
        if (task == null) {
            throw new BusinessException(409, "表尚未完成资源采集汇聚: " + table.getTableCode());
        }
        if (!tableExists(null, table.getPhysicalTableName())) {
            throw new BusinessException(404, "物理表不存在: " + table.getPhysicalTableName());
        }
        return new EligibleTable(source, table, task);
    }

    public EligibleTable requireEligibleOrDefault(Long tableId, String defaultTableCode) {
        if (tableId != null) {
            return requireEligible(tableId);
        }
        IngDataTable table = dataTableMapper.selectOne(new LambdaQueryWrapper<IngDataTable>()
                .eq(IngDataTable::getTableCode, defaultTableCode)
                .eq(IngDataTable::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (table == null) {
            throw new BusinessException(404, "默认样例表不存在: " + defaultTableCode);
        }
        return requireEligible(table.getId());
    }

    public String sourceEntryCode(IngDataSource source, IngDataTable table) {
        return stableCode("TBL_ING_" + source.getId() + "_" + table.getPhysicalTableName());
    }

    public String sourceEntryCode(EligibleTable sample) {
        return sourceEntryCode(sample.source(), sample.table());
    }

    public String producedEntryCode(String producedTable) {
        return stableCode("TBL_FUS_" + producedTable);
    }

    public String columnEntryCode(String parentEntry, String column) {
        return stableCode("COL_" + parentEntry + "_" + column);
    }

    public String catalogResourceCode(String prefix, EligibleTable sample) {
        return stableCode(prefix + "_" + sample.source().getId() + "_" + sample.table().getPhysicalTableName());
    }

    public String catalogResourceCode(String prefix, String producedTable) {
        return stableCode(prefix + "_" + producedTable);
    }

    public String metaTaskCode(String prefix, EligibleTable sample) {
        return stableCode(prefix + "_" + sample.table().getId());
    }

    public String qualityTaskName(String prefix, EligibleTable sample) {
        return prefix + "-" + sample.table().getTableName();
    }

    public String qualityRuleCode(String prefix, Long tableId, String checkType, String column) {
        return stableCode(prefix + "_" + tableId + "_" + checkType + "_" + (column == null ? "ALL" : column));
    }

    public List<ColumnDef> inspectColumns(String tableName) {
        return inspectColumns(null, tableName);
    }

    public List<ColumnDef> inspectColumns(String database, String tableName) {
        String table = requireIdentifier(tableName, "physicalTableName");
        String db = resolveDatabase(database, tableName);
        List<ColumnDef> columns = new ArrayList<>();
        try (Connection connection = layerJdbc.open(db)) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(db, null, table, new String[]{"TABLE"})) {
                if (!tables.next()) {
                    return columns;
                }
            }
            try (ResultSet rs = metadata.getColumns(db, null, table, null)) {
                while (rs.next()) {
                    columns.add(new ColumnDef(
                            rs.getString("COLUMN_NAME"),
                            rs.getString("TYPE_NAME"),
                            rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                            rs.getInt("ORDINAL_POSITION")));
                }
            }
        } catch (Exception ex) {
            throw new BusinessException(500, "探测表结构失败: " + ex.getMessage());
        }
        return columns;
    }

    public Map<String, Object> sampleMap(EligibleTable sample) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tableId", sample.table().getId());
        out.put("dataSourceId", sample.source().getId());
        out.put("sourceCode", sample.source().getSourceCode());
        out.put("sourceName", sample.source().getSourceName());
        out.put("tableCode", sample.table().getTableCode());
        out.put("tableName", sample.table().getTableName());
        out.put("physicalTableName", sample.table().getPhysicalTableName());
        out.put("registrationStatus", sample.table().getStatus());
        out.put("collectStatus", sample.table().getCollectStatus());
        out.put("ingestTaskId", sample.ingestTask().getId());
        out.put("ingestTaskStatus", sample.ingestTask().getStatus());
        out.put("collectedRows", sample.ingestTask().getCollectedRows());
        out.put("physicalRows", countRows(null, sample.table().getPhysicalTableName()));
        out.put("entryCode", sourceEntryCode(sample));
        out.put("columns", inspectColumns(null, sample.table().getPhysicalTableName()).stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c.name());
            m.put("typeName", c.typeName());
            m.put("nullable", c.nullable());
            return m;
        }).toList());
        return out;
    }

    public long countRows(String tableName) {
        return countRows(null, tableName);
    }

    public long countRows(String database, String tableName) {
        String table = requireIdentifier(tableName, "physicalTableName");
        String db = resolveDatabase(database, tableName);
        String qualified = DataLayerSupport.qualify(db, table);
        try (Connection connection = layerJdbc.open(db);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception ex) {
            throw new BusinessException(500, "读取表失败: " + ex.getMessage());
        }
    }

    public long countRowsQuiet(String tableName) {
        return countRowsQuiet(null, tableName);
    }

    public long countRowsQuiet(String database, String tableName) {
        try {
            return countRows(database, tableName);
        } catch (Exception ex) {
            return -1;
        }
    }

    public boolean tableExists(String tableName) {
        return tableExists(null, tableName);
    }

    public boolean tableExists(String database, String tableName) {
        if (tableName == null || !IDENT.matcher(tableName).matches()) {
            return false;
        }
        String db = resolveDatabase(database, tableName);
        return layerJdbc.tableExists(db, tableName);
    }

    public String requireIdentifier(String value, String field) {
        if (value == null || !IDENT.matcher(value).matches()) {
            throw new BusinessException(400, field + " 非法: " + value);
        }
        return value;
    }

    /** 稳定编码：超长时截断并追加 8 位短哈希，避免静默碰撞。 */
    public String stableCode(String raw) {
        String code = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        if (code.length() <= 64) {
            return code;
        }
        String hash = shortHash(code);
        int keep = 64 - 1 - hash.length();
        return code.substring(0, keep) + "_" + hash;
    }

    private IngIngestTask findSuccessTask(Long tableId) {
        return ingestTaskMapper.selectOne(new LambdaQueryWrapper<IngIngestTask>()
                .eq(IngIngestTask::getTableId, tableId)
                .eq(IngIngestTask::getStatus, "SUCCESS")
                .orderByDesc(IngIngestTask::getId)
                .last("LIMIT 1"));
    }

    private String resolveDatabase(String database, String tableName) {
        if (database != null && !database.isBlank()) {
            return database.trim();
        }
        return DataLayerSupport.databaseForLayer(DataLayerSupport.layerForTableName(tableName));
    }

    private static String shortHash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02X", dig[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode()).toUpperCase(Locale.ROOT);
        }
    }

    public record EligibleTable(IngDataSource source, IngDataTable table, IngIngestTask ingestTask) {
    }

    public record ColumnDef(String name, String typeName, boolean nullable, int ordinal) {
    }
}
