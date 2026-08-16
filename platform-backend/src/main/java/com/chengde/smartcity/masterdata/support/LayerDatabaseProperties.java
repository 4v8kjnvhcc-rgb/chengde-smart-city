package com.chengde.smartcity.masterdata.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台分层库连接（ODS/DWD/DWS/ADS）。
 * <p>
 * 本地方案 A：各层 host 留空，自动回落到 {@code spring.datasource}
 * （同机多库；Carte 侧再由 host-map 翻译）。
 * 生产方案 B：为 S6（ods/dwd）与 S7（dws/ads）分别配置 host。
 */
@ConfigurationProperties(prefix = "app.data-layer")
public class LayerDatabaseProperties {

    private Endpoint ods = new Endpoint();
    private Endpoint dwd = new Endpoint();
    private Endpoint dws = new Endpoint();
    private Endpoint ads = new Endpoint();

    public Endpoint getOds() {
        return ods;
    }

    public void setOds(Endpoint ods) {
        this.ods = ods;
    }

    public Endpoint getDwd() {
        return dwd;
    }

    public void setDwd(Endpoint dwd) {
        this.dwd = dwd;
    }

    public Endpoint getDws() {
        return dws;
    }

    public void setDws(Endpoint dws) {
        this.dws = dws;
    }

    public Endpoint getAds() {
        return ads;
    }

    public void setAds(Endpoint ads) {
        this.ads = ads;
    }

    public Endpoint byDatabase(String database) {
        if (database == null || database.isBlank()) {
            return ods;
        }
        String src = DataLayerSupport.sourceDatabaseOf(database);
        return switch (src) {
            case DataLayerSupport.CONTROL -> new Endpoint();
            case DataLayerSupport.DWD -> dwd;
            case DataLayerSupport.DWS -> dws;
            case DataLayerSupport.ADS -> ads;
            case DataLayerSupport.ODS -> ods;
            default -> ods;
        };
    }

    public static class Endpoint {
        /** 空则回落 spring.datasource / kettle.target-host */
        private String host = "";
        /** ≤0 则回落 kettle.target-port / MYSQL_PORT */
        private int port = 0;
        /** 空则用标准库名 smart_city_ods 等 */
        private String database = "";
        /** 空则回落 kettle.target-user / MYSQL_USER */
        private String username = "";
        /** 空则回落 kettle.target-password / MYSQL_PASSWORD */
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

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
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
}
