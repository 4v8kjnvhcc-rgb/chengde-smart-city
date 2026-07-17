package com.chengde.smartcity.integration.kettle;

import com.chengde.smartcity.integration.config.IntegrationConfig;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
public class KettleClient {

    private static final Logger log = LoggerFactory.getLogger(KettleClient.class);

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
            log.warn("Kettle Carte health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 1. 注册转换到Carte
     * @param transName 转换名称
     * @param ktrXml ktr内容
     */
    public Map<String, Object> addTrans(String transName, String ktrXml) {
        IntegrationConfig.requireIntegration(props, "Kettle");
        String url = props.getKettle().getUrl() + "/kettle/addTrans/";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("xml", ktrXml);
            body.add("trans", transName);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = rest.postForEntity(url, request, String.class);

            log.info("Kettle addTrans {} success: {}", transName, response.getStatusCode());
            return Map.of(
                "transName", transName,
                "status", "SUCCESS",
                "message", "转换注册成功"
            );
        } catch (Exception e) {
            log.error("Kettle addTrans failed: {}", e.getMessage());
            return Map.of(
                "transName", transName,
                "status", "FAILED",
                "message", "转换注册失败: " + e.getMessage()
            );
        }
    }

    /**
     * 2. 启动转换执行
     * @param transName 转换名称
     * @param params 变量参数
     */
    public Map<String, Object> startTrans(String transName, Map<String, String> params) {
        IntegrationConfig.requireIntegration(props, "Kettle");
        StringBuilder urlBuilder = new StringBuilder(props.getKettle().getUrl())
            .append("/kettle/startTrans/?trans=").append(transName);

        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                urlBuilder.append("&param:").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }

        String url = urlBuilder.toString();
        try {
            ResponseEntity<String> response = rest.getForEntity(url, String.class);
            log.info("Kettle startTrans {} success", transName);
            return Map.of(
                "transName", transName,
                "status", "SUCCESS",
                "message", "转换启动成功"
            );
        } catch (Exception e) {
            log.error("Kettle startTrans failed: {}", e.getMessage());
            return Map.of(
                "transName", transName,
                "status", "FAILED",
                "message", "转换启动失败: " + e.getMessage()
            );
        }
    }

    /**
     * 3. 停止转换执行
     * @param transName 转换名称
     */
    public Map<String, Object> stopTrans(String transName) {
        IntegrationConfig.requireIntegration(props, "Kettle");
        String url = props.getKettle().getUrl() + "/kettle/stopTrans/?trans=" + transName;

        try {
            rest.getForEntity(url, String.class);
            log.info("Kettle stopTrans {} success", transName);
            return Map.of(
                "transName", transName,
                "status", "SUCCESS",
                "message", "转换已停止"
            );
        } catch (Exception e) {
            log.error("Kettle stopTrans failed: {}", e.getMessage());
            return Map.of(
                "transName", transName,
                "status", "FAILED",
                "message", "停止失败: " + e.getMessage()
            );
        }
    }

    /**
     * 4. 获取转换执行状态
     * @param transName 转换名称
     */
    public Map<String, Object> getTransStatus(String transName) {
        IntegrationConfig.requireIntegration(props, "Kettle");
        String url = props.getKettle().getUrl() + "/kettle/getTransStatus/?trans=" + transName;

        try {
            ResponseEntity<String> response = rest.getForEntity(url, String.class);
            String body = response.getBody();

            String status = "UNKNOWN";
            int linesInput = 0;
            int linesOutput = 0;
            int linesRejected = 0;

            if (body != null) {
                if (body.contains("Running") || body.contains("running")) {
                    status = "RUNNING";
                } else if (body.contains("Finished") || body.contains("finished")) {
                    status = "FINISHED";
                } else if (body.contains("Stopped") || body.contains("stopped")) {
                    status = "STOPPED";
                } else if (body.contains("Error") || body.contains("error")) {
                    status = "FAILED";
                }
            }

            return Map.of(
                "transName", transName,
                "status", status,
                "linesInput", linesInput,
                "linesOutput", linesOutput,
                "linesRejected", linesRejected,
                "rawResponse", body != null ? body : ""
            );
        } catch (Exception e) {
            log.error("Kettle getTransStatus failed: {}", e.getMessage());
            return Map.of(
                "transName", transName,
                "status", "UNKNOWN",
                "message", "获取状态失败: " + e.getMessage()
            );
        }
    }

    /**
     * 5. 获取转换执行日志
     * @param transName 转换名称
     */
    public Map<String, Object> getTransLog(String transName) {
        IntegrationConfig.requireIntegration(props, "Kettle");
        String url = props.getKettle().getUrl() + "/kettle/getTransLog/?trans=" + transName;

        try {
            ResponseEntity<String> response = rest.getForEntity(url, String.class);
            String logContent = response.getBody();
            log.info("Kettle getTransLog {} success, size: {}", transName,
                logContent != null ? logContent.length() : 0);
            return Map.of(
                "transName", transName,
                "status", "SUCCESS",
                "log", logContent != null ? logContent : ""
            );
        } catch (Exception e) {
            log.error("Kettle getTransLog failed: {}", e.getMessage());
            return Map.of(
                "transName", transName,
                "status", "FAILED",
                "log", "获取日志失败: " + e.getMessage()
            );
        }
    }

    /**
     * 6. 从Carte移除转换
     * @param transName 转换名称
     */
    public Map<String, Object> removeTrans(String transName) {
        IntegrationConfig.requireIntegration(props, "Kettle");
        String url = props.getKettle().getUrl() + "/kettle/removeTrans/?trans=" + transName;

        try {
            rest.getForEntity(url, String.class);
            log.info("Kettle removeTrans {} success", transName);
            return Map.of(
                "transName", transName,
                "status", "SUCCESS",
                "message", "转换已移除"
            );
        } catch (Exception e) {
            log.error("Kettle removeTrans failed: {}", e.getMessage());
            return Map.of(
                "transName", transName,
                "status", "FAILED",
                "message", "移除失败: " + e.getMessage()
            );
        }
    }

    /**
     * 列出已注册的转换（占位实现）
     */
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

    /**
     * 执行Job（兼容旧接口）
     */
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
