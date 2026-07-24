package com.chengde.smartcity.masterdata.service;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.support.DataLayerSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 治理落层建表与输出表样例预览：ODS 治理写入 DWD/DWS/ADS 时，平台主动按源表结构创建目标表（Carte 不负责 DDL）。
 */
@Service
public class GovernanceLayerTableService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceLayerTableService.class);
    private static final ObjectMapper OM = new ObjectMapper();
    private static final int DEFAULT_PREVIEW_LIMIT = 100;
    private static final int MAX_PREVIEW_LIMIT = 500;

    private final DataSource platformDataSource;

    public GovernanceLayerTableService(DataSource platformDataSource) {
        this.platformDataSource = platformDataSource;
    }

    /**
     * 扫描画布 OUTPUT，对尚不存在的平台分层目标表执行 CREATE TABLE IF NOT EXISTS … LIKE 源表。
     *
     * @return 本次新建的表限定名列表
     */
    public List<String> ensureOutputTables(String graphJson) {
        List<String> created = new ArrayList<>();
        if (graphJson == null || graphJson.isBlank()) {
            return created;
        }
        try {
            JsonNode root = OM.readTree(graphJson);
            JsonNode nodes = root.get("nodes");
            if (nodes == null || !nodes.isArray()) {
                return created;
            }

            String sourceDb = null;
            String sourceTable = null;
            for (JsonNode n : nodes) {
                JsonNode data = n.path("data");
                if (!"INPUT".equals(data.path("nodeType").asText(""))) {
                    continue;
                }
                JsonNode cfg = data.path("config");
                String table = text(cfg, "tableName");
                if (table == null || table.isBlank()) {
                    continue;
                }
                sourceTable = sanitizeIdent(table);
                sourceDb = resolveDb(text(cfg, "connection"), sourceTable);
                break;
            }
            if (sourceTable == null || sourceDb == null) {
                log.info("governance ensure tables skipped: no INPUT table");
                return created;
            }

            List<Map<String, String>> targets = resolveOutputTargets(nodes);
            if (targets.isEmpty()) {
                return created;
            }

            try (Connection conn = platformDataSource.getConnection(); Statement st = conn.createStatement()) {
                if (!tableExists(conn, sourceDb, sourceTable)) {
                    throw new IllegalStateException("源表不存在: " + DataLayerSupport.qualify(sourceDb, sourceTable)
                            + "，无法按源结构创建目标表");
                }
                String sourceQualified = DataLayerSupport.qualify(sourceDb, sourceTable);

                for (Map<String, String> target : targets) {
                    String targetDb = target.get("database");
                    String targetTable = target.get("table");
                    st.execute("CREATE DATABASE IF NOT EXISTS `" + targetDb + "`");
                    if (tableExists(conn, targetDb, targetTable)) {
                        continue;
                    }
                    String targetQualified = DataLayerSupport.qualify(targetDb, targetTable);
                    String ddl = "CREATE TABLE IF NOT EXISTS " + targetQualified
                            + " LIKE " + sourceQualified;
                    st.execute(ddl);
                    created.add(targetQualified);
                    log.info("governance auto-created target table: {} LIKE {}", targetQualified, sourceQualified);
                }
            }
            return created;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("自动创建目标表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按画布输出节点预览平台分层目标表样例行。
     */
    public Map<String, Object> previewFromGraph(String graphJson, String preferredTable, Integer limit) {
        int lim = normalizeLimit(limit);
        List<Map<String, String>> targets;
        try {
            if (graphJson == null || graphJson.isBlank()) {
                throw new BusinessException(400, "任务尚未配置画布，无输出表可预览");
            }
            JsonNode root = OM.readTree(graphJson);
            JsonNode nodes = root.get("nodes");
            if (nodes == null || !nodes.isArray()) {
                throw new BusinessException(400, "任务尚未配置输出表");
            }
            targets = resolveOutputTargets(nodes);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "解析任务画布失败: " + e.getMessage());
        }
        if (targets.isEmpty()) {
            throw new BusinessException(400, "任务尚未配置平台分层输出表");
        }

        Map<String, String> selected = pickTarget(targets, preferredTable);
        String database = selected.get("database");
        String table = selected.get("table");
        String layer = selected.get("layer");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("targets", targets);
        out.put("database", database);
        out.put("table", table);
        out.put("layer", layer);
        out.put("qualifiedName", DataLayerSupport.qualify(database, table));
        out.put("limit", lim);

        try (Connection conn = platformDataSource.getConnection()) {
            if (!tableExists(conn, database, table)) {
                out.put("tableExists", false);
                out.put("columns", List.of());
                out.put("rows", List.of());
                out.put("rowCount", 0);
                out.put("message", "目标表尚未创建或未写入，请先成功运行治理任务");
                return out;
            }
            String sql = "SELECT * FROM " + DataLayerSupport.qualify(database, table) + " LIMIT " + lim;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                List<String> columns = new ArrayList<>();
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                for (int i = 1; i <= cols; i++) {
                    columns.add(meta.getColumnLabel(i));
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String col : columns) {
                        Object val = rs.getObject(col);
                        row.put(col, val == null ? null : String.valueOf(val));
                    }
                    rows.add(row);
                }
                out.put("tableExists", true);
                out.put("columns", columns);
                out.put("rows", rows);
                out.put("rowCount", rows.size());
                out.put("message", rows.isEmpty() ? "表已存在但暂无数据" : "已加载 " + rows.size() + " 行样例");
                return out;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("preview output failed db={} table={}: {}", database, table, e.getMessage());
            throw new BusinessException(500, "读取治理结果失败: " + e.getMessage());
        }
    }

    private List<Map<String, String>> resolveOutputTargets(JsonNode nodes) {
        List<Map<String, String>> targets = new ArrayList<>();
        for (JsonNode n : nodes) {
            JsonNode data = n.path("data");
            String type = data.path("nodeType").asText("");
            if (!"OUTPUT".equals(type) && !"INSERT_UPDATE".equals(type)) {
                continue;
            }
            JsonNode cfg = data.path("config");
            String table = text(cfg, "table");
            if (table == null || table.isBlank()) {
                table = text(cfg, "outputTable");
            }
            if (table == null || table.isBlank() || "output_table".equals(table)) {
                continue;
            }
            String targetTable = sanitizeIdent(table);
            if (!isSafeIdent(targetTable)) {
                continue;
            }
            String connName = text(cfg, "outputConnection");
            if (connName == null || connName.isBlank()) {
                connName = text(cfg, "connection");
            }
            String targetDb = resolveDb(connName, targetTable);
            if (!DataLayerSupport.isPlatformLayerDb(targetDb) || !isSafeIdent(targetDb)) {
                log.warn("skip non-platform target db={} table={}", targetDb, targetTable);
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("database", targetDb);
            item.put("table", targetTable);
            item.put("layer", DataLayerSupport.layerForDatabase(targetDb));
            targets.add(item);
        }
        return targets;
    }

    private static Map<String, String> pickTarget(List<Map<String, String>> targets, String preferredTable) {
        if (preferredTable != null && !preferredTable.isBlank()) {
            String want = preferredTable.trim();
            for (Map<String, String> t : targets) {
                if (want.equalsIgnoreCase(t.get("table"))
                        || want.equalsIgnoreCase(t.get("database") + "." + t.get("table"))) {
                    return t;
                }
            }
            throw new BusinessException(400, "指定输出表不在任务配置中: " + preferredTable);
        }
        return targets.get(0);
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PREVIEW_LIMIT;
        }
        return Math.min(limit, MAX_PREVIEW_LIMIT);
    }

    private static boolean tableExists(Connection conn, String db, String table) throws Exception {
        try (ResultSet rs = conn.getMetaData().getTables(db, null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static String resolveDb(String connectionName, String tableName) {
        if (connectionName != null && !connectionName.isBlank()) {
            String c = connectionName.trim();
            if (DataLayerSupport.isPlatformLayerDb(c)) {
                return c.toLowerCase(Locale.ROOT);
            }
            if ("PLATFORM".equalsIgnoreCase(c) || "default".equalsIgnoreCase(c)) {
                return DataLayerSupport.databaseForLayer(DataLayerSupport.layerForTableName(tableName));
            }
        }
        return DataLayerSupport.databaseForLayer(DataLayerSupport.layerForTableName(tableName));
    }

    private static String text(JsonNode cfg, String field) {
        if (cfg == null || cfg.isMissingNode() || cfg.isNull() || !cfg.has(field) || cfg.get(field).isNull()) {
            return null;
        }
        String s = cfg.get(field).asText();
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String sanitizeIdent(String name) {
        return name == null ? "" : name.replace("`", "").trim();
    }

    private static boolean isSafeIdent(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c >= 'a' && c <= 'z')
                    && !(c >= 'A' && c <= 'Z')
                    && !(c >= '0' && c <= '9')
                    && c != '_') {
                return false;
            }
        }
        return true;
    }
}
