package com.chengde.smartcity.integration.esb;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * AEAI ESB：获取 Token →（库表）gatewayonline /（接口）gatewayService → 创建消费者。
 * 库表/接口流程须同一 Token 串调，见 docs/vendor/库表接口调用流程.md、ESB-注册服务接口.md。
 */
@Component
public class EsbGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(EsbGatewayClient.class);
    private static final int DEFAULT_CONNECT_MS = 5_000;
    private static final int DEFAULT_READ_MS = 10_000;

    private final IntegrationProperties props;
    private final RestTemplate rest;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public EsbGatewayClient(IntegrationProperties props) {
        this.props = props;
        IntegrationProperties.Esb esb = props.getEsb();
        this.connectTimeoutMs = clamp(esb == null ? DEFAULT_CONNECT_MS : esb.getConnectTimeoutMs(), 1_000, 10_000);
        this.readTimeoutMs = clamp(esb == null ? DEFAULT_READ_MS : esb.getReadTimeoutMs(), 3_000, 20_000);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(this.connectTimeoutMs);
        factory.setReadTimeout(this.readTimeoutMs);
        this.rest = new RestTemplate(factory);
    }

    public record ConsumerCredential(String clientId, String clientSecret, String customerId) {
    }

    public record GatewayOnlineResult(String path, String method, String apiId) {
    }

    public record GatewayServiceResult(String path, String method, String param) {
    }

    /** 库表：同一 Token 完成上线 + 创建消费者。 */
    public record TableOnlineCredential(String apiUrl, String apiMethod, ConsumerCredential credential) {
    }

    /** 接口：同一 Token 完成注册服务 + 创建消费者。 */
    public record ApiRegisterCredential(String apiUrl, String apiMethod, ConsumerCredential credential) {
    }

    public boolean isConfigured() {
        IntegrationProperties.Esb esb = props.getEsb();
        return esb != null
                && notBlank(esb.getBaseUrl())
                && notBlank(esb.getAppCode())
                && notBlank(esb.getAppPwd());
    }

    public boolean isTableOnlineConfigured() {
        return isConfigured();
    }

    /** 接口资源：获取 Token 后创建消费者。 */
    public ConsumerCredential createConsumer(String clientName) {
        assertConfigured();
        String token = fetchToken();
        return registerConsumer(token, clientName);
    }

    /**
     * 库表资源：获取 Token → gatewayonline(sql=表名, dbConfigId/sourceName=元数据 source_code) → 同一 Token 创建消费者。
     */
    public TableOnlineCredential provisionTableWithSql(String clientName, String sql,
                                                       String dbConfigId, String sourceName) {
        assertConfigured();
        if (!notBlank(sql)) {
            throw new BusinessException(400, "库表上线 SQL（表名）不能为空");
        }
        if (!notBlank(dbConfigId)) {
            throw new BusinessException(400, "库表上线缺少数据源 source_code（dbConfigId）");
        }
        String token = fetchToken();
        GatewayOnlineResult online = gatewayOnline(token, sql, dbConfigId, sourceName);
        String apiUrl = joinBaseAndPath(publicBase(), online.path());
        String method = notBlank(online.method()) ? online.method().toUpperCase(Locale.ROOT) : "GET";
        ConsumerCredential cred = registerConsumer(token, clientName);
        return new TableOnlineCredential(apiUrl, method, cred);
    }

    /**
     * 接口资源：获取 Token → gatewayService(code/path/param/method) → 同一 Token 创建消费者。
     */
    public ApiRegisterCredential provisionApiService(String clientName, String code, String fullPath,
                                                     String param, String method) {
        return provisionApiService(clientName, code, fullPath, param, method, List.of(), List.of());
    }

    /**
     * 接口资源：获取 Token → gatewayService → 创建消费者（含编目出入参日志）。
     */
    public ApiRegisterCredential provisionApiService(String clientName, String code, String fullPath,
                                                     String param, String method,
                                                     List<Map<String, Object>> requestParams,
                                                     List<Map<String, Object>> responseParams) {
        assertConfigured();
        if (!notBlank(code)) {
            throw new BusinessException(400, "接口注册 code 不能为空");
        }
        if (!notBlank(fullPath)) {
            throw new BusinessException(400, "接口注册 path（业务全路径）不能为空");
        }
        if (!fullPath.startsWith("http://") && !fullPath.startsWith("https://")) {
            throw new BusinessException(400, "接口注册 path 须为完整 URL（含 http/https）");
        }
        log.info("[ESB] 接口串联 开始 clientName={} code={} fullPath={} method={}",
                clientName, code, fullPath, method);
        log.info("[ESB] 接口串联 编目请求入参 requestParams={}", requestParams);
        log.info("[ESB] 接口串联 编目响应出参 responseParams={}", responseParams);
        log.info("[ESB] 接口串联 gatewayService.param={}", param);
        String token = fetchToken();
        GatewayServiceResult registered = gatewayService(token, code, fullPath, param, method);
        String apiUrl = joinBaseAndPath(publicBase(), registered.path());
        String apiMethod = notBlank(registered.method())
                ? registered.method().toUpperCase(Locale.ROOT)
                : (notBlank(method) ? method.toUpperCase(Locale.ROOT) : "POST");
        ConsumerCredential cred = registerConsumer(token, clientName);
        log.info("[ESB] 接口串联 完成 apiUrl={} apiMethod={} clientId={} clientSecret={} customerId={}",
                apiUrl, apiMethod, cred.clientId(), cred.clientSecret(), cred.customerId());
        return new ApiRegisterCredential(apiUrl, apiMethod, cred);
    }

    public GatewayServiceResult gatewayService(String token, String code, String fullPath,
                                               String param, String method) {
        IntegrationProperties.Esb esb = props.getEsb();
        String url = trimSlash(esb.getBaseUrl()) + "/External/services/Gateway/gatewayService";
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code.trim());
        body.put("path", fullPath.trim());
        body.put("param", notBlank(param) ? param : "{}");
        body.put("method", notBlank(method) ? method.toUpperCase(Locale.ROOT) : "POST");
        Map<String, String> headerLog = new LinkedHashMap<>();
        headerLog.put("token", token);
        headerLog.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        logCall("注册服务", "POST", url, headerLog, body);
        try {
            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> json = resp.getBody();
            logResult("注册服务", resp.getStatusCode().value(), json);
            if (json == null) {
                throw new BusinessException(502, "ESB「注册服务」无响应");
            }
            Object codeObj = json.get("code");
            if (codeObj != null && !"200".equals(String.valueOf(codeObj))) {
                String hint = jsonErrorHint(json);
                throw new BusinessException(502, "ESB「注册服务」失败，code=" + codeObj
                        + (hint.isEmpty() ? "" : "，" + hint));
            }
            Object dataObj = json.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                String hint = jsonErrorHint(json);
                throw new BusinessException(502, "ESB「注册服务」返回缺少 data"
                        + (hint.isEmpty() ? "" : "：" + hint));
            }
            String path = str(data.get("path"));
            String respMethod = str(data.get("method"));
            String respParam = str(data.get("param"));
            if (path.isEmpty()) {
                throw new BusinessException(502, "ESB「注册服务」未返回 path");
            }
            log.info("[ESB] 注册服务 响应明细 path={} method={} param={}", path, respMethod, respParam);
            return new GatewayServiceResult(path, respMethod, respParam);
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            logFailure("注册服务", e);
            throw wrapEsbError("注册服务", e);
        }
    }

    public String fetchToken() {
        IntegrationProperties.Esb esb = props.getEsb();
        String url = UriComponentsBuilder
                .fromUriString(trimSlash(esb.getBaseUrl()) + "/SMC/services/ApiAuthenticater/security/authenticate")
                .queryParam("appCode", esb.getAppCode())
                .queryParam("appPwd", esb.getAppPwd())
                .encode()
                .build()
                .toUriString();
        Map<String, Object> inParams = new LinkedHashMap<>();
        inParams.put("appCode", esb.getAppCode());
        inParams.put("appPwd", esb.getAppPwd());
        logCall("获取Token", "POST", url, new LinkedHashMap<>(), inParams);
        try {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
            String body = resp.getBody() == null ? "" : resp.getBody().trim();
            logResult("获取Token", resp.getStatusCode().value(), body);
            if (!resp.getStatusCode().is2xxSuccessful() || body.isEmpty() || looksLikeJson(body)) {
                throw new BusinessException(502, "ESB「获取Token」失败：" + truncate(body.isEmpty() ? "空响应" : body));
            }
            return body;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            logFailure("获取Token", e);
            throw wrapEsbError("获取Token", e);
        }
    }

    public GatewayOnlineResult gatewayOnline(String token, String sql, String dbConfigId, String sourceName) {
        IntegrationProperties.Esb esb = props.getEsb();
        String url = trimSlash(esb.getBaseUrl()) + "/External/services/Gateway/gatewayonline";
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String resolvedDbConfigId = firstNonBlank(dbConfigId, esb.getDbConfigId());
        String resolvedSourceName = firstNonBlank(sourceName, dbConfigId, esb.getSourceName(), "esb");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", sql);
        body.put("dbConfigId", resolvedDbConfigId);
        body.put("customerId", firstNonBlank(esb.getCustomerId(), "esb"));
        body.put("serverName", firstNonBlank(esb.getServerName(), "esb"));
        body.put("basePath", firstNonBlank(esb.getOnlineBasePath(), "http://localhost:9090/"));
        body.put("apiName", firstNonBlank(esb.getApiName(), "esb"));
        body.put("method", firstNonBlank(esb.getOnlineMethod(), "get"));
        body.put("sourceName", resolvedSourceName);
        Map<String, String> headerLog = new LinkedHashMap<>();
        headerLog.put("token", token);
        headerLog.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        logCall("网关上线", "POST", url, headerLog, body);
        try {
            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> json = resp.getBody();
            logResult("网关上线", resp.getStatusCode().value(), json);
            if (json == null) {
                throw new BusinessException(502, "ESB「网关上线」无响应");
            }
            Object code = json.get("code");
            if (code != null && !"200".equals(String.valueOf(code))) {
                String hint = jsonErrorHint(json);
                throw new BusinessException(502, "ESB「网关上线」失败，code=" + code
                        + (hint.isEmpty() ? "" : "，" + hint));
            }
            Object dataObj = json.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                String hint = jsonErrorHint(json);
                throw new BusinessException(502, "ESB「网关上线」返回缺少 data"
                        + (hint.isEmpty() ? "" : "：" + hint));
            }
            String path = str(data.get("path"));
            String method = str(data.get("method"));
            String apiId = str(data.get("apiId"));
            if (path.isEmpty()) {
                throw new BusinessException(502, "ESB「网关上线」未返回 path");
            }
            return new GatewayOnlineResult(path, method, apiId);
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            logFailure("网关上线", e);
            throw wrapEsbError("网关上线", e);
        }
    }

    ConsumerCredential registerConsumer(String token, String clientName) {
        IntegrationProperties.Esb esb = props.getEsb();
        String url = trimSlash(esb.getBaseUrl()) + "/External/services/Gateway/consumer/create";
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("clientName", clientName);
        Map<String, String> headerLog = new LinkedHashMap<>();
        headerLog.put("token", token);
        headerLog.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        logCall("创建消费者", "POST", url, headerLog, body);
        try {
            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> json = resp.getBody();
            logResult("创建消费者", resp.getStatusCode().value(), json);
            if (json == null) {
                throw new BusinessException(502, "ESB「创建消费者」无响应");
            }
            Object code = json.get("code");
            if (code != null && !"200".equals(String.valueOf(code))) {
                String hint = jsonErrorHint(json);
                throw new BusinessException(502, "ESB「创建消费者」失败，code=" + code
                        + (hint.isEmpty() ? "" : "，" + hint));
            }
            Object dataObj = json.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                String hint = jsonErrorHint(json);
                throw new BusinessException(502, "ESB「创建消费者」返回缺少 data"
                        + (hint.isEmpty() ? "" : "：" + hint));
            }
            String clientId = str(data.get("client_id"));
            String clientSecret = str(data.get("client_secret"));
            String customerId = str(data.get("customerId"));
            if (clientId.isEmpty() || clientSecret.isEmpty()) {
                throw new BusinessException(502, "ESB「创建消费者」未返回 client_id / client_secret");
            }
            log.info("[ESB] 创建消费者 响应明细 clientId={} clientSecret={} customerId={}",
                    clientId, clientSecret, customerId);
            return new ConsumerCredential(clientId, clientSecret, customerId);
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            logFailure("创建消费者", e);
            throw wrapEsbError("创建消费者", e);
        }
    }

    private void assertConfigured() {
        if (!isConfigured()) {
            throw new BusinessException(502, "未配置 ESB（ESB_SMC_BASE / ESB_APP_CODE / ESB_APP_PWD），无法调用网关");
        }
    }

    private String publicBase() {
        IntegrationProperties.Esb esb = props.getEsb();
        String gateway = esb == null ? "" : nz(esb.getGatewayBase()).trim();
        if (notBlank(gateway)) {
            return trimSlash(gateway);
        }
        return trimSlash(esb == null ? "" : nz(esb.getBaseUrl()));
    }

    static String joinBaseAndPath(String base, String path) {
        String b = nz(base).trim();
        String p = nz(path).trim();
        if (p.isEmpty()) {
            return b;
        }
        if (p.startsWith("http://") || p.startsWith("https://")) {
            return p;
        }
        if (b.isEmpty()) {
            return p.startsWith("/") ? p : "/" + p;
        }
        if (b.endsWith("/") && p.startsWith("/")) {
            return b.substring(0, b.length() - 1) + p;
        }
        if (!b.endsWith("/") && !p.startsWith("/")) {
            return b + "/" + p;
        }
        return b + p;
    }

    private BusinessException wrapEsbError(String step, RestClientException e) {
        if (e instanceof RestClientResponseException re) {
            String body = re.getResponseBodyAsString();
            String detail = truncate(notBlank(body) ? body : re.getStatusText());
            return new BusinessException(502, "ESB「" + step + "」失败：HTTP "
                    + re.getStatusCode().value() + " " + detail);
        }
        if (e instanceof ResourceAccessException rae) {
            Throwable cause = rae.getMostSpecificCause();
            if (cause instanceof SocketTimeoutException) {
                String cm = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase();
                if (cm.contains("connect")) {
                    return new BusinessException(504, "ESB「" + step + "」连接超时（"
                            + (connectTimeoutMs / 1000) + "秒无法建连），请检查网关 " + esbBase());
                }
                return new BusinessException(504, "ESB「" + step + "」超时（"
                        + (readTimeoutMs / 1000) + "秒未响应），请检查网关 " + esbBase() + " 后重试");
            }
            if (cause instanceof ConnectException) {
                return new BusinessException(502, "无法连接 ESB 网关 " + esbBase() + "：" + cause.getMessage());
            }
            if (cause instanceof UnknownHostException) {
                return new BusinessException(502, "无法解析 ESB 网关主机 " + esbBase() + "：" + cause.getMessage());
            }
            String msg = cause == null ? rae.getMessage() : cause.getMessage();
            return new BusinessException(502, "ESB「" + step + "」调用失败：" + msg);
        }
        return new BusinessException(502, "ESB「" + step + "」失败：" + e.getMessage());
    }

    private String esbBase() {
        IntegrationProperties.Esb esb = props.getEsb();
        return esb == null ? "" : nz(esb.getBaseUrl());
    }

    private void logCall(String step, String method, String url, Map<String, String> headers, Object inParams) {
        log.info("[ESB] {} 请求 method={} url={}", step, method, url);
        log.info("[ESB] {} 请求头 {}", step, headers);
        log.info("[ESB] {} 入参 {}", step, inParams);
    }

    private void logResult(String step, int status, Object outParams) {
        log.info("[ESB] {} 响应 status={} 出参={}", step, status, outParams);
    }

    private void logFailure(String step, RestClientException e) {
        if (e instanceof RestClientResponseException re) {
            log.warn("[ESB] {} 失败 status={} 出参={} message={}",
                    step, re.getStatusCode().value(), re.getResponseBodyAsString(), re.getMessage());
            return;
        }
        log.warn("[ESB] {} 失败 message={}", step, e.getMessage());
    }

    private static String jsonErrorHint(Map<String, Object> json) {
        for (String key : List.of("message", "msg", "error", "errorMsg")) {
            Object v = json.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return truncate(String.valueOf(v).trim());
            }
        }
        return "";
    }

    private static boolean looksLikeJson(String s) {
        return s.startsWith("{") || s.startsWith("[");
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return "";
        }
        for (String v : vals) {
            if (notBlank(v)) {
                return v.trim();
            }
        }
        return "";
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
