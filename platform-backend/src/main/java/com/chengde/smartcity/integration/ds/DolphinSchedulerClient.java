package com.chengde.smartcity.integration.ds;

import com.chengde.smartcity.integration.config.IntegrationConfig;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
public class DolphinSchedulerClient {

    private final IntegrationProperties props;
    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile String sessionId;

    public DolphinSchedulerClient(IntegrationProperties props, RestTemplate integrationRestTemplate) {
        this.props = props;
        this.rest = integrationRestTemplate;
    }

    public boolean isHealthy() {
        if (!props.isEnabled()) {
            return false;
        }
        try {
            rest.getForEntity(props.getDs().getUrl() + "/actuator/health", String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Map<String, Object>> listWorkflows() {
        IntegrationConfig.requireIntegration(props, "DolphinScheduler");
        ensureSession();
        String url = props.getDs().getUrl() + "/projects/1/process-definition?pageNo=1&pageSize=50";
        ResponseEntity<String> res = rest.exchange(url, HttpMethod.GET, sessionEntity(null), String.class);
        return parseList(res.getBody());
    }

    public Map<String, Object> startWorkflow(Long definitionId) {
        IntegrationConfig.requireIntegration(props, "DolphinScheduler");
        ensureSession();
        String url = props.getDs().getUrl() + "/projects/1/executors/start-process-instance";
        Map<String, Object> body = new HashMap<>();
        body.put("processDefinitionId", definitionId);
        body.put("failureStrategy", "CONTINUE");
        body.put("warningType", "NONE");
        ResponseEntity<String> res = rest.exchange(url, HttpMethod.POST, sessionEntity(body), String.class);
        Map<String, Object> out = parseObject(res.getBody());
        out.put("status", "SUCCESS");
        out.put("source", "dolphinscheduler-live");
        return out;
    }

    private void ensureSession() {
        if (sessionId != null) {
            return;
        }
        String url = props.getDs().getUrl() + "/login";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("userName", props.getDs().getUser());
        form.add("userPassword", props.getDs().getPassword());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> res = rest.exchange(url, HttpMethod.POST, new HttpEntity<>(form, h), String.class);
        try {
            JsonNode node = mapper.readTree(res.getBody());
            sessionId = node.path("data").path("sessionId").asText("ds-session");
        } catch (Exception e) {
            sessionId = "ds-session";
        }
    }

    private HttpEntity<?> sessionEntity(Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (sessionId != null) {
            h.add("sessionId", sessionId);
        }
        return body == null ? new HttpEntity<>(h) : new HttpEntity<>(body, h);
    }

    private List<Map<String, Object>> parseList(String json) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (json == null) {
            return out;
        }
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode data = root.path("data").path("totalList");
            if (data.isArray()) {
                for (JsonNode n : data) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", n.path("id").asLong());
                    row.put("workflowCode", n.path("code").asText());
                    row.put("workflowName", n.path("name").asText());
                    row.put("status", n.path("releaseState").asText("ONLINE"));
                    row.put("lastMessage", "from DolphinScheduler");
                    out.add(row);
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
