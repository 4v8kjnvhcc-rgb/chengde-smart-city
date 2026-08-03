package com.chengde.smartcity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.portal-sso")
public record PortalSsoProperties(
        boolean enabled,
        String secret,
        long ticketTtlSeconds
) {
    public PortalSsoProperties {
        if (ticketTtlSeconds <= 0) {
            ticketTtlSeconds = 60;
        }
        if (secret == null || secret.isBlank()) {
            secret = "chengde-portal-assessment-sso-shared-secret-2026";
        }
    }
}
