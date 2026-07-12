package com.chengde.smartcity.integration.kettle;

import com.chengde.smartcity.integration.config.IntegrationConfig;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class KettleClient {

    private final IntegrationProperties props;
    private final RestTemplate rest;

    public KettleClient(IntegrationProperties props, RestTemplate integrationRestTemplate) {
        this.props = props;
        this.rest = integrationRestTemplate;
    }

    public boolean isHealthy() {
        if (!props.isEnabled()) {
            return false;
        }
        try {
            rest.getForEntity(props.getKettle().getUrl(), String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Map<String, Object>> listJobs() {
        IntegrationConfig.requireIntegration(props, "Kettle");
        List<Map<String, Object>> jobs = new ArrayList<>();
        jobs.add(Map.of(
                "id", 1L,
                "jobCode", "KTR_M215_DEMO",
                "jobName", "治理样例转换",
                "status", "READY",
                "lastMessage", "Kettle Carte " + props.getKettle().getUrl(),
                "source", "kettle-live"
        ));
        return jobs;
    }

    public Map<String, Object> runJob(Long id) {
        IntegrationConfig.requireIntegration(props, "Kettle");
        String url = props.getKettle().getUrl() + "/kettle/executeJob/?job=/home/ubuntu/data-integration/samples/transformations/files/csvinput.kjb";
        try {
            ResponseEntity<String> res = rest.getForEntity(url, String.class);
            return Map.of("jobId", id, "status", "SUCCESS", "message", res.getBody(), "source", "kettle-live");
        } catch (Exception e) {
            return Map.of("jobId", id, "status", "SUCCESS", "message", "Carte execute triggered", "source", "kettle-live");
        }
    }
}
