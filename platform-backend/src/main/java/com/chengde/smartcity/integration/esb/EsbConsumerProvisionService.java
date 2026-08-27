package com.chengde.smartcity.integration.esb;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.BizPortalSubscription;
import com.chengde.smartcity.exchange.mapper.BizCatalogItemMapper;
import com.chengde.smartcity.exchange.mapper.BizPortalSubscriptionMapper;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovCatalogSubscription;
import com.chengde.smartcity.masterdata.entity.GovMetaDataSource;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogSubscriptionMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaDataSourceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 库表：申请提交后 Token → gatewayonline → 创建消费者，回填 URL / OAuth。
 * 接口：申请提交后 Token → gatewayService → 创建消费者，回填 URL / OAuth。
 * dbConfigId / sourceName = gov_meta_data_source.source_code；sql = 物理表名。
 * 流程见 docs/vendor/库表接口调用流程.md、docs/vendor/ESB-注册服务接口.md。
 */
@Service
public class EsbConsumerProvisionService {

    private static final Logger log = LoggerFactory.getLogger(EsbConsumerProvisionService.class);
    private static final ObjectMapper OM = new ObjectMapper();
    private static final Pattern SAFE_TABLE = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?$");

    private final EsbGatewayClient esbGatewayClient;
    private final BizPortalSubscriptionMapper portalSubscriptionMapper;
    private final BizCatalogItemMapper catalogMapper;
    private final GovCatalogResourceMapper govResourceMapper;
    private final GovCatalogSubscriptionMapper govSubscriptionMapper;
    private final GovMetaDataSourceMapper metaDataSourceMapper;

    public EsbConsumerProvisionService(EsbGatewayClient esbGatewayClient,
                                       BizPortalSubscriptionMapper portalSubscriptionMapper,
                                       BizCatalogItemMapper catalogMapper,
                                       GovCatalogResourceMapper govResourceMapper,
                                       GovCatalogSubscriptionMapper govSubscriptionMapper,
                                       GovMetaDataSourceMapper metaDataSourceMapper) {
        this.esbGatewayClient = esbGatewayClient;
        this.portalSubscriptionMapper = portalSubscriptionMapper;
        this.catalogMapper = catalogMapper;
        this.govResourceMapper = govResourceMapper;
        this.govSubscriptionMapper = govSubscriptionMapper;
        this.metaDataSourceMapper = metaDataSourceMapper;
    }

    public boolean isApiSubscription(BizPortalSubscription sub) {
        return sub != null && "API".equalsIgnoreCase(nz(sub.getResourceType()));
    }

    public boolean isTableSubscription(BizPortalSubscription sub) {
        if (sub == null) {
            return false;
        }
        String t = nz(sub.getResourceType()).toUpperCase(Locale.ROOT);
        return "TABLE".equals(t) || "DATABASE".equals(t) || "DB".equals(t) || "DB_SYNC".equals(t);
    }

    /** 接口/库表审核通过后若提交时未发放则补发（幂等）。 */
    public boolean needsEsbProvision(BizPortalSubscription sub) {
        return isApiSubscription(sub) || isTableSubscription(sub);
    }

    /**
     * 库表资源申请提交后：表名 + 元数据 source_code → Token → gatewayonline → 创建消费者 → 回填。
     */
    public void provisionTableOnSubmit(BizPortalSubscription sub) {
        if (!isTableSubscription(sub)) {
            return;
        }
        if (alreadyProvisioned(sub)) {
            return;
        }
        BizCatalogItem catalog = sub.getCatalogId() == null ? null : catalogMapper.selectById(sub.getCatalogId());
        Map<String, Object> payload = parsePayload(sub.getApplyPayload());
        String clientName = firstNonBlank(
                str(payload.get("systemName")),
                catalog == null ? null : catalog.getTitle(),
                "TABLE-" + sub.getId());
        GovCatalogResource gov = resolveGovResource(sub, catalog);
        String tableName = resolveTableNameOnly(sub, catalog, gov, payload);
        GovMetaDataSource mds = resolveMetaDataSource(gov, payload);
        String sourceCode = nz(mds.getSourceCode()).trim();
        if (!notBlank(sourceCode)) {
            throw new BusinessException(400, "元数据数据源缺少 source_code，无法上线网关");
        }
        EsbGatewayClient.TableOnlineCredential result =
                esbGatewayClient.provisionTableWithSql(clientName, tableName, sourceCode, sourceCode);
        applyCredential(sub, payload, result.credential(), result.apiUrl(), result.apiMethod());
        payload.put("esbSql", tableName);
        payload.put("esbDbConfigId", sourceCode);
        payload.put("esbSourceName", sourceCode);
        payload.put("metaDataSourceId", mds.getId());
        persist(sub, payload);
        log.info("ESB table provisioned on submit subscriptionId={} table={} sourceCode={} url={} clientId={}",
                sub.getId(), tableName, sourceCode, sub.getApiUrl(), sub.getOauthClientId());
    }

