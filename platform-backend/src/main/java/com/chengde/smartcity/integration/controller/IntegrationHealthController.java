package com.chengde.smartcity.integration.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.dataease.DataEaseClient;
import com.chengde.smartcity.integration.ds.DolphinSchedulerClient;
import com.chengde.smartcity.integration.kettle.KettleClient;
import com.chengde.smartcity.integration.openmetadata.OpenMetadataClient;
import com.chengde.smartcity.integration.storage.StorageIntegrationClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integration")
public class IntegrationHealthController {

    private final IntegrationProperties props;
    private final OpenMetadataClient om;
    private final DataEaseClient de;
    private final DolphinSchedulerClient ds;
    private final KettleClient kettle;
    private final StorageIntegrationClient storage;

    public IntegrationHealthController(IntegrationProperties props, OpenMetadataClient om, DataEaseClient de,
                                       DolphinSchedulerClient ds, KettleClient kettle,
                                       StorageIntegrationClient storage) {
        this.props = props;
        this.om = om;
        this.de = de;
        this.ds = ds;
        this.kettle = kettle;
        this.storage = storage;
    }

    @GetMapping("/health")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", props.isEnabled());
        out.put("demoFallback", props.isDemoFallback());
        out.put("openmetadata", om.isHealthy());
        out.put("dataease", de.isHealthy());
        out.put("dolphinscheduler", ds.isHealthy());
        out.put("kettle", kettle.isHealthy());
        out.put("elasticsearch", storage.isElasticsearchHealthy());
        out.put("seaweedfs", storage.isSeaweedHealthy());
        out.put("canal", props.isEnabled());
        return ApiResponse.ok(out);
    }

    /** DolphinScheduler 控制台深链（不含账号密码）。 */
    @GetMapping("/ds/console")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> dsConsole() {
        Map<String, Object> out = new LinkedHashMap<>();
        String url = props.getDs() == null ? null : props.getDs().getUrl();
        if (url != null) {
            url = url.trim();
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
        }
        boolean enabled = props.isEnabled();
        boolean healthy = enabled && ds.isHealthy();
        out.put("enabled", enabled);
        out.put("healthy", healthy);
        out.put("consoleUrl", url == null || url.isBlank() ? null : url);
        return ApiResponse.ok(out);
    }

    @GetMapping("/canal/status")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> canal() {
        return ApiResponse.ok(storage.canalStatus());
    }
}
