package com.chengde.smartcity.integration.esb;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
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
 * AEAI ESB：获取 Token，再创建消费者，换取 OAuth2 client_id / client_secret。
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

    public boolean isConfigured() {
        IntegrationProperties.Esb esb = props.getEsb();
        return esb != null
                && notBlank(esb.getBaseUrl())
                && notBlank(esb.getAppCode())
                && notBlank(esb.getAppPwd());
    }

    public ConsumerCredential createConsumer(String clientName) {
        // TODO(临时)：测页面信息，跳过真实 Token/创建消费者；测完删除本段
        log.warn("[ESB] 使用写死回参（未调网关） clientName={}", clientName);
        return new ConsumerCredential(
                "C2DC6087-3262-42FB-8C50-21B793730863",
                "TVhWZGZNZVU4K1E5c1VPR1ovT3VFTEVtWTZJVHdRZFA=",
                "inFE2Id6umRCQNMb8+oFHh4QJanMCVhKRPAa7kZWNAmyVwga1m1CJA==");
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

    private ConsumerCredential registerConsumer(String token, String clientName) {
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
            return new ConsumerCredential(clientId, clientSecret, customerId);
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            logFailure("创建消费者", e);
            throw wrapEsbError("创建消费者", e);
        }
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
