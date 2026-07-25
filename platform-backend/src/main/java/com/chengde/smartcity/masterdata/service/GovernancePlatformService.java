package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.openmetadata.OpenMetadataClient;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovFusionAsset;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.entity.GovQualityReport;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskRun;
import com.chengde.smartcity.masterdata.entity.GovStandardItem;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovFusionAssetMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetadataRegistryMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityReportMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskRunMapper;
import com.chengde.smartcity.masterdata.mapper.GovStandardItemMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernancePlatformService {

    private final MasterDataDemoService demoService;
    private final GovStandardItemMapper standardMapper;
    private final GovMetadataRegistryMapper metadataMapper;
    private final GovFusionAssetMapper fusionMapper;
    private final GovCatalogResourceMapper catalogMapper;
    private final GovQualityReportMapper reportMapper;
    private final GovQualityTaskRunMapper taskRunMapper;
    private final AuditService auditService;
    private final IntegrationProperties integrationProperties;
    private final OpenMetadataClient openMetadataClient;

    public GovernancePlatformService(MasterDataDemoService demoService, GovStandardItemMapper standardMapper,
                                     GovMetadataRegistryMapper metadataMapper, GovFusionAssetMapper fusionMapper,
                                     GovCatalogResourceMapper catalogMapper, GovQualityReportMapper reportMapper,
                                     GovQualityTaskRunMapper taskRunMapper,
                                     AuditService auditService, IntegrationProperties integrationProperties,
                                     OpenMetadataClient openMetadataClient) {
        this.demoService = demoService;
        this.standardMapper = standardMapper;
        this.metadataMapper = metadataMapper;
        this.fusionMapper = fusionMapper;
        this.catalogMapper = catalogMapper;
        this.reportMapper = reportMapper;
        this.taskRunMapper = taskRunMapper;
        this.auditService = auditService;
        this.integrationProperties = integrationProperties;
        this.openMetadataClient = openMetadataClient;
    }

    public Map<String, Object> qualityOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rules", demoService.listRules());
        out.put("tasks", demoService.listQualityTasks());
        out.put("reports", reportMapper.selectList(new LambdaQueryWrapper<GovQualityReport>().orderByDesc(GovQualityReport::getId)));
        out.put("standards", standardMapper.selectList(new LambdaQueryWrapper<GovStandardItem>().orderByAsc(GovStandardItem::getId)));
        out.put("ruleTypes", List.of("COMPLETENESS", "ACCURACY", "CONSISTENCY", "TIMELINESS", "VALIDITY", "UNIQUENESS", "INTEGRITY", "CUSTOM"));
        return out;
    }

    public List<GovStandardItem> listStandards(String itemType) {
        LambdaQueryWrapper<GovStandardItem> q = new LambdaQueryWrapper<GovStandardItem>().orderByAsc(GovStandardItem::getId);
        if (itemType != null && !itemType.isBlank()) {
            q.eq(GovStandardItem::getItemType, itemType);
        }
        return standardMapper.selectList(q);
    }

    @Transactional
    public Long createStandard(UserPrincipal operator, Map<String, Object> body) {
        GovStandardItem item = new GovStandardItem();
        item.setItemCode(str(body.get("itemCode"), "STD_" + System.currentTimeMillis()));
        item.setItemName(required(body.get("itemName"), "itemName").toString());
        item.setItemType(str(body.get("itemType"), "ELEMENT"));
        item.setStandardRef(str(body.get("standardRef"), ""));
        item.setStatus("ACTIVE");
        standardMapper.insert(item);
        return item.getId();
    }

    @Transactional
    public GovQualityReport generateReport(UserPrincipal operator, Map<String, Object> body) {
        GovQualityReport r = new GovQualityReport();
        r.setReportCode("RPT_" + System.currentTimeMillis());
        r.setReportName(str(body.get("reportName"), "质量分析报告"));
        r.setDimension(str(body.get("dimension"), "六性指标"));

        List<GovQualityTaskRun> scoredRuns = taskRunMapper.selectList(new LambdaQueryWrapper<GovQualityTaskRun>()
                .isNotNull(GovQualityTaskRun::getScore)
                .orderByDesc(GovQualityTaskRun::getId)
                .last("LIMIT 20"));
        if (scoredRuns == null || scoredRuns.isEmpty()) {
            throw new BusinessException(400, "尚无质量任务运行记录，请先在「质量任务」中执行稽核后再生成报告");
        }
        BigDecimal sum = BigDecimal.ZERO;
        int runCount = 0;
        for (GovQualityTaskRun run : scoredRuns) {
            sum = sum.add(run.getScore());
            runCount++;
        }
        BigDecimal score = sum.divide(BigDecimal.valueOf(runCount), 2, RoundingMode.HALF_UP);
        r.setScore(score);
        r.setExportPayload("dimension=" + r.getDimension() + ",score=" + r.getScore()
                + ",sourceRuns=" + runCount + ",fallback=NONE");
        reportMapper.insert(r);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "QUALITY_REPORT", "gov_quality_report", String.valueOf(r.getId()), r.getReportName());
        return r;
    }

    public Map<String, Object> metadataOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("connectors", demoService.listConnectors());
        out.put("registry", metadataMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>().orderByAsc(GovMetadataRegistry::getId)));
        out.put("lineageGraph", buildLineageGraph());
        out.put("omHealthy", openMetadataClient.isHealthy());
        if (integrationProperties.isEnabled() && openMetadataClient.isHealthy()) {
            try {
                out.put("omServices", openMetadataClient.listDatabaseServices());
            } catch (Exception e) {
                out.put("omServices", List.of());
                out.put("omNote", e.getMessage());
            }
        } else {
            out.put("omServices", List.of());
        }
        return out;
    }

    public List<GovMetadataRegistry> listMetadata(String entryType) {
        LambdaQueryWrapper<GovMetadataRegistry> q = new LambdaQueryWrapper<GovMetadataRegistry>().orderByAsc(GovMetadataRegistry::getId);
        if (entryType != null && !entryType.isBlank()) {
            q.eq(GovMetadataRegistry::getEntryType, entryType);
        }
        return metadataMapper.selectList(q);
    }

    @Transactional
    public Long createMetadataEntry(UserPrincipal operator, Map<String, Object> body) {
        GovMetadataRegistry e = new GovMetadataRegistry();
        e.setEntryCode(str(body.get("entryCode"), "META_" + UUID.randomUUID().toString().substring(0, 8)));
        e.setEntryName(required(body.get("entryName"), "entryName").toString());
        e.setEntryType(str(body.get("entryType"), "SOURCE"));
        e.setParentCode(str(body.get("parentCode"), null));
        e.setOmRef(str(body.get("omRef"), null));
        e.setStatus("ACTIVE");
        metadataMapper.insert(e);
        return e.getId();
    }

    public List<GovFusionAsset> listFusionAssets(String assetType) {
        LambdaQueryWrapper<GovFusionAsset> q = new LambdaQueryWrapper<GovFusionAsset>().orderByAsc(GovFusionAsset::getId);
        if (assetType != null && !assetType.isBlank()) {
            q.eq(GovFusionAsset::getAssetType, assetType);
        }
        return fusionMapper.selectList(q);
    }

    @Transactional
    public Map<String, Object> runFusionAsset(UserPrincipal operator, Long id) {
        GovFusionAsset asset = fusionMapper.selectById(id);
        if (asset == null) {
            throw new BusinessException(404, "融合资产不存在: " + id);
        }
        // D20：禁止 mock SUCCESS；真实落库走加工共享黄金路径或治理 ETL
        throw new BusinessException(400,
                "融合资产「一键运行」已停用（原为演示假成功）。请改用："
                        + "① 加工共享黄金路径 POST /governance/processed-share/fusion/run；"
                        + "② 数据治理 ETL 画布运行；"
                        + "③ 融合脚本 SELECT/UPDATE 校验（/governance/fusion/scripts/{id}/execute）。"
                        + " assetId=" + id + " type=" + asset.getAssetType());
    }

    public List<GovCatalogResource> listCatalogResources(String resourceType) {
        LambdaQueryWrapper<GovCatalogResource> q = new LambdaQueryWrapper<GovCatalogResource>().orderByDesc(GovCatalogResource::getId);
        if (resourceType != null && !resourceType.isBlank()) {
            q.eq(GovCatalogResource::getResourceType, resourceType);
        }
        return catalogMapper.selectList(q);
    }

    @Transactional
    public Long createCatalogResource(UserPrincipal operator, Map<String, Object> body) {
        GovCatalogResource r = new GovCatalogResource();
        r.setResourceCode(str(body.get("resourceCode"), "RES_" + System.currentTimeMillis()));
        r.setResourceName(required(body.get("resourceName"), "resourceName").toString());
        r.setResourceType(str(body.get("resourceType"), "DATA"));
        r.setCategoryPath(str(body.get("categoryPath"), "政务/目录"));
        r.setSecretFlag(intVal(body.get("secretFlag"), 0));
        r.setPublishStatus("DRAFT");
        r.setApprovalStatus("PENDING");
        r.setVersionNo(1);
        r.setCreatedBy(operator.getUsername());
        catalogMapper.insert(r);
        return r.getId();
    }

    @Transactional
    public void approveCatalogResource(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovCatalogResource r = catalogMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(404, "资源不存在");
        }
        String action = str(body.get("action"), "APPROVE").toUpperCase();
        if ("APPROVE".equals(action)) {
            r.setApprovalStatus("APPROVED");
            r.setPublishStatus("PUBLISHED");
        } else {
            r.setApprovalStatus("REJECTED");
        }
        catalogMapper.updateById(r);
    }

    @Transactional
    public Map<String, Object> subscribeResource(UserPrincipal operator, Long id) {
        GovCatalogResource r = catalogMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(404, "资源不存在");
        }
        if (!"PUBLISHED".equals(r.getPublishStatus())) {
            throw new BusinessException(400, "仅已发布资源可订阅");
        }
        r.setSubscriptionStatus("SUBSCRIBED");
        catalogMapper.updateById(r);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "CATALOG_SUBSCRIBE", "gov_catalog_resource", String.valueOf(id), r.getResourceName());
        return Map.of("resourceId", id, "subscriptionStatus", "SUBSCRIBED", "testEndpoint", "/api/v1/exchange/catalog");
    }

    @Transactional
    public Map<String, Object> distributeResource(UserPrincipal operator, Long id) {
        GovCatalogResource r = catalogMapper.selectById(id);
        if (r == null || !"SUBSCRIBED".equals(r.getSubscriptionStatus())) {
            throw new BusinessException(400, "须先完成订阅审批");
        }
        r.setSubscriptionStatus("DISTRIBUTED");
        catalogMapper.updateById(r);
        return Map.of("resourceId", id, "status", "DISTRIBUTED", "invokeResult", "OK traceId=GOV-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private Map<String, Object> buildLineageGraph() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        for (GovMetadataRegistry e : metadataMapper.selectList(null)) {
            nodes.add(Map.of("id", e.getEntryCode(), "label", e.getEntryName(), "type", e.getEntryType()));
        }
        edges.add(Map.of("from", "META_DS_DEMO", "to", "META_CAT_ASSET", "label", "采集"));
        edges.add(Map.of("from", "META_CAT_ASSET", "to", "META_LINEAGE_01", "label", "血缘"));
        return Map.of("nodes", nodes, "edges", edges);
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " required");
        }
        return v;
    }

    private int intVal(Object v, int def) {
        if (v == null) {
            return def;
        }
        return Integer.parseInt(String.valueOf(v));
    }
}
