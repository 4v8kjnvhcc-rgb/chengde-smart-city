package com.chengde.smartcity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(long sessionIdleMinutes, int loginMaxFailures, int loginLockMinutes) {
}
