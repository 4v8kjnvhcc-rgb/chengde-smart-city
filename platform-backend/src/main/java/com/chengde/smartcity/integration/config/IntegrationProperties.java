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
        private String user = "admin";
        private String password = "dolphinscheduler123";
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class Kettle {
        private String url = "http://localhost:8081";
        private String user = "cluster";
        private String password = "cluster";
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
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
