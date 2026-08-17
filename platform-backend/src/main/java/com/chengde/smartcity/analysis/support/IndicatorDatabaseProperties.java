package com.chengde.smartcity.analysis.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 指标结果库 JDBC（与控制面 smart_city / 分层 ODS 等分离）。
 * <p>
 * 生产：{@code 10.10.10.12:13306}；本地：{@code 127.0.0.1:3306}。
 * 指标域新增/保存时幂等建库；结果表与落数仍在任务执行（手动 / DS 回调）时完成。
 */
@ConfigurationProperties(prefix = "app.indicator-db")
public class IndicatorDatabaseProperties {

    private String host = "127.0.0.1";
    private int port = 3306;
    private String username = "root";
    private String password = "";

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
