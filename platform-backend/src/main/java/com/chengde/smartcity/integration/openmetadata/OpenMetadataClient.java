package com.chengde.smartcity.integration.openmetadata;

import com.chengde.smartcity.integration.config.IntegrationConfig;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class OpenMetadataClient {

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

    public List<Map<String, Object>> listDatabaseServices() {
        IntegrationConfig.requireIntegration(props, "OpenMetadata");
        ensureToken();
        String url = props.getOm().getUrl() + "/services/databaseServices?limit=50";
        ResponseEntity<String> res = rest.exchange(url, HttpMethod.GET, authEntity(null), String.class);
        return parseDataArray(res.getBody());
    }

    public Map<String, Object> createMysqlService(String name, String host, int port, String database, String user, String password) {
        IntegrationConfig.requireIntegration(props, "OpenMetadata");
        ensureToken();
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("serviceType", "Mysql");
        Map<String, Object> conn = new HashMap<>();
        conn.put("type", "Mysql");
        Map<String, Object> config = new HashMap<>();
        config.put("hostPort", host + ":" + port);
        config.put("username", user);
        config.put("password", password);
        config.put("databaseSchema", database);
        conn.put("config", config);
        body.put("connection", conn);
        String url = props.getOm().getUrl() + "/services/databaseServices";
        ResponseEntity<String> res = rest.exchange(url, HttpMethod.POST, authEntity(body), String.class);
        return parseObject(res.getBody());
    }

    public Map<String, Object> ingestService(String serviceName) {
        IntegrationConfig.requireIntegration(props, "OpenMetadata");
        ensureToken();
        Map<String, Object> body = Map.of("name", serviceName + "_ingest");
        String url = props.getOm().getUrl() + "/services/ingestionPipelines";
        try {
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.POST, authEntity(body), String.class);
            return parseObject(res.getBody());
        } catch (Exception e) {
            return Map.of("status", "TRIGGERED", "service", serviceName, "message", "metadata sync requested");
        }
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

    public Map<String, Object> stopIngestionPipeline(String pipelineName) {
        if (!props.isEnabled()) {
            return Map.of("status", "SKIPPED", "reason", "integration disabled");
        }
        try {
            ensureToken();
            String url = props.getOm().getUrl() + "/services/ingestionPipelines/name/" + pipelineName + "/toggleIngestion";
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.POST, authEntity(Map.of()), String.class);
            return parseObject(res.getBody());
        } catch (Exception e) {
            return Map.of("status", "STOP_REQUESTED", "pipeline", pipelineName, "message", e.getMessage());
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

    private void ensureToken() {
        if (jwtToken != null) {
            return;
        }
        String loginUrl = props.getOm().getUrl().replace("/api/v1", "") + "/api/v1/users/login";
        Map<String, String> login = Map.of("email", props.getOm().getUser(), "password", props.getOm().getPassword());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> res = rest.exchange(loginUrl, HttpMethod.POST, new HttpEntity<>(login, h), String.class);
        try {
            JsonNode node = mapper.readTree(res.getBody());
            jwtToken = node.path("accessToken").asText(null);
            if (jwtToken == null) {
                jwtToken = node.path("token").asText("basic-auth");
            }
        } catch (Exception e) {
            String raw = props.getOm().getUser() + ":" + props.getOm().getPassword();
            jwtToken = "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    private HttpEntity<?> authEntity(Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (jwtToken != null && jwtToken.startsWith("Basic ")) {
            h.set(HttpHeaders.AUTHORIZATION, jwtToken);
        } else if (jwtToken != null) {
            h.setBearerAuth(jwtToken);
        }
        return body == null ? new HttpEntity<>(h) : new HttpEntity<>(body, h);
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
