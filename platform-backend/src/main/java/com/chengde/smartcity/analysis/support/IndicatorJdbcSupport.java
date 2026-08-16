package com.chengde.smartcity.analysis.support;

import com.chengde.smartcity.analysis.entity.AnaIndicator;
import com.chengde.smartcity.common.exception.BusinessException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 指标结果库：建库/建表/补列，任务执行时以全量覆盖方式写入查询结果（TRUNCATE + INSERT）。
 * <ul>
 *   <li>库：{@code CREATE DATABASE IF NOT EXISTS}</li>
 *   <li>表：已存在则跳过建表，仅 {@code ADD COLUMN} 补缺字段；去掉历史多余列 task_id / trigger_type</li>
 *   <li>落数：仅指标任务执行（手动执行或发布后的调度）写入；保存语句不落结果表</li>
 * </ul>
 */
@Component
public class IndicatorJdbcSupport {

    private static final Logger log = LoggerFactory.getLogger(IndicatorJdbcSupport.class);
    private static final Pattern SAFE_IDENT = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    private final IndicatorDatabaseProperties props;

    public IndicatorJdbcSupport(IndicatorDatabaseProperties props) {
        this.props = props;
    }

    public String endpointLabel() {
        return props.getHost() + ":" + (props.getPort() <= 0 ? 3306 : props.getPort());
    }

    /** 无库名连接（用于 CREATE DATABASE）。 */
    public Connection openServer() {
        requireHost();
        String url = "jdbc:mysql://" + props.getHost() + ":" + effectivePort()
                + "/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
        try {
            return DriverManager.getConnection(url, props.getUsername(), nullToEmpty(props.getPassword()));
        } catch (SQLException e) {
            throw new BusinessException(500, "连接指标库主机失败 " + endpointLabel() + " — " + e.getMessage());
        }
    }

    public Connection openDatabase(String database) {
        String db = sanitizeDbName(database);
        requireHost();
        String url = "jdbc:mysql://" + props.getHost() + ":" + effectivePort() + "/" + db
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
        try {
            Connection conn = DriverManager.getConnection(url, props.getUsername(), nullToEmpty(props.getPassword()));
            try {
                conn.setCatalog(db);
            } catch (SQLException e) {
                log.debug("setCatalog {} skipped: {}", db, e.getMessage());
            }
            return conn;
        } catch (SQLException e) {
            throw new BusinessException(500, "连接指标库失败 " + db + "@" + endpointLabel() + " — " + e.getMessage());
        }
    }

