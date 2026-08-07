package com.chengde.smartcity.masterdata.service;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.support.DataLayerSupport;
import com.chengde.smartcity.masterdata.support.LayerJdbcSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 治理落层建表与输出表样例预览：ODS 治理写入 DWD/DWS/ADS 时，平台主动按源表结构创建目标表（Carte 不负责 DDL）。
 * 支持同机 LIKE 与跨机（S6/S7）SHOW CREATE 拷贝。
 */
@Service
public class GovernanceLayerTableService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceLayerTableService.class);
    private static final ObjectMapper OM = new ObjectMapper();
    private static final int DEFAULT_PREVIEW_LIMIT = 100;
    private static final int MAX_PREVIEW_LIMIT = 500;

    private final LayerJdbcSupport layerJdbc;

    public GovernanceLayerTableService(LayerJdbcSupport layerJdbc) {
        this.layerJdbc = layerJdbc;
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

            if (!layerJdbc.tableExists(sourceDb, sourceTable)) {
                throw new IllegalStateException("源表不存在: " + DataLayerSupport.qualify(sourceDb, sourceTable)
                        + "，无法按源结构创建目标表");
            }

            for (Map<String, String> target : targets) {
                String targetDb = target.get("database");
                String targetTable = target.get("table");
                String targetQualified = DataLayerSupport.qualify(targetDb, targetTable);
                if (!layerJdbc.tableExists(targetDb, targetTable)) {
                    layerJdbc.createTableLike(sourceDb, sourceTable, targetDb, targetTable);
                    created.add(targetQualified);
                    log.info("governance auto-created target table: {} from {}.{}",
                            targetQualified, sourceDb, sourceTable);
                }
            }
            ensureMaskResultColumns(nodes, targets);
            return created;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("自动创建目标表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 为 MASK 新列（含 MD5 强制新列、日期类字段强制新列）在目标表补充 VARCHAR 列。
     */
    private void ensureMaskResultColumns(JsonNode nodes, List<Map<String, String>> targets) throws Exception {
        Set<String> newCols = collectMaskNewColumns(nodes);
        if (newCols.isEmpty() || targets.isEmpty()) {
            return;
        }
        for (Map<String, String> target : targets) {
            String db = target.get("database");
            String table = target.get("table");
            if (!isGovernedResultLayer(target.get("layer")) || !layerJdbc.tableExists(db, table)) {
                continue;
            }
            try (Connection conn = layerJdbc.open(db); Statement st = conn.createStatement()) {
                for (String col : newCols) {
                    if (!isSafeIdent(col) || columnExists(conn, db, table, col)) {
                        continue;
                    }
                    String q = DataLayerSupport.qualify(db, table);
                    String ddl = "ALTER TABLE " + q + " ADD COLUMN `" + col + "` VARCHAR(64) NULL";
                    st.execute(ddl);
                    log.info("governance added mask column {}.{} {}", db, table, col);
                }
            }
        }
    }

    private static Set<String> collectMaskNewColumns(JsonNode nodes) {
        Set<String> cols = new LinkedHashSet<>();
        if (nodes == null || !nodes.isArray()) {
            return cols;
        }
        for (JsonNode n : nodes) {
            JsonNode data = n.path("data");
            if (!"MASK".equals(data.path("nodeType").asText(""))) {
                continue;
            }
            JsonNode cfg = data.path("config");
            String maskType = text(cfg, "maskType");
            boolean md5 = maskType != null && "MD5".equalsIgnoreCase(maskType);
            String writeMode = text(cfg, "writeMode");
            boolean newColumn = md5 || "NEW_COLUMN".equalsIgnoreCase(writeMode == null ? "" : writeMode);
            String suffix = text(cfg, "targetSuffix");
            if (suffix == null || suffix.isBlank()) {
                suffix = "_masked";
            }
            JsonNode fields = cfg.get("fields");
            if (fields == null || !fields.isArray()) {
                continue;
            }
            for (JsonNode f : fields) {
                String src = f == null ? "" : f.asText("").trim();
                if (src.isEmpty() || !isSafeIdent(src)) {
                    continue;
                }
                boolean fieldNew = newColumn || looksLikeNonStringField(src);
                if (fieldNew) {
                    String col = sanitizeIdent(src + suffix);
                    if (isSafeIdent(col)) {
                        cols.add(col);
                    }
                }
            }
        }
        return cols;
    }

    private static boolean looksLikeNonStringField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String n = fieldName.trim().toLowerCase(Locale.ROOT);
        if (n.contains("idcard") || n.contains("id_card") || n.contains("phone") || n.contains("mobile")
                || n.contains("email") || n.contains("name") || n.contains("address") || n.contains("code")) {
            return false;
        }
        return n.contains("date") || n.contains("time") || n.endsWith("_at") || n.endsWith("_dt")
                || n.startsWith("amt_") || n.endsWith("_amt") || n.contains("amount")
                || n.contains("count") || n.contains("qty") || n.contains("price")
                || n.startsWith("is_") || n.startsWith("has_");
    }

    private static boolean columnExists(Connection conn, String db, String table, String column) throws Exception {
        try (ResultSet rs = conn.getMetaData().getColumns(db, null, table, column)) {
            return rs.next();
        }
    }

    /**
     * 预览治理后落层结果：仅读取输出节点目标表（DWD/DWS/ADS），不读 ODS/源表原始数据。
     */
    public Map<String, Object> previewFromGraph(String graphJson, String preferredTable, Integer limit) {
        int lim = normalizeLimit(limit);
        List<Map<String, String>> allTargets;
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
            allTargets = resolveOutputTargets(nodes);
            targets = allTargets.stream()
                    .filter(t -> isGovernedResultLayer(t.get("layer")))
                    .toList();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "解析任务画布失败: " + e.getMessage());
        }
        if (allTargets.isEmpty()) {
            throw new BusinessException(400, "任务尚未配置平台分层输出表");
        }
        if (targets.isEmpty()) {
            throw new BusinessException(400,
                    "「查看数据」仅展示治理后写入 DWD/DWS/ADS 的结果。"
                            + "当前输出落在 ODS/其它库，请将输出节点改为 DWD（或 DWS/ADS）后再查看");
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
        out.put("previewKind", "GOVERNED_RESULT");

        try (Connection conn = layerJdbc.open(database)) {
            if (!LayerJdbcSupport.tableExists(conn, database, table)) {
                out.put("tableExists", false);
                out.put("columns", List.of());
                out.put("rows", List.of());
                out.put("rowCount", 0);
                out.put("message", "治理目标表尚未创建或未写入，请先成功运行任务后再查看 "
                        + DataLayerSupport.qualify(database, table));
                return out;
            }
            String sql = "SELECT * FROM `" + table + "` LIMIT " + lim;
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
                if (rows.isEmpty()) {
                    out.put("message", "治理目标表 " + DataLayerSupport.qualify(database, table)
                            + " 已存在但暂无数据，请确认任务已成功写入");
                } else {
                    out.put("message", "已加载治理后 " + layer + " 层样例 "
                            + rows.size() + " 行（" + DataLayerSupport.qualify(database, table) + "）");
                }
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

    private static boolean isGovernedResultLayer(String layer) {
        if (layer == null || layer.isBlank()) {
            return false;
        }
        String u = layer.trim().toUpperCase(Locale.ROOT);
        return "DWD".equals(u) || "DWS".equals(u) || "ADS".equals(u);
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
            throw new BusinessException(400, "指定输出表不在治理结果层（DWD/DWS/ADS）中: " + preferredTable);
        }
        for (String prefer : List.of("DWD", "DWS", "ADS")) {
            for (Map<String, String> t : targets) {
                if (prefer.equalsIgnoreCase(t.get("layer"))) {
                    return t;
                }
            }
        }
        return targets.get(0);
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PREVIEW_LIMIT;
        }
        return Math.min(limit, MAX_PREVIEW_LIMIT);
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
