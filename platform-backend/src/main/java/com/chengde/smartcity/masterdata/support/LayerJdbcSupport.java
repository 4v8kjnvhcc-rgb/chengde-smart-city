package com.chengde.smartcity.masterdata.support;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 按分层库名解析真实 JDBC 端点并开连接。
 * 方案 A（同机）：各层 host 未配时回落到 {@code spring.datasource}（后端 JVM 视角）。
 * Carte 容器侧地址由 {@code KettleKtrCompiler} 的 host-map 再翻译（如 localhost→host.docker.internal）。
 * 方案 B（分机）：生产通过 {@code app.data-layer.*.host} 指向 S6/S7。
 */
@Component
public class LayerJdbcSupport {

    private static final Logger log = LoggerFactory.getLogger(LayerJdbcSupport.class);
    private static final Pattern JDBC_MYSQL = Pattern.compile(
            "^jdbc:mysql://([^:/?]+)(?::(\\d+))?/([^?]*)", Pattern.CASE_INSENSITIVE);

    private final LayerDatabaseProperties layerProps;
    private final IntegrationProperties integrationProperties;
    private final String springJdbcUrl;
    private final String springUser;
    private final String springPassword;

    public LayerJdbcSupport(LayerDatabaseProperties layerProps,
                            IntegrationProperties integrationProperties,
                            @Value("${spring.datasource.url}") String springJdbcUrl,
                            @Value("${spring.datasource.username}") String springUser,
                            @Value("${spring.datasource.password:}") String springPassword) {
        this.layerProps = layerProps;
        this.integrationProperties = integrationProperties;
        this.springJdbcUrl = springJdbcUrl;
        this.springUser = springUser;
        this.springPassword = springPassword == null ? "" : springPassword;
    }

    public ResolvedEndpoint resolve(String databaseOrLayer) {
        String db = normalizeDatabase(databaseOrLayer);
        LayerDatabaseProperties.Endpoint cfg = layerProps.byDatabase(db);
        // DWD 可继承 ODS 主机；ADS 可继承 DWS（方案 B 两机时少配几项）
        LayerDatabaseProperties.Endpoint inherit = inheritEndpoint(db);
        Fallback fb = fallback();

        String host = firstNonBlank(cfg.getHost(), firstNonBlank(inherit.getHost(), fb.host));
        int port = cfg.getPort() > 0 ? cfg.getPort()
                : (inherit.getPort() > 0 ? inherit.getPort() : fb.port);
        String database = firstNonBlank(cfg.getDatabase(), db);
        String username = firstNonBlank(cfg.getUsername(),
                firstNonBlank(inherit.getUsername(), fb.username));
        String password = firstNonBlank(cfg.getPassword(),
                firstNonBlank(inherit.getPassword(), fb.password));

        if (host == null || host.isBlank()) {
            throw new BusinessException(500, "分层库未配置主机: " + database);
        }
        return new ResolvedEndpoint(host, port, database, username, password == null ? "" : password);
    }

    private LayerDatabaseProperties.Endpoint inheritEndpoint(String db) {
        if (DataLayerSupport.DWD.equals(db)) {
            return layerProps.getOds();
        }
        if (DataLayerSupport.ADS.equals(db)) {
            return layerProps.getDws();
        }
        return new LayerDatabaseProperties.Endpoint();
    }

    public Connection open(String databaseOrLayer) {
        ResolvedEndpoint ep = resolve(databaseOrLayer);
        try {
            Connection conn = DriverManager.getConnection(ep.jdbcUrl(), ep.username(), ep.password());
            try {
                conn.setCatalog(ep.database());
            } catch (SQLException e) {
                log.debug("setCatalog {} skipped: {}", ep.database(), e.getMessage());
            }
            return conn;
        } catch (SQLException e) {
            throw new BusinessException(500, "连接分层库失败 " + ep.database() + "@" + ep.host()
                    + ":" + ep.port() + " — " + e.getMessage());
        }
    }

    public boolean sameInstance(String dbA, String dbB) {
        ResolvedEndpoint a = resolve(dbA);
        ResolvedEndpoint b = resolve(dbB);
        return a.host().equalsIgnoreCase(b.host())
                && a.port() == b.port()
                && Objects.equals(nullToEmpty(a.username()), nullToEmpty(b.username()));
    }

