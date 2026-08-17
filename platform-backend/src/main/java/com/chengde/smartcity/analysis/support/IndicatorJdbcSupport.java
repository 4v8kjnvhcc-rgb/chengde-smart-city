package com.chengde.smartcity.analysis.support;

import com.chengde.smartcity.analysis.entity.IndField;
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
 * 指标结果库：建库/建表/补列，任务执行时写入查询结果。
 * <ul>
 *   <li>库：指标域新增/保存时 {@code CREATE DATABASE IF NOT EXISTS}；任务执行时再幂等校验</li>
 *   <li>表：已存在则跳过建表，仅 {@code ADD COLUMN} 补缺字段；去掉历史多余列 task_id / trigger_type</li>
 *   <li>落数：仅指标任务执行（手动执行或发布后的调度）写入；保存/预览不落结果表</li>
 *   <li>主表只保留本批；若主表已有数据，先追加进 {@code {table}_history}（保留原 calc_at），再写入本批</li>
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
            st.execute("CREATE DATABASE IF NOT EXISTS `" + db + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            log.info("指标库新建 db={} @{}", db, endpointLabel());
        } catch (SQLException e) {
            throw new BusinessException(500, "创建指标库失败 " + db + " — " + e.getMessage());
        }
    }

    /**
     * 增量就绪结果表：库/表已存在则跳过重建，仅补新增业务字段列。
     * 固定列：id / calc_at。
     */
    public void ensureResultTable(String database, String tableName, List<IndField> indicators) {
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
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        return ddl.toString();
    }

    /**
     * 任务执行写入查询结果：主表只留本批。
     * 主表已有行时先 {@code INSERT…SELECT} 追加到 {@code {table}_history}（保留原 calc_at），再删除主表旧行后插入本批。
     * 主表为空（首次落数）只写主表，不建空历史表。
     * 预览/保存不得调用本方法。
     *
     * @return 写入主表行数
     */
    public int replaceResultRows(String database, String tableName, List<IndField> indicators,
                                 List<Map<String, Object>> sourceRows, String taskId, String triggerType) {
        String db = sanitizeDbName(database);
        String table = sanitizeTableName(tableName);
        LinkedHashMap<String, IndField> colToInd = new LinkedHashMap<>();
        if (indicators != null) {
            for (IndField ind : indicators) {
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
            boolean archived = false;
            int archivedRows = 0;
            if (tableHasRows(conn, table)) {
                String history = historyTableName(table);
                ensureHistoryTable(conn, db, table, history);
                conn.setAutoCommit(false);
                archivedRows = archiveMainToHistory(conn, table, history);
                archived = true;
            } else {
                conn.setAutoCommit(false);
            }
            int written = insertLatestRows(conn, table, colToInd, sourceRows);
            conn.commit();
            log.info("指标结果表写入 db={} table={} latestRows={} archived={} histRows={} taskId={} trigger={}",
                    db, table, written, archived, archivedRows, taskId, triggerType);
            return written;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                    /* ignore */
                }
            }
            throw new BusinessException(500, "写入指标结果失败 " + db + "." + table + " — " + e.getMessage());
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

    /** 兼容旧调用：主表只留本批快照，旧行进历史表。 */
    public void insertSnapshot(String database, String tableName, String taskId, String triggerType,
                               Map<String, Object> values) {
        List<IndField> fake = new ArrayList<>();
        if (values != null) {
            for (String key : values.keySet()) {
                IndField ind = new IndField();
                ind.setFieldName(key);
                ind.setResultField(key);
                fake.add(ind);
            }
        }
        List<Map<String, Object>> rows = values == null ? List.of() : List.of(new LinkedHashMap<>(values));
        replaceResultRows(database, tableName, fake, rows, taskId, triggerType);
    }

    private int insertLatestRows(Connection conn, String table, LinkedHashMap<String, IndField> colToInd,
                                 List<Map<String, Object>> sourceRows) throws SQLException {
        int written = 0;
        if (sourceRows == null || sourceRows.isEmpty() || colToInd.isEmpty()) {
            return 0;
        }
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
                for (Map.Entry<String, IndField> e : colToInd.entrySet()) {
                    IndField ind = e.getValue();
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
        return written;
    }

    private static boolean tableHasRows(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM `" + table + "` LIMIT 1")) {
            return rs.next();
        }
    }

    /** {@code ind_xxx} → {@code ind_xxx_history}，批次用已有 calc_at 区分。 */
    static String historyTableName(String table) {
        String hist = table + "_history";
        if (hist.length() > 64 || !SAFE_IDENT.matcher(hist).matches()) {
            throw new BusinessException(400, "指标历史表名不合法（超过 64 字符或含非法字符）: " + hist);
        }
        return hist;
    }

    private void ensureHistoryTable(Connection conn, String db, String table, String history) throws SQLException {
        try (Statement st = conn.createStatement()) {
            if (!tableExists(conn, db, history)) {
                st.execute("CREATE TABLE `" + history + "` LIKE `" + table + "`");
                log.info("指标历史表新建 db={} table={} like={}", db, history, table);
            }
        }
        alignHistoryColumns(conn, db, table, history);
    }

    private void alignHistoryColumns(Connection conn, String db, String table, String history) throws SQLException {
        List<String[]> missing = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY ORDINAL_POSITION")) {
            ps.setString(1, db);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString(1);
                    if (col == null || "id".equalsIgnoreCase(col)) {
                        continue;
                    }
                    missing.add(new String[] { col, rs.getString(2) });
                }
            }
        }
        try (Statement st = conn.createStatement()) {
            for (String[] item : missing) {
                if (!columnExists(conn, db, history, item[0])) {
                    st.execute("ALTER TABLE `" + history + "` ADD COLUMN `" + item[0] + "` " + item[1] + " NULL");
                    log.info("指标历史表补列 db={} table={} col={}", db, history, item[0]);
                }
            }
        }
    }

    private int archiveMainToHistory(Connection conn, String table, String history) throws SQLException {
        List<String> cols = listColumnsExcludeId(conn, table);
        if (cols.isEmpty()) {
            throw new SQLException("结果表无可用列，无法归档到 " + history);
        }
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) list.append(',');
            list.append('`').append(cols.get(i)).append('`');
        }
        String insertSql = "INSERT INTO `" + history + "` (" + list + ") SELECT " + list + " FROM `" + table + "`";
        int moved;
        try (Statement st = conn.createStatement()) {
            moved = st.executeUpdate(insertSql);
            st.executeUpdate("DELETE FROM `" + table + "`");
        }
        return moved;
    }

    private List<String> listColumnsExcludeId(Connection conn, String table) throws SQLException {
        List<String> cols = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME<>'id' "
                        + "ORDER BY ORDINAL_POSITION")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cols.add(rs.getString(1));
                }
            }
        }
        return cols;
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

    private LinkedHashMap<String, String> columnDefs(List<IndField> indicators) {
        LinkedHashMap<String, String> cols = new LinkedHashMap<>();
        if (indicators == null) {
            return cols;
        }
        for (IndField ind : indicators) {
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
        int len = length == null || length <= 0 ? 100 : Math.min(Math.max(length, 1), 4000);
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

    /**
     * 预览指标结果表数据（只读，最多 limit 行）。
     * 表不存在时返回空列/空行，不抛错（任务尚未执行落数时常见）。
     */
    public Map<String, Object> previewResultRows(String database, String tableName, int limit) {
        String db = sanitizeDbName(database);
        String table = sanitizeTableName(tableName);
        int lim = Math.max(1, Math.min(limit <= 0 ? 200 : limit, 500));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("database", db);
        out.put("tableName", table);
        out.put("columns", List.of());
        out.put("rows", List.of());
        out.put("rowCount", 0);
        out.put("truncated", false);
        out.put("message", "");
        try (Connection conn = openDatabase(db)) {
            if (!tableExists(conn, db, table)) {
                out.put("message", "结果表尚未创建，请先发布指标并执行指标任务");
                return out;
            }
            List<String> columns = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY ORDINAL_POSITION")) {
                ps.setString(1, db);
                ps.setString(2, table);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        columns.add(rs.getString(1));
                    }
                }
            }
            out.put("columns", columns);
            if (columns.isEmpty()) {
                out.put("message", "结果表无列");
                return out;
            }
            String sql = "SELECT * FROM `" + table + "` ORDER BY `id` DESC LIMIT " + lim;
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String col : columns) {
                        Object v = rs.getObject(col);
                        if (v instanceof Timestamp ts) {
                            row.put(col, ts.toLocalDateTime().toString().replace('T', ' '));
                        } else {
                            row.put(col, v);
                        }
                    }
                    rows.add(row);
                }
            }
            out.put("rows", rows);
            out.put("rowCount", rows.size());
            out.put("truncated", rows.size() >= lim);
            return out;
        } catch (SQLException e) {
            throw new BusinessException(500, "读取指标结果表失败 " + db + "." + table + " — " + e.getMessage());
        }
    }
}