    /** 库不存在则创建；已存在则跳过。 */
    public void ensureDatabase(String database) {
        String db = sanitizeDbName(database);
        if (databaseExists(db)) {
            log.debug("指标库已存在，跳过建库 db={} @{}", db, endpointLabel());
            return;
        }
        try (Connection conn = openServer(); Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS `" + db + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            log.info("指标库新建 db={} @{}", db, endpointLabel());
        } catch (SQLException e) {
            throw new BusinessException(500, "创建指标库失败 " + db + " — " + e.getMessage());
        }
    }

    /**
     * 增量就绪结果表：库/表已存在则跳过重建，仅补新增业务字段列。
     * 固定列：id / calc_at。
     */
    public void ensureResultTable(String database, String tableName, List<AnaIndicator> indicators) {
        String db = sanitizeDbName(database);
        String table = sanitizeTableName(tableName);
        ensureDatabase(db);

        LinkedHashMap<String, String> cols = columnDefs(indicators);

        try (Connection conn = openDatabase(db); Statement st = conn.createStatement()) {
            boolean exists = tableExists(conn, db, table);
            if (!exists) {
                st.execute(buildCreateTableDdl(table, cols));
                log.info("指标结果表新建 db={} table={} @{}", db, table, endpointLabel());
            } else {
                log.info("指标结果表已存在，跳过建表 db={} table={}", db, table);
                dropLegacyMetaColumns(conn, st, db, table);
            }
            int added = 0;
            for (Map.Entry<String, String> e : cols.entrySet()) {
                if (!columnExists(conn, db, table, e.getKey())) {
                    st.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + e.getKey() + "` " + e.getValue() + " NULL");
                    added++;
                    log.info("指标结果表补列 db={} table={} col={}", db, table, e.getKey());
                }
            }
            if (added == 0 && exists) {
                log.debug("指标结果表字段无增量 db={} table={}", db, table);
            }
        } catch (SQLException e) {
            throw new BusinessException(500, "准备指标结果表失败 " + db + "." + table + " — " + e.getMessage());
        }
    }

    private void dropLegacyMetaColumns(Connection conn, Statement st, String db, String table) throws SQLException {
        for (String extra : List.of("task_id", "trigger_type")) {
            if (columnExists(conn, db, table, extra)) {
                st.execute("ALTER TABLE `" + table + "` DROP COLUMN `" + extra + "`");
                log.info("指标结果表删除多余列 db={} table={} col={}", db, table, extra);
            }
        }
    }

    private String buildCreateTableDdl(String table, LinkedHashMap<String, String> cols) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS `").append(table).append("` (");
        ddl.append("`id` BIGINT NOT NULL AUTO_INCREMENT,");
        ddl.append("`calc_at` DATETIME NOT NULL,");
        for (Map.Entry<String, String> e : cols.entrySet()) {
            ddl.append("`").append(e.getKey()).append("` ").append(e.getValue()).append(" NULL,");
        }
        ddl.append("PRIMARY KEY (`id`), KEY `idx_calc_at` (`calc_at`)");
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        return ddl.toString();
    }

    /**
     * 全量覆盖写入查询结果：TRUNCATE 后按指标字段映射插入全部行。
     * 预览/执行查询不得调用本方法。
     *
     * @return 写入行数
     */
    public int replaceResultRows(String database, String tableName, List<AnaIndicator> indicators,
                                 List<Map<String, Object>> sourceRows, Long taskId, String triggerType) {
        String db = sanitizeDbName(database);
        String table = sanitizeTableName(tableName);
        LinkedHashMap<String, AnaIndicator> colToInd = new LinkedHashMap<>();
        if (indicators != null) {
            for (AnaIndicator ind : indicators) {
                if (ind == null) continue;
                String name = ind.getFieldName();
                if (name == null || name.isBlank()) {
                    name = ind.getResultField();
                }
                if (name == null || name.isBlank()) continue;
                String col = sanitizeColumn(name);
                if ("id".equals(col) || "calc_at".equals(col) || "task_id".equals(col) || "trigger_type".equals(col)) {
                    continue;
                }
                colToInd.putIfAbsent(col, ind);
            }
        }

        Connection conn = null;
        try {
            conn = openDatabase(db);
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("TRUNCATE TABLE `" + table + "`");
            }
            int written = 0;
            if (sourceRows != null && !sourceRows.isEmpty() && !colToInd.isEmpty()) {
                List<String> cols = new ArrayList<>();
                cols.add("calc_at");
                cols.addAll(colToInd.keySet());
                StringBuilder sql = new StringBuilder("INSERT INTO `").append(table).append("` (");
                for (int i = 0; i < cols.size(); i++) {
                    if (i > 0) sql.append(',');
                    sql.append('`').append(cols.get(i)).append('`');
                }
                sql.append(") VALUES (");
                for (int i = 0; i < cols.size(); i++) {
                    if (i > 0) sql.append(',');
                    sql.append('?');
                }
                sql.append(')');
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    int batch = 0;
                    for (Map<String, Object> row : sourceRows) {
                        ps.setTimestamp(1, now);
                        int idx = 2;
                        for (Map.Entry<String, AnaIndicator> e : colToInd.entrySet()) {
                            AnaIndicator ind = e.getValue();
                            ps.setObject(idx++, pickValue(row, ind.getResultField(), ind.getFieldName()));
                        }
                        ps.addBatch();
                        batch++;
                        written++;
                        if (batch >= 200) {
                            ps.executeBatch();
                            batch = 0;
                        }
                    }
                    if (batch > 0) {
                        ps.executeBatch();
                    }
                }
            }
            conn.commit();
            log.info("指标结果表覆盖写入 db={} table={} rows={} taskId={} trigger={}",
                    db, table, written, taskId, triggerType);
            return written;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                    /* ignore */
                }
            }
            throw new BusinessException(500, "覆盖写入指标结果失败 " + db + "." + table + " — " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                    /* ignore */
                }
                try {
                    conn.close();
                } catch (SQLException ignored) {
                    /* ignore */
                }
            }
        }
    }

    /** 兼容旧调用：先清空再写入一行快照。 */
    public void insertSnapshot(String database, String tableName, Long taskId, String triggerType,
                               Map<String, Object> values) {
        List<AnaIndicator> fake = new ArrayList<>();
        if (values != null) {
            for (String key : values.keySet()) {
                AnaIndicator ind = new AnaIndicator();
                ind.setFieldName(key);
                ind.setResultField(key);
                fake.add(ind);
            }
        }
        List<Map<String, Object>> rows = values == null ? List.of() : List.of(new LinkedHashMap<>(values));
        replaceResultRows(database, tableName, fake, rows, taskId, triggerType);
    }

    private static Object pickValue(Map<String, Object> row, String resultField, String fieldName) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        String key = findKeyIgnoreCase(row, resultField);
        if (key != null) {
            return row.get(key);
        }
        key = findKeyIgnoreCase(row, fieldName);
        if (key != null) {
            return row.get(key);
        }
        if (row.size() == 1) {
            return row.values().iterator().next();
        }
        return null;
    }

    private static String findKeyIgnoreCase(Map<String, Object> row, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (String k : row.keySet()) {
            if (k != null && k.equalsIgnoreCase(name.trim())) {
                return k;
            }
        }
        return null;
    }

    public static String sanitizeDbName(String raw) {
        String n = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_IDENT.matcher(n).matches() || n.endsWith("_")) {
            throw new BusinessException(400, "指标域库名不合法（须小写字母开头，仅字母数字下划线，不以 _ 结尾）: " + raw);
        }
        return n;
    }

    public static String sanitizeTableName(String raw) {
        String n = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!n.startsWith("ind_") || !SAFE_IDENT.matcher(n).matches() || n.endsWith("_")) {
            throw new BusinessException(400, "指标组结果表名不合法（须 ind_ 开头）: " + raw);
        }
        return n;
    }

    private static String sanitizeColumn(String raw) {
        String n = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (n.isBlank() || !Character.isLetter(n.charAt(0))) {
            n = "c_" + n;
        }
        if (n.length() > 64) {
            n = n.substring(0, 64);
        }
        return n;
    }

    private LinkedHashMap<String, String> columnDefs(List<AnaIndicator> indicators) {
        LinkedHashMap<String, String> cols = new LinkedHashMap<>();
        if (indicators == null) {
            return cols;
        }
        for (AnaIndicator ind : indicators) {
            if (ind == null) continue;
            String name = ind.getFieldName();
            if (name == null || name.isBlank()) {
                name = ind.getResultField();
            }
            if (name == null || name.isBlank()) continue;
            String col = sanitizeColumn(name);
            if ("id".equals(col) || "calc_at".equals(col) || "task_id".equals(col) || "trigger_type".equals(col)) {
                continue;
            }
            cols.putIfAbsent(col, toMysqlType(ind.getFieldType(), ind.getFieldLength(), ind.getFieldPrecision()));
        }
        return cols;
    }

    private static String toMysqlType(String fieldType, Integer length, Integer precision) {
        String t = fieldType == null ? "" : fieldType.trim().toLowerCase(Locale.ROOT);
        if (t.contains("浮点") || t.contains("小数") || t.contains("decimal") || t.contains("double") || t.contains("float")) {
            int p = precision == null || precision <= 0 ? 4 : Math.min(precision, 10);
            return "DECIMAL(20," + p + ")";
        }
        if (t.contains("整数") || t.contains("数值") || t.contains("int") || t.contains("long") || t.contains("bigint")) {
            return "BIGINT";
        }
        if (t.contains("日期时间") || t.contains("datetime") || t.contains("timestamp")) {
            return "DATETIME";
        }
        if (t.contains("日期") || t.equals("date")) {
            return "DATE";
        }
        int len = length == null || length <= 0 ? 255 : Math.min(Math.max(length, 1), 4000);
        if (len > 1000) {
            return "TEXT";
        }
        return "VARCHAR(" + len + ")";
    }

    private static boolean columnExists(Connection conn, String db, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND COLUMN_NAME=? LIMIT 1")) {
            ps.setString(1, db);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean tableExists(Connection conn, String db, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND TABLE_TYPE='BASE TABLE' LIMIT 1")) {
            ps.setString(1, db);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean databaseExists(String db) {
        try (Connection conn = openServer();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=? LIMIT 1")) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new BusinessException(500, "检查指标库是否存在失败 " + db + " — " + e.getMessage());
        }
    }

    private void requireHost() {
        if (props.getHost() == null || props.getHost().isBlank()) {
            throw new BusinessException(500, "未配置 app.indicator-db.host（指标结果库主机）");
        }
    }

    private int effectivePort() {
        return props.getPort() <= 0 ? 3306 : props.getPort();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