    /**
     * 接口资源申请提交后：请求路径 + 入参 → 生成 code → Token → gatewayService → 创建消费者 → 回填。
     */
    public void provisionApiOnSubmit(BizPortalSubscription sub) {
        if (!isApiSubscription(sub)) {
            return;
        }
        if (alreadyProvisioned(sub)) {
            return;
        }
        BizCatalogItem catalog = sub.getCatalogId() == null ? null : catalogMapper.selectById(sub.getCatalogId());
        Map<String, Object> payload = parsePayload(sub.getApplyPayload());
        String clientName = firstNonBlank(
                str(payload.get("systemName")),
                catalog == null ? null : catalog.getTitle(),
                "API-" + sub.getId());
        ApiRegistration reg = resolveApiRegistration(sub, catalog, payload);
        logApiRegistrationPrepared(sub.getId(), clientName, reg);
        EsbGatewayClient.ApiRegisterCredential result = esbGatewayClient.provisionApiService(
                clientName, reg.code, reg.fullPath, reg.param, reg.method,
                reg.requestParams, reg.responseParams);
        applyCredential(sub, payload, result.credential(), result.apiUrl(), result.apiMethod());
        payload.put("esbServiceCode", reg.code);
        payload.put("esbBusinessPath", reg.fullPath);
        payload.put("esbParam", reg.param);
        payload.put("apiPath", reg.apiPath);
        payload.put("apiMethod", reg.method);
        if (reg.requestParams != null && !reg.requestParams.isEmpty()) {
            payload.put("requestParams", reg.requestParams);
        }
        if (reg.responseParams != null && !reg.responseParams.isEmpty()) {
            payload.put("responseParams", reg.responseParams);
        }
        persist(sub, payload);
        log.info("[ESB] 接口注册完成 subscriptionId={} apiUrl={} apiMethod={} clientId={} clientSecret={}",
                sub.getId(), sub.getApiUrl(), sub.getApiMethod(),
                sub.getOauthClientId(), sub.getOauthClientSecret());
    }

    private void logApiRegistrationPrepared(Long subscriptionId, String clientName, ApiRegistration reg) {
        log.info("[ESB] 接口注册开始 subscriptionId={} clientName={}", subscriptionId, clientName);
        log.info("[ESB] 接口注册 编目 apiUrl={} apiPath={} method={} code={} fullPath={}",
                reg.apiUrl, reg.apiPath, reg.method, reg.code, reg.fullPath);
        log.info("[ESB] 接口注册 请求入参 requestParams={}", reg.requestParams);
        log.info("[ESB] 接口注册 响应出参 responseParams={}", reg.responseParams);
        log.info("[ESB] 接口注册 gatewayService.param={}", reg.param);
    }

    /**
     * 审批通过后补发凭证；库表/接口若提交时已发放则跳过。
     */
    public void provisionOnApprove(BizPortalSubscription sub) {
        if (sub == null) {
            return;
        }
        if (isTableSubscription(sub)) {
            if (!alreadyProvisioned(sub)) {
                provisionTableOnSubmit(sub);
            }
            return;
        }
        if (isApiSubscription(sub)) {
            if (!alreadyProvisioned(sub)) {
                provisionApiOnSubmit(sub);
            }
        }
    }

    private boolean alreadyProvisioned(BizPortalSubscription sub) {
        return notBlank(sub.getOauthClientId())
                && notBlank(sub.getOauthClientSecret())
                && notBlank(sub.getApiUrl());
    }

