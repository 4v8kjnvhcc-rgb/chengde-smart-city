package com.chengde.smartcity.integration.dataease;

import com.chengde.smartcity.integration.config.IntegrationConfig;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DataEaseClient {

    private final IntegrationProperties props;
    private final RestTemplate rest;

    public DataEaseClient(IntegrationProperties props, RestTemplate integrationRestTemplate) {
        this.props = props;
        this.rest = integrationRestTemplate;
    }

    public boolean isHealthy() {
        if (!props.isEnabled()) {
            return false;
        }
        try {
            rest.getForEntity(props.getDe().getUrl(), String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String buildEmbedUrl(String targetId, String token, Long userId) {
        String base = props.getDe().getEmbedBase();
        return base + "/#/chart-view?dvId=" + targetId + "&portalToken=" + token + "&uid=" + userId;
    }

    public Map<String, Object> buildEmbed(String targetType, String targetId, Long userId) {
        IntegrationConfig.requireIntegration(props, "DataEase");
        String token = "DE_" + UUID.randomUUID().toString().replace("-", "");
        return Map.of(
                "token", token,
                "embedUrl", "/analytics/embed-preview?targetType=" + targetType + "&targetId=" + targetId + "&token=" + token,
                "dataeaseUrl", buildEmbedUrl(targetId, token, userId),
                "targetType", targetType,
                "targetId", targetId,
                "source", "dataease-live"
        );
    }
}
