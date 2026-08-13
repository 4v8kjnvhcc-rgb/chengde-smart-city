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
 * 指标结果库：任务执行（手动「执行」/ DS 定时回调）时幂等增量建库/建表/补列并落数。
 * <ul>
 *   <li>库：{@code CREATE DATABASE IF NOT EXISTS}</li>
 *   <li>表：已存在则跳过建表，仅 {@code ADD COLUMN} 补缺字段</li>
 *   <li>字段：仅新增指标定义中尚不存在的列</li>
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
     * 增量就绪结果表：库/表已存在则跳过重建，仅补新增字段列。
     * 固定列：id / calc_at / task_id / trigger_type。
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

    private String buildCreateTableDdl(String table, LinkedHashMap<String, String> cols) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS `").append(table).append("` (");
        ddl.append("`id` BIGINT NOT NULL AUTO_INCREMENT,");
        ddl.append("`calc_at` DATETIME NOT NULL,");
        ddl.append("`task_id` BIGINT NULL,");
        ddl.append("`trigger_type` VARCHAR(32) NULL,");
        for (Map.Entry<String, String> e : cols.entrySet()) {
            ddl.append("`").append(e.getKey()).append("` ").append(e.getValue()).append(" NULL,");
        }
        ddl.append("PRIMARY KEY (`id`), KEY `idx_calc_at` (`calc_at`)");
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        return ddl.toString();
    }

    /** 写入一行计算结果快照。 */
    public void insertSnapshot(String database, String tableName, Long taskId, String triggerType,
                               Map<String, Object> values) {
        String db = sanitizeDbName(database);
        String table = sanitizeTableName(tableName);
        List<String> cols = new ArrayList<>();
        cols.add("calc_at");
        cols.add("task_id");
        cols.add("trigger_type");
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(LocalDateTime.now()));
        params.add(taskId);
        params.add(triggerType == null ? "" : triggerType);

        if (values != null) {
            for (Map.Entry<String, Object> e : values.entrySet()) {
                String col = sanitizeColumn(e.getKey());
                cols.add(col);
                params.add(e.getValue());
            }
        }

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

        try (Connection conn = openDatabase(db);
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new BusinessException(500, "写入指标结果失败 " + db + "." + table + " — " + e.getMessage());
        }
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
