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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class StorageIntegrationClient {

    /** 非结构文档本地镜像根目录：{app.upload.dir}/unstruct（生产即 /data/uploads/unstruct） */
    private final Path documentRoot;
    private final IntegrationProperties props;
    private final RestTemplate rest;
    private final ObjectMapper objectMapper;

    public StorageIntegrationClient(IntegrationProperties props, RestTemplate integrationRestTemplate,
                                    ObjectMapper objectMapper,
                                    @Value("${app.upload.dir:./data/uploads}") String uploadDir) {
        this.props = props;
        this.rest = integrationRestTemplate;
        this.objectMapper = objectMapper;
        this.documentRoot = Path.of(uploadDir, "unstruct").toAbsolutePath().normalize();
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
        String safeName = title == null ? "file" : title.replaceAll("[^a-zA-Z0-9._\\u4e00-\\u9fa5-]", "_");
        String key = "docs/" + UUID.randomUUID() + "-" + safeName;
        Path local = documentRoot.resolve(key).normalize();
        if (!local.startsWith(documentRoot)) {
            throw new IllegalArgumentException("非法文件存储路径");
        }
        try {
            Files.createDirectories(local.getParent());
            Files.write(local, bytes == null ? new byte[0] : bytes);
            if (isSeaweedHealthy()) {
                try {
                    String assignUrl = props.getStorage().getSeaweedS3Endpoint().replace(":8333", ":9333") + "/dir/assign";
                    ResponseEntity<String> assign = rest.getForEntity(assignUrl, String.class);
                    return props.getStorage().getSeaweedBucket() + "/" + key + "?assign=" + assign.getBody();
                } catch (Exception seaweedEx) {
                    // 本地已落盘：Seaweed 分配失败时仍返回可预览的本地键
                    return "local://" + local.toAbsolutePath();
                }
            }
            return "local://" + local.toAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("store failed: " + e.getMessage(), e);
        }
    }

    /**
     * 读取平台登记文件。上传时始终保留本地镜像，因此 SeaweedFS 暂不可用时仍可预览和下载。
     */
    public byte[] readDocument(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("文件存储键为空");
        }
        try {
            Path local = resolveDocumentLocalPath(storageKey);
            if (!Files.exists(local) || !Files.isRegularFile(local)) {
                throw new IllegalArgumentException("文件内容不存在或已迁移");
            }
            return Files.readAllBytes(local);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }
    }

    /** 是否已在本地落盘（可预览/下载）。外部引用键或无法解析的键视为未落盘。 */
    public boolean documentExists(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.startsWith("external://")) {
            return false;
        }
        try {
            Path local = resolveDocumentLocalPath(storageKey);
            return Files.exists(local) && Files.isRegularFile(local);
        } catch (Exception e) {
            return false;
        }
    }

    public void deleteDocument(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.startsWith("external://")) {
            return;
        }
        try {
            Files.deleteIfExists(resolveDocumentLocalPath(storageKey));
        } catch (Exception e) {
            throw new RuntimeException("删除文件失败: " + e.getMessage(), e);
        }
    }

    private Path resolveDocumentLocalPath(String storageKey) {
        if (storageKey.startsWith("local://")) {
            return Path.of(storageKey.substring("local://".length()));
        }
        int docsIndex = storageKey.indexOf("docs/");
        if (docsIndex < 0) {
            throw new IllegalArgumentException("不支持的文件存储键");
        }
        String key = storageKey.substring(docsIndex);
        int queryIndex = key.indexOf('?');
        if (queryIndex >= 0) {
            key = key.substring(0, queryIndex);
        }
        Path modern = documentRoot.resolve(key).normalize();
        if (Files.exists(modern)) {
            return modern;
        }
        // 兼容历史相对路径 data/nas-demo/seaweed-fallback（容器内曾为 /app/data/...）
        Path legacy = Path.of("data", "nas-demo", "seaweed-fallback", key).toAbsolutePath().normalize();
        if (Files.exists(legacy)) {
            return legacy;
        }
        return modern;
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