    /**
     * 在目标库创建与源表同结构的表。
     * 统一走 SHOW CREATE + 改写：跨机可用，并剥离 MySQL 8 专有校对序（如 utf8mb4_0900_ai_ci），
     * 避免目标为 5.7 / 部分环境时报 Unknown collation。
     */
    public void createTableLike(String sourceDb, String sourceTable, String targetDb, String targetTable)
            throws SQLException {
        String srcT = sanitizeIdent(sourceTable);
        String tgtT = sanitizeIdent(targetTable);
        String srcD = normalizeDatabase(sourceDb);
        String tgtD = normalizeDatabase(targetDb);

        String createSql;
        try (Connection src = open(srcD); Statement st = src.createStatement();
             ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + DataLayerSupport.qualify(srcD, srcT))) {
            if (!rs.next()) {
                throw new SQLException("源表不存在: " + DataLayerSupport.qualify(srcD, srcT));
            }
            createSql = rs.getString(2);
        }
        String rewritten = rewriteCreateTable(createSql, tgtT);
        try (Connection tgt = open(tgtD); Statement st = tgt.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS `" + tgtD
                    + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            if (!tableExists(tgt, tgtD, tgtT)) {
                st.execute(rewritten);
            }
        }
    }

    public boolean tableExists(String database, String table) {
        String db = normalizeDatabase(database);
        String tbl = sanitizeIdent(table);
        ResolvedEndpoint ep = resolve(db);
        try (Connection conn = open(db);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM information_schema.TABLES "
                             + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE' LIMIT 1")) {
            ps.setString(1, db);
            ps.setString(2, tbl);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "检查表是否存在失败 "
                    + DataLayerSupport.qualify(db, tbl) + "@" + ep.host() + ":" + ep.port()
                    + " — " + e.getMessage());
        }
    }

    public static boolean tableExists(Connection conn, String db, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE' LIMIT 1")) {
            ps.setString(1, db);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 目标表已存在但缺列时，按源表补齐（仅 ADD COLUMN，不改类型/不删列）。
     * @return 新增列数量
     */
    public int syncMissingColumns(String sourceDb, String sourceTable, String targetDb, String targetTable)
            throws SQLException {
        String srcD = normalizeDatabase(sourceDb);
        String tgtD = normalizeDatabase(targetDb);
        String srcT = sanitizeIdent(sourceTable);
        String tgtT = sanitizeIdent(targetTable);
        List<ColDef> sourceCols = listColumnDefs(srcD, srcT);
        if (sourceCols.isEmpty()) {
            return 0;
        }
        Set<String> existing = new HashSet<>();
        try (Connection tgt = open(tgtD);
             ResultSet rs = tgt.getMetaData().getColumns(tgtD, null, tgtT, null)) {
            while (rs.next()) {
                existing.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        int added = 0;
        try (Connection tgt = open(tgtD); Statement st = tgt.createStatement()) {
            String q = DataLayerSupport.qualify(tgtD, tgtT);
            for (ColDef col : sourceCols) {
                if (existing.contains(col.name().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                st.execute("ALTER TABLE " + q + " ADD COLUMN `" + col.name() + "` " + col.typeSql());
                added++;
            }
        }
        return added;
    }

    private List<ColDef> listColumnDefs(String database, String table) throws SQLException {
        List<ColDef> cols = new ArrayList<>();
        try (Connection conn = open(database);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW COLUMNS FROM " + DataLayerSupport.qualify(database, table))) {
            while (rs.next()) {
                String name = rs.getString("Field");
                String type = rs.getString("Type");
                String nullable = rs.getString("Null");
                String def = rs.getString("Default");
                String extra = rs.getString("Extra");
                if (name == null || type == null) continue;
                StringBuilder typeSql = new StringBuilder(type);
                if ("NO".equalsIgnoreCase(nullable)) {
                    typeSql.append(" NOT NULL");
                } else {
                    typeSql.append(" NULL");
                }
                if (def != null) {
                    if (def.equalsIgnoreCase("CURRENT_TIMESTAMP")
                            || def.toUpperCase(Locale.ROOT).startsWith("CURRENT_TIMESTAMP")) {
                        typeSql.append(" DEFAULT ").append(def);
                    } else {
                        typeSql.append(" DEFAULT '").append(def.replace("'", "''")).append("'");
                    }
                }
                if (extra != null && !extra.isBlank()
                        && !extra.toLowerCase(Locale.ROOT).contains("auto_increment")) {
                    typeSql.append(' ').append(extra);
                }
                cols.add(new ColDef(name, typeSql.toString()));
            }
        }
        return cols;
    }

    private record ColDef(String name, String typeSql) {}

    public String normalizeDatabase(String databaseOrLayer) {
        if (databaseOrLayer == null || databaseOrLayer.isBlank()) {
            return DataLayerSupport.ODS;
        }
        String t = databaseOrLayer.trim();
        if (DataLayerSupport.isPlatformLayerDb(t) || DataLayerSupport.CONTROL.equalsIgnoreCase(t)) {
            return t.toLowerCase(Locale.ROOT);
        }
        String upper = t.toUpperCase(Locale.ROOT);
        if ("ODS".equals(upper) || "DWD".equals(upper) || "DWS".equals(upper)
                || "ADS".equals(upper) || "CONTROL".equals(upper)) {
            return DataLayerSupport.databaseForLayer(upper);
        }
        return DataLayerSupport.databaseForLayer(DataLayerSupport.layerForTableName(t));
    }

    private Fallback fallback() {
        // 优先 spring.datasource：后端在宿主机跑时必须用 localhost，不能用 Carte 视角的 host.docker.internal
        // （Windows 上 host.docker.internal 常解析到不可达网卡，导致「创建/准备目标表」Connect timed out）
        IntegrationProperties.Kettle k = integrationProperties.getKettle();
        String host = null;
        int port = 3306;
        Matcher m = JDBC_MYSQL.matcher(springJdbcUrl == null ? "" : springJdbcUrl);
        boolean fromSpring = m.find();
        if (fromSpring) {
            host = m.group(1);
            port = m.group(2) != null ? Integer.parseInt(m.group(2)) : 3306;
        } else if (k != null) {
            host = k.getTargetHost();
            if (k.getTargetPort() > 0) {
                port = k.getTargetPort();
            }
        }
        String user = k != null ? k.getTargetUser() : null;
        String pass = k != null ? k.getTargetPassword() : null;
        if (user == null || user.isBlank()) {
            user = springUser;
        }
        if (pass == null || pass.isBlank()) {
            pass = springPassword;
        }
        return new Fallback(host, port, user, pass);
    }

    private static String rewriteCreateTable(String showCreate, String newTable) {
        // SHOW CREATE TABLE 形如：CREATE TABLE `old` ( ... ) ENGINE=...
        String sql = showCreate;
        sql = sql.replaceFirst("(?i)CREATE\\s+TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?`?[^`\\s(]+`?",
                "CREATE TABLE IF NOT EXISTS `" + newTable + "`");
        // 去掉 AUTO_INCREMENT=n 避免跨机拷贝序号冲突噪音（可选）
        sql = sql.replaceAll("(?i)\\sAUTO_INCREMENT=\\d+", "");
        // 生产常见：源在 MySQL 8（.16 ODS/DWD），目标在 5.7/不支持 0900 的实例（.15 DWS/ADS）
        // SHOW CREATE 会带 utf8mb4_0900_*，目标执行即 Unknown collation
        sql = sql.replaceAll("(?i)utf8mb4_0900_ai_ci", "utf8mb4_unicode_ci");
        sql = sql.replaceAll("(?i)utf8mb4_0900_as_ci", "utf8mb4_unicode_ci");
        sql = sql.replaceAll("(?i)utf8mb4_0900_as_cs", "utf8mb4_bin");
        sql = sql.replaceAll("(?i)utf8mb4_0900_bin", "utf8mb4_bin");
        // 兜底：其它 0900 变体一律落到 unicode_ci，避免再漏一种校对序名
        sql = sql.replaceAll("(?i)utf8mb4_0900_[a-z0-9_]+", "utf8mb4_unicode_ci");
        sql = sql.replaceAll("(?i)utf8mb3_0900_[a-z0-9_]+", "utf8mb4_unicode_ci");
        return sql;
    }

    private static String sanitizeIdent(String name) {
        return name == null ? "" : name.replace("`", "").trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private record Fallback(String host, int port, String username, String password) {
    }

    public record ResolvedEndpoint(String host, int port, String database, String username, String password) {
        public String jdbcUrl() {
            return "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";
        }
    }
}
