package com.chengde.smartcity.integration.kettle;

import com.chengde.smartcity.integration.config.IntegrationConfig;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Carte(Pentaho Kettle) 客户端：真实 Basic Auth + URL 编码 + XML 响应解析。
 * 任何 Carte 业务失败（result=ERROR 或 HTTP 异常）都转成 FAILED 并携带真实原因，不伪装成功。
 */
@Component
public class KettleClient {

    private static final Logger log = LoggerFactory.getLogger(KettleClient.class);

    private final IntegrationProperties props;
    private final RestTemplate rest;

    public KettleClient(IntegrationProperties props, RestTemplate integrationRestTemplate) {
        this.props = props;
        this.rest = integrationRestTemplate;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String user = props.getKettle().getUser();
        String password = props.getKettle().getPassword();
        if (user != null && !user.isBlank()) {
            String raw = user + ":" + (password == null ? "" : password);
            headers.set(HttpHeaders.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
        return headers;
    }

    public boolean isHealthy() {
        if (!props.isEnabled()) {
            log.warn("Kettle health skipped: app.integration.enabled=false");
            return false;
        }
        String base = props.getKettle() == null ? null : props.getKettle().getUrl();
        if (base == null || base.isBlank()) {
            log.warn("Kettle health skipped: app.integration.kettle.url empty");
            return false;
        }
        try {
            String url = base + "/kettle/status/?xml=Y";
            HttpEntity<Void> req = new HttpEntity<>(authHeaders());
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.GET, req, String.class);
            boolean ok = res.getStatusCode().is2xxSuccessful();
            if (!ok) {
                log.warn("Kettle Carte health check non-2xx url={} status={}", url, res.getStatusCode());
            }
            return ok;
        } catch (Exception e) {
            log.warn("Kettle Carte health check failed url={}/kettle/status/: {}", base, e.getMessage());
            return false;
        }
    }

    /** 注册转换到 Carte（不立即执行），校验 webresult.result=OK。 */
    public Map<String, Object> addTrans(String transName, String ktrXml) {
        IntegrationConfig.requireIntegration(props, "Kettle");
        // registerTrans 仅注册，交由后续 startTrans / DS SHELL 启动，符合“DS 必经”主链
        String url = props.getKettle().getUrl() + "/kettle/registerTrans/?xml=Y&name=" + enc(transName);
        try {
            HttpHeaders headers = authHeaders();
            // 必须 UTF-8：中文步名否则在 Carte 侧损坏/撞名，只剩部分 step
            headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
            byte[] body = (ktrXml == null ? "" : ktrXml).getBytes(StandardCharsets.UTF_8);
            HttpEntity<byte[]> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = rest.postForEntity(url, request, String.class);
            Map<String, Object> out = webResult(transName, response.getBody(), "转换注册");
            String carteId = extractTag(response.getBody(), "id");
            if (carteId != null && !carteId.isBlank()) {
                out.put("carteId", carteId);
            }
            return out;
        } catch (Exception e) {
            log.error("Kettle registerTrans {} failed: {}", transName, e.getMessage());
            return fail(transName, "转换注册失败: " + e.getMessage());
        }
    }

    /** 启动转换执行；参数 URL 编码，校验 webresult。 */
    public Map<String, Object> startTrans(String transName, Map<String, String> params) {
        IntegrationConfig.requireIntegration(props, "Kettle");
        StringBuilder url = new StringBuilder(props.getKettle().getUrl())
                .append("/kettle/startTrans/?xml=Y&name=").append(enc(transName));
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                url.append("&param:").append(enc(e.getKey())).append('=').append(enc(e.getValue()));
            }
        }
        try {
            HttpEntity<Void> req = new HttpEntity<>(authHeaders());
            ResponseEntity<String> response = rest.exchange(url.toString(), HttpMethod.GET, req, String.class);
            return webResult(transName, response.getBody(), "转换启动");
        } catch (Exception e) {
            log.error("Kettle startTrans {} failed: {}", transName, e.getMessage());
            return fail(transName, "转换启动失败: " + e.getMessage());
        }
    }

    public Map<String, Object> stopTrans(String transName) {
        IntegrationConfig.requireIntegration(props, "Kettle");
        String url = props.getKettle().getUrl() + "/kettle/stopTrans/?xml=Y&name=" + enc(transName);
        try {
            HttpEntity<Void> req = new HttpEntity<>(authHeaders());
            ResponseEntity<String> response = rest.exchange(url, HttpMethod.GET, req, String.class);
            return webResult(transName, response.getBody(), "转换停止");
        } catch (Exception e) {
            log.error("Kettle stopTrans {} failed: {}", transName, e.getMessage());
            return fail(transName, "停止失败: " + e.getMessage());
        }
    }

    /** 获取转换执行状态，真实解析 Carte XML 的状态与各 step 行数。 */
    public Map<String, Object> getTransStatus(String transName) {
        return getTransStatus(transName, null);
    }

    public Map<String, Object> getTransStatus(String transName, String carteId) {
        IntegrationConfig.requireIntegration(props, "Kettle");
        StringBuilder url = new StringBuilder(props.getKettle().getUrl())
                .append("/kettle/transStatus/?xml=Y&name=").append(enc(transName));
        if (carteId != null && !carteId.isBlank()) {
            url.append("&id=").append(enc(carteId));
        }
        try {
            HttpEntity<Void> req = new HttpEntity<>(authHeaders());
            ResponseEntity<String> response = rest.exchange(url.toString(), HttpMethod.GET, req, String.class);
            return parseTransStatus(transName, response.getBody());
        } catch (Exception e) {
            log.error("Kettle getTransStatus {} failed: {}", transName, e.getMessage());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("transName", transName);
            out.put("status", "UNKNOWN");
            out.put("message", "获取状态失败: " + e.getMessage());
            return out;
        }
    }

    public Map<String, Object> getTransLog(String transName) {
        return getTransLog(transName, null);
    }

    public Map<String, Object> getTransLog(String transName, String carteId) {
        IntegrationConfig.requireIntegration(props, "Kettle");
        StringBuilder url = new StringBuilder(props.getKettle().getUrl())
                .append("/kettle/transStatus/?xml=Y&name=").append(enc(transName));
        if (carteId != null && !carteId.isBlank()) {
            url.append("&id=").append(enc(carteId));
        }
        try {
            HttpEntity<Void> req = new HttpEntity<>(authHeaders());
            ResponseEntity<String> response = rest.exchange(url.toString(), HttpMethod.GET, req, String.class);
            String body = response.getBody();
            String logText = decodeLoggingString(extractTag(body, "logging_string"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("transName", transName);
            out.put("status", "SUCCESS");
            out.put("log", logText != null ? logText : "");
            return out;
        } catch (Exception e) {
            log.error("Kettle getTransLog {} failed: {}", transName, e.getMessage());
            return fail(transName, "获取日志失败: " + e.getMessage());
        }
    }

    public Map<String, Object> removeTrans(String transName) {
        IntegrationConfig.requireIntegration(props, "Kettle");
        String url = props.getKettle().getUrl() + "/kettle/removeTrans/?xml=Y&name=" + enc(transName);
        try {
            HttpEntity<Void> req = new HttpEntity<>(authHeaders());
            ResponseEntity<String> response = rest.exchange(url, HttpMethod.GET, req, String.class);
            return webResult(transName, response.getBody(), "转换移除");
        } catch (Exception e) {
            log.error("Kettle removeTrans {} failed: {}", transName, e.getMessage());
            return fail(transName, "移除失败: " + e.getMessage());
        }
    }

    // ---------- Carte XML 解析 ----------

    private Map<String, Object> webResult(String transName, String body, String action) {
        String result = extractTag(body, "result");
        String message = extractTag(body, "message");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transName", transName);
        if (result != null && result.toUpperCase().contains("OK")) {
            out.put("status", "SUCCESS");
            out.put("message", action + "成功" + (message == null ? "" : "：" + message));
        } else {
            out.put("status", "FAILED");
            out.put("message", action + "失败：" + (message != null ? message : (body == null ? "无响应" : trim(body))));
        }
        out.put("rawResponse", body == null ? "" : body);
        return out;
    }

    private Map<String, Object> parseTransStatus(String transName, String body) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transName", transName);
        long linesInput = 0;
        long linesOutput = 0;
        long linesRejected = 0;
        long errors = 0;
        int stepCount = 0;
        java.util.List<Map<String, Object>> stepList = new java.util.ArrayList<>();
        String statusDesc = extractTag(body, "status_desc");
        String carteId = extractTag(body, "id");
        String logText = decodeLoggingString(extractTag(body, "logging_string"));
        try {
            Document doc = parseXml(body);
            if (doc != null) {
                NodeList steps = doc.getElementsByTagName("stepstatus");
                stepCount = steps.getLength();
                for (int i = 0; i < steps.getLength(); i++) {
                    Element st = (Element) steps.item(i);
                    long in = longChild(st, "linesInput");
                    long outRows = longChild(st, "linesOutput");
                    long read = longChild(st, "linesRead");
                    long written = longChild(st, "linesWritten");
                    long rej = longChild(st, "linesRejected");
                    long err = longChild(st, "errors");
                    linesInput += in;
                    linesOutput += outRows;
                    linesRejected += rej;
                    errors += err;
                    String stepName = textChild(st, "stepname");
                    String stepStatusDesc = textChild(st, "statusDescription");
                    Map<String, Object> step = new LinkedHashMap<>();
                    step.put("stepName", stepName == null ? ("step_" + i) : stepName);
                    step.put("statusDesc", stepStatusDesc == null ? "" : stepStatusDesc);
                    step.put("status", mapStatus(stepStatusDesc, err));
                    step.put("linesInput", in);
                    step.put("linesOutput", outRows);
                    step.put("linesRead", read);
                    step.put("linesWritten", written);
                    step.put("linesRejected", rej);
                    step.put("errors", err);
                    step.put("seconds", textChild(st, "seconds"));
                    step.put("speed", textChild(st, "speed"));
                    stepList.add(step);
                }
            }
        } catch (Exception e) {
            log.debug("解析 transStatus 失败: {}", e.getMessage());
        }
        String status = mapStatus(statusDesc, errors);
        if ("UNKNOWN".equals(status)) {
            log.warn("Kettle status unmapped transName={} statusDesc={}", transName, statusDesc);
        }
        out.put("status", status);
        out.put("statusDesc", statusDesc == null ? "" : statusDesc);
        out.put("carteId", carteId == null ? "" : carteId);
        out.put("stepCount", stepCount);
        out.put("steps", stepList);
        out.put("linesInput", linesInput);
        out.put("linesOutput", linesOutput);
        out.put("linesRejected", linesRejected);
        out.put("errors", errors);
        out.put("log", logText == null ? "" : logText);
        out.put("rawResponse", body == null ? "" : body);
        return out;
    }

    private String mapStatus(String desc, long errors) {
        if (desc == null) {
            return "UNKNOWN";
        }
        String d = desc.toLowerCase();
        if (d.contains("running")) return "RUNNING";
        if (d.contains("finished")) return errors > 0 ? "FAILED" : "FINISHED";
        if (d.contains("stopped")) return "STOPPED";
        if (d.contains("error") || d.contains("halt")) return "FAILED";
        if (d.contains("waiting") || d.contains("initializing")) return "RUNNING";
        return "UNKNOWN";
    }

    private Document parseXml(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return null;
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private long longChild(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) {
            return 0;
        }
        Node n = nl.item(0);
        String txt = n.getTextContent();
        try {
            return txt == null || txt.isBlank() ? 0 : Long.parseLong(txt.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String textChild(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) {
            return null;
        }
        String txt = nl.item(0).getTextContent();
        return txt == null ? null : txt.trim();
    }

    /** Carte logging_string 常为 Base64(GZIP(text))，失败则原样返回。 */
    private String decodeLoggingString(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = unwrapCdata(raw.trim());
        // 偶发双重实体：&lt;![CDATA[...
        if (s.startsWith("&lt;")) {
            s = s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
            s = unwrapCdata(s.trim());
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(s.replaceAll("\\s", ""));
            try {
                java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(
                        new ByteArrayInputStream(decoded));
                return new String(gz.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception notGzip) {
                return new String(decoded, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return s;
        }
    }

    private String extractTag(String body, String tag) {
        if (body == null) {
            return null;
        }
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int s = body.indexOf(open);
        int e = body.indexOf(close);
        if (s >= 0 && e > s) {
            String inner = body.substring(s + open.length(), e).trim();
            return unwrapCdata(inner);
        }
        return null;
    }

    private String unwrapCdata(String s) {
        if (s.startsWith("<![CDATA[") && s.endsWith("]]>")) {
            return s.substring(9, s.length() - 3);
        }
        return s;
    }

    private Map<String, Object> fail(String transName, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transName", transName);
        out.put("status", "FAILED");
        out.put("message", message);
        return out;
    }

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private String trim(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
