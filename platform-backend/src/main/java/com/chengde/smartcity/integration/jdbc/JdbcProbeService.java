package com.chengde.smartcity.integration.jdbc;

import com.chengde.smartcity.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 真实 JDBC 探测服务：建立连接、执行 SELECT 1、读取 DatabaseMetaData（库/表/列/主键/行数）。
 * 全部为真实调用，失败即抛出携带真实原因的 {@link BusinessException}，不做任何伪成功降级。
 */
@Service
public class JdbcProbeService {

    private static final Logger log = LoggerFactory.getLogger(JdbcProbeService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CredentialCipher credentialCipher;

    public JdbcProbeService(CredentialCipher credentialCipher) {
        this.credentialCipher = credentialCipher;
    }

    /** 连接配置（从 ing_data_source.conn_config_json 解析）。 */
    public static class ConnConfig {
        public String sourceType;
        public String host;
        public int port;
        public String database;
        public String username;
        public String password;
    }

    /** 从数据源类型 + conn_config_json 解析连接配置，并解密密码。 */
    public ConnConfig parse(String sourceType, String connConfigJson) {
        if (connConfigJson == null || connConfigJson.isBlank()) {
            throw new BusinessException(400, "请先配置主机、端口、库名、用户名与密码后再测试连接");
        }
        Map<String, Object> cfg;
        try {
            cfg = MAPPER.readValue(connConfigJson, Map.class);
        } catch (Exception e) {
            throw new BusinessException(400, "连接配置 JSON 无法解析");
        }
        ConnConfig c = new ConnConfig();
        c.sourceType = sourceType == null ? "MYSQL" : sourceType.toUpperCase();
        c.host = str(cfg.get("host"));
        c.port = cfg.get("port") == null ? defaultPort(c.sourceType) : Integer.parseInt(String.valueOf(cfg.get("port")));
        c.database = str(cfg.get("database"));
        c.username = str(cfg.get("username"));
        String cipher = cfg.get("passwordCipher") != null ? str(cfg.get("passwordCipher")) : str(cfg.get("password"));
        c.password = cipher == null ? "" : credentialCipher.decrypt(cipher);
        if (c.host == null || c.username == null) {
            throw new BusinessException(400, "连接配置不完整：需包含 host 与 username");
        }
        return c;
    }

    public String jdbcUrl(ConnConfig c) {
        return switch (c.sourceType) {
            case "POSTGRES", "POSTGRESQL" -> "jdbc:postgresql://" + c.host + ":" + c.port + "/" + c.database;
            case "ORACLE" -> "jdbc:oracle:thin:@" + c.host + ":" + c.port + ":" + c.database;
            default -> "jdbc:mysql://" + c.host + ":" + c.port + "/" + c.database
                    + "?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci"
                    + "&serverTimezone=Asia/Shanghai"
                    + "&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=15000";
        };
    }

    private int defaultPort(String type) {
        return switch (type) {
            case "POSTGRES", "POSTGRESQL" -> 5432;
            case "ORACLE" -> 1521;
            default -> 3306;
        };
    }

    private Connection open(ConnConfig c) throws Exception {
        DriverManager.setLoginTimeout(6);
        return DriverManager.getConnection(jdbcUrl(c), c.username, c.password);
    }

    /** 真实测试连接：SELECT 1 + 统计表数量。失败抛出真实原因。 */
    public Map<String, Object> testConnection(ConnConfig c) {
        long start = System.currentTimeMillis();
        try (Connection conn = open(c)) {
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT 1")) {
                rs.next();
            }
            int tableCount = 0;
            String catalog = c.database;
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tableCount++;
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("tableCount", tableCount);
            out.put("schema", catalog);
            out.put("product", md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
            out.put("elapsedMs", System.currentTimeMillis() - start);
            return out;
        } catch (Exception e) {
            log.warn("JDBC 探测失败 host={} db={}: {}", c.host, c.database, e.getMessage());
            throw new BusinessException(502, "连接失败：" + rootMessage(e));
        }
    }

    /** 列出所有表及基本信息（含行数与主键，供登记勾选）。 */
    public List<Map<String, Object>> listTables(ConnConfig c) {
        try (Connection conn = open(c)) {
            List<Map<String, Object>> tables = new ArrayList<>();
            DatabaseMetaData md = conn.getMetaData();
            List<String> names = new ArrayList<>();
            try (ResultSet rs = md.getTables(c.database, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    names.add(rs.getString("TABLE_NAME"));
                }
            }
            for (String name : names) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("sourceTable", name);
                t.put("columns", readColumns(md, c.database, name));
                t.put("primaryKeys", readPrimaryKeys(md, c.database, name));
                t.put("rowCount", countRows(conn, name));
                tables.add(t);
            }
            return tables;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(502, "探库失败：" + rootMessage(e));
        }
    }

    /** 探测单表结构（列/主键/行数）。 */
    public Map<String, Object> describeTable(ConnConfig c, String tableName) {
        try (Connection conn = open(c)) {
            DatabaseMetaData md = conn.getMetaData();
            List<Map<String, Object>> cols = readColumns(md, c.database, tableName);
            if (cols.isEmpty()) {
                throw new BusinessException(404, "源库不存在表 " + tableName + " 或无列信息");
            }
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("sourceTable", tableName);
            t.put("columns", cols);
            t.put("primaryKeys", readPrimaryKeys(md, c.database, tableName));
            t.put("rowCount", countRows(conn, tableName));
            return t;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(502, "探表失败：" + rootMessage(e));
        }
    }

    private List<Map<String, Object>> readColumns(DatabaseMetaData md, String catalog, String table) throws Exception {
        List<Map<String, Object>> cols = new ArrayList<>();
        try (ResultSet rs = md.getColumns(catalog, null, table, "%")) {
            int ordinal = 0;
            while (rs.next()) {
                Map<String, Object> col = new LinkedHashMap<>();
                col.put("columnName", rs.getString("COLUMN_NAME"));
                col.put("dataType", rs.getString("TYPE_NAME"));
                col.put("columnSize", rs.getInt("COLUMN_SIZE"));
                col.put("nullable", rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
                col.put("remarks", rs.getString("REMARKS"));
                col.put("sortOrder", ordinal++);
                cols.add(col);
            }
        }
        return cols;
    }

    private List<String> readPrimaryKeys(DatabaseMetaData md, String catalog, String table) throws Exception {
        List<String> pks = new ArrayList<>();
        try (ResultSet rs = md.getPrimaryKeys(catalog, null, table)) {
            while (rs.next()) {
                pks.add(rs.getString("COLUMN_NAME"));
            }
        }
        return pks;
    }

    private long countRows(Connection conn, String table) {
        String safe = table.replace("`", "");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + safe + "`")) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (Exception e) {
            log.debug("统计行数失败 table={}: {}", table, e.getMessage());
            return -1L;
        }
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }

    private String str(Object v) {
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v).trim();
    }
}
