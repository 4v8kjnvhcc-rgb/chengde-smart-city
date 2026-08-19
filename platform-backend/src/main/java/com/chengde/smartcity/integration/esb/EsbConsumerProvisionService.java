package com.chengde.smartcity.integration.esb;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.BizPortalSubscription;
import com.chengde.smartcity.exchange.mapper.BizCatalogItemMapper;
import com.chengde.smartcity.exchange.mapper.BizPortalSubscriptionMapper;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovCatalogSubscription;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogSubscriptionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 接口 / 库表资源审批通过后：调 ESB 创建消费者，把 OAuth 凭证写入申请单。
 */
@Service
public class EsbConsumerProvisionService {

    private static final Logger log = LoggerFactory.getLogger(EsbConsumerProvisionService.class);
    private static final ObjectMapper OM = new ObjectMapper();
    /** 库表资源对外调用地址（固定数据服务）。 */
    static final String TABLE_DATASERVICE_URL =
            "http://10.216.131.100:7000/External/service/dataservicePost/1776766691032";

    private final EsbGatewayClient esbGatewayClient;
    private final IntegrationProperties integrationProperties;
    private final BizPortalSubscriptionMapper portalSubscriptionMapper;
    private final BizCatalogItemMapper catalogMapper;
    private final GovCatalogResourceMapper govResourceMapper;
    private final GovCatalogSubscriptionMapper govSubscriptionMapper;

    public EsbConsumerProvisionService(EsbGatewayClient esbGatewayClient,
                                       IntegrationProperties integrationProperties,
                                       BizPortalSubscriptionMapper portalSubscriptionMapper,
                                       BizCatalogItemMapper catalogMapper,
                                       GovCatalogResourceMapper govResourceMapper,
                                       GovCatalogSubscriptionMapper govSubscriptionMapper) {
        this.esbGatewayClient = esbGatewayClient;
        this.integrationProperties = integrationProperties;
        this.portalSubscriptionMapper = portalSubscriptionMapper;
        this.catalogMapper = catalogMapper;
        this.govResourceMapper = govResourceMapper;
        this.govSubscriptionMapper = govSubscriptionMapper;
    }

    public boolean isApiSubscription(BizPortalSubscription sub) {
        return sub != null && "API".equalsIgnoreCase(nz(sub.getResourceType()));
    }

    public boolean isTableSubscription(BizPortalSubscription sub) {
        if (sub == null) {
            return false;
        }
        String t = nz(sub.getResourceType()).toUpperCase();
        return "TABLE".equals(t) || "DATABASE".equals(t) || "DB".equals(t) || "DB_SYNC".equals(t);
    }

    /** 接口与库表审核通过后均需发放 ESB 消费者凭证。 */
    public boolean needsEsbProvision(BizPortalSubscription sub) {
        return isApiSubscription(sub) || isTableSubscription(sub);
    }

    /**
     * 审批通过后发放凭证。已有 client_id 则跳过。文件类资源直接返回。
     * 库表接口地址固定为数据服务 URL。
     */
    public void provisionOnApprove(BizPortalSubscription sub) {
        if (!needsEsbProvision(sub)) {
            return;
        }
        if (notBlank(sub.getOauthClientId()) && notBlank(sub.getOauthClientSecret())) {
            return;
        }
        BizCatalogItem catalog = sub.getCatalogId() == null ? null : catalogMapper.selectById(sub.getCatalogId());
        Map<String, Object> payload = parsePayload(sub.getApplyPayload());
        String clientName = firstNonBlank(
                str(payload.get("systemName")),
                catalog == null ? null : catalog.getTitle(),
                (isTableSubscription(sub) ? "TABLE-" : "API-") + sub.getId());
        ApiMeta meta = isTableSubscription(sub) ? tableApiMeta() : resolveApiMeta(catalog);
        EsbGatewayClient.ConsumerCredential cred = esbGatewayClient.createConsumer(clientName);
        sub.setOauthClientId(cred.clientId());
        sub.setOauthClientSecret(cred.clientSecret());
        sub.setEsbCustomerId(cred.customerId());
        if (isTableSubscription(sub) || (notBlank(meta.url) && !notBlank(sub.getApiUrl()))) {
            sub.setApiUrl(meta.url);
        }
        if (notBlank(meta.method) && (isTableSubscription(sub) || !notBlank(sub.getApiMethod()))) {
            sub.setApiMethod(meta.method);
        }
        payload.put("oauthClientId", cred.clientId());
        payload.put("oauthClientSecret", cred.clientSecret());
        payload.put("esbCustomerId", cred.customerId());
        if (notBlank(sub.getApiUrl())) {
            payload.put("apiUrl", sub.getApiUrl());
        }
        if (notBlank(sub.getApiMethod())) {
            payload.put("apiMethod", sub.getApiMethod());
        }
        try {
            sub.setApplyPayload(OM.writeValueAsString(payload));
        } catch (Exception e) {
            throw new BusinessException(500, "写入接口凭证失败");
        }
        portalSubscriptionMapper.updateById(sub);
        syncGovPayload(sub, payload);
        log.info("ESB consumer provisioned for portal subscription {} clientId={}", sub.getId(), cred.clientId());
    }

