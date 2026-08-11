package com.chengde.smartcity.masterdata.support;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;
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
     * 在目标库创建与源表同结构的表。同机可用 LIKE；跨机则 SHOW CREATE 后改写。
     */
    public void createTableLike(String sourceDb, String sourceTable, String targetDb, String targetTable)
            throws SQLException {
        String srcT = sanitizeIdent(sourceTable);
        String tgtT = sanitizeIdent(targetTable);
        String srcD = normalizeDatabase(sourceDb);
        String tgtD = normalizeDatabase(targetDb);

        if (sameInstance(srcD, tgtD)) {
            try (Connection conn = open(tgtD); Statement st = conn.createStatement()) {
                st.execute("CREATE DATABASE IF NOT EXISTS `" + tgtD + "`");
                if (!tableExists(conn, tgtD, tgtT)) {
                    st.execute("CREATE TABLE IF NOT EXISTS " + DataLayerSupport.qualify(tgtD, tgtT)
                            + " LIKE " + DataLayerSupport.qualify(srcD, srcT));
                }
            }
            return;
        }

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
            st.execute("CREATE DATABASE IF NOT EXISTS `" + tgtD + "`");
            if (!tableExists(tgt, tgtD, tgtT)) {
                st.execute(rewritten);
            }
        }
    }

    public boolean tableExists(String database, String table) {
        try (Connection conn = open(database)) {
            return tableExists(conn, normalizeDatabase(database), sanitizeIdent(table));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean tableExists(Connection conn, String db, String table) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(db, null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

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
