package com.chengde.smartcity.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integration")
public class IntegrationProperties {

    private boolean enabled = false;
    private boolean demoFallback = false;
    private OpenMetadata om = new OpenMetadata();
    private DataEase de = new DataEase();
    private DolphinScheduler ds = new DolphinScheduler();
    private Kettle kettle = new Kettle();
    private Storage storage = new Storage();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isDemoFallback() { return demoFallback; }
    public void setDemoFallback(boolean demoFallback) { this.demoFallback = demoFallback; }
    public OpenMetadata getOm() { return om; }
    public void setOm(OpenMetadata om) { this.om = om; }
    public DataEase getDe() { return de; }
    public void setDe(DataEase de) { this.de = de; }
    public DolphinScheduler getDs() { return ds; }
    public void setDs(DolphinScheduler ds) { this.ds = ds; }
    public Kettle getKettle() { return kettle; }
    public void setKettle(Kettle kettle) { this.kettle = kettle; }
    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }

    public static class OpenMetadata {
        private String url = "http://localhost:8585/api/v1";
        private String user = "admin@open-metadata.org";
        private String password = "admin";
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class DataEase {
        private String url = "http://localhost:8100";
        private String embedBase = "http://localhost:8100";
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getEmbedBase() { return embedBase; }
        public void setEmbedBase(String embedBase) { this.embedBase = embedBase; }
    }

    public static class DolphinScheduler {
        private String url = "http://localhost:12345/dolphinscheduler";
        /** 浏览器可达的 DS 基址（外链打开 UI）；默认与 url 相同，Docker 内网场景请单独配 DS_UI_BASE */
        private String uiBase = "";
        private String user = "admin";
        private String password = "dolphinscheduler123";
        private String token = "";
        private String tenantCode = "default";
        /** DS Worker 回调平台后端的基址，默认 host.docker.internal:9090 */
        private String callbackBaseUrl = "http://host.docker.internal:9090";
        /** DS Shell 回调鉴权令牌；为空时使用 password 作为 fallback */
        private String callbackToken = "";
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUiBase() { return uiBase; }
        public void setUiBase(String uiBase) { this.uiBase = uiBase; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getTenantCode() { return tenantCode; }
        public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
        public String getCallbackBaseUrl() { return callbackBaseUrl; }
        public void setCallbackBaseUrl(String callbackBaseUrl) { this.callbackBaseUrl = callbackBaseUrl; }
        public String getCallbackToken() { return callbackToken; }
        public void setCallbackToken(String callbackToken) { this.callbackToken = callbackToken; }

        /** 浏览器打开 DS UI 用的基址 */
        public String resolveUiBase() {
            if (uiBase != null && !uiBase.isBlank()) {
                return uiBase.endsWith("/") ? uiBase.substring(0, uiBase.length() - 1) : uiBase;
            }
            if (url == null || url.isBlank()) {
                return "http://localhost:12345/dolphinscheduler";
            }
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
    }

    public static class Kettle {
        private String url = "http://localhost:18081";
        private String user = "cluster";
        private String password = "cluster";
        // Carte 视角下平台 ODS/DWS 目标库连接（容器内通过 host-gateway 访问宿主机 smart_city）
        private String targetHost = "host.docker.internal";
        private int targetPort = 3306;
        private String targetDatabase = "smart_city_ods";
        private String targetUser = "root";
        private String targetPassword = "";

        // Carte API 在容器网络中的可达地址（给 DS 的 SHELL 调用）
        private String carteHost = "kettle-carte";
        private int cartePort = 8080;

        // 源库 host:port -> Carte 可达 host:port 的映射（如 localhost:3308 -> source-mysql:3306）
        private java.util.Map<String, String> hostMap = new java.util.LinkedHashMap<>();
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getTargetHost() { return targetHost; }
        public void setTargetHost(String targetHost) { this.targetHost = targetHost; }
        public int getTargetPort() { return targetPort; }
        public void setTargetPort(int targetPort) { this.targetPort = targetPort; }
        public String getTargetDatabase() { return targetDatabase; }
        public void setTargetDatabase(String targetDatabase) { this.targetDatabase = targetDatabase; }
        public String getTargetUser() { return targetUser; }
        public void setTargetUser(String targetUser) { this.targetUser = targetUser; }
        public String getTargetPassword() { return targetPassword; }
        public void setTargetPassword(String targetPassword) { this.targetPassword = targetPassword; }
        public String getCarteHost() { return carteHost; }
        public void setCarteHost(String carteHost) { this.carteHost = carteHost; }
        public int getCartePort() { return cartePort; }
        public void setCartePort(int cartePort) { this.cartePort = cartePort; }
        public java.util.Map<String, String> getHostMap() { return hostMap; }
        public void setHostMap(java.util.Map<String, String> hostMap) { this.hostMap = hostMap; }
    }

    public static class Storage {
        private String esUrl = "http://localhost:9200";
        private String seaweedS3Endpoint = "http://localhost:8333";
        private String seaweedBucket = "smartcity-docs";
        private String mongoUrl = "mongodb://localhost:27017";
        private String canalUrl = "http://localhost:19090";
        public String getEsUrl() { return esUrl; }
        public void setEsUrl(String esUrl) { this.esUrl = esUrl; }
        public String getSeaweedS3Endpoint() { return seaweedS3Endpoint; }
        public void setSeaweedS3Endpoint(String seaweedS3Endpoint) { this.seaweedS3Endpoint = seaweedS3Endpoint; }
        public String getSeaweedBucket() { return seaweedBucket; }
        public void setSeaweedBucket(String seaweedBucket) { this.seaweedBucket = seaweedBucket; }
        public String getMongoUrl() { return mongoUrl; }
        public void setMongoUrl(String mongoUrl) { this.mongoUrl = mongoUrl; }
        public String getCanalUrl() { return canalUrl; }
        public void setCanalUrl(String canalUrl) { this.canalUrl = canalUrl; }
    }
}
