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

    @GetMapping("/canal/status")
    public ApiResponse<Map<String, Object>> canal() {
        return ApiResponse.ok(storage.canalStatus());
    }
}