    private void syncGovPayload(BizPortalSubscription portal, Map<String, Object> payload) {
        if (portal.getGovSubscriptionId() == null) {
            return;
        }
        GovCatalogSubscription gov = govSubscriptionMapper.selectById(portal.getGovSubscriptionId());
        if (gov == null) {
            return;
        }
        try {
            gov.setApplyPayload(OM.writeValueAsString(payload));
            govSubscriptionMapper.updateById(gov);
        } catch (Exception e) {
            log.warn("sync gov applyPayload oauth failed: {}", e.getMessage());
        }
    }

    private static ApiMeta tableApiMeta() {
        ApiMeta meta = new ApiMeta();
        meta.url = TABLE_DATASERVICE_URL;
        meta.method = "POST";
        return meta;
    }

    private ApiMeta resolveApiMeta(BizCatalogItem catalog) {
        ApiMeta meta = new ApiMeta();
        meta.method = "POST";
        if (catalog == null || catalog.getGovResourceId() == null) {
            return meta;
        }
        GovCatalogResource gov = govResourceMapper.selectById(catalog.getGovResourceId());
        if (gov == null || gov.getExtJson() == null || gov.getExtJson().isBlank()) {
            return meta;
        }
        try {
            Map<String, Object> ext = OM.readValue(gov.getExtJson(), new TypeReference<Map<String, Object>>() {});
            Object apiObj = ext.get("api");
            if (apiObj instanceof Map<?, ?> api) {
                String url = joinApiUrl(str(api.get("apiUrl")), str(api.get("apiPath")));
                meta.method = firstNonBlank(str(api.get("apiMethod")), "POST");
                meta.url = qualifyUrl(url);
            }
        } catch (Exception e) {
            log.warn("parse catalog api extJson failed: {}", e.getMessage());
        }
        return meta;
    }

    /** 目标地址 + 请求路径拼接；路径已是完整 URL 或已包含在地址中则不重复拼。 */
    private static String joinApiUrl(String base, String path) {
        String b = nz(base).trim();
        String p = nz(path).trim();
        if (p.isEmpty()) {
            return b;
        }
        if (p.startsWith("http://") || p.startsWith("https://")) {
            return p;
        }
        if (b.isEmpty()) {
            return p;
        }
        String left = b.replaceAll("/+$", "");
        String right = p.replaceAll("^/+", "");
        if (b.endsWith(p) || b.endsWith("/" + right)) {
            return b;
        }
        return left + "/" + right;
    }

    private String qualifyUrl(String url) {
        if (!notBlank(url)) {
            return "";
        }
        String u = url.trim();
        if (u.startsWith("http://") || u.startsWith("https://")) {
            return u;
        }
        String base = integrationProperties.getEsb() == null ? "" : nz(integrationProperties.getEsb().getGatewayBase());
        if (!notBlank(base)) {
            return u;
        }
        if (base.endsWith("/") && u.startsWith("/")) {
            return base.substring(0, base.length() - 1) + u;
        }
        if (!base.endsWith("/") && !u.startsWith("/")) {
            return base + "/" + u;
        }
        return base + u;
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> m = OM.readValue(json, new TypeReference<Map<String, Object>>() {});
            return m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
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

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static final class ApiMeta {
        private String url = "";
        private String method = "POST";
    }
}
