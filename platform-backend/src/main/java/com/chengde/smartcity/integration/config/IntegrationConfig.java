package com.chengde.smartcity.integration.config;

import com.chengde.smartcity.analysis.support.IndicatorDatabaseProperties;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.support.LayerDatabaseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties({IntegrationProperties.class, LayerDatabaseProperties.class, IndicatorDatabaseProperties.class})
public class IntegrationConfig {

    @Bean
    public RestTemplate integrationRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(30000);
        return new RestTemplate(factory);
    }

    public static void requireIntegration(IntegrationProperties props, String component) {
        if (!props.isEnabled()) {
            throw new BusinessException(503, component + " integration disabled");
        }
    }

    public static void failOrFallback(IntegrationProperties props, String component, Exception e) {
        if (props.isDemoFallback()) {
            return;
        }
        throw new BusinessException(503, component + " unavailable: " + e.getMessage());
    }
}
