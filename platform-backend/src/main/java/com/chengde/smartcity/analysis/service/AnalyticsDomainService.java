package com.chengde.smartcity.analysis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.analysis.entity.AnaAnalysisModel;
import com.chengde.smartcity.analysis.entity.AnaDomainModule;
import com.chengde.smartcity.analysis.entity.AnaIndicator;
import com.chengde.smartcity.analysis.entity.AnaModelIndicator;
import com.chengde.smartcity.analysis.entity.AnaModelSample;
import com.chengde.smartcity.analysis.entity.AnaPopBatchLedger;
import com.chengde.smartcity.analysis.entity.AnaPopServiceContract;
import com.chengde.smartcity.analysis.entity.AnaPopVerifyLedger;
import com.chengde.smartcity.analysis.entity.AnaZoneBinding;
import com.chengde.smartcity.analysis.entity.IndArea;
import com.chengde.smartcity.analysis.entity.IndField;
import com.chengde.smartcity.analysis.entity.IndGroup;
import com.chengde.smartcity.analysis.mapper.AnaAnalysisModelMapper;
import com.chengde.smartcity.analysis.mapper.AnaDomainModuleMapper;
import com.chengde.smartcity.analysis.mapper.AnaIndicatorMapper;
import com.chengde.smartcity.analysis.mapper.AnaModelIndicatorMapper;
import com.chengde.smartcity.analysis.mapper.AnaPopBatchLedgerMapper;
import com.chengde.smartcity.analysis.mapper.AnaPopServiceContractMapper;
import com.chengde.smartcity.analysis.mapper.AnaPopVerifyLedgerMapper;
import com.chengde.smartcity.analysis.mapper.AnaZoneBindingMapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.dataease.DataEaseClient;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.entity.RcManagedTable;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetadataRegistryMapper;
import com.chengde.smartcity.masterdata.mapper.RcManagedTableMapper;
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
import org.springframework.context.annotation.Lazy;
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
    private final IndicatorLedgerService indicatorLedgerService;
    private final IndicatorTaskService indicatorTaskService;
    private final AnaModelIndicatorMapper modelIndicatorMapper;
    private final AnaPopVerifyLedgerMapper popVerifyLedgerMapper;
    private final AnaPopServiceContractMapper popServiceContractMapper;
    private final AnaPopBatchLedgerMapper popBatchLedgerMapper;
    private final GovMetadataRegistryMapper registryMapper;
    private final RcManagedTableMapper managedTableMapper;
    private final GovCatalogResourceMapper catalogResourceMapper;
    private final IngDataSourceMapper ingDataSourceMapper;
    private final AnalysisDemoService analysisDemoService;
    private final AuditService auditService;
    private final IntegrationProperties integrationProperties;
    private final DataEaseClient dataEaseClient;
    private final JdbcTemplate jdbcTemplate;

    public AnalyticsDomainService(AnaDomainModuleMapper moduleMapper,
                                  AnaAnalysisModelMapper modelMapper,
                                  AnaZoneBindingMapper zoneBindingMapper,
                                  AnaIndicatorMapper indicatorMapper,
                                  IndicatorLedgerService indicatorLedgerService,
                                  @Lazy IndicatorTaskService indicatorTaskService,
                                  AnaModelIndicatorMapper modelIndicatorMapper,
                                  AnaPopVerifyLedgerMapper popVerifyLedgerMapper,
                                  AnaPopServiceContractMapper popServiceContractMapper,
                                  AnaPopBatchLedgerMapper popBatchLedgerMapper,
                                  GovMetadataRegistryMapper registryMapper,
                                  RcManagedTableMapper managedTableMapper,
                                  GovCatalogResourceMapper catalogResourceMapper,
                                  IngDataSourceMapper ingDataSourceMapper,
                                  AnalysisDemoService analysisDemoService,
                                  AuditService auditService,
                                  IntegrationProperties integrationProperties,
                                  DataEaseClient dataEaseClient,
                                  JdbcTemplate jdbcTemplate) {
        this.moduleMapper = moduleMapper;
        this.modelMapper = modelMapper;
        this.zoneBindingMapper = zoneBindingMapper;
        this.indicatorMapper = indicatorMapper;
        this.indicatorLedgerService = indicatorLedgerService;
        this.indicatorTaskService = indicatorTaskService;
        this.modelIndicatorMapper = modelIndicatorMapper;
        this.popVerifyLedgerMapper = popVerifyLedgerMapper;
        this.popServiceContractMapper = popServiceContractMapper;
        this.popBatchLedgerMapper = popBatchLedgerMapper;
        this.registryMapper = registryMapper;
        this.managedTableMapper = managedTableMapper;
        this.catalogResourceMapper = catalogResourceMapper;
        this.ingDataSourceMapper = ingDataSourceMapper;
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
        if ("collect".equals(z) || "govern".equals(z) || "core".equals(z) || "internal".equals(z)) {
            LambdaQueryWrapper<GovMetadataRegistry> q = new LambdaQueryWrapper<GovMetadataRegistry>()
                    .eq(GovMetadataRegistry::getEntryType, "TABLE")
                    .orderByDesc(GovMetadataRegistry::getId)
                    .last("LIMIT 120");
            if ("collect".equals(z)) {
                q.and(w -> w.eq(GovMetadataRegistry::getDataLayer, "ODS")
                        .or().eq(GovMetadataRegistry::getDataLayer, "SOURCE")
                        .or().isNull(GovMetadataRegistry::getDataLayer)
                        .or().eq(GovMetadataRegistry::getDataLayer, "")
                        .or().like(GovMetadataRegistry::getPhysicalTableName, "ods_"));
            } else if ("govern".equals(z)) {
                q.and(w -> w.eq(GovMetadataRegistry::getDataLayer, "DWD")
                        .or().like(GovMetadataRegistry::getPhysicalTableName, "dwd_"));
            } else if ("core".equals(z)) {
                q.and(w -> w.eq(GovMetadataRegistry::getDataLayer, "DWS")
                        .or().like(GovMetadataRegistry::getPhysicalTableName, "dws_"));
            } else {
                q.and(w -> w.eq(GovMetadataRegistry::getDataLayer, "DWS")
                        .or().eq(GovMetadataRegistry::getDataLayer, "ADS")
                        .or().like(GovMetadataRegistry::getPhysicalTableName, "dws_")
                        .or().like(GovMetadataRegistry::getPhysicalTableName, "ads_"));
            }
            for (GovMetadataRegistry e : registryMapper.selectList(q)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("assetType", "METADATA");
                m.put("assetRef", e.getEntryCode());
                m.put("assetName", e.getEntryName());
                m.put("physicalTable", e.getPhysicalTableName());
                m.put("metaEntryCode", e.getEntryCode());
                m.put("dataLayer", e.getDataLayer() != null && !e.getDataLayer().isBlank()
                        ? e.getDataLayer()
                        : inferLayerFromTable(e.getPhysicalTableName(), z));
                out.add(m);
            }
            if ("core".equals(z) || "internal".equals(z)) {
                for (RcManagedTable mt : managedTableMapper.selectList(new LambdaQueryWrapper<RcManagedTable>()
                        .eq(RcManagedTable::getStatus, "ACTIVE").orderByDesc(RcManagedTable::getId).last("LIMIT 80"))) {
                    String table = mt.getPhysicalTable() == null ? "" : mt.getPhysicalTable();
                    String layer = inferLayerFromTable(table, z);
                    if ("core".equals(z) && !"DWS".equals(layer)) continue;
                    if ("internal".equals(z) && !"DWS".equals(layer) && !"ADS".equals(layer)) continue;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("assetType", "MANAGED");
                    m.put("assetRef", table);
                    m.put("assetName", table);
                    m.put("physicalTable", table);
                    m.put("metaEntryCode", mt.getMetaEntryCode());
                    m.put("dataLayer", layer);
                    out.add(m);
                }
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

    private static String inferLayerFromTable(String table, String zone) {
        String t = table == null ? "" : table.toLowerCase(Locale.ROOT);
        if (t.startsWith("ads_")) return "ADS";
        if (t.startsWith("dws_")) return "DWS";
        if (t.startsWith("dwd_")) return "DWD";
        if (t.startsWith("ods_")) return "ODS";
        if ("internal".equals(zone)) return "DWS";
        if ("core".equals(zone)) return "DWS";
        if ("govern".equals(zone)) return "DWD";
        return "ODS";
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
        String layer = str(body.get("dataLayer"), "");
        String table = str(body.get("physicalTable"), "");
        if (layer == null || layer.isBlank()) {
            layer = inferLayerFromTable(table, z);
        }
        if ("collect".equals(z)) {
            if ("STRUCT".equalsIgnoreCase(str(body.get("accessMode"), "STRUCT"))
                    && ("DWD".equalsIgnoreCase(layer) || "DWS".equalsIgnoreCase(layer) || "ADS".equalsIgnoreCase(layer))) {
                throw new BusinessException(400, "数据采集区请挂载 ODS/贴源层表");
            }
        } else if ("govern".equals(z)) {
            if ("DWS".equalsIgnoreCase(layer) || "ADS".equalsIgnoreCase(layer)) {
                throw new BusinessException(400, "治理反馈区不宜挂载资源层表，请挂过程层或问题数据");
            }
            if (table.toLowerCase(Locale.ROOT).startsWith("ads_")) {
                throw new BusinessException(400, "治理反馈区不宜挂载 ADS 表");
            }
            if ("STRUCT".equalsIgnoreCase(str(body.get("accessMode"), ""))
                    && ("ODS".equalsIgnoreCase(layer) || table.toLowerCase(Locale.ROOT).startsWith("ods_"))) {
                throw new BusinessException(400, "治理反馈区请挂载 DWD 过程层表");
            }
        } else if ("core".equals(z)) {
            if ("STRUCT".equalsIgnoreCase(str(body.get("accessMode"), ""))
                    && !"DWS".equalsIgnoreCase(layer)
                    && !table.toLowerCase(Locale.ROOT).startsWith("dws_")) {
                throw new BusinessException(400, "核心数据区请挂载 DWS 主题表");
            }
        } else if ("internal".equals(z)) {
            if ("STRUCT".equalsIgnoreCase(str(body.get("accessMode"), ""))) {
                boolean ok = "DWS".equalsIgnoreCase(layer) || "ADS".equalsIgnoreCase(layer)
                        || table.toLowerCase(Locale.ROOT).startsWith("dws_")
                        || table.toLowerCase(Locale.ROOT).startsWith("ads_");
                if (!ok) {
                    throw new BusinessException(400, "内部服务区请挂载 DWS/ADS 资源表");
                }
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
        b.setDataLayer(layer);
        b.setDimGroup(normalizeDimGroup(body.get("dimGroup")));
        b.setAccessMode(normalizeAccessMode(body.get("accessMode")));
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

    // ---------- indicator domains（指标域） ----------

    private static boolean isUnifiedIndicatorScope(String domain) {
        return "all".equals(domain) || "gov".equals(domain);
    }

    public List<IndArea> listIndicatorDomains(String domain, String domainName, String domainDbName) {
        return indicatorLedgerService.listDomains(domain, domainName, domainDbName);
    }

    @Transactional
    public String createIndicatorDomain(UserPrincipal operator, String domain, Map<String, Object> body) {
        return indicatorLedgerService.createDomain(operator, domain, body);
    }

    @Transactional
    public void updateIndicatorDomain(UserPrincipal operator, String id, Map<String, Object> body) {
        indicatorLedgerService.updateDomain(operator, id, body);
    }

    @Transactional
    public void deleteIndicatorDomain(UserPrincipal operator, String id) {
        indicatorLedgerService.deleteDomain(operator, id);
    }

    @Transactional
    public Map<String, Object> publishIndicatorDomain(UserPrincipal operator, String domainId, Map<String, Object> body) {
        return indicatorLedgerService.publishDomain(operator, domainId, body);
    }

    public List<IndGroup> listIndicatorGroups(String domain, String indicatorDomainId,
                                              String groupName, String targetTable, String groupCategory) {
        return indicatorLedgerService.listGroups(domain, indicatorDomainId, groupName, targetTable, groupCategory);
    }

    public IndGroup getIndicatorGroup(String id) {
        return indicatorLedgerService.getGroup(id);
    }

    @Transactional
    public String createIndicatorGroup(UserPrincipal operator, String domain, Map<String, Object> body) {
        return indicatorLedgerService.createGroup(operator, domain, body);
    }

    @Transactional
    public void updateIndicatorGroup(UserPrincipal operator, String id, Map<String, Object> body) {
        indicatorLedgerService.updateGroup(operator, id, body);
    }

    @Transactional
    public void deleteIndicatorGroup(UserPrincipal operator, String id) {
        indicatorLedgerService.deleteGroup(operator, id);
    }

    @Transactional
    public void publishIndicatorGroup(UserPrincipal operator, String id) {
        indicatorLedgerService.publishGroup(operator, id, Map.of());
    }

    @Transactional
    public void publishIndicatorGroup(UserPrincipal operator, String id, Map<String, Object> body) {
        indicatorLedgerService.publishGroup(operator, id, body);
    }

    @Transactional
    public Map<String, Object> batchPublishIndicatorGroups(UserPrincipal operator, Map<String, Object> body) {
        return indicatorLedgerService.batchPublishGroups(operator, body);
    }

    public List<IndField> listIndicatorsByGroup(String groupId) {
        return indicatorLedgerService.listFieldsByGroup(groupId);
    }

    public Map<String, Object> previewIndicatorGroupResult(String groupId, Integer limit) {
        int lim = limit == null || limit <= 0 ? 200 : limit;
        return indicatorLedgerService.previewGroupResult(groupId, lim);
    }

    public Map<String, Object> latestGroupSql(String groupId) {
        return indicatorLedgerService.latestGroupSql(groupId);
    }

    public List<Map<String, Object>> listIndicatorDatasourceCatalog(String domain, String category, String keyword) {
        normalizeDomain(domain);
        List<Map<String, Object>> all = new ArrayList<>();
        all.add(dsRow("platform", "平台库", "其他", "V1", "平台"));
        all.add(dsRow("population_demo", "人口主题库", "主题库", "V1", "平台"));
        all.add(dsRow("legal_demo", "法人主题库", "主题库", "V1", "平台"));
        all.add(dsRow("ods_collect", "原始归集库", "原始库", "V1", "平台"));
        all.add(dsRow("dwd_govern", "治理库", "治理库", "V1", "平台"));
        all.add(dsRow("dws_basic", "基础库", "基础库", "V1", "平台"));
        try {
            List<IngDataSource> sources = ingDataSourceMapper.selectList(new LambdaQueryWrapper<IngDataSource>()
                    .orderByDesc(IngDataSource::getId)
                    .last("LIMIT 200"));
            for (IngDataSource ds : sources) {
                if (ds.getSourceName() == null || ds.getSourceName().isBlank()) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", "ing:" + ds.getId());
                row.put("name", ds.getSourceName());
                row.put("category", "来源");
                row.put("version", "V1");
                row.put("deptName", str(ds.getSystemName(), "—"));
                row.put("sourceType", str(ds.getSourceType(), ""));
                all.add(row);
            }
        } catch (Exception ignored) {
            // 登记源不可用时仍返回内置源
        }
        String cat = category == null ? "" : category.trim();
        String kw = keyword == null ? "" : keyword.trim();
        return all.stream()
                .filter(r -> cat.isEmpty() || cat.equals(String.valueOf(r.get("category"))))
                .filter(r -> kw.isEmpty() || String.valueOf(r.get("name")).contains(kw))
                .toList();
    }

    private static Map<String, Object> dsRow(String key, String name, String category, String version, String dept) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("name", name);
        row.put("category", category);
        row.put("version", version);
        row.put("deptName", dept);
        return row;
    }

    // ---------- indicators ----------

    public List<AnaIndicator> listIndicators(String domain) {
        String d = normalizeDomain(domain);
        List<AnaIndicator> list = indicatorMapper.selectList(new LambdaQueryWrapper<AnaIndicator>()
                .eq(AnaIndicator::getDomainCode, d)
                .eq(AnaIndicator::getStatus, "ACTIVE")
                .orderByDesc(AnaIndicator::getId));
        enrichIndicatorDisplay(list);
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
        String dsKey = str(body.get("datasourceKey"), null);
        if (dsKey == null || dsKey.isBlank()) {
            throw new BusinessException(400, "请选择数据源");
        }
        int timeout = 60;
        try {
            if (body.get("timeoutSec") != null) timeout = Math.max(5, Integer.parseInt(String.valueOf(body.get("timeoutSec"))));
        } catch (Exception ignored) { /* keep default */ }
        List<Map<String, Object>> raw = indicatorTaskService.runSelect(dsKey, sql, timeout, 200);
        List<String> columns = new ArrayList<>();
        if (!raw.isEmpty()) {
            columns.addAll(raw.get(0).keySet());
        } else {
            columns.addAll(extractSelectAliases(sql).stream()
                    .map(f -> String.valueOf(f.get("resultField"))).toList());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("columns", columns);
        out.put("rows", raw);
        out.put("rowCount", raw.size());
        out.put("truncated", raw.size() >= 200);
        return out;
    }

    @Transactional
    public String createIndicatorSql(UserPrincipal operator, String domain, Map<String, Object> body) {
        String sql = required(body.get("sqlText"), "sqlText").toString().trim();
        String dsKey = str(body.get("datasourceKey"), "platform");
        String dsName = str(body.get("datasourceName"), datasourceLabel(dsKey));
        int timeout = 60;
        try {
            if (body.get("timeoutSec") != null) timeout = Math.max(5, Integer.parseInt(String.valueOf(body.get("timeoutSec"))));
        } catch (Exception ignored) { /* default */ }

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
            fields = extractSelectAliases(sql);
        }
        if (fields.isEmpty()) {
            try {
                List<Map<String, Object>> sample = indicatorTaskService.runSelect(dsKey, sql, timeout, 1);
                if (!sample.isEmpty()) {
                    fields = enrichFieldsFromMeta(new ArrayList<>(sample.get(0).keySet()), sql);
                }
            } catch (BusinessException e) {
                throw new BusinessException(400, "未能解析结果列：" + e.getMessage());
            }
        }
        if (fields.isEmpty()) {
            throw new BusinessException(400, "未能从 SQL 解析结果列，请为列指定 AS 别名");
        }

        Object rawGid = body.get("groupId");
        if (rawGid != null && !String.valueOf(rawGid).isBlank()) {
            return indicatorLedgerService.saveGroupSql(operator, domain, body, fields, sql, dsKey, dsName, timeout);
        }
        String d = normalizeDomain(domain);
        if (isUnifiedIndicatorScope(d)) {
            throw new BusinessException(400, "统一入口新增指标须指定 groupId");
        }

        String slug = str(body.get("querySlug"), fields.get(0).get("fieldName") != null
                ? String.valueOf(fields.get(0).get("fieldName"))
                : "query");
        slug = slug.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
        if (slug.isBlank()) slug = "query";
        long seq = indicatorMapper.selectCount(new LambdaQueryWrapper<AnaIndicator>()
                .eq(AnaIndicator::getDomainCode, d));
        String queryNo = d + ".ind_" + slug + "_sql" + seq;
        Long lastId = null;
        int i = 0;
        for (Map<String, Object> f : fields) {
            String resultField = required(f.get("resultField"), "resultField").toString();
            String fieldName = str(f.get("fieldName"), "ind_" + resultField);
            String indName = str(f.get("indicatorName"), resultField);
            AnaIndicator ind = new AnaIndicator();
            ind.setDomainCode(d);
            ind.setResultField(resultField);
            ind.setFieldType(str(f.get("fieldType"), "VARCHAR"));
            Integer flen = intVal(f.get("fieldLength"));
            ind.setFieldLength(flen == null || flen <= 0 ? 100 : flen);
            ind.setFieldPrecision(intVal(f.get("fieldPrecision")));
            ind.setFieldName(fieldName);
            ind.setIndicatorFlag(str(f.get("indicatorFlag"), null));
            ind.setIndicatorCode(queryNo + "_" + (++i));
            ind.setIndicatorName(indName);
            ind.setSourceColumn(resultField);
            ind.setAggFunc("EXPR");
            ind.setExprText(sql);
            ind.setStatus("ACTIVE");
            ind.setCreatedBy(operator.getUsername());
            ind.setCreatedAt(LocalDateTime.now());
            ind.setUpdatedAt(LocalDateTime.now());
            indicatorMapper.insert(ind);
            lastId = ind.getId();
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_SQL_CREATE", d, queryNo, "fields=" + fields.size());
        return lastId == null ? queryNo : String.valueOf(lastId);
    }

    @Transactional
    public Long createIndicator(UserPrincipal operator, String domain, Map<String, Object> body) {
        // 兼容旧接口；若带 sqlText 则走语句模式（分析模型种子仍写 ana_indicator）
        if (body.get("sqlText") != null && !String.valueOf(body.get("sqlText")).isBlank()) {
            String sid = createIndicatorSql(operator, domain, body);
            Long n = longVal(sid);
            return n != null ? n : 0L;
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
    public void updateIndicator(UserPrincipal operator, String id, Map<String, Object> body) {
        if (id != null && !id.isBlank() && indicatorLedgerService.updateField(operator, id.trim(), body)) {
            return;
        }
        Long lid = longVal(id);
        if (lid == null) throw new BusinessException(404, "指标不存在");
        AnaIndicator ind = indicatorMapper.selectById(lid);
        if (ind == null || !"ACTIVE".equalsIgnoreCase(ind.getStatus())) {
            throw new BusinessException(404, "指标不存在");
        }
        if (body.get("indicatorName") != null) ind.setIndicatorName(String.valueOf(body.get("indicatorName")));
        if (body.get("fieldName") != null) ind.setFieldName(String.valueOf(body.get("fieldName")));
        if (body.containsKey("indicatorFlag")) ind.setIndicatorFlag(str(body.get("indicatorFlag"), null));
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
    public void deleteIndicator(UserPrincipal operator, String id) {
        if (id != null && !id.isBlank() && indicatorLedgerService.deleteField(operator, id.trim())) {
            return;
        }
        Long lid = longVal(id);
        if (lid == null) throw new BusinessException(404, "指标不存在");
        AnaIndicator ind = indicatorMapper.selectById(lid);
        if (ind == null) throw new BusinessException(404, "指标不存在");
        ind.setStatus("INACTIVE");
        ind.setUpdatedAt(LocalDateTime.now());
        indicatorMapper.updateById(ind);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_DELETE", ind.getDomainCode(), ind.getIndicatorCode(), "INACTIVE");
    }

    private void enrichIndicatorDisplay(List<AnaIndicator> list) {
        if (list == null) return;
        for (AnaIndicator ind : list) {
            if (ind.getQueryNo() == null || ind.getQueryNo().isBlank()) {
                ind.setQueryNo(ind.getIndicatorCode());
            }
            if (ind.getResultField() == null || ind.getResultField().isBlank()) {
                ind.setResultField(str(ind.getSourceColumn(), ind.getIndicatorCode()));
            }
            if (ind.getFieldName() == null || ind.getFieldName().isBlank()) {
                ind.setFieldName(ind.getIndicatorCode());
            }
            if (ind.getFieldType() == null || ind.getFieldType().isBlank()) {
                ind.setFieldType("VARCHAR");
            }
        }
    }

    private static String datasourceLabel(String key) {
        if (key == null) return "平台库";
        return switch (key) {
            case "population_demo" -> "人口主题库";
            case "legal_demo" -> "法人主题库";
            case "ods_collect" -> "原始归集库";
            case "dwd_govern" -> "治理库";
            case "dws_basic" -> "基础库";
            default -> key.startsWith("ing:") ? "登记数据源" : "平台库";
        };
    }

    private static List<Map<String, Object>> extractSelectAliases(String sql) {
        String cleaned = sql.replaceAll("(?is)/\\*.*?\\*/", " ").replaceAll("--.*?(\\r?\\n|$)", "\n").trim();
        // 支持有 FROM 的标准 SELECT，也支持无 FROM 的常量/函数 SELECT
        Matcher withFrom = Pattern.compile("(?is)^\\s*select\\s+(.*?)\\s+from\\s").matcher(cleaned);
        String selectList;
        if (withFrom.find()) {
            selectList = withFrom.group(1).trim();
        } else {
            Matcher noFrom = Pattern.compile("(?is)^\\s*select\\s+(.+?)\\s*(?:;\\s*)?$").matcher(cleaned);
            if (!noFrom.find()) {
                return List.of();
            }
            selectList = noFrom.group(1).trim();
            // 去掉尾部 where/group/order/limit（极少见于无 FROM 语句）
            selectList = selectList.replaceAll("(?is)\\s+(where|group\\s+by|order\\s+by|limit)\\b.*$", "").trim();
        }
        if ("*".equals(selectList) || selectList.isEmpty()) {
            return List.of();
        }
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
            row.put("fieldType", "VARCHAR");
            row.put("fieldLength", 100);
            row.put("fieldPrecision", 0);
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

    private List<Map<String, Object>> enrichFieldsFromMeta(List<String> columns, String sql) {
        List<Map<String, Object>> parsed = extractSelectAliases(sql);
        Map<String, Map<String, Object>> byName = new HashMap<>();
        for (Map<String, Object> p : parsed) byName.put(String.valueOf(p.get("resultField")), p);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String col : columns) {
            Map<String, Object> base = byName.getOrDefault(col, new LinkedHashMap<>());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resultField", col);
            row.put("fieldType", base.getOrDefault("fieldType", "VARCHAR"));
            row.put("fieldLength", base.getOrDefault("fieldLength", 100));
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
        // 业务支撑域（人口/法人/宏观/重点）不维护 DataEase 看板标识；智能 BI Hub 另路
        boolean selfBuilt = model.getDomainCode() != null
                && Set.of("population", "legal", "macro", "key")
                .contains(model.getDomainCode().trim().toLowerCase(Locale.ROOT));
        if (!selfBuilt && body.containsKey("deDashboardId")) {
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

    // ---------- 人口校核台账 / 服务契约（LEDGER） ----------

    public List<AnaPopVerifyLedger> listPopVerifyLedger(String domain, String mCode) {
        String d = normalizeDomain(domain);
        if (!"population".equals(d)) {
            throw new BusinessException(400, "校核台账仅支持人口域");
        }
        LambdaQueryWrapper<AnaPopVerifyLedger> q = new LambdaQueryWrapper<AnaPopVerifyLedger>()
                .eq(AnaPopVerifyLedger::getDomainCode, d)
                .orderByDesc(AnaPopVerifyLedger::getId);
        if (mCode != null && !mCode.isBlank()) {
            q.eq(AnaPopVerifyLedger::getMCode, mCode.trim().toUpperCase(Locale.ROOT));
        }
        return popVerifyLedgerMapper.selectList(q);
    }

    @Transactional
    public Long createPopVerifyLedger(UserPrincipal operator, String domain, Map<String, Object> body) {
        String d = normalizeDomain(domain);
        if (!"population".equals(d)) {
            throw new BusinessException(400, "校核台账仅支持人口域");
        }
        String mCode = String.valueOf(required(body.get("mCode"), "mCode")).toUpperCase(Locale.ROOT);
        if (!"M155".equals(mCode) && !"M156".equals(mCode)) {
            throw new BusinessException(400, "mCode 须为 M155 或 M156");
        }
        AnaPopVerifyLedger row = new AnaPopVerifyLedger();
        row.setDomainCode(d);
        row.setMCode(mCode);
        row.setSceneCode(String.valueOf(required(body.get("sceneCode"), "sceneCode")));
        row.setSceneName(String.valueOf(required(body.get("sceneName"), "sceneName")));
        row.setCheckType(str(body.get("checkType"), "MULTI_SOURCE"));
        row.setSourceDept(str(body.get("sourceDept"), null));
        row.setIssueSummary(str(body.get("issueSummary"), null));
        row.setFeedbackStatus(str(body.get("feedbackStatus"), "OPEN"));
        row.setRelatedPersonId(str(body.get("relatedPersonId"), null));
        row.setStatus("LEDGER");
        row.setCreatedBy(operator.getUsername());
        row.setCreatedAt(LocalDateTime.now());
        popVerifyLedgerMapper.insert(row);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_POP_VERIFY_CREATE", d, row.getSceneCode(), row.getSceneName());
        return row.getId();
    }

    @Transactional
    public void updatePopVerifyFeedback(UserPrincipal operator, Long id, Map<String, Object> body) {
        AnaPopVerifyLedger row = popVerifyLedgerMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "校核台账不存在");
        String fs = str(body.get("feedbackStatus"), row.getFeedbackStatus());
        if (!Set.of("OPEN", "FEEDBACK", "CLOSED").contains(fs)) {
            throw new BusinessException(400, "feedbackStatus 须为 OPEN|FEEDBACK|CLOSED");
        }
        row.setFeedbackStatus(fs);
        if (body.containsKey("issueSummary")) {
            row.setIssueSummary(str(body.get("issueSummary"), null));
        }
        row.setUpdatedAt(LocalDateTime.now());
        popVerifyLedgerMapper.updateById(row);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_POP_VERIFY_FEEDBACK", row.getDomainCode(), String.valueOf(id), fs);
    }

    public List<AnaPopServiceContract> listPopServiceContracts(String domain) {
        String d = normalizeDomain(domain);
        if (!"population".equals(d)) {
            throw new BusinessException(400, "服务契约仅支持人口域");
        }
        return popServiceContractMapper.selectList(new LambdaQueryWrapper<AnaPopServiceContract>()
                .eq(AnaPopServiceContract::getDomainCode, d)
                .orderByAsc(AnaPopServiceContract::getMCode)
                .orderByAsc(AnaPopServiceContract::getId));
    }

    @Transactional
    public Map<String, Object> invokePopService(UserPrincipal operator, String domain, String serviceCode,
                                                Map<String, Object> body) {
        String d = normalizeDomain(domain);
        if (!"population".equals(d)) {
            throw new BusinessException(400, "服务契约仅支持人口域");
        }
        AnaPopServiceContract c = popServiceContractMapper.selectOne(new LambdaQueryWrapper<AnaPopServiceContract>()
                .eq(AnaPopServiceContract::getDomainCode, d)
                .eq(AnaPopServiceContract::getServiceCode, serviceCode));
        if (c == null) throw new BusinessException(404, "服务契约不存在");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("serviceCode", c.getServiceCode());
        out.put("serviceName", c.getServiceName());
        out.put("mCode", c.getMCode());
        out.put("mode", "LEDGER");
        out.put("message", "LEDGER 试调：已返回契约样例响应，未连接真实 ESB/批量通道");
        out.put("request", body == null ? Map.of() : body);
        out.put("responseSample", c.getResponseSample());
        if ("BATCH".equalsIgnoreCase(c.getMode())) {
            String batchCode = "BATCH-POP-" + System.currentTimeMillis();
            AnaPopBatchLedger batch = new AnaPopBatchLedger();
            batch.setDomainCode(d);
            batch.setBatchCode(batchCode);
            batch.setServiceCode(c.getServiceCode());
            batch.setChannel(c.getPathOrChannel());
            batch.setTableName(str(body == null ? null : body.get("table"), "dws_population_base"));
            Object lim = body == null ? null : body.get("rowLimit");
            batch.setRowLimit(lim instanceof Number n ? n.intValue() : 1000);
            batch.setBatchStatus("ACCEPTED");
            batch.setMessage("LEDGER：批量试调已登记，未执行真实前置交换");
            batch.setStatus("LEDGER");
            batch.setCreatedBy(operator.getUsername());
            batch.setCreatedAt(LocalDateTime.now());
            popBatchLedgerMapper.insert(batch);
            out.put("batchCode", batchCode);
            out.put("batchId", batch.getId());
            out.put("batchStatus", batch.getBatchStatus());
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_POP_SERVICE_INVOKE", d, serviceCode, c.getMode());
        return out;
    }

    public List<AnaPopBatchLedger> listPopBatchLedger(String domain) {
        String d = normalizeDomain(domain);
        if (!"population".equals(d)) {
            throw new BusinessException(400, "批量台账仅支持人口域");
        }
        return popBatchLedgerMapper.selectList(new LambdaQueryWrapper<AnaPopBatchLedger>()
                .eq(AnaPopBatchLedger::getDomainCode, d)
                .orderByDesc(AnaPopBatchLedger::getId));
    }

    @Transactional
    public Long createPopBatchLedger(UserPrincipal operator, String domain, Map<String, Object> body) {
        String d = normalizeDomain(domain);
        if (!"population".equals(d)) {
            throw new BusinessException(400, "批量台账仅支持人口域");
        }
        String batchCode = str(body.get("batchCode"), "BATCH-POP-" + System.currentTimeMillis());
        AnaPopBatchLedger row = new AnaPopBatchLedger();
        row.setDomainCode(d);
        row.setBatchCode(batchCode);
        row.setServiceCode(str(body.get("serviceCode"), "POP_BATCH_EXCHANGE"));
        row.setChannel(str(body.get("channel"), "前置库 ↔ 交换系统 ↔ 共享结果库"));
        row.setTableName(str(body.get("tableName"), "dws_population_base"));
        Object lim = body.get("rowLimit");
        row.setRowLimit(lim instanceof Number n ? n.intValue() : 1000);
        row.setBatchStatus(str(body.get("batchStatus"), "OPEN"));
        row.setMessage(str(body.get("message"), "LEDGER 登记"));
        row.setStatus("LEDGER");
        row.setCreatedBy(operator.getUsername());
        row.setCreatedAt(LocalDateTime.now());
        popBatchLedgerMapper.insert(row);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_POP_BATCH_CREATE", d, batchCode, row.getBatchStatus());
        return row.getId();
    }

    @Transactional
    public void updatePopBatchStatus(UserPrincipal operator, Long id, Map<String, Object> body) {
        AnaPopBatchLedger row = popBatchLedgerMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "批量台账不存在");
        String st = str(body.get("batchStatus"), row.getBatchStatus());
        if (!Set.of("OPEN", "ACCEPTED", "DONE", "FAILED").contains(st)) {
            throw new BusinessException(400, "batchStatus 须为 OPEN|ACCEPTED|DONE|FAILED");
        }
        row.setBatchStatus(st);
        if (body.containsKey("message")) {
            row.setMessage(str(body.get("message"), null));
        }
        row.setUpdatedAt(LocalDateTime.now());
        popBatchLedgerMapper.updateById(row);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_POP_BATCH_STATUS", row.getDomainCode(), String.valueOf(id), st);
    }

    /** 人口核心区存储/分区设计摘要（只读 LEDGER，不执行 DDL） */
    public Map<String, Object> populationStorageSummary(String domain) {
        String d = normalizeDomain(domain);
        if (!"population".equals(d)) {
            throw new BusinessException(400, "存储摘要仅支持人口域");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", "LEDGER");
        out.put("message", "预检/策略台账 ≠ 已物理分区；真正 ALTER 属运维窗口");
        try {
            List<Map<String, Object>> defs = jdbcTemplate.queryForList(
                    "SELECT id, partition_code AS partitionCode, partition_name AS partitionName, partition_type AS partitionType, "
                            + "table_name AS tableName, partition_column AS partitionColumn, pretest_status AS pretestStatus, "
                            + "pretest_message AS pretestMessage FROM rc_partition_def "
                            + "WHERE table_name IN ('dws_population_base','ads_pop_district_summary') OR partition_code LIKE 'PART_POP%' "
                            + "ORDER BY id DESC");
            out.put("partitions", defs);
            List<Map<String, Object>> ops = jdbcTemplate.queryForList(
                    "SELECT id, physical_table AS physicalTable, op_type AS opType, op_status AS opStatus, message, created_at AS createdAt "
                            + "FROM rc_partition_op WHERE physical_table IN ('dws_population_base','ads_pop_district_summary') "
                            + "ORDER BY id DESC LIMIT 20");
            out.put("ops", ops);
            List<Map<String, Object>> managed = jdbcTemplate.queryForList(
                    "SELECT id, physical_table AS physicalTable, meta_entry_code AS metaEntryCode, record_count AS recordCount, status "
                            + "FROM rc_managed_table WHERE physical_table IN ('dws_population_base','ads_pop_district_summary')");
            out.put("managedTables", managed);
        } catch (Exception e) {
            out.put("partitions", List.of());
            out.put("ops", List.of());
            out.put("managedTables", List.of());
            out.put("hint", "资源中心表尚未就绪或 V190 未执行：" + e.getMessage());
        }
        return out;
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

    private static String normalizeDimGroup(Object v) {
        String s = str(v, null);
        if (s == null) return null;
        s = s.trim().toUpperCase(Locale.ROOT);
        if ("DATATYPE".equals(s) || "LATENCY".equals(s)) return s;
        throw new BusinessException(400, "dimGroup 须为 DATATYPE 或 LATENCY");
    }

    private static String normalizeAccessMode(Object v) {
        String s = str(v, null);
        if (s == null) return null;
        s = s.trim().toUpperCase(Locale.ROOT);
        if ("STRUCT".equals(s) || "UNSTRUCT".equals(s) || "API".equals(s) || "CDC".equals(s)) return s;
        throw new BusinessException(400, "accessMode 须为 STRUCT|UNSTRUCT|API|CDC");
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