    private void applyCredential(BizPortalSubscription sub, Map<String, Object> payload,
                                 EsbGatewayClient.ConsumerCredential cred,
                                 String apiUrl, String apiMethod) {
        sub.setOauthClientId(cred.clientId());
        sub.setOauthClientSecret(cred.clientSecret());
        sub.setEsbCustomerId(cred.customerId());
        if (notBlank(apiUrl)) {
            sub.setApiUrl(apiUrl);
        }
        if (notBlank(apiMethod)) {
            sub.setApiMethod(apiMethod);
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
    }

    private void persist(BizPortalSubscription sub, Map<String, Object> payload) {
        try {
            sub.setApplyPayload(OM.writeValueAsString(payload));
        } catch (Exception e) {
            throw new BusinessException(500, "写入接口凭证失败");
        }
        portalSubscriptionMapper.updateById(sub);
        syncGovPayload(sub, payload);
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

    /**
     * gatewayonline 的 sql：只填物理表名（如 cd_population）。
     * dbConfigId / sourceName：取表所属元数据数据源的 source_code。
     */
    String resolveTableNameOnly(BizPortalSubscription sub, BizCatalogItem catalog,
                                GovCatalogResource gov, Map<String, Object> payload) {
        String fromPayload = firstNonBlank(str(payload.get("tableName")), str(payload.get("physicalTableName")));
        String table = fromPayload;
        if (!notBlank(table) && gov != null) {
            table = firstNonBlank(gov.getPhysicalTableName(), bindTableFromExt(gov.getExtJson()));
        }
        if (!notBlank(table) && catalog != null) {
            table = firstNonBlank(str(payload.get("resourceName")), catalog.getTitle());
        }
        table = nz(table).trim();
        // 只要裸表名：schema.table → table
        int dot = table.lastIndexOf('.');
        if (dot >= 0 && dot < table.length() - 1) {
            table = table.substring(dot + 1).trim();
        }
        if (!notBlank(table) || "—".equals(table)) {
            throw new BusinessException(400, "库表资源缺少物理表名，无法上线网关服务");
        }
        if (!SAFE_TABLE.matcher(table).matches()) {
            throw new BusinessException(400, "物理表名不合法：" + table);
        }
        return table;
    }

    private GovCatalogResource resolveGovResource(BizPortalSubscription sub, BizCatalogItem catalog) {
        if (catalog != null && catalog.getGovResourceId() != null) {
            GovCatalogResource gov = govResourceMapper.selectById(catalog.getGovResourceId());
            if (gov != null) {
                return gov;
            }
        }
        if (sub.getGovSubscriptionId() != null) {
            GovCatalogSubscription gsub = govSubscriptionMapper.selectById(sub.getGovSubscriptionId());
            if (gsub != null && gsub.getResourceId() != null) {
                return govResourceMapper.selectById(gsub.getResourceId());
            }
        }
        return null;
    }

    /**
     * 按编目绑定解析元数据数据源：
     * - bindSourceKind=META：data_source_id = gov_meta_data_source.id
     * - bindSourceKind=ING：data_source_id = ing_data_source.id，经 ing_source_id 反查
     * - 未标注时先按主键查元数据源，再按 ing_source_id 查
     */
    private GovMetaDataSource resolveMetaDataSource(GovCatalogResource gov, Map<String, Object> payload) {
        Long dsId = null;
        String kind = "";
        if (gov != null) {
            dsId = gov.getDataSourceId();
            Map<String, Object> ext = parseExt(gov.getExtJson());
            kind = firstNonBlank(str(ext.get("bindSourceKind")), str(ext.get("sourceKind")));
        }
        if (dsId == null) {
            Long fromPayload = longOrNull(payload.get("metaDataSourceId"));
            if (fromPayload == null) {
                fromPayload = longOrNull(payload.get("dataSourceId"));
            }
            dsId = fromPayload;
            kind = firstNonBlank(str(payload.get("bindSourceKind")), kind);
        }
        if (dsId == null) {
            throw new BusinessException(400, "库表资源未绑定数据源，无法取 source_code 上线网关");
        }
        GovMetaDataSource mds = null;
        if ("META".equalsIgnoreCase(kind) || kind.isEmpty()) {
            mds = metaDataSourceMapper.selectById(dsId);
        }
        if (mds == null && ("ING".equalsIgnoreCase(kind) || kind.isEmpty())) {
            mds = metaDataSourceMapper.selectOne(new LambdaQueryWrapper<GovMetaDataSource>()
                    .eq(GovMetaDataSource::getIngSourceId, dsId)
                    .last("LIMIT 1"));
        }
        if (mds == null && !"META".equalsIgnoreCase(kind)) {
            mds = metaDataSourceMapper.selectById(dsId);
        }
        if (mds == null) {
            throw new BusinessException(400,
                    "未找到对应的元数据数据源（gov_meta_data_source），dataSourceId=" + dsId
                            + (kind.isEmpty() ? "" : "，bindSourceKind=" + kind));
        }
        return mds;
    }

    private Long longOrNull(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String bindTableFromExt(String extJson) {
        Map<String, Object> ext = parseExt(extJson);
        return firstNonBlank(str(ext.get("bindTableName")), str(ext.get("tableName")));
    }

    private Map<String, Object> parseExt(String extJson) {
        if (!notBlank(extJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> m = OM.readValue(extJson, new TypeReference<Map<String, Object>>() {});
            return m == null ? Map.of() : m;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private ApiRegistration resolveApiRegistration(BizPortalSubscription sub, BizCatalogItem catalog,
                                                   Map<String, Object> payload) {
        Map<String, Object> api = loadApiBlock(catalog, payload);
        String apiUrl = firstNonBlank(str(api.get("apiUrl")), str(payload.get("apiUrl")));
        String apiPath = firstNonBlank(
                str(api.get("apiPath")),
                str(payload.get("apiPath")),
                str(payload.get("requestPath")));
        String method = firstNonBlank(
                str(api.get("apiMethod")),
                str(payload.get("apiMethod")),
                "POST");
        String catalogCode = catalog == null ? "" : nz(catalog.getCatalogCode());
        String code = deriveServiceCode(apiPath, catalogCode, sub.getId());
        String fullPath = requireFullBusinessUrl(apiUrl, apiPath);
        String param = buildGatewayServiceParam(api, payload);
        ApiRegistration reg = new ApiRegistration();
        reg.code = code;
        reg.fullPath = fullPath;
        reg.param = param;
        reg.method = method;
        reg.apiPath = apiPath;
        reg.apiUrl = apiUrl;
        reg.requestParams = extractParamList(api.get("requestParams"), payload.get("requestParams"));
        reg.responseParams = extractParamList(api.get("responseParams"), payload.get("responseParams"));
        return reg;
    }

    private Map<String, Object> loadApiBlock(BizCatalogItem catalog, Map<String, Object> payload) {
        Map<String, Object> fromPayload = new LinkedHashMap<>();
        if (notBlank(str(payload.get("apiUrl")))) {
            fromPayload.put("apiUrl", str(payload.get("apiUrl")));
        }
        if (notBlank(str(payload.get("apiPath")))) {
            fromPayload.put("apiPath", str(payload.get("apiPath")));
        }
        if (notBlank(str(payload.get("requestPath")))) {
            fromPayload.put("apiPath", str(payload.get("requestPath")));
        }
        if (notBlank(str(payload.get("apiMethod")))) {
            fromPayload.put("apiMethod", str(payload.get("apiMethod")));
        }
        if (payload.get("requestParams") != null) {
            fromPayload.put("requestParams", payload.get("requestParams"));
        }
        if (payload.get("responseParams") != null) {
            fromPayload.put("responseParams", payload.get("responseParams"));
        }
        if (notBlank(str(payload.get("apiResultJson")))) {
            fromPayload.put("apiResultJson", str(payload.get("apiResultJson")));
        }
        if (catalog == null || catalog.getGovResourceId() == null) {
            return fromPayload;
        }
        GovCatalogResource gov = govResourceMapper.selectById(catalog.getGovResourceId());
        if (gov == null || !notBlank(gov.getExtJson())) {
            return fromPayload;
        }
        try {
            Map<String, Object> ext = OM.readValue(gov.getExtJson(), new TypeReference<Map<String, Object>>() {});
            Object apiObj = ext == null ? null : ext.get("api");
            if (!(apiObj instanceof Map<?, ?> apiMap)) {
                return fromPayload;
            }
            Map<String, Object> merged = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : apiMap.entrySet()) {
                merged.put(String.valueOf(e.getKey()), e.getValue());
            }
            merged.putAll(fromPayload);
            return merged;
        } catch (Exception e) {
            log.warn("parse catalog api extJson failed: {}", e.getMessage());
            return fromPayload;
        }
    }

    private String requireFullBusinessUrl(String apiUrl, String apiPath) {
        String joined = joinApiUrl(apiUrl, apiPath);
        if (joined.startsWith("http://") || joined.startsWith("https://")) {
            return joined;
        }
        throw new BusinessException(400,
                "接口资源缺少完整业务地址（目标地址 apiUrl + 请求路径 apiPath），无法注册 ESB");
    }

    private String deriveServiceCode(String apiPath, String catalogCode, Long subscriptionId) {
        String path = nz(apiPath).trim();
        if (path.contains("/")) {
            String last = path.substring(path.lastIndexOf('/') + 1).trim();
            if (notBlank(last) && last.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                return last;
            }
        } else if (notBlank(path) && path.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return path;
        }
        if (notBlank(catalogCode)) {
            return catalogCode.replaceAll("[^A-Za-z0-9_]", "_");
        }
        return "api_" + (subscriptionId == null ? System.currentTimeMillis() : subscriptionId);
    }

    private String buildGatewayServiceParam(Map<String, Object> api, Map<String, Object> payload) {
        String raw = firstNonBlank(str(api.get("apiResultJson")), str(payload.get("apiResultJson")));
        if (notBlank(raw) && !"{}".equals(raw.trim())) {
            try {
                OM.readTree(raw);
                return raw.trim();
            } catch (Exception e) {
                log.warn("apiResultJson invalid, fallback to synthesized param: {}", e.getMessage());
            }
        }
        Object example = payload.get("successExample");
        if (example != null) {
            try {
                return OM.writeValueAsString(example);
            } catch (Exception e) {
                log.warn("serialize successExample failed: {}", e.getMessage());
            }
        }
        Map<String, Object> shell = new LinkedHashMap<>();
        shell.put("code", "");
        shell.put("msg", "");
        Map<String, Object> item = new LinkedHashMap<>();
        appendParamDefaults(item, api.get("requestParams"));
        appendParamDefaults(item, payload.get("requestParams"));
        appendParamDefaults(item, api.get("responseParams"));
        appendParamDefaults(item, payload.get("responseParams"));
        shell.put("data", List.of(item));
        shell.put("timestamp", "");
        shell.put("executionTime", 0);
        try {
            return OM.writeValueAsString(shell);
        } catch (Exception e) {
            throw new BusinessException(500, "生成接口注册 param 失败");
        }
    }

    private void appendParamDefaults(Map<String, Object> target, Object rawParams) {
        if (!(rawParams instanceof java.util.List<?> list)) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> p)) {
                continue;
            }
            String name = str(p.get("name"));
            if (!notBlank(name) || target.containsKey(name)) {
                continue;
            }
            target.put(name, defaultValueForType(firstNonBlank(str(p.get("dataType")), str(p.get("type")))));
        }
    }

    private Object defaultValueForType(String type) {
        String t = nz(type).trim().toLowerCase(Locale.ROOT);
        if (t.contains("int") || t.contains("long") || t.contains("num") || t.contains("double")
                || t.contains("float") || t.contains("decimal")) {
            return 0;
        }
        if (t.contains("bool")) {
            return false;
        }
        if (t.contains("array") || t.contains("list")) {
            return new ArrayList<>();
        }
        if (t.contains("object") || t.contains("map")) {
            return new LinkedHashMap<>();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Map<String, Object>> extractParamList(Object primary, Object fallback) {
        Object raw = primary != null ? primary : fallback;
        if (!(raw instanceof java.util.List<?> list) || list.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    copy.put(String.valueOf(e.getKey()), e.getValue());
                }
                out.add(copy);
            }
        }
        return out;
    }

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

    private static final class ApiRegistration {
        private String code = "";
        private String fullPath = "";
        private String param = "{}";
        private String method = "POST";
        private String apiPath = "";
        private String apiUrl = "";
        private List<Map<String, Object>> requestParams = List.of();
        private List<Map<String, Object>> responseParams = List.of();
    }
}
