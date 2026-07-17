package com.chengde.smartcity.integration.openmetadata;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationConfig;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * OpenMetadata 1.5 REST 客户端：真实 JWT 登录（失败即抛错，不伪造 Basic），
 * 真实 databaseService/database/schema/table/column upsert 与 lineage 写入。
 * 移除 TRIGGERED / STOP_REQUESTED 等伪成功降级。
 */
@Component
public class OpenMetadataClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMetadataClient.class);

    private final IntegrationProperties props;
    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile String jwtToken;

    public OpenMetadataClient(IntegrationProperties props, RestTemplate integrationRestTemplate) {
        this.props = props;
        this.rest = integrationRestTemplate;
    }

    public boolean isHealthy() {
        if (!props.isEnabled()) {
            return false;
        }
        try {
            rest.getForEntity(props.getOm().getUrl() + "/system/version", String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- 实体 upsert（createOrUpdate = PUT）----------

    /** 创建或更新 Mysql database service，返回 {id, fqn, name}。 */
    public Map<String, Object> ensureDatabaseService(String name, String host, int port,
                                                     String database, String user, String password) {
        IntegrationConfig.requireIntegration(props, "OpenMetadata");
        ObjectNode body = mapper.createObjectNode();
        body.put("name", name);
        body.put("serviceType", "Mysql");
        ObjectNode conn = body.putObject("connection");
        ObjectNode config = conn.putObject("config");
        config.put("type", "Mysql");
        config.put("scheme", "mysql+pymysql");
        config.put("hostPort", host + ":" + port);
        config.put("username", user);
        ObjectNode auth = config.putObject("authType");
        auth.put("password", password == null ? "" : password);
        config.put("databaseSchema", database);
        JsonNode node = put("/services/databaseServices", body, "OM 建库服务");
        return entityRef(node);
    }

    /** 创建或更新 database，parent=service 名。 */
    public Map<String, Object> ensureDatabase(String serviceName, String databaseName) {
        ObjectNode body = mapper.createObjectNode();
        body.put("name", databaseName);
        body.put("service", serviceName);
        JsonNode node = put("/databases", body, "OM 建库");
        return entityRef(node);
    }

    /** 创建或更新 schema，parent=database FQN。 */
    public Map<String, Object> ensureSchema(String databaseFqn, String schemaName) {
        ObjectNode body = mapper.createObjectNode();
        body.put("name", schemaName);
        body.put("database", databaseFqn);
        JsonNode node = put("/databaseSchemas", body, "OM 建 schema");
        return entityRef(node);
    }

    /**
     * 创建或更新 table 及其列，parent=schema FQN。
     * columns：每项含 name / dataType(JDBC 类型名) / length。
     */
    public Map<String, Object> upsertTable(String schemaFqn, String tableName, List<Map<String, Object>> columns) {
        ObjectNode body = mapper.createObjectNode();
        body.put("name", tableName);
        body.put("databaseSchema", schemaFqn);
        ArrayNode cols = body.putArray("columns");
        for (Map<String, Object> c : columns) {
            ObjectNode col = cols.addObject();
            col.put("name", String.valueOf(c.get("columnName") != null ? c.get("columnName") : c.get("name")));
            String omType = mapOmType(String.valueOf(c.get("dataType")));
            col.put("dataType", omType);
            col.put("dataTypeDisplay", String.valueOf(c.get("dataType")));
            if ("VARCHAR".equals(omType) || "CHAR".equals(omType)) {
                Object len = c.getOrDefault("columnSize", c.get("lengthVal"));
                int l = 255;
                if (len != null) {
                    try {
                        l = Math.max(1, Math.min(Integer.parseInt(String.valueOf(len)), 65535));
                    } catch (NumberFormatException ignored) {
                        l = 255;
                    }
                }
                col.put("dataLength", l);
            }
        }
        JsonNode node = put("/tables", body, "OM 建表");
        return entityRef(node);
    }

    /** 写入表级血缘 from -> to（entityId + type=table）。 */
    public Map<String, Object> addLineage(String fromId, String toId) {
        if (fromId == null || toId == null) {
            throw new BusinessException(400, "血缘两端实体 id 必填");
        }
        ObjectNode body = mapper.createObjectNode();
        ObjectNode edge = body.putObject("edge");
        ObjectNode from = edge.putObject("fromEntity");
        from.put("id", fromId);
        from.put("type", "table");
        ObjectNode to = edge.putObject("toEntity");
        to.put("id", toId);
        to.put("type", "table");
        JsonNode node = put("/lineage", body, "OM 写血缘");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromId", fromId);
        out.put("toId", toId);
        out.put("status", "SUCCESS");
        out.put("raw", node == null ? "" : node.toString());
        return out;
    }

    public Map<String, Object> getTableByFqn(String fqn) {
        IntegrationConfig.requireIntegration(props, "OpenMetadata");
        ensureToken();
        String url = props.getOm().getUrl() + "/tables/name/" + fqn + "?fields=columns";
        try {
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.GET, authEntity(null), String.class);
            return entityRef(parseNode(res.getBody()));
        } catch (Exception e) {
            throw new BusinessException(502, "查询 OM 表失败: " + e.getMessage());
        }
    }

    // ---------- 只读 / 兼容 ----------

    public List<Map<String, Object>> listDatabaseServices() {
        IntegrationConfig.requireIntegration(props, "OpenMetadata");
        ensureToken();
        String url = props.getOm().getUrl() + "/services/databaseServices?limit=50";
        ResponseEntity<String> res = rest.exchange(url, HttpMethod.GET, authEntity(null), String.class);
        return parseDataArray(res.getBody());
    }

    public List<Map<String, Object>> listIngestionPipelines(int limit) {
        if (!props.isEnabled()) {
            return List.of();
        }
        try {
            ensureToken();
            String url = props.getOm().getUrl() + "/services/ingestionPipelines?limit=" + Math.max(1, Math.min(limit, 100));
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.GET, authEntity(null), String.class);
            return parseDataArray(res.getBody());
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> listTables(String database, int limit) {
        if (!props.isEnabled()) {
            return List.of();
        }
        try {
            ensureToken();
            String url = props.getOm().getUrl() + "/tables?limit=" + Math.max(1, Math.min(limit, 100));
            if (database != null && !database.isBlank()) {
                url += "&database=" + database;
            }
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.GET, authEntity(null), String.class);
            return parseDataArray(res.getBody());
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> getLineage(String fqn) {
        if (!props.isEnabled() || fqn == null || fqn.isBlank()) {
            return Map.of();
        }
        try {
            ensureToken();
            String url = props.getOm().getUrl() + "/lineage/table/name/" + fqn + "?upstreamDepth=2&downstreamDepth=2";
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.GET, authEntity(null), String.class);
            return parseObject(res.getBody());
        } catch (Exception e) {
            return Map.of("status", "UNAVAILABLE", "fqn", fqn, "message", e.getMessage());
        }
    }

    /** 触发已配置的 ingestion pipeline；失败抛出真实原因（不再伪 TRIGGERED）。 */
    public Map<String, Object> ingestService(String serviceName) {
        IntegrationConfig.requireIntegration(props, "OpenMetadata");
        ensureToken();
        String pipeline = serviceName + ".metadata";
        String url = props.getOm().getUrl() + "/services/ingestionPipelines/trigger/name/" + pipeline;
        try {
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.POST, authEntity(null), String.class);
            return parseObject(res.getBody());
        } catch (Exception e) {
            throw new BusinessException(502, "OM 采集触发失败: " + e.getMessage());
        }
    }

    /** 停止 ingestion pipeline；失败抛出真实原因（不再伪 STOP_REQUESTED）。 */
    public Map<String, Object> stopIngestionPipeline(String pipelineName) {
        if (!props.isEnabled()) {
            return Map.of("status", "SKIPPED", "reason", "integration disabled");
        }
        ensureToken();
        String url = props.getOm().getUrl() + "/services/ingestionPipelines/name/" + pipelineName + "/toggleIngestion";
        try {
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.POST, authEntity(Map.of()), String.class);
            return parseObject(res.getBody());
        } catch (Exception e) {
            throw new BusinessException(502, "OM 停止采集失败: " + e.getMessage());
        }
    }

    // ---------- 底层 ----------

    private JsonNode put(String path, JsonNode body, String action) {
        IntegrationConfig.requireIntegration(props, "OpenMetadata");
        ensureToken();
        String url = props.getOm().getUrl() + path;
        try {
            HttpEntity<String> req = new HttpEntity<>(mapper.writeValueAsString(body), jsonAuthHeaders());
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.PUT, req, String.class);
            return parseNode(res.getBody());
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(502, action + "失败: " + e.getMessage());
        }
    }

    private Map<String, Object> entityRef(JsonNode node) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (node == null) {
            return out;
        }
        out.put("id", node.path("id").asText(null));
        out.put("name", node.path("name").asText(null));
        out.put("fqn", node.path("fullyQualifiedName").asText(null));
        return out;
    }

    private void ensureToken() {
        if (jwtToken != null) {
            return;
        }
        String loginUrl = props.getOm().getUrl().replace("/api/v1", "") + "/api/v1/users/login";
        Map<String, String> login = Map.of("email", props.getOm().getUser(), "password", props.getOm().getPassword());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> res = rest.exchange(loginUrl, HttpMethod.POST, new HttpEntity<>(login, h), String.class);
            JsonNode node = mapper.readTree(res.getBody());
            String token = node.path("accessToken").asText(null);
            if (token == null || token.isBlank()) {
                token = node.path("token").asText(null);
            }
            if (token == null || token.isBlank()) {
                throw new BusinessException(502, "OM 登录未返回 accessToken");
            }
            jwtToken = token;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(502, "OpenMetadata 登录失败: " + e.getMessage());
        }
    }

    private HttpHeaders jsonAuthHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (jwtToken != null) {
            h.setBearerAuth(jwtToken);
        }
        return h;
    }

    private HttpEntity<?> authEntity(Object body) {
        HttpHeaders h = jsonAuthHeaders();
        return body == null ? new HttpEntity<>(h) : new HttpEntity<>(body, h);
    }

    private String mapOmType(String jdbcType) {
        String t = jdbcType == null ? "" : jdbcType.toUpperCase();
        if (t.contains("BIGINT")) return "BIGINT";
        if (t.contains("TINYINT")) return "TINYINT";
        if (t.contains("SMALLINT")) return "SMALLINT";
        if (t.contains("MEDIUMINT")) return "MEDIUMINT";
        if (t.contains("INT")) return "INT";
        if (t.contains("DECIMAL") || t.contains("NUMERIC") || t.equals("DEC")) return "DECIMAL";
        if (t.contains("DOUBLE")) return "DOUBLE";
        if (t.contains("FLOAT") || t.contains("REAL")) return "FLOAT";
        if (t.contains("DATETIME") || t.contains("TIMESTAMP")) return "DATETIME";
        if (t.equals("DATE")) return "DATE";
        if (t.contains("TIME")) return "TIME";
        if (t.contains("TEXT")) return "TEXT";
        if (t.contains("JSON")) return "JSON";
        if (t.contains("BLOB")) return "BLOB";
        if (t.contains("CHAR")) return "VARCHAR";
        return "VARCHAR";
    }

    private JsonNode parseNode(String json) {
        try {
            return json == null ? null : mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, Object>> parseDataArray(String json) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (json == null) {
            return out;
        }
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode n : data) {
                    out.add(mapper.convertValue(n, Map.class));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private Map<String, Object> parseObject(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("raw", json == null ? "" : json);
        }
    }
}
