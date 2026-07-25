package com.chengde.smartcity.integration.storage;

import com.chengde.smartcity.integration.config.IntegrationConfig;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class StorageIntegrationClient {

    private final IntegrationProperties props;
    private final RestTemplate rest;
    private final ObjectMapper objectMapper;

    public StorageIntegrationClient(IntegrationProperties props, RestTemplate integrationRestTemplate,
                                    ObjectMapper objectMapper) {
        this.props = props;
        this.rest = integrationRestTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isElasticsearchHealthy() {
        if (!props.isEnabled()) {
            return false;
        }
        try {
            rest.getForEntity(props.getStorage().getEsUrl() + "/_cluster/health", String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isSeaweedHealthy() {
        if (!props.isEnabled()) {
            return false;
        }
        try {
            rest.getForEntity(props.getStorage().getSeaweedS3Endpoint().replace(":8333", ":9333") + "/dir/status", String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String storeDocument(String title, String contentType, byte[] bytes) {
        IntegrationConfig.requireIntegration(props, "SeaweedFS");
        String key = "docs/" + UUID.randomUUID() + "-" + title.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path local = Path.of("data", "nas-demo", "seaweed-fallback", key);
        try {
            Files.createDirectories(local.getParent());
            Files.write(local, bytes == null ? new byte[0] : bytes);
            if (isSeaweedHealthy()) {
                HttpHeaders h = new HttpHeaders();
                h.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                String assignUrl = props.getStorage().getSeaweedS3Endpoint().replace(":8333", ":9333") + "/dir/assign";
                ResponseEntity<String> assign = rest.getForEntity(assignUrl, String.class);
                return props.getStorage().getSeaweedBucket() + "/" + key + "?assign=" + assign.getBody();
            }
            return "local://" + local.toAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("store failed: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> indexCatalog(String catalogId, String catalogCode, String title, String description) {
        if (!props.isEnabled() || !isElasticsearchHealthy()) {
            return Map.of("indexStatus", "SKIPPED", "index", "smartcity_catalog", "source", "elasticsearch-offline");
        }
        String index = "smartcity_catalog";
        Map<String, Object> doc = Map.of(
                "catalogId", catalogId,
                "catalogCode", catalogCode == null ? "" : catalogCode,
                "title", title == null ? "" : title,
                "description", description == null ? "" : description,
                "publishStatus", "PUBLISHED");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String url = props.getStorage().getEsUrl() + "/" + index + "/_doc/" + catalogId;
        try {
            rest.exchange(url, HttpMethod.PUT, new HttpEntity<>(doc, h), String.class);
            return Map.of("indexStatus", "INDEXED", "index", index, "source", "elasticsearch-live");
        } catch (Exception e) {
            return Map.of("indexStatus", "FAILED", "index", index, "note", e.getMessage());
        }
    }

    public List<Map<String, Object>> searchCatalog(String keyword, int size) {
        if (!props.isEnabled() || !isElasticsearchHealthy() || keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String index = "smartcity_catalog";
        Map<String, Object> query = Map.of(
                "query", Map.of("multi_match", Map.of(
                        "query", keyword,
                        "fields", List.of("title^3", "description^2", "catalogCode"),
                        "type", "best_fields")),
                "size", size <= 0 ? 20 : size);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String url = props.getStorage().getEsUrl() + "/" + index + "/_search";
        try {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(query, h), String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            List<Map<String, Object>> hits = new ArrayList<>();
            for (JsonNode hit : root.path("hits").path("hits")) {
                JsonNode src = hit.path("_source");
                Map<String, Object> row = new HashMap<>();
                row.put("id", src.path("catalogId").asText());
                row.put("catalogCode", src.path("catalogCode").asText());
                row.put("title", src.path("title").asText());
                row.put("description", src.path("description").asText());
                row.put("publishStatus", src.path("publishStatus").asText("PUBLISHED"));
                row.put("score", hit.path("_score").asDouble(0));
                row.put("source", "elasticsearch");
                hits.add(row);
            }
            return hits;
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> indexDocument(String docId, String title, String storageKey) {
        if (!props.isEnabled() || !isElasticsearchHealthy()) {
            return Map.of(
                    "indexStatus", "FAILED",
                    "index", "smartcity_docs",
                    "source", "elasticsearch-offline",
                    "note", "Elasticsearch 不可用，禁止标记为已索引");
        }
        String index = "smartcity_docs";
        Map<String, Object> doc = new HashMap<>();
        doc.put("docId", docId);
        doc.put("title", title == null ? "" : title);
        doc.put("storageKey", storageKey == null ? "" : storageKey);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String url = props.getStorage().getEsUrl() + "/" + index + "/_doc/" + docId;
        try {
            rest.exchange(url, HttpMethod.PUT, new HttpEntity<>(doc, h), String.class);
            return Map.of("indexStatus", "INDEXED", "index", index, "source", "elasticsearch-live");
        } catch (Exception e) {
            return Map.of("indexStatus", "FAILED", "index", index, "source", "elasticsearch-error", "note", e.getMessage());
        }
    }

    /** 非结构化文档检索：ES 健康时查 smartcity_docs，否则返回空由调用方回退台账 */
    public List<Map<String, Object>> searchDocuments(String keyword, int size) {
        if (!props.isEnabled() || !isElasticsearchHealthy() || keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String index = "smartcity_docs";
        Map<String, Object> query = Map.of(
                "query", Map.of("multi_match", Map.of(
                        "query", keyword,
                        "fields", List.of("title^3", "storageKey", "docId"),
                        "type", "best_fields")),
                "size", size <= 0 ? 50 : size);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String url = props.getStorage().getEsUrl() + "/" + index + "/_search";
        try {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(query, h), String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            List<Map<String, Object>> hits = new ArrayList<>();
            for (JsonNode hit : root.path("hits").path("hits")) {
                JsonNode src = hit.path("_source");
                Map<String, Object> row = new HashMap<>();
                row.put("id", src.path("docId").asText());
                row.put("title", src.path("title").asText());
                row.put("storageKey", src.path("storageKey").asText());
                row.put("score", hit.path("_score").asDouble(0));
                row.put("source", "elasticsearch");
                hits.add(row);
            }
            return hits;
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> canalStatus() {
        IntegrationConfig.requireIntegration(props, "Canal");
        try {
            URI uri = URI.create(props.getStorage().getCanalUrl());
            int port = uri.getPort() > 0 ? uri.getPort() : 19090;
            String host = uri.getHost() != null ? uri.getHost() : "localhost";
            try (Socket socket = new Socket(host, port)) {
                return Map.of("status", "RUNNING", "detail", host + ":" + port, "source", "canal-live");
            }
        } catch (Exception e) {
            return Map.of("status", "WAITING", "detail", e.getMessage(), "source", "canal");
        }
    }

    public String mongoCdcCollection() {
        return props.getStorage().getMongoUrl() + "/smartcity_cdc";
    }
}
