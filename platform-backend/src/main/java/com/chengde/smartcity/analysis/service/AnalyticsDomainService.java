package com.chengde.smartcity.analysis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.analysis.entity.AnaAnalysisModel;
import com.chengde.smartcity.analysis.entity.AnaDomainModule;
import com.chengde.smartcity.analysis.entity.AnaIndicator;
import com.chengde.smartcity.analysis.entity.AnaModelIndicator;
import com.chengde.smartcity.analysis.entity.AnaModelSample;
import com.chengde.smartcity.analysis.entity.AnaZoneBinding;
import com.chengde.smartcity.analysis.mapper.AnaAnalysisModelMapper;
import com.chengde.smartcity.analysis.mapper.AnaDomainModuleMapper;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorMapper;
import com.chengde.smartcity.analysis.mapper.AnaModelIndicatorMapper;
import com.chengde.smartcity.analysis.mapper.AnaZoneBindingMapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.dataease.DataEaseClient;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.entity.RcManagedTable;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetadataRegistryMapper;
import com.chengde.smartcity.masterdata.mapper.RcManagedTableMapper;
import com.chengde.smartcity.analysis.entity.AnaIndicatorQuery;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorQueryMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsDomainService {

    private static final Set<String> ZONES = Set.of("collect", "govern", "core", "internal", "share");
    private static final Set<String> AGGS = Set.of("COUNT", "SUM", "AVG", "MAX", "MIN", "EXPR");

    private final AnaDomainModuleMapper moduleMapper;
    private final AnaAnalysisModelMapper modelMapper;
    private final AnaZoneBindingMapper zoneBindingMapper;
    private final AnaIndicatorMapper indicatorMapper;
    private final AnaIndicatorQueryMapper indicatorQueryMapper;
    private final AnaModelIndicatorMapper modelIndicatorMapper;
    private final GovMetadataRegistryMapper registryMapper;
    private final RcManagedTableMapper managedTableMapper;
    private final GovCatalogResourceMapper catalogResourceMapper;
    private final AnalysisDemoService analysisDemoService;
    private final AuditService auditService;
    private final IntegrationProperties integrationProperties;
    private final DataEaseClient dataEaseClient;
    private final JdbcTemplate jdbcTemplate;

    public AnalyticsDomainService(AnaDomainModuleMapper moduleMapper,
                                  AnaAnalysisModelMapper modelMapper,
                                  AnaZoneBindingMapper zoneBindingMapper,
                                  AnaIndicatorMapper indicatorMapper,
                                  AnaIndicatorQueryMapper indicatorQueryMapper,
                                  AnaModelIndicatorMapper modelIndicatorMapper,
                                  GovMetadataRegistryMapper registryMapper,
                                  RcManagedTableMapper managedTableMapper,
                                  GovCatalogResourceMapper catalogResourceMapper,
                                  AnalysisDemoService analysisDemoService,
                                  AuditService auditService,
                                  IntegrationProperties integrationProperties,
                                  DataEaseClient dataEaseClient,
                                  JdbcTemplate jdbcTemplate) {
        this.moduleMapper = moduleMapper;
        this.modelMapper = modelMapper;
        this.zoneBindingMapper = zoneBindingMapper;
        this.indicatorMapper = indicatorMapper;
        this.indicatorQueryMapper = indicatorQueryMapper;
        this.modelIndicatorMapper = modelIndicatorMapper;
        this.registryMapper = registryMapper;
        this.managedTableMapper = managedTableMapper;
        this.catalogResourceMapper = catalogResourceMapper;
        this.analysisDemoService = analysisDemoService;
        this.auditService = auditService;
        this.integrationProperties = integrationProperties;
        this.dataEaseClient = dataEaseClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> domainOverview(String domain) {
        String d = normalizeDomain(domain);
        List<AnaDomainModule> modules = moduleMapper.selectList(new LambdaQueryWrapper<AnaDomainModule>()
                .eq(AnaDomainModule::getDomainCode, d)
                .orderByAsc(AnaDomainModule::getSortOrder));
        long dataOps = modules.stream().filter(m -> "DATA_OPS".equals(m.getModuleType())).count();
        long analysis = modules.stream().filter(m -> "ANALYSIS".equals(m.getModuleType())).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("domain", d);
        out.put("modules", modules);
        out.put("dataOpsCount", dataOps);
        out.put("analysisCount", analysis);
        out.put("totalModules", modules.size());
        out.put("bindingCount", zoneBindingMapper.selectCount(new LambdaQueryWrapper<AnaZoneBinding>()
                .eq(AnaZoneBinding::getDomainCode, d).eq(AnaZoneBinding::getStatus, "ACTIVE")));
        out.put("indicatorCount", indicatorMapper.selectCount(new LambdaQueryWrapper<AnaIndicator>()
                .eq(AnaIndicator::getDomainCode, d).eq(AnaIndicator::getStatus, "ACTIVE")));
        out.put("modelCount", modelMapper.selectCount(new LambdaQueryWrapper<AnaAnalysisModel>()
                .eq(AnaAnalysisModel::getDomainCode, d)));
        out.put("dataEaseHealthy", integrationProperties.isEnabled() && dataEaseClient.isHealthy());
        return out;
    }

    public Map<String, Object> moduleDetail(String mCode) {
        AnaDomainModule mod = getModule(mCode);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("module", mod);
        if ("ANALYSIS".equals(mod.getModuleType())) {
            AnaAnalysisModel model = modelMapper.selectOne(new LambdaQueryWrapper<AnaAnalysisModel>()
                    .eq(AnaAnalysisModel::getMCode, mCode)
                    .last("LIMIT 1"));
            out.put("model", model);
            if (model != null) {
                out.put("indicators", listIndicatorsForModel(model.getId()));
                List<AnaModelSample> samples = analysisDemoService.samples(model.getId());
                out.put("sampleCount", samples.size());
                out.put("samplesPreview", samples.size() > 20 ? samples.subList(0, 20) : samples);
            }
        }
        return out;
    }

    @Transactional
    public Map<String, Object> runDataOps(UserPrincipal operator, String mCode) {
        AnaDomainModule mod = getModule(mCode);
        if (!"DATA_OPS".equals(mod.getModuleType())) {
            throw new BusinessException(400, "不是数据区设计/运维模块");
        }
        mod.setLastRunAt(LocalDateTime.now());
        mod.setLastMessage("区设计任务已记台账（未触发外部调度集群）");
        mod.setStatus("LEDGER");
        moduleMapper.updateById(mod);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_DOMAIN_RUN", mod.getDomainCode(), mCode, mod.getModuleName());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mCode", mCode);
        out.put("status", "LEDGER");
        out.put("message", mod.getLastMessage());
        return out;
    }

    public Map<String, Object> issueModuleEmbed(UserPrincipal operator, String mCode) {
        AnaDomainModule mod = getModule(mCode);
        if (!"ANALYSIS".equals(mod.getModuleType())) {
            throw new BusinessException(400, "不是分析模型模块");
        }
        rejectPopulationBiEmbed(mod.getDomainCode());
        String targetId = mod.getDeDashboardId() != null ? mod.getDeDashboardId() : "demo";
        Map<String, Object> token = new HashMap<>(analysisDemoService.issueEmbedToken(operator, "model", targetId));
        token.put("module", mod);
        return token;
    }

    public Map<String, Object> issueModelEmbedById(UserPrincipal operator, Long modelId) {
        AnaAnalysisModel model = modelMapper.selectById(modelId);
        if (model == null) throw new BusinessException(404, "分析模型不存在");
        rejectPopulationBiEmbed(model.getDomainCode());
        String targetId = model.getDeDashboardId() != null ? model.getDeDashboardId() : "demo";
        Map<String, Object> token = new HashMap<>(analysisDemoService.issueEmbedToken(operator, "model", targetId));
        token.put("model", model);
        token.put("indicators", listIndicatorsForModel(modelId));
        return token;
    }

    /** 人口域取消 DataEase/BI：模型结果走自研样例表，禁止签发嵌入令牌。 */
    private static void rejectPopulationBiEmbed(String domainCode) {
        if (domainCode != null && "population".equalsIgnoreCase(domainCode.trim())) {
            throw new BusinessException(400, "人口域已取消 DataEase/BI 展示，请使用模型样例/结果表预览");
        }
    }

    public List<AnaModelSample> listModelSamples(Long modelId) {
        AnaAnalysisModel model = modelMapper.selectById(modelId);
        if (model == null) throw new BusinessException(404, "分析模型不存在");
        return analysisDemoService.samples(modelId);
    }

    // ---------- zone bindings ----------

    public List<AnaZoneBinding> listBindings(String domain, String zone) {
        String d = normalizeDomain(domain);
        String z = normalizeZone(zone);
        return zoneBindingMapper.selectList(new LambdaQueryWrapper<AnaZoneBinding>()
                .eq(AnaZoneBinding::getDomainCode, d)
                .eq(AnaZoneBinding::getZoneCode, z)
                .eq(AnaZoneBinding::getStatus, "ACTIVE")
                .orderByDesc(AnaZoneBinding::getId));
    }

    public List<Map<String, Object>> zoneCandidates(String domain, String zone) {
        String z = normalizeZone(zone);
        List<Map<String, Object>> out = new ArrayList<>();
        if ("collect".equals(z) || "govern".equals(z)) {
            LambdaQueryWrapper<GovMetadataRegistry> q = new LambdaQueryWrapper<GovMetadataRegistry>()
                    .eq(GovMetadataRegistry::getEntryType, "TABLE")
                    .orderByDesc(GovMetadataRegistry::getId)
                    .last("LIMIT 80");
            if ("collect".equals(z)) {
                q.and(w -> w.eq(GovMetadataRegistry::getDataLayer, "ODS")
                        .or().eq(GovMetadataRegistry::getDataLayer, "SOURCE")
                        .or().isNull(GovMetadataRegistry::getDataLayer)
                        .or().eq(GovMetadataRegistry::getDataLayer, ""));
            } else {
                q.and(w -> w.eq(GovMetadataRegistry::getDataLayer, "DWD")
                        .or().like(GovMetadataRegistry::getPhysicalTableName, "dwd_"));
            }
            for (GovMetadataRegistry e : registryMapper.selectList(q)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("assetType", "METADATA");
                m.put("assetRef", e.getEntryCode());
                m.put("assetName", e.getEntryName());
                m.put("physicalTable", e.getPhysicalTableName());
                m.put("metaEntryCode", e.getEntryCode());
                m.put("dataLayer", e.getDataLayer());
                out.add(m);
            }
        } else if ("core".equals(z) || "internal".equals(z)) {
            for (RcManagedTable mt : managedTableMapper.selectList(new LambdaQueryWrapper<RcManagedTable>()
                    .eq(RcManagedTable::getStatus, "ACTIVE").orderByDesc(RcManagedTable::getId).last("LIMIT 80"))) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("assetType", "MANAGED");
                m.put("assetRef", mt.getPhysicalTable());
                m.put("assetName", mt.getPhysicalTable());
                m.put("physicalTable", mt.getPhysicalTable());
                m.put("metaEntryCode", mt.getMetaEntryCode());
                m.put("dataLayer", "DWS");
                out.add(m);
            }
        } else {
            for (GovCatalogResource r : catalogResourceMapper.selectList(new LambdaQueryWrapper<GovCatalogResource>()
                    .orderByDesc(GovCatalogResource::getId).last("LIMIT 80"))) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("assetType", "CATALOG");
                m.put("assetRef", r.getResourceCode() != null ? r.getResourceCode() : String.valueOf(r.getId()));
                m.put("assetName", r.getResourceName());
                m.put("physicalTable", r.getPhysicalTableName());
                m.put("metaEntryCode", r.getMetadataEntryCode());
                m.put("dataLayer", "SHARE");
                out.add(m);
            }
        }
        return out;
    }

    @Transactional
    public Long bindAsset(UserPrincipal operator, String domain, String zone, Map<String, Object> body) {
        String d = normalizeDomain(domain);
        String z = normalizeZone(zone);
        String assetType = str(body.get("assetType"), "METADATA").toUpperCase(Locale.ROOT);
        String assetRef = required(body.get("assetRef"), "assetRef").toString();
        AnaZoneBinding exists = zoneBindingMapper.selectOne(new LambdaQueryWrapper<AnaZoneBinding>()
                .eq(AnaZoneBinding::getDomainCode, d)
                .eq(AnaZoneBinding::getZoneCode, z)
                .eq(AnaZoneBinding::getAssetType, assetType)
                .eq(AnaZoneBinding::getAssetRef, assetRef)
                .last("LIMIT 1"));
        if (exists != null && "ACTIVE".equalsIgnoreCase(exists.getStatus())) {
            throw new BusinessException(400, "该资产已挂载到本区");
        }
        if ("govern".equals(z)) {
            String layer = str(body.get("dataLayer"), "");
            String table = str(body.get("physicalTable"), "");
            if ("DWS".equalsIgnoreCase(layer) || "ADS".equalsIgnoreCase(layer)) {
                throw new BusinessException(400, "治理反馈区不宜挂载资源层表，请挂过程层或问题数据");
            }
            if (table.toLowerCase(Locale.ROOT).startsWith("ads_")) {
                throw new BusinessException(400, "治理反馈区不宜挂载 ADS 表");
            }
        }
        AnaZoneBinding b = exists != null ? exists : new AnaZoneBinding();
        b.setDomainCode(d);
        b.setZoneCode(z);
        b.setAssetType(assetType);
        b.setAssetRef(assetRef);
        b.setAssetName(str(body.get("assetName"), assetRef));
        b.setPhysicalTable(str(body.get("physicalTable"), null));
        b.setMetaEntryCode(str(body.get("metaEntryCode"), null));
        b.setDataLayer(str(body.get("dataLayer"), null));
        b.setRemark(str(body.get("remark"), null));
        b.setStatus("ACTIVE");
        b.setCreatedBy(operator.getUsername());
        b.setCreatedAt(LocalDateTime.now());
        if (exists == null) zoneBindingMapper.insert(b);
        else zoneBindingMapper.updateById(b);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_ZONE_BIND", d + ":" + z, assetRef, assetType);
        return b.getId();
    }

    @Transactional
    public void unbindAsset(UserPrincipal operator, Long id) {
        AnaZoneBinding b = zoneBindingMapper.selectById(id);
        if (b == null) throw new BusinessException(404, "挂载不存在");
        b.setStatus("INACTIVE");
        zoneBindingMapper.updateById(b);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_ZONE_UNBIND", b.getDomainCode() + ":" + b.getZoneCode(), b.getAssetRef(), "INACTIVE");
    }

    // ---------- indicators ----------

    public List<AnaIndicator> listIndicators(String domain) {
        String d = normalizeDomain(domain);
        List<AnaIndicator> list = indicatorMapper.selectList(new LambdaQueryWrapper<AnaIndicator>()
                .eq(AnaIndicator::getDomainCode, d)
                .eq(AnaIndicator::getStatus, "ACTIVE")
                .orderByDesc(AnaIndicator::getId));
        Set<Long> qids = list.stream().map(AnaIndicator::getQueryId).filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, String> qmap = new HashMap<>();
        if (!qids.isEmpty()) {
            for (AnaIndicatorQuery q : indicatorQueryMapper.selectBatchIds(qids)) {
                qmap.put(q.getId(), q.getQueryNo());
            }
        }
        for (AnaIndicator ind : list) {
            if (ind.getQueryId() != null && qmap.containsKey(ind.getQueryId())) {
                ind.setQueryNo(qmap.get(ind.getQueryId()));
            } else {
                ind.setQueryNo(ind.getIndicatorCode());
            }
            if (ind.getResultField() == null || ind.getResultField().isBlank()) {
                ind.setResultField(str(ind.getSourceColumn(), ind.getIndicatorCode()));
            }
            if (ind.getFieldName() == null || ind.getFieldName().isBlank()) {
                ind.setFieldName(ind.getIndicatorCode());
            }
            if (ind.getFieldType() == null || ind.getFieldType().isBlank()) {
                ind.setFieldType("字符串");
            }
        }
        return list;
    }

    public List<Map<String, String>> listIndicatorDatasources(String domain) {
        normalizeDomain(domain);
        List<Map<String, String>> out = new ArrayList<>();
        out.add(Map.of("key", "platform", "name", "平台库"));
        out.add(Map.of("key", "population_demo", "name", "人口主题库"));
        out.add(Map.of("key", "legal_demo", "name", "法人主题库"));
        return out;
    }

    /** 解析 SELECT 结果字段（优先 AS 别名） */
    public List<Map<String, Object>> parseIndicatorSql(Map<String, Object> body) {
        String sql = required(body.get("sqlText"), "sqlText").toString().trim();
        if (!sql.toLowerCase(Locale.ROOT).startsWith("select")) {
            throw new BusinessException(400, "仅支持 SELECT 查询语句");
        }
        List<Map<String, Object>> fields = extractSelectAliases(sql);
        if (fields.isEmpty()) {
            throw new BusinessException(400, "未能解析出结果字段，请为列指定 AS 别名");
        }
        return fields;
    }

    public Map<String, Object> previewIndicatorSql(Map<String, Object> body) {
        String sql = required(body.get("sqlText"), "sqlText").toString().trim();
        if (!sql.toLowerCase(Locale.ROOT).startsWith("select")) {
            throw new BusinessException(400, "仅支持 SELECT 查询语句");
        }
        int timeout = 60;
        try {
            if (body.get("timeoutSec") != null) timeout = Math.max(5, Integer.parseInt(String.valueOf(body.get("timeoutSec"))));
        } catch (Exception ignored) { /* keep default */ }
        String wrapped = "SELECT * FROM (" + sql.replaceAll(";\\s*$", "") + ") _ana_ind_preview LIMIT 20";
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            Integer old = null;
            try {
                old = jdbcTemplate.getQueryTimeout();
                jdbcTemplate.setQueryTimeout(timeout);
            } catch (Exception ignored) { /* optional */ }
            try {
                List<Map<String, Object>> raw = jdbcTemplate.queryForList(wrapped);
                if (!raw.isEmpty()) {
                    columns.addAll(raw.get(0).keySet());
                } else {
                    columns.addAll(extractSelectAliases(sql).stream()
                            .map(f -> String.valueOf(f.get("resultField"))).toList());
                }
                rows.addAll(raw);
            } finally {
                if (old != null) {
                    try { jdbcTemplate.setQueryTimeout(old); } catch (Exception ignored) { /* ignore */ }
                }
            }
        } catch (Exception e) {
            // 预览失败仍返回解析字段，便于先保存语句
            List<Map<String, Object>> parsed = extractSelectAliases(sql);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("columns", parsed.stream().map(f -> String.valueOf(f.get("resultField"))).toList());
            out.put("rows", List.of());
            out.put("message", "预览执行失败：" + e.getMessage());
            out.put("fields", parsed);
            return out;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("columns", columns);
        out.put("rows", rows);
        out.put("fields", enrichFieldsFromMeta(columns, sql));
        return out;
    }

    @Transactional
    public Long createIndicatorSql(UserPrincipal operator, String domain, Map<String, Object> body) {
        String d = normalizeDomain(domain);
        String sql = required(body.get("sqlText"), "sqlText").toString().trim();
        String dsKey = str(body.get("datasourceKey"), "platform");
        String dsName = str(body.get("datasourceName"), datasourceLabel(dsKey));
        int timeout = 60;
        try {
            if (body.get("timeoutSec") != null) timeout = Math.max(5, Integer.parseInt(String.valueOf(body.get("timeoutSec"))));
        } catch (Exception ignored) { /* default */ }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields;
        Object rawFields = body.get("fields");
        if (rawFields instanceof List<?> list && !list.isEmpty()) {
            fields = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, v) -> row.put(String.valueOf(k), v));
                    fields.add(row);
                }
            }
        } else {
            fields = parseIndicatorSql(Map.of("sqlText", sql));
        }
        if (fields.isEmpty()) throw new BusinessException(400, "请先解析结果字段");

        String slug = str(body.get("querySlug"), fields.get(0).get("fieldName") != null
                ? String.valueOf(fields.get(0).get("fieldName"))
                : "query");
        slug = slug.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
        if (slug.isBlank()) slug = "query";
        long seq = indicatorQueryMapper.selectCount(new LambdaQueryWrapper<AnaIndicatorQuery>()
                .eq(AnaIndicatorQuery::getDomainCode, d));
        String queryNo = d + ".ind_" + slug + "_sql" + seq;

        AnaIndicatorQuery q = new AnaIndicatorQuery();
        q.setDomainCode(d);
        q.setQueryNo(queryNo);
        q.setDatasourceKey(dsKey);
        q.setDatasourceName(dsName);
        q.setTimeoutSec(timeout);
        q.setSqlText(sql);
        q.setStatus("ACTIVE");
        q.setCreatedBy(operator.getUsername());
        q.setCreatedAt(LocalDateTime.now());
        q.setUpdatedAt(LocalDateTime.now());
        indicatorQueryMapper.insert(q);

        int i = 0;
        for (Map<String, Object> f : fields) {
            String resultField = required(f.get("resultField"), "resultField").toString();
            String fieldName = str(f.get("fieldName"), "ind_" + resultField);
            String indName = str(f.get("indicatorName"), resultField);
            AnaIndicator ind = new AnaIndicator();
            ind.setDomainCode(d);
            ind.setQueryId(q.getId());
            ind.setResultField(resultField);
            ind.setFieldType(str(f.get("fieldType"), "字符串"));
            ind.setFieldLength(intVal(f.get("fieldLength")));
            ind.setFieldPrecision(intVal(f.get("fieldPrecision")));
            ind.setFieldName(fieldName);
            ind.setIndicatorCode(queryNo + "_" + (++i));
            ind.setIndicatorName(indName);
            ind.setSourceTable(null);
            ind.setSourceColumn(resultField);
            ind.setAggFunc("EXPR");
            ind.setExprText(sql);
            ind.setStatus("ACTIVE");
            ind.setCreatedBy(operator.getUsername());
            ind.setCreatedAt(LocalDateTime.now());
            ind.setUpdatedAt(LocalDateTime.now());
            indicatorMapper.insert(ind);
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_SQL_CREATE", d, queryNo, "fields=" + fields.size());
        return q.getId();
    }

    @Transactional
    public Long createIndicator(UserPrincipal operator, String domain, Map<String, Object> body) {
        // 兼容旧接口；若带 sqlText 则走语句模式
        if (body.get("sqlText") != null && !String.valueOf(body.get("sqlText")).isBlank()) {
            return createIndicatorSql(operator, domain, body);
        }
        String d = normalizeDomain(domain);
        String agg = str(body.get("aggFunc"), "COUNT").toUpperCase(Locale.ROOT);
        if (!AGGS.contains(agg)) throw new BusinessException(400, "aggFunc 不支持: " + agg);
        String table = required(body.get("sourceTable"), "sourceTable").toString();
        AnaIndicator ind = new AnaIndicator();
        ind.setDomainCode(d);
        ind.setIndicatorCode(str(body.get("indicatorCode"), "IND_" + System.currentTimeMillis()));
        ind.setIndicatorName(required(body.get("indicatorName"), "indicatorName").toString());
        ind.setSourceTable(table);
        ind.setSourceColumn(str(body.get("sourceColumn"), null));
        ind.setResultField(str(body.get("sourceColumn"), ind.getIndicatorCode()));
        ind.setFieldName(ind.getIndicatorCode());
        ind.setFieldType("数值");
        ind.setAggFunc(agg);
        ind.setExprText(str(body.get("exprText"), null));
        ind.setUnitLabel(str(body.get("unitLabel"), null));
        ind.setDescription(str(body.get("description"), null));
        ind.setStatus("ACTIVE");
        ind.setCreatedBy(operator.getUsername());
        ind.setCreatedAt(LocalDateTime.now());
        ind.setUpdatedAt(LocalDateTime.now());
        indicatorMapper.insert(ind);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_CREATE", d, ind.getIndicatorCode(), ind.getIndicatorName());
        return ind.getId();
    }

    @Transactional
    public void updateIndicator(UserPrincipal operator, Long id, Map<String, Object> body) {
        AnaIndicator ind = indicatorMapper.selectById(id);
        if (ind == null || !"ACTIVE".equalsIgnoreCase(ind.getStatus())) {
            throw new BusinessException(404, "指标不存在");
        }
        if (body.get("indicatorName") != null) ind.setIndicatorName(String.valueOf(body.get("indicatorName")));
        if (body.get("fieldName") != null) ind.setFieldName(String.valueOf(body.get("fieldName")));
        if (body.get("sourceTable") != null) ind.setSourceTable(String.valueOf(body.get("sourceTable")));
        if (body.containsKey("sourceColumn")) ind.setSourceColumn(str(body.get("sourceColumn"), null));
        if (body.get("aggFunc") != null) {
            String agg = String.valueOf(body.get("aggFunc")).toUpperCase(Locale.ROOT);
            if (!AGGS.contains(agg)) throw new BusinessException(400, "aggFunc 不支持");
            ind.setAggFunc(agg);
        }
        if (body.containsKey("exprText")) ind.setExprText(str(body.get("exprText"), null));
        if (body.containsKey("unitLabel")) ind.setUnitLabel(str(body.get("unitLabel"), null));
        if (body.containsKey("description")) ind.setDescription(str(body.get("description"), null));
        if (body.containsKey("fieldType")) ind.setFieldType(str(body.get("fieldType"), null));
        ind.setUpdatedAt(LocalDateTime.now());
        indicatorMapper.updateById(ind);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_UPDATE", ind.getDomainCode(), ind.getIndicatorCode(), ind.getIndicatorName());
    }

    @Transactional
    public void deleteIndicator(UserPrincipal operator, Long id) {
        AnaIndicator ind = indicatorMapper.selectById(id);
        if (ind == null) throw new BusinessException(404, "指标不存在");
        ind.setStatus("INACTIVE");
        ind.setUpdatedAt(LocalDateTime.now());
        indicatorMapper.updateById(ind);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_DELETE", ind.getDomainCode(), ind.getIndicatorCode(), "INACTIVE");
    }

    private static String datasourceLabel(String key) {
        return switch (key) {
            case "population_demo" -> "人口主题库";
            case "legal_demo" -> "法人主题库";
            default -> "平台库";
        };
    }

    private static List<Map<String, Object>> extractSelectAliases(String sql) {
        String cleaned = sql.replaceAll("(?is)/\\*.*?\\*/", " ").replaceAll("--.*?(\\r?\\n|$)", "\n");
        Matcher m = Pattern.compile("(?is)^\\s*select\\s+(.*?)\\s+from\\s").matcher(cleaned);
        if (!m.find()) return List.of();
        String selectList = m.group(1).trim();
        if ("*".equals(selectList)) return List.of();
        List<String> parts = splitSelectItems(selectList);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            String alias = null;
            Matcher as = Pattern.compile("(?i)\\bas\\s+([`\"]?)([a-zA-Z_][\\w]*)\\1\\s*$").matcher(p);
            if (as.find()) {
                alias = as.group(2);
            } else {
                Matcher bare = Pattern.compile("([`\"]?)([a-zA-Z_][\\w]*)\\1\\s*$").matcher(p);
                if (bare.find()) alias = bare.group(2);
            }
            if (alias == null || !names.add(alias)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resultField", alias);
            row.put("fieldType", guessType(p));
            row.put("fieldLength", guessType(p).contains("浮") ? 40 : (guessType(p).contains("整") ? 20 : 64));
            row.put("fieldPrecision", guessType(p).contains("浮") ? 2 : 0);
            row.put("indicatorName", alias);
            row.put("fieldName", "ind_" + alias);
            out.add(row);
        }
        return out;
    }

    private static List<String> splitSelectItems(String selectList) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < selectList.length(); i++) {
            char c = selectList.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            if (c == ',' && depth == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (!cur.isEmpty()) parts.add(cur.toString());
        return parts;
    }

    private static String guessType(String expr) {
        String e = expr.toLowerCase(Locale.ROOT);
        if (e.contains("count(") || e.contains("sum(") || e.contains("avg(") || e.contains("/") || e.contains("rate")) {
            return e.contains("/") || e.contains("avg(") || e.contains("rate") ? "浮点数" : "整数";
        }
        if (e.contains("date_format") || e.contains("concat") || e.contains("'")) return "字符串";
        return "字符串";
    }

    private List<Map<String, Object>> enrichFieldsFromMeta(List<String> columns, String sql) {
        List<Map<String, Object>> parsed = extractSelectAliases(sql);
        Map<String, Map<String, Object>> byName = new HashMap<>();
        for (Map<String, Object> p : parsed) byName.put(String.valueOf(p.get("resultField")), p);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String col : columns) {
            Map<String, Object> base = byName.getOrDefault(col, new LinkedHashMap<>());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resultField", col);
            row.put("fieldType", base.getOrDefault("fieldType", "字符串"));
            row.put("fieldLength", base.getOrDefault("fieldLength", 64));
            row.put("fieldPrecision", base.getOrDefault("fieldPrecision", 0));
            row.put("indicatorName", base.getOrDefault("indicatorName", col));
            row.put("fieldName", base.getOrDefault("fieldName", "ind_" + col));
            out.add(row);
        }
        return out;
    }

    private static Integer intVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    // ---------- models ----------

    public List<Map<String, Object>> listModels(String domain) {
        String d = normalizeDomain(domain);
        List<AnaAnalysisModel> models = modelMapper.selectList(new LambdaQueryWrapper<AnaAnalysisModel>()
                .eq(AnaAnalysisModel::getDomainCode, d)
                .orderByAsc(AnaAnalysisModel::getModelCode));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AnaAnalysisModel m : models) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("modelCode", m.getModelCode());
            row.put("modelName", m.getModelName());
            row.put("mCode", m.getMCode());
            row.put("deDashboardId", m.getDeDashboardId());
            row.put("dimensionJson", m.getDimensionJson());
            row.put("description", m.getDescription());
            row.put("status", m.getStatus());
            row.put("indicators", listIndicatorsForModel(m.getId()));
            out.add(row);
        }
        return out;
    }

    @Transactional
    public void updateModelDesign(UserPrincipal operator, Long modelId, Map<String, Object> body) {
        AnaAnalysisModel model = modelMapper.selectById(modelId);
        if (model == null) throw new BusinessException(404, "分析模型不存在");
        boolean population = model.getDomainCode() != null
                && "population".equalsIgnoreCase(model.getDomainCode().trim());
        if (!population && body.containsKey("deDashboardId")) {
            model.setDeDashboardId(str(body.get("deDashboardId"), null));
        }
        if (body.containsKey("dimensionJson")) model.setDimensionJson(str(body.get("dimensionJson"), null));
        if (body.containsKey("description")) model.setDescription(str(body.get("description"), null));
        if (body.containsKey("modelName")) model.setModelName(String.valueOf(body.get("modelName")));
        model.setUpdatedAt(LocalDateTime.now());
        modelMapper.updateById(model);

        if (body.containsKey("indicatorIds")) {
            modelIndicatorMapper.delete(new LambdaQueryWrapper<AnaModelIndicator>()
                    .eq(AnaModelIndicator::getModelId, modelId));
            Object raw = body.get("indicatorIds");
            if (raw instanceof List<?> list) {
                int order = 0;
                for (Object o : list) {
                    Long iid = longVal(o);
                    if (iid == null) continue;
                    AnaIndicator ind = indicatorMapper.selectById(iid);
                    if (ind == null || !"ACTIVE".equalsIgnoreCase(ind.getStatus())) continue;
                    if (!model.getDomainCode().equals(ind.getDomainCode())) continue;
                    AnaModelIndicator mi = new AnaModelIndicator();
                    mi.setModelId(modelId);
                    mi.setIndicatorId(iid);
                    mi.setSortOrder(order++);
                    modelIndicatorMapper.insert(mi);
                }
            }
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_MODEL_DESIGN", model.getDomainCode(), model.getModelCode(), model.getModelName());
    }

    private List<AnaIndicator> listIndicatorsForModel(Long modelId) {
        List<AnaModelIndicator> links = modelIndicatorMapper.selectList(new LambdaQueryWrapper<AnaModelIndicator>()
                .eq(AnaModelIndicator::getModelId, modelId)
                .orderByAsc(AnaModelIndicator::getSortOrder));
        if (links.isEmpty()) return List.of();
        Set<Long> ids = links.stream().map(AnaModelIndicator::getIndicatorId).collect(Collectors.toSet());
        List<AnaIndicator> all = indicatorMapper.selectBatchIds(ids);
        Map<Long, AnaIndicator> map = all.stream().collect(Collectors.toMap(AnaIndicator::getId, x -> x, (a, b) -> a));
        List<AnaIndicator> ordered = new ArrayList<>();
        for (AnaModelIndicator link : links) {
            AnaIndicator ind = map.get(link.getIndicatorId());
            if (ind != null && "ACTIVE".equalsIgnoreCase(ind.getStatus())) ordered.add(ind);
        }
        return ordered;
    }

    private AnaDomainModule getModule(String mCode) {
        String code = mCode.toUpperCase(Locale.ROOT);
        if (!code.startsWith("M")) code = "M" + code;
        AnaDomainModule mod = moduleMapper.selectOne(new LambdaQueryWrapper<AnaDomainModule>()
                .eq(AnaDomainModule::getMCode, code));
        if (mod == null) throw new BusinessException(404, "domain module not found");
        return mod;
    }

    private static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) throw new BusinessException(400, "domain required");
        return domain.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeZone(String zone) {
        if (zone == null || zone.isBlank()) throw new BusinessException(400, "zone required");
        String z = zone.trim().toLowerCase(Locale.ROOT);
        if (z.startsWith("zone.")) z = z.substring(5);
        if (!ZONES.contains(z)) throw new BusinessException(400, "zone 须为 collect|govern|core|internal|share");
        return z;
    }

    private static String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private static Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) throw new BusinessException(400, field + " required");
        return v;
    }

    private static Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }
}
