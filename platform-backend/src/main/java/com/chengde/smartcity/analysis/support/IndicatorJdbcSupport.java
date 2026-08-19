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
import java.util.Set;
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
 *   <li>主表只保留本批；若主表已有数据，先追加进 {@code {table}_history}（保留原 update_time），再写入本批</li>
 * </ul>
 */
@Component
public class IndicatorJdbcSupport {

    private static final Logger log = LoggerFactory.getLogger(IndicatorJdbcSupport.class);
    private static final Pattern SAFE_IDENT = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    private static final String UPDATE_TIME_COL = "update_time";
    private static final String LEGACY_CALC_AT = "calc_at";

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
     * 固定列：{@code id}（首列）与 {@code update_time}（末列，注释：更新时间）。
     * 业务列：{@code ind_} + 查询结果英文字段；COMMENT=中文指标名称；类型/长度与字段映射一致。
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
                    // 业务列插在 update_time / calc_at 之前，保持更新时间在末列
                    StringBuilder ddl = new StringBuilder();
                    ddl.append("ALTER TABLE `").append(table).append("` ADD COLUMN `")
                            .append(e.getKey()).append("` ").append(e.getValue()).append(" NULL");
                    String afterCol = null;
                    if (columnExists(conn, db, table, UPDATE_TIME_COL)) {
                        afterCol = lastColumnName(conn, db, table, Set.of(UPDATE_TIME_COL));
                    } else if (columnExists(conn, db, table, LEGACY_CALC_AT)) {
                        afterCol = lastColumnName(conn, db, table, Set.of(LEGACY_CALC_AT));
                    }
                    if (afterCol != null && !afterCol.isBlank()) {
                        ddl.append(" AFTER `").append(afterCol).append('`');
                    }
                    st.execute(ddl.toString());
                    added++;
                    log.info("指标结果表补列 db={} table={} col={}", db, table, e.getKey());
                }
            }
            ensureUpdateTimeColumn(conn, st, db, table);
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

    /**
     * 将历史 {@code calc_at} 迁移为末列 {@code update_time}（注释：更新时间）；新建表已符合则仅校正位置/索引。
     */
    private void ensureUpdateTimeColumn(Connection conn, Statement st, String db, String table) throws SQLException {
        boolean hasCalc = columnExists(conn, db, table, LEGACY_CALC_AT);
        boolean hasUpdate = columnExists(conn, db, table, UPDATE_TIME_COL);

        if (hasCalc && !hasUpdate) {
            String after = lastColumnName(conn, db, table, Set.of(LEGACY_CALC_AT));
            StringBuilder sql = new StringBuilder();
            sql.append("ALTER TABLE `").append(table)
                    .append("` CHANGE COLUMN `").append(LEGACY_CALC_AT).append("` `").append(UPDATE_TIME_COL)
                    .append("` DATETIME NOT NULL COMMENT '更新时间'");
            if (after != null && !after.isBlank()) {
                sql.append(" AFTER `").append(after).append('`');
            }
            st.execute(sql.toString());
            log.info("指标结果表列迁移 db={} table={} {}→{}", db, table, LEGACY_CALC_AT, UPDATE_TIME_COL);
            hasUpdate = true;
            hasCalc = false;
        } else if (!hasUpdate) {
            st.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + UPDATE_TIME_COL
                    + "` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'");
            log.info("指标结果表补列 db={} table={} col={}", db, table, UPDATE_TIME_COL);
            hasUpdate = true;
        }

        if (hasCalc && hasUpdate) {
            st.execute("UPDATE `" + table + "` SET `" + UPDATE_TIME_COL + "` = COALESCE(`"
                    + UPDATE_TIME_COL + "`, `" + LEGACY_CALC_AT + "`)");
            st.execute("ALTER TABLE `" + table + "` DROP COLUMN `" + LEGACY_CALC_AT + "`");
            log.info("指标结果表删除遗留列 db={} table={} col={}", db, table, LEGACY_CALC_AT);
        }

        if (hasUpdate) {
            String after = lastColumnName(conn, db, table, Set.of(UPDATE_TIME_COL));
            StringBuilder mod = new StringBuilder();
            mod.append("ALTER TABLE `").append(table).append("` MODIFY COLUMN `").append(UPDATE_TIME_COL)
                    .append("` DATETIME NOT NULL COMMENT '更新时间'");
            if (after != null && !after.isBlank()) {
                mod.append(" AFTER `").append(after).append('`');
            }
            st.execute(mod.toString());
        }

        dropIndexIfExists(conn, st, db, table, "idx_calc_at");
        if (hasUpdate && !indexExists(conn, db, table, "idx_update_time")) {
            st.execute("ALTER TABLE `" + table + "` ADD INDEX `idx_update_time` (`" + UPDATE_TIME_COL + "`)");
        }
    }

    private String buildCreateTableDdl(String table, LinkedHashMap<String, String> cols) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS `").append(table).append("` (");
        ddl.append("`id` BIGINT NOT NULL AUTO_INCREMENT,");
        for (Map.Entry<String, String> e : cols.entrySet()) {
            ddl.append("`").append(e.getKey()).append("` ").append(e.getValue()).append(" NULL,");
        }
        ddl.append("`").append(UPDATE_TIME_COL).append("` DATETIME NOT NULL COMMENT '更新时间',");
        ddl.append("PRIMARY KEY (`id`), KEY `idx_update_time` (`").append(UPDATE_TIME_COL).append("`)");
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        return ddl.toString();
    }

    /**
     * 任务执行写入查询结果：主表只留本批。
     * 主表已有行时先 {@code INSERT…SELECT} 追加到 {@code {table}_history}（保留原 update_time），再删除主表旧行后插入本批。
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
                String col = resolveResultColumn(ind);
                if (col == null || col.isBlank()) continue;
                if (isReservedResultColumn(col)) {
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
        cols.addAll(colToInd.keySet());
        cols.add(UPDATE_TIME_COL);
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
                int idx = 1;
                for (Map.Entry<String, IndField> e : colToInd.entrySet()) {
                    IndField ind = e.getValue();
                    ps.setObject(idx++, pickValue(row, ind.getResultField(), ind.getFieldName()));
                }
                ps.setTimestamp(idx, now);
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

    /** {@code ind_xxx} → {@code ind_xxx_history}，批次用已有 update_time 区分。 */
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
            ensureUpdateTimeColumn(conn, st, db, history);
        }
        alignHistoryColumns(conn, db, table, history);
        try (Statement st = conn.createStatement()) {
            ensureUpdateTimeColumn(conn, st, db, history);
        }
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

    /**
     * 结果表业务列名：{@code ind_} + 查询结果英文字段（resultField）。
     * 若 fieldName 已是合法 ind_ 前缀则优先采用（与字段映射列表一致）。
     */
    static String resolveResultColumn(IndField ind) {
        if (ind == null) return null;
        String fn = ind.getFieldName();
        if (fn != null && !fn.isBlank()) {
            String col = sanitizeColumn(fn);
            if (col.startsWith("ind_")) return col;
        }
        String rf = ind.getResultField();
        if (rf == null || rf.isBlank()) return null;
        String base = sanitizeColumn(rf);
        if (base.startsWith("ind_")) return base;
        String col = "ind_" + base;
        return col.length() > 64 ? col.substring(0, 64) : col;
    }

    private static String escapeMysqlComment(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return raw.replace("\\", "\\\\").replace("'", "''");
    }

    /**
     * 列定义：类型/长度来自字段映射，COMMENT 为中文指标名称。
     * value 形如 {@code VARCHAR(64) COMMENT '人口学历'}
     */
    private LinkedHashMap<String, String> columnDefs(List<IndField> indicators) {
        LinkedHashMap<String, String> cols = new LinkedHashMap<>();
        if (indicators == null) {
            return cols;
        }
        for (IndField ind : indicators) {
            if (ind == null) continue;
            String col = resolveResultColumn(ind);
            if (col == null || col.isBlank()) continue;
            if (isReservedResultColumn(col)) {
                continue;
            }
            String typeSql = toMysqlType(ind.getFieldType(), ind.getFieldLength(), ind.getFieldPrecision());
            String comment = ind.getIndicatorName();
            if (comment == null || comment.isBlank()) {
                comment = ind.getResultField() != null ? ind.getResultField() : col;
            }
            String def = typeSql + " COMMENT '" + escapeMysqlComment(comment) + "'";
            cols.putIfAbsent(col, def);
        }
        return cols;
    }

    /** 按字段映射的数据类型/长度生成 MySQL 8 类型片段（不含 COMMENT）。 */
    private static String toMysqlType(String fieldType, Integer length, Integer precision) {
        String t = fieldType == null ? "VARCHAR" : fieldType.trim().toUpperCase(Locale.ROOT);
        int len = length == null || length <= 0 ? 64 : Math.min(Math.max(length, 1), 65535);
        int scale = precision == null || precision < 0 ? 0 : precision;

        // 兼容历史中文类型
        if (t.contains("浮点") || t.contains("小数")) {
            return "DECIMAL(20," + (scale <= 0 ? 4 : Math.min(scale, 10)) + ")";
        }
        if (t.contains("整数") || t.contains("数值")) {
            return "BIGINT";
        }
        if (t.contains("日期时间")) {
            return "DATETIME";
        }
        if ("日期".equals(t)) {
            return "DATE";
        }

        return switch (t) {
            case "CHAR" -> "CHAR(" + Math.min(len, 255) + ")";
            case "VARCHAR" -> "VARCHAR(" + Math.min(len, 16383) + ")";
            case "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT",
                 "TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB",
                 "JSON", "DATE", "DATETIME", "TIMESTAMP", "TIME", "YEAR" -> t;
            case "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "BIGINT" -> t;
            case "INTEGER" -> "INT";
            case "FLOAT", "DOUBLE" -> t;
            case "DECIMAL", "NUMERIC" -> {
                int p = Math.min(Math.max(len, 1), 65);
                int s = scale <= 0 ? 2 : Math.min(scale, Math.min(30, p));
                yield "DECIMAL(" + p + "," + s + ")";
            }
            case "BOOLEAN", "BOOL" -> "TINYINT(1)";
            case "BIT" -> "BIT(" + Math.min(Math.max(len, 1), 64) + ")";
            default -> {
                if (t.contains("DECIMAL") || t.contains("DOUBLE") || t.contains("FLOAT")) {
                    yield "DECIMAL(20," + (scale <= 0 ? 4 : Math.min(scale, 10)) + ")";
                }
                if (t.contains("INT") || t.contains("LONG") || t.contains("BIGINT")) {
                    yield "BIGINT";
                }
                if (t.contains("DATETIME") || t.contains("TIMESTAMP")) {
                    yield "DATETIME";
                }
                if (t.equals("DATE") || t.contains("DATE")) {
                    yield "DATE";
                }
                if (len > 1000) {
                    yield "TEXT";
                }
                yield "VARCHAR(" + Math.min(len, 16383) + ")";
            }
        };
    }

    private static boolean isReservedResultColumn(String col) {
        String c = col == null ? "" : col.trim().toLowerCase(Locale.ROOT);
        return "id".equals(c)
                || UPDATE_TIME_COL.equals(c)
                || LEGACY_CALC_AT.equals(c)
                || "task_id".equals(c)
                || "trigger_type".equals(c);
    }

    /** 按 ordinal 取最后一个不在 exclude 中的列名（用于 AFTER）。 */
    private static String lastColumnName(Connection conn, String db, String table, Set<String> exclude)
            throws SQLException {
        String last = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY ORDINAL_POSITION")) {
            ps.setString(1, db);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString(1);
                    if (col == null) continue;
                    boolean skip = false;
                    if (exclude != null) {
                        for (String ex : exclude) {
                            if (ex != null && ex.equalsIgnoreCase(col)) {
                                skip = true;
                                break;
                            }
                        }
                    }
                    if (!skip) {
                        last = col;
                    }
                }
            }
        }
        return last;
    }

    private static boolean indexExists(Connection conn, String db, String table, String indexName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND INDEX_NAME=? LIMIT 1")) {
            ps.setString(1, db);
            ps.setString(2, table);
            ps.setString(3, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void dropIndexIfExists(Connection conn, Statement st, String db, String table, String indexName)
            throws SQLException {
        if (indexExists(conn, db, table, indexName)) {
            st.execute("ALTER TABLE `" + table + "` DROP INDEX `" + indexName + "`");
        }
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
