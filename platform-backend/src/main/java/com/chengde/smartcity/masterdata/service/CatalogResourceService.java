package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.mapper.BizCatalogItemMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.integration.jdbc.JdbcProbeService;
import com.chengde.smartcity.masterdata.entity.GovCatalogApproval;
import com.chengde.smartcity.masterdata.entity.GovCatalogCategory;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovCatalogResourceVersion;
import com.chengde.smartcity.masterdata.entity.GovMetaDataSource;
import com.chengde.smartcity.masterdata.entity.GovMetaSourceCategory;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.mapper.GovCatalogApprovalMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogCategoryMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceVersionMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaDataSourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaSourceCategoryMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetadataRegistryMapper;
import com.chengde.smartcity.masterdata.support.DataLayerSupport;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.entity.SysUser;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import com.chengde.smartcity.system.mapper.SysUserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogResourceService {

    private static final Logger log = LoggerFactory.getLogger(CatalogResourceService.class);
    private static final Set<String> ACTION_TYPES = Set.of(
            "PUBLISH", "OFFLINE", "UPDATE", "DELETE", "CREATE", "BIND", "UNBIND");
    private static final Set<String> CATEGORY_ACTION_TYPES = Set.of("CAT_CREATE", "CAT_UPDATE", "CAT_DELETE");
    private static final ObjectMapper OM = new ObjectMapper();
    private static final String[] EXPORT_HEADERS = {
            "resourceCode", "resourceName", "resourceType", "categoryId", "providerOrg",
            "resourceFormat", "shareType", "updateCycle", "description", "secretFlag",
            "metadataEntryCode", "sourcePathType", "physicalTableName"
    };
    /** 发布前质量分建议门槛（未评分不拦截，评分过低拦截） */
    private static final BigDecimal MIN_QUALITY_SCORE = new BigDecimal("60");

    private static final long PLATFORM_ODS_ID = -1L;
    private static final long PLATFORM_DWD_ID = -2L;
    private static final long PLATFORM_DWS_ID = -3L;
    private static final long PLATFORM_ADS_ID = -4L;

    private final GovCatalogResourceMapper resourceMapper;
    private final GovCatalogCategoryMapper categoryMapper;
    private final GovCatalogApprovalMapper approvalMapper;
    private final GovCatalogResourceVersionMapper versionMapper;
    private final GovMetadataRegistryMapper metadataRegistryMapper;
    private final BizCatalogItemMapper portalCatalogMapper;
    private final CatalogCategoryService categoryService;
    private final IngDataSourceMapper ingDataSourceMapper;
    private final JdbcProbeService jdbcProbeService;
    private final MetadataSubsystemService metadataSubsystemService;
    private final MetaDataSourceService metaDataSourceService;
    private final GovMetaDataSourceMapper metaDataSourceMapper;
    private final GovMetaSourceCategoryMapper metaSourceCategoryMapper;
    private final SysUserMapper sysUserMapper;
    private final SysOrgMapper orgMapper;

    public CatalogResourceService(GovCatalogResourceMapper resourceMapper,
                                  GovCatalogCategoryMapper categoryMapper,
                                  GovCatalogApprovalMapper approvalMapper,
                                  GovCatalogResourceVersionMapper versionMapper,
                                  GovMetadataRegistryMapper metadataRegistryMapper,
                                  BizCatalogItemMapper portalCatalogMapper,
                                  CatalogCategoryService categoryService,
                                  IngDataSourceMapper ingDataSourceMapper,
                                  JdbcProbeService jdbcProbeService,
                                  MetadataSubsystemService metadataSubsystemService,
                                  MetaDataSourceService metaDataSourceService,
                                  GovMetaDataSourceMapper metaDataSourceMapper,
                                  GovMetaSourceCategoryMapper metaSourceCategoryMapper,
                                  SysUserMapper sysUserMapper,
                                  SysOrgMapper orgMapper) {
        this.resourceMapper = resourceMapper;
        this.categoryMapper = categoryMapper;
        this.approvalMapper = approvalMapper;
        this.versionMapper = versionMapper;
        this.metadataRegistryMapper = metadataRegistryMapper;
        this.portalCatalogMapper = portalCatalogMapper;
        this.categoryService = categoryService;
        this.ingDataSourceMapper = ingDataSourceMapper;
        this.jdbcProbeService = jdbcProbeService;
        this.metadataSubsystemService = metadataSubsystemService;
        this.metaDataSourceService = metaDataSourceService;
        this.metaDataSourceMapper = metaDataSourceMapper;
        this.metaSourceCategoryMapper = metaSourceCategoryMapper;
        this.sysUserMapper = sysUserMapper;
        this.orgMapper = orgMapper;
    }

    public List<GovCatalogResource> list(Long categoryId, String resourceType, String publishStatus,
                                         String approvalStatus, String keyword) {
        return list(categoryId, resourceType, publishStatus, approvalStatus, keyword, null, null, null, null, null, null, null);
    }

    public List<GovCatalogResource> list(Long categoryId, String resourceType, String publishStatus,
                                         String approvalStatus, String keyword,
                                         String sourcePathType, String providerOrg, Boolean unboundOnly) {
        return list(categoryId, resourceType, publishStatus, approvalStatus, keyword,
                sourcePathType, providerOrg, unboundOnly, null, null, null, null);
    }

    public List<GovCatalogResource> list(Long categoryId, String resourceType, String publishStatus,
                                         String approvalStatus, String keyword,
                                         String sourcePathType, String providerOrg, Boolean unboundOnly,
                                         String catalogOrigin, String shareType) {
        return list(categoryId, resourceType, publishStatus, approvalStatus, keyword,
                sourcePathType, providerOrg, unboundOnly, catalogOrigin, shareType, null, null);
    }

    public List<GovCatalogResource> list(Long categoryId, String resourceType, String publishStatus,
                                         String approvalStatus, String keyword,
                                         String sourcePathType, String providerOrg, Boolean unboundOnly,
                                         String catalogOrigin, String shareType, Boolean excludeApprovalDraft) {
        return list(categoryId, resourceType, publishStatus, approvalStatus, keyword,
                sourcePathType, providerOrg, unboundOnly, catalogOrigin, shareType, excludeApprovalDraft, null);
    }

    public List<GovCatalogResource> list(Long categoryId, String resourceType, String publishStatus,
                                         String approvalStatus, String keyword,
                                         String sourcePathType, String providerOrg, Boolean unboundOnly,
                                         String catalogOrigin, String shareType, Boolean excludeApprovalDraft,
                                         String resourceFormat) {
        return list(categoryId, resourceType, publishStatus, approvalStatus, keyword,
                sourcePathType, providerOrg, unboundOnly, catalogOrigin, shareType, excludeApprovalDraft,
                resourceFormat, null);
    }

    public List<GovCatalogResource> list(Long categoryId, String resourceType, String publishStatus,
                                         String approvalStatus, String keyword,
                                         String sourcePathType, String providerOrg, Boolean unboundOnly,
                                         String catalogOrigin, String shareType, Boolean excludeApprovalDraft,
                                         String resourceFormat, UserPrincipal operator) {
        return list(categoryId, resourceType, publishStatus, approvalStatus, keyword,
                sourcePathType, providerOrg, unboundOnly, catalogOrigin, shareType, excludeApprovalDraft,
                resourceFormat, operator, false);
    }

    /**
     * @param forPortal true=资源目录门户浏览：不按提供方组织隔离，与部门数据共享门户一致展示全部已发布目录
     */
    public List<GovCatalogResource> list(Long categoryId, String resourceType, String publishStatus,
                                         String approvalStatus, String keyword,
                                         String sourcePathType, String providerOrg, Boolean unboundOnly,
                                         String catalogOrigin, String shareType, Boolean excludeApprovalDraft,
                                         String resourceFormat, UserPrincipal operator, boolean forPortal) {
        LambdaQueryWrapper<GovCatalogResource> q = new LambdaQueryWrapper<GovCatalogResource>()
                .orderByDesc(GovCatalogResource::getId);
        if (Boolean.TRUE.equals(unboundOnly)) {
            q.and(w -> w.isNull(GovCatalogResource::getCategoryId).or().eq(GovCatalogResource::getCategoryId, 0L));
        } else if (categoryId != null) {
            q.eq(GovCatalogResource::getCategoryId, categoryId);
        }
        if (resourceType != null && !resourceType.isBlank()) {
            q.eq(GovCatalogResource::getResourceType, resourceType);
        }
        if (publishStatus != null && !publishStatus.isBlank()) {
            q.eq(GovCatalogResource::getPublishStatus, publishStatus);
        }
        if (approvalStatus != null && !approvalStatus.isBlank()) {
            q.eq(GovCatalogResource::getApprovalStatus, approvalStatus);
            // 「草稿」筛选不含已下线（已下线用 publishStatus=OFFLINE）
            if ("DRAFT".equalsIgnoreCase(approvalStatus.trim())) {
                q.and(w -> w.isNull(GovCatalogResource::getPublishStatus)
                        .or().ne(GovCatalogResource::getPublishStatus, "OFFLINE"));
            }
        }
        if (Boolean.TRUE.equals(excludeApprovalDraft)) {
            // 目录注册发布：仅编目「提交」后可见（TO_REGISTER），及已提交发布/下线审批中的（PENDING）
            q.in(GovCatalogResource::getApprovalStatus, "TO_REGISTER", "PENDING");
        }
        if (sourcePathType != null && !sourcePathType.isBlank()) {
            q.eq(GovCatalogResource::getSourcePathType, sourcePathType.trim().toUpperCase(Locale.ROOT));
        }
        if (providerOrg != null && !providerOrg.isBlank()) {
            q.like(GovCatalogResource::getProviderOrg, providerOrg.trim());
        }
        // 组织隔离：非超管/平台管理员仅能看本机构提供方目录（门户浏览除外）
        if (!forPortal) {
            applyProviderOrgScope(q, operator);
        }
        if (catalogOrigin != null && !catalogOrigin.isBlank()) {
            q.eq(GovCatalogResource::getCatalogOrigin, catalogOrigin.trim().toUpperCase(Locale.ROOT));
        }
        if (shareType != null && !shareType.isBlank()) {
            q.eq(GovCatalogResource::getShareType, shareType.trim().toUpperCase(Locale.ROOT));
        }
        if (resourceFormat != null && !resourceFormat.isBlank()) {
            q.eq(GovCatalogResource::getResourceFormat, resourceFormat.trim().toUpperCase(Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(GovCatalogResource::getResourceCode, keyword)
                    .or().like(GovCatalogResource::getResourceName, keyword)
                    .or().like(GovCatalogResource::getProviderOrg, keyword)
                    .or().like(GovCatalogResource::getMetadataEntryCode, keyword)
                    .or().like(GovCatalogResource::getPhysicalTableName, keyword)
                    .or().like(GovCatalogResource::getDescription, keyword));
        }
        return resourceMapper.selectList(q);
    }

    private void applyProviderOrgScope(LambdaQueryWrapper<GovCatalogResource> q, UserPrincipal operator) {
        if (operator == null || operator.isSystemAdmin() || operator.isPlatformAdmin()) {
            return;
        }
        String orgName = resolveOrgName(operator.getOrgId());
        if (orgName == null || orgName.isBlank()) {
            q.eq(GovCatalogResource::getId, -1L);
            return;
        }
        q.eq(GovCatalogResource::getProviderOrg, orgName);
    }

    private String resolveOrgName(Long orgId) {
        if (orgId == null) {
            return null;
        }
        SysOrg org = orgMapper.selectById(orgId);
        return org == null ? null : org.getOrgName();
    }

    private void assertResourceOrgAccess(UserPrincipal operator, GovCatalogResource r) {
        if (operator == null || operator.isSystemAdmin() || operator.isPlatformAdmin()) {
            return;
        }
        String orgName = resolveOrgName(operator.getOrgId());
        if (orgName == null || orgName.isBlank()
                || r.getProviderOrg() == null
                || !orgName.equals(r.getProviderOrg())) {
            throw new BusinessException(403, "无权访问其他部门的目录数据");
        }
    }

    /** 可编目登记对象：TABLE/资产类，排除过程层 DWD */
    public List<Map<String, Object>> listEligibleMetadata(String keyword) {
        LambdaQueryWrapper<GovMetadataRegistry> q = new LambdaQueryWrapper<GovMetadataRegistry>()
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .ne(GovMetadataRegistry::getEntryType, "COLUMN")
                .orderByDesc(GovMetadataRegistry::getId)
                .last("limit 200");
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(GovMetadataRegistry::getEntryCode, kw)
                    .or().like(GovMetadataRegistry::getEntryName, kw)
                    .or().like(GovMetadataRegistry::getPhysicalTableName, kw));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovMetadataRegistry e : metadataRegistryMapper.selectList(q)) {
            String layer = resolveLayer(e);
            if (DataLayerSupport.isProcessLayer(layer)) {
                continue;
            }
            if (!DataLayerSupport.isCatalogableLayer(layer) && layer != null && !layer.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entryCode", e.getEntryCode());
            row.put("entryName", e.getEntryName());
            row.put("entryType", e.getEntryType());
            row.put("dataLayer", layer);
            row.put("databaseName", e.getDatabaseName());
            row.put("physicalTableName", e.getPhysicalTableName());
            row.put("dataSourceId", e.getDataSourceId());
            row.put("ownerName", e.getOwnerName());
            row.put("sourcePathType", DataLayerSupport.sourcePathTypeForLayer(layer));
            row.put("catalogable", true);
            out.add(row);
        }
        return out;
    }

    /**
     * 编目库表挂载：可选数据源（按分类侧栏）。
     * sourceKind=ING：登记源 + 平台分层虚拟源；sourceKind=META：元数据「数据源管理」。
     */
    public List<Map<String, Object>> listBindSources(String categoryKey, Long categoryId, String keyword, String sourceKind) {
        if ("META".equalsIgnoreCase(sourceKind)) {
            return listMetaBindSources(categoryId, keyword);
        }
        return listIngBindSources(categoryKey, keyword);
    }

    private List<Map<String, Object>> listMetaBindSources(Long categoryId, String keyword) {
        Map<Long, String> layerByCat = metaSourceCategoryMapper.selectList(new LambdaQueryWrapper<GovMetaSourceCategory>()
                        .eq(GovMetaSourceCategory::getStatus, "ACTIVE"))
                .stream()
                .collect(Collectors.toMap(GovMetaSourceCategory::getId,
                        c -> c.getLayerCode() == null ? "" : c.getLayerCode().trim().toUpperCase(Locale.ROOT),
                        (a, b) -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> src : metaDataSourceService.list(categoryId, keyword)) {
            Long catId = longOrNull(src.get("categoryId"));
            String layer = catId == null || !layerByCat.containsKey(catId) || layerByCat.get(catId).isBlank()
                    ? "ODS" : layerByCat.get(catId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", src.get("id"));
            row.put("sourceCode", src.get("sourceCode"));
            row.put("sourceName", src.get("sourceName"));
            row.put("sourceType", src.get("adapterType"));
            row.put("categoryId", src.get("categoryId"));
            row.put("categoryName", src.get("categoryName"));
            row.put("databaseName", src.get("dbName"));
            row.put("providerOrg", firstNonBlank(src.get("deptName"), src.get("orgName")));
            row.put("versionLabel", "V1");
            row.put("sourceKind", "META");
            row.put("platformLayer", false);
            row.put("dataLayer", layer);
            row.put("ingSourceId", src.get("ingSourceId"));
            row.put("catalogable", !DataLayerSupport.isProcessLayer(layer));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> listIngBindSources(String categoryKey, String keyword) {
        List<Map<String, Object>> raw = new ArrayList<>();
        // 登记数据源（含未测通，便于新增后立刻可选）
        List<IngDataSource> sources = ingDataSourceMapper.selectList(new LambdaQueryWrapper<IngDataSource>()
                .orderByDesc(IngDataSource::getId)
                .last("limit 300"));
        for (IngDataSource ds : sources) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", ds.getId());
            row.put("sourceCode", ds.getSourceCode());
            row.put("sourceName", ds.getSourceName());
            row.put("sourceType", ds.getSourceType());
            row.put("layerHint", "EXTERNAL");
            row.put("platformLayer", false);
            row.put("systemName", ds.getSystemName());
            row.put("connStatus", ds.getConnStatus());
            row.put("registerStatus", ds.getRegisterStatus());
            try {
                JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
                row.put("databaseName", conn.database);
            } catch (Exception ignored) {
                row.put("databaseName", null);
            }
            raw.add(row);
        }
        // 平台分层库
        raw.addAll(metadataSubsystemService.listCollectDataSources().stream()
                .filter(s -> Boolean.TRUE.equals(s.get("platformLayer")))
                .toList());

        List<Map<String, Object>> out = new ArrayList<>();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        for (Map<String, Object> src : raw) {
            String layer = String.valueOf(src.getOrDefault("layerHint",
                    src.getOrDefault("dataLayer", "EXTERNAL")));
            String cat = bindCategoryOf(layer, src);
            if (categoryKey != null && !categoryKey.isBlank() && !"ALL".equalsIgnoreCase(categoryKey)
                    && !cat.equalsIgnoreCase(categoryKey)) {
                continue;
            }
            String name = String.valueOf(src.getOrDefault("sourceName", ""));
            String code = String.valueOf(src.getOrDefault("sourceCode", ""));
            if (!kw.isEmpty()
                    && !name.toLowerCase(Locale.ROOT).contains(kw)
                    && !code.toLowerCase(Locale.ROOT).contains(kw)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(src);
            row.put("categoryKey", cat);
            row.put("categoryName", bindCategoryLabel(cat));
            row.put("catalogable", !"GOVERNANCE".equals(cat) && !"DWD".equalsIgnoreCase(layer));
            row.put("providerOrg", src.getOrDefault("systemName", src.getOrDefault("ownerName", "")));
            String conn = String.valueOf(src.getOrDefault("connStatus", ""));
            row.put("versionLabel", "OK".equalsIgnoreCase(conn) ? "V1"
                    : ("UNTESTED".equalsIgnoreCase(conn) ? "待采集" : statusOrDefault(conn, "待采集")));
            row.put("sourceKind", "ING");
            out.add(row);
        }
        return out;
    }

    public List<Map<String, Object>> listBindTables(Long sourceId) {
        return listBindTables(sourceId, "ING");
    }

    public List<Map<String, Object>> listBindTables(Long sourceId, String sourceKind) {
        if (sourceId == null) {
            throw new BusinessException(400, "sourceId 必填");
        }
        if ("META".equalsIgnoreCase(sourceKind)) {
            return listMetaBindTables(sourceId);
        }
        List<Map<String, Object>> summaries = listBindTableSummaries(sourceId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> summary : summaries) {
            String name = String.valueOf(summary.getOrDefault("tableName", "")).trim();
            if (name.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tableName", name);
            row.put("sourceTable", name);
            String remark = str(summary.get("remarks"), null);
            GovMetadataRegistry meta = findMetadataBySourceTable(sourceId, name);
            if (meta != null) {
                row.put("metadataEntryCode", meta.getEntryCode());
                row.put("entryName", meta.getEntryName());
                row.put("dataLayer", resolveLayer(meta));
                row.put("catalogable", !DataLayerSupport.isProcessLayer(resolveLayer(meta)));
                if (remark == null || remark.isBlank()) {
                    remark = meta.getEntryName();
                }
            } else {
                row.put("metadataEntryCode", null);
                row.put("catalogable", !isPlatformDwd(sourceId));
            }
            row.put("tableComment", remark);
            row.put("chineseName", remark);
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> listMetaBindTables(Long metaSourceId) {
        String layer = resolveMetaDataSourceLayer(metaSourceId);
        boolean processLayer = DataLayerSupport.isProcessLayer(layer);
        List<Map<String, Object>> tables = metadataSubsystemService.listCollectMetaDataSourceTables(metaSourceId, null);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> t : tables) {
            String name = String.valueOf(t.getOrDefault("tableName", t.get("sourceTable"))).trim();
            if (name.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tableName", name);
            row.put("sourceTable", name);
            Object remark = t.get("remarks");
            if (remark == null) {
                remark = t.get("tableComment");
            }
            row.put("tableComment", remark);
            row.put("chineseName", remark);
            GovMetadataRegistry meta = findMetadataByTableNameOnly(name);
            if (meta != null) {
                row.put("metadataEntryCode", meta.getEntryCode());
                row.put("entryName", meta.getEntryName());
                String metaLayer = resolveLayer(meta);
                row.put("dataLayer", metaLayer);
                row.put("catalogable", !DataLayerSupport.isProcessLayer(metaLayer));
            } else {
                row.put("metadataEntryCode", null);
                row.put("dataLayer", layer);
                row.put("catalogable", !processLayer);
            }
            out.add(row);
        }
        return out;
    }

    public Map<String, Object> describeBindTable(Long sourceId, String tableName) {
        return describeBindTable(sourceId, tableName, "ING");
    }

    public Map<String, Object> describeBindTable(Long sourceId, String tableName, String sourceKind) {
        if (sourceId == null || tableName == null || tableName.isBlank()) {
            throw new BusinessException(400, "sourceId 与 tableName 必填");
        }
        if ("META".equalsIgnoreCase(sourceKind)) {
            return describeMetaBindTable(sourceId, tableName.trim());
        }
        if (isPlatformDwd(sourceId)) {
            throw new BusinessException(400, "过程层（治理库/DWD）不可编目进资源目录");
        }
        Map<String, Object> described = describeTableRaw(sourceId, tableName.trim());
        GovMetadataRegistry meta = findMetadataBySourceTable(sourceId, tableName.trim());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceId", sourceId);
        out.put("tableName", tableName.trim());
        out.put("columns", described.get("columns"));
        out.put("primaryKeys", described.getOrDefault("primaryKeys", List.of()));
        if (meta != null) {
            String layer = resolveLayer(meta);
            if (DataLayerSupport.isProcessLayer(layer)) {
                throw new BusinessException(400, "过程层（DWD）不可编目进资源目录");
            }
            out.put("metadataEntryCode", meta.getEntryCode());
            out.put("entryName", meta.getEntryName());
            out.put("dataLayer", layer);
            out.put("sourcePathType", DataLayerSupport.sourcePathTypeForLayer(layer));
            out.put("ownerName", meta.getOwnerName());
            out.put("physicalTableName", meta.getPhysicalTableName());
            out.put("catalogable", true);
        } else {
            out.put("metadataEntryCode", null);
            out.put("catalogable", true);
            out.put("physicalTableName", tableName.trim());
            out.put("sourcePathType", isPlatformLayerId(sourceId)
                    ? DataLayerSupport.sourcePathTypeForLayer(platformLayerDatabase(sourceId))
                    : "DIRECT");
        }
        return out;
    }

    private Map<String, Object> describeMetaBindTable(Long metaSourceId, String tableName) {
        String layer = resolveMetaDataSourceLayer(metaSourceId);
        if (DataLayerSupport.isProcessLayer(layer)) {
            throw new BusinessException(400, "过程层（治理库/DWD）不可编目进资源目录");
        }
        Map<String, Object> probe = metadataSubsystemService.probeMetaDataSourceTableColumns(metaSourceId, tableName, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) probe.get("fields");
        List<Map<String, Object>> columns = new ArrayList<>();
        if (fields != null) {
            for (Map<String, Object> f : fields) {
                Map<String, Object> col = new LinkedHashMap<>();
                col.put("columnName", f.get("code"));
                col.put("dataType", f.get("type"));
                String remark = str(f.get("name"), null);
                col.put("remarks", remark);
                col.put("comment", remark);
                columns.add(col);
            }
        }
        GovMetadataRegistry meta = findMetadataByTableNameOnly(tableName);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceId", metaSourceId);
        out.put("tableName", tableName);
        out.put("sourceKind", "META");
        out.put("columns", columns);
        out.put("primaryKeys", probe.getOrDefault("primaryKeys", List.of()));
        if (meta != null) {
            String metaLayer = resolveLayer(meta);
            if (DataLayerSupport.isProcessLayer(metaLayer)) {
                throw new BusinessException(400, "过程层（DWD）不可编目进资源目录");
            }
            out.put("metadataEntryCode", meta.getEntryCode());
            out.put("entryName", meta.getEntryName());
            out.put("dataLayer", metaLayer);
            out.put("sourcePathType", DataLayerSupport.sourcePathTypeForLayer(metaLayer));
            out.put("ownerName", meta.getOwnerName());
            out.put("physicalTableName", meta.getPhysicalTableName());
        } else {
            out.put("metadataEntryCode", null);
            out.put("dataLayer", layer);
            out.put("sourcePathType", DataLayerSupport.sourcePathTypeForLayer(layer));
            out.put("physicalTableName", tableName);
        }
        out.put("catalogable", true);
        return out;
    }

    private String resolveMetaDataSourceLayer(Long metaSourceId) {
        GovMetaDataSource mds = metaDataSourceMapper.selectById(metaSourceId);
        if (mds == null || !"ACTIVE".equals(mds.getStatus())) {
            throw new BusinessException(404, "元数据数据源不存在");
        }
        if (mds.getCategoryId() == null) {
            return "ODS";
        }
        GovMetaSourceCategory cat = metaSourceCategoryMapper.selectById(mds.getCategoryId());
        if (cat == null || cat.getLayerCode() == null || cat.getLayerCode().isBlank()) {
            return "ODS";
        }
        return cat.getLayerCode().trim().toUpperCase(Locale.ROOT);
    }

    private GovMetadataRegistry findMetadataByTableNameOnly(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return null;
        }
        List<GovMetadataRegistry> list = metadataRegistryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .eq(GovMetadataRegistry::getEntryType, "TABLE")
                .and(w -> w.eq(GovMetadataRegistry::getPhysicalTableName, tableName)
                        .or().eq(GovMetadataRegistry::getEntryName, tableName))
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private List<String> listBindTableNames(Long sourceId) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> t : listBindTableSummaries(sourceId)) {
            Object n = t.get("tableName");
            if (n != null && !String.valueOf(n).isBlank()) {
                names.add(String.valueOf(n));
            }
        }
        return names;
    }

    /** 编目选表：表名 + 表注释（中文名称）。 */
    private List<Map<String, Object>> listBindTableSummaries(Long sourceId) {
        if (isPlatformLayerId(sourceId)) {
            String database = platformLayerDatabase(sourceId);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> t : metadataSubsystemService.listCollectDataSourceTables(sourceId)) {
                Object n = t.get("sourceTable");
                if (n == null) n = t.get("tableName");
                if (n == null || String.valueOf(n).isBlank()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("tableName", String.valueOf(n));
                Object remark = t.get("remarks");
                if (remark == null) remark = t.get("tableComment");
                if (remark == null) remark = t.get("tableNameZh");
                row.put("remarks", remark == null || String.valueOf(remark).isBlank()
                        ? null : String.valueOf(remark).trim());
                out.add(row);
            }
            if (!out.isEmpty() || database == null) {
                return out;
            }
        }
        IngDataSource ds = ingDataSourceMapper.selectById(sourceId);
        if (ds == null) {
            throw new BusinessException(404, "登记数据源不存在");
        }
        JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
        return jdbcProbeService.listTableSummaries(conn);
    }

    private Map<String, Object> describeTableRaw(Long sourceId, String tableName) {
        if (isPlatformLayerId(sourceId)) {
            List<Map<String, Object>> tables = metadataSubsystemService.listCollectDataSourceTables(sourceId);
            for (Map<String, Object> t : tables) {
                Object n = t.get("sourceTable");
                if (n == null) n = t.get("tableName");
                if (tableName.equalsIgnoreCase(String.valueOf(n))) {
                    return t;
                }
            }
            throw new BusinessException(404, "分层库中未找到表 " + tableName);
        }
        IngDataSource ds = ingDataSourceMapper.selectById(sourceId);
        if (ds == null) {
            throw new BusinessException(404, "登记数据源不存在");
        }
        JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
        return jdbcProbeService.describeTable(conn, tableName);
    }

    private GovMetadataRegistry findMetadataBySourceTable(Long sourceId, String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<GovMetadataRegistry> q = new LambdaQueryWrapper<GovMetadataRegistry>()
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .eq(GovMetadataRegistry::getEntryType, "TABLE")
                .and(w -> w.eq(GovMetadataRegistry::getPhysicalTableName, tableName)
                        .or().eq(GovMetadataRegistry::getEntryName, tableName))
                .last("limit 20");
        if (sourceId != null && sourceId > 0) {
            q.eq(GovMetadataRegistry::getDataSourceId, sourceId);
        }
        List<GovMetadataRegistry> list = metadataRegistryMapper.selectList(q);
        if (list.isEmpty() && sourceId != null && sourceId > 0) {
            // 宽松：仅按表名匹配（平台分层或 dataSourceId 未回填时）
            list = metadataRegistryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                    .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                    .eq(GovMetadataRegistry::getEntryType, "TABLE")
                    .and(w -> w.eq(GovMetadataRegistry::getPhysicalTableName, tableName)
                            .or().eq(GovMetadataRegistry::getEntryName, tableName))
                    .last("limit 5"));
        }
        return list.isEmpty() ? null : list.get(0);
    }

    private static String bindCategoryOf(String layer, Map<String, Object> src) {
        if (Boolean.TRUE.equals(src.get("platformLayer"))) {
            String l = layer == null ? "" : layer.trim().toUpperCase(Locale.ROOT);
            if ("ODS".equals(l)) return "ODS";
            if ("DWD".equals(l)) return "GOVERNANCE";
            if ("DWS".equals(l) || "ADS".equals(l)) return "THEME";
            return "OTHER";
        }
        return "SOURCE";
    }

    private static String bindCategoryLabel(String cat) {
        return switch (cat == null ? "" : cat.toUpperCase(Locale.ROOT)) {
            case "SOURCE" -> "来源";
            case "ODS" -> "原始库";
            case "GOVERNANCE" -> "治理库";
            case "THEME" -> "主题专题";
            case "DICT" -> "字典";
            default -> "其他";
        };
    }

    private static String statusOrDefault(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private static boolean isPlatformLayerId(Long id) {
        return id != null && (id == PLATFORM_ODS_ID || id == PLATFORM_DWD_ID
                || id == PLATFORM_DWS_ID || id == PLATFORM_ADS_ID);
    }

    private static boolean isPlatformDwd(Long id) {
        return id != null && id == PLATFORM_DWD_ID;
    }

    private static String platformLayerDatabase(Long id) {
        if (id == null) return null;
        if (id == PLATFORM_ODS_ID) return DataLayerSupport.ODS;
        if (id == PLATFORM_DWD_ID) return DataLayerSupport.DWD;
        if (id == PLATFORM_DWS_ID) return DataLayerSupport.DWS;
        if (id == PLATFORM_ADS_ID) return DataLayerSupport.ADS;
        return null;
    }

    public GovCatalogResource get(Long id) {
        return require(id);
    }

    public GovCatalogResource get(UserPrincipal operator, Long id) {
        GovCatalogResource r = require(id);
        assertResourceOrgAccess(operator, r);
        return r;
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        GovCatalogResource r = new GovCatalogResource();
        applyBody(r, body, true);
        // 部门账号：信息资源提供方强制为本机构，防止越权编目
        if (operator != null && !operator.isSystemAdmin() && !operator.isPlatformAdmin()) {
            String orgName = resolveOrgName(operator.getOrgId());
            if (orgName == null || orgName.isBlank()) {
                throw new BusinessException(400, "当前账号未绑定组织机构，无法编目");
            }
            r.setProviderOrg(orgName);
        }
        enrichFromMetadata(r, true);
        assertCatalogable(r, false);
        validateShareOpen(r);
        r.setPublishStatus("DRAFT");
        r.setApprovalStatus("DRAFT");
        r.setVersionNo(1);
        r.setSecretFlag(intVal(body.get("secretFlag"), 0));
        if (operator != null) {
            r.setCreatedBy(operator.getUsername());
            r.setUpdatedBy(operator.getUsername());
        }
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        resourceMapper.insert(r);
        // 编目新增不进审批：先到「目录注册发布」挂载/发布，点发布后再生成 PUBLISH 审批
        log.info("catalog resource created id={} code={}, awaiting register-publish",
                r.getId(), r.getResourceCode());
        return r.getId();
    }

    /**
     * 批量新增：从已登记元数据（库表等）抽取核心字段生成标准资源目录。
     * 逐条独立落库（本方法不加总事务），单条失败不影响其它条。
     * body: entryCodes[], catalogOrigin?, shareType?, updateCycle?, resourceFormat?
     */
    public Map<String, Object> batchCreateFromMetadata(UserPrincipal operator, Map<String, Object> body) {
        Object raw = body.get("entryCodes");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(400, "entryCodes 不能为空");
        }
        String catalogOrigin = str(body.get("catalogOrigin"), "GOVERNANCE");
        String shareType = str(body.get("shareType"), "OPEN");
        String updateCycle = str(body.get("updateCycle"), "DAILY");
        String resourceFormat = str(body.get("resourceFormat"), "DATABASE");
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (Object item : list) {
            String entryCode = item == null ? "" : String.valueOf(item).trim();
            if (entryCode.isEmpty()) {
                skipped++;
                continue;
            }
            try {
                Long exist = resourceMapper.selectCount(new LambdaQueryWrapper<GovCatalogResource>()
                        .eq(GovCatalogResource::getMetadataEntryCode, entryCode)
                        .eq(GovCatalogResource::getCatalogOrigin, catalogOrigin.toUpperCase(Locale.ROOT)));
                if (exist != null && exist > 0) {
                    skipped++;
                    errors.add(entryCode + "：该来源下已编目，已跳过");
                    continue;
                }
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("metadataEntryCode", entryCode);
                one.put("catalogOrigin", catalogOrigin);
                one.put("shareType", shareType);
                one.put("updateCycle", updateCycle);
                one.put("resourceFormat", resourceFormat);
                one.put("resourceType", "DATA");
                create(operator, one);
                created++;
            } catch (BusinessException ex) {
                skipped++;
                errors.add(entryCode + "：" + ex.getMessage());
            } catch (Exception ex) {
                skipped++;
                errors.add(entryCode + "：" + (ex.getMessage() == null ? "失败" : ex.getMessage()));
                log.warn("batchCreateFromMetadata failed entry={}", entryCode, ex);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovCatalogResource r = require(id);
        assertResourceOrgAccess(operator, r);
        if ("PUBLISHED".equalsIgnoreCase(r.getPublishStatus())) {
            throw new BusinessException(400, "已发布资源不可直接编辑，请先下线");
        }
        if ("PENDING".equalsIgnoreCase(r.getApprovalStatus())) {
            throw new BusinessException(400, "审批中不可编辑");
        }
        if ("TO_REGISTER".equalsIgnoreCase(r.getApprovalStatus())) {
            throw new BusinessException(400, "待发布状态不可编辑，请先撤回");
        }
        // 部门账号不可改提供方到其他机构
        if (operator != null && !operator.isSystemAdmin() && !operator.isPlatformAdmin()
                && body != null && body.containsKey("providerOrg")) {
            String orgName = resolveOrgName(operator.getOrgId());
            String next = str(body.get("providerOrg"), null);
            if (orgName != null && next != null && !orgName.equals(next)) {
                throw new BusinessException(403, "部门管理员不可修改信息资源提供方");
            }
        }
        applyBody(r, body, false);
        enrichFromMetadata(r, false);
        assertCatalogable(r, false);
        validateShareOpen(r);
        if (operator != null) {
            r.setUpdatedBy(operator.getUsername());
        }
        r.setUpdatedAt(LocalDateTime.now());
        resourceMapper.updateById(r);
    }

    /**
     * 删除：
     * - 草稿（approvalStatus=DRAFT）：直接物理删除，无需审批
     * - 审批中（PENDING）：禁止删除
     * - 已通过等其它状态：提交删除审批（仅草稿/已下线发布态可提）
     */
    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        GovCatalogResource r = require(id);
        if ("PENDING".equalsIgnoreCase(r.getApprovalStatus())) {
            throw new BusinessException(400, "审批中不可删除，请先撤回或处理待审申请");
        }
        if ("PUBLISHED".equalsIgnoreCase(r.getPublishStatus())) {
            throw new BusinessException(400, "已发布资源请先下线后再删除");
        }
        String approval = r.getApprovalStatus() == null ? "DRAFT" : r.getApprovalStatus().toUpperCase(Locale.ROOT);
        if ("TO_REGISTER".equals(approval)) {
            throw new BusinessException(400, "待发布状态请先撤回再删除");
        }
        // 草稿 / 驳回待提交 / 已撤回：直接删除
        if ("DRAFT".equals(approval) || "REJECTED".equals(approval) || "WITHDRAWN".equals(approval)) {
            offlinePortal(r);
            resourceMapper.deleteById(r.getId());
            return;
        }
        // 已通过等：走删除审批
        if (!"DRAFT".equalsIgnoreCase(r.getPublishStatus())
                && !"OFFLINE".equalsIgnoreCase(r.getPublishStatus())) {
            throw new BusinessException(400, "仅草稿或已下线资源可提交删除审批");
        }
        submit(operator, id, Map.of("actionType", "DELETE", "comment", "提交编目删除审批"));
    }

    @Transactional
    public void batchDelete(UserPrincipal operator, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "ids 不能为空");
        }
        for (Long id : ids) {
            delete(operator, id);
        }
    }

    @Transactional
    public GovCatalogApproval submit(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovCatalogResource r = require(id);
        if ("PENDING".equalsIgnoreCase(r.getApprovalStatus())) {
            throw new BusinessException(400, "已有待审批申请");
        }
        String actionType = str(body.get("actionType"), "PUBLISH").toUpperCase(Locale.ROOT);
        if (!ACTION_TYPES.contains(actionType)) {
            throw new BusinessException(400, "actionType 须为 PUBLISH/OFFLINE/UPDATE/DELETE/CREATE/BIND/UNBIND");
        }
        if ("OFFLINE".equals(actionType)) {
            if (!"PUBLISHED".equalsIgnoreCase(r.getPublishStatus())) {
                throw new BusinessException(400, "仅已发布资源可提交下线审批");
            }
        } else if ("DELETE".equals(actionType)) {
            if (!"DRAFT".equalsIgnoreCase(r.getPublishStatus())
                    && !"OFFLINE".equalsIgnoreCase(r.getPublishStatus())) {
                throw new BusinessException(400, "仅草稿或已下线资源可提交删除审批");
            }
        } else if (!"BIND".equals(actionType) && !"UNBIND".equals(actionType)) {
            if ("PUBLISHED".equalsIgnoreCase(r.getPublishStatus())) {
                throw new BusinessException(400, "已发布资源请走下线审批");
            }
            if ("PUBLISH".equals(actionType)) {
                assertCatalogable(r, false);
                assertQualityGate(r);
                if (r.getCategoryId() == null || r.getCategoryId() <= 0) {
                    throw new BusinessException(400, "发布前请先在「目录注册发布」将资源关联到分类");
                }
            }
        }
        GovCatalogApproval a = insertApproval(operator, id, r.getCategoryId(), r.getCatalogOrigin(),
                actionType, str(body.get("comment"), null), null);
        r.setApprovalStatus("PENDING");
        touch(r, operator);
        resourceMapper.updateById(r);
        return a;
    }

    /**
     * 编目页「提交」：草稿进入目录注册发布可见范围（approvalStatus=TO_REGISTER），不创建审批单。
     * 真正发布仍须在「目录注册发布」关联分类后提交 PUBLISH 审批。
     */
    @Transactional
    public GovCatalogResource submitToRegister(UserPrincipal operator, Long id) {
        GovCatalogResource r = require(id);
        if ("PENDING".equalsIgnoreCase(r.getApprovalStatus())) {
            throw new BusinessException(400, "审批中不可重复提交，请先处理待审申请");
        }
        if ("PUBLISHED".equalsIgnoreCase(r.getPublishStatus())) {
            throw new BusinessException(400, "已发布资源无需再提交到注册发布，如需变更请先下线");
        }
        String st = r.getApprovalStatus() == null ? "" : r.getApprovalStatus().toUpperCase(Locale.ROOT);
        if ("TO_REGISTER".equals(st)) {
            return r;
        }
        if (!"DRAFT".equals(st) && !"REJECTED".equals(st) && !"WITHDRAWN".equals(st) && !"APPROVED".equals(st)) {
            throw new BusinessException(400, "当前状态不可提交到注册发布");
        }
        if (r.getResourceName() == null || r.getResourceName().isBlank()) {
            throw new BusinessException(400, "请先完善资源名称后再提交");
        }
        r.setApprovalStatus("TO_REGISTER");
        touch(r, operator);
        resourceMapper.updateById(r);
        return r;
    }

    /**
     * 编目页「撤回」：待发布（TO_REGISTER）退回草稿，离开注册发布可见范围。
     */
    @Transactional
    public GovCatalogResource withdrawFromRegister(UserPrincipal operator, Long id) {
        GovCatalogResource r = require(id);
        if (!"TO_REGISTER".equalsIgnoreCase(r.getApprovalStatus())) {
            throw new BusinessException(400, "仅待发布状态可撤回为草稿");
        }
        r.setApprovalStatus("DRAFT");
        touch(r, operator);
        resourceMapper.updateById(r);
        return r;
    }

    @Transactional
    public GovCatalogApproval approve(UserPrincipal operator, Long approvalId, Map<String, Object> body) {
        assertCatalogAdmin(operator);
        GovCatalogApproval a = requireApproval(approvalId);
        if (!"PENDING".equalsIgnoreCase(a.getStatus())) {
            throw new BusinessException(400, "仅待处理审批可通过");
        }
        applyReviewerFields(a, body == null ? Map.of() : body);
        a.setStatus("APPROVED");
        a.setReviewComment(str(body == null ? null : body.get("comment"), "同意"));
        a.setReviewedAt(LocalDateTime.now());
        approvalMapper.updateById(a);

        String action = a.getActionType() == null ? "" : a.getActionType().toUpperCase(Locale.ROOT);
        if (CATEGORY_ACTION_TYPES.contains(action)) {
            applyCategoryApproval(operator, a, action);
            return a;
        }

        GovCatalogResource r = require(a.getResourceId());
        if ("DELETE".equals(action)) {
            offlinePortal(r);
            resourceMapper.deleteById(r.getId());
            return a;
        }
        if ("BIND".equals(action)) {
            applyBindFromApproval(operator, a, r);
            r.setApprovalStatus("APPROVED");
            touch(r, operator);
            resourceMapper.updateById(r);
            return a;
        }
        if ("UNBIND".equals(action)) {
            applyUnbindImmediate(operator, r);
            r.setApprovalStatus("APPROVED");
            touch(r, operator);
            resourceMapper.updateById(r);
            return a;
        }
        r.setApprovalStatus("APPROVED");
        if ("PUBLISH".equals(action)) {
            r.setPublishStatus("PUBLISHED");
            snapshotOnPublish(r, operator, "审批发布 v");
            syncPortal(r);
        } else if ("OFFLINE".equals(action)) {
            r.setPublishStatus("OFFLINE");
            // 下线审批通过后回到编目，须重新提交才进注册发布
            r.setApprovalStatus("DRAFT");
            offlinePortal(r);
        } else if ("CREATE".equals(action) || "UPDATE".equals(action)) {
            // 编目新增/变更通过后进入待注册发布，可在注册发布页挂载
            r.setApprovalStatus("TO_REGISTER");
        }
        touch(r, operator);
        resourceMapper.updateById(r);
        return a;
    }

    @Transactional
    public GovCatalogApproval reject(UserPrincipal operator, Long approvalId, Map<String, Object> body) {
        assertCatalogAdmin(operator);
        GovCatalogApproval a = requireApproval(approvalId);
        if (!"PENDING".equalsIgnoreCase(a.getStatus())) {
            throw new BusinessException(400, "仅待处理审批可驳回");
        }
        Map<String, Object> payload = body == null ? Map.of() : body;
        String comment = str(payload.get("comment"), null);
        if (comment == null || comment.isBlank()) {
            throw new BusinessException(400, "驳回须填写驳回意见");
        }
        applyReviewerFields(a, payload);
        a.setStatus("REJECTED");
        a.setReviewComment(comment);
        a.setReviewedAt(LocalDateTime.now());
        approvalMapper.updateById(a);

        String action = a.getActionType() == null ? "" : a.getActionType().toUpperCase(Locale.ROOT);
        if (CATEGORY_ACTION_TYPES.contains(action)) {
            return a;
        }
        if (a.getResourceId() != null) {
            GovCatalogResource r = require(a.getResourceId());
            r.setApprovalStatus("REJECTED");
            touch(r, operator);
            resourceMapper.updateById(r);
        }
        return a;
    }

    @Transactional
    public GovCatalogApproval withdraw(UserPrincipal operator, Long approvalId) {
        GovCatalogApproval a = requireApproval(approvalId);
        if (!"PENDING".equalsIgnoreCase(a.getStatus())) {
            throw new BusinessException(400, "仅待处理审批可撤回");
        }
        a.setStatus("WITHDRAWN");
        a.setReviewedAt(LocalDateTime.now());
        if (operator != null) {
            a.setReviewedBy(operator.getUsername());
        }
        approvalMapper.updateById(a);

        String action = a.getActionType() == null ? "" : a.getActionType().toUpperCase(Locale.ROOT);
        if (CATEGORY_ACTION_TYPES.contains(action)) {
            return a;
        }
        if (a.getResourceId() != null) {
            GovCatalogResource r = require(a.getResourceId());
            // 待审核撤回后回到待发布，可再次在注册发布页发布
            r.setApprovalStatus("TO_REGISTER");
            touch(r, operator);
            resourceMapper.updateById(r);
        }
        return a;
    }

    @Transactional
    public GovCatalogResource publish(UserPrincipal operator, Long id) {
        throw new BusinessException(400, "禁止直接发布：请提交「发布」审批，由「资源目录审批」通过后自动上架");
    }

    /** 兼容旧入口：转为提交下线审批，不旁路 */
    @Transactional
    public GovCatalogResource offline(UserPrincipal operator, Long id) {
        submit(operator, id, Map.of("actionType", "OFFLINE", "comment", "提交下线审批"));
        return require(id);
    }

    /**
     * 平台/系统管理员对已发布目录即时下线（审批页「已通过」操作列）。
     * 下线后 publishStatus=OFFLINE，资源回到编目管理可再编辑/发布。
     */
    @Transactional
    public GovCatalogResource adminForceOffline(UserPrincipal operator, Long id, Map<String, Object> body) {
        assertCatalogAdmin(operator);
        GovCatalogResource r = require(id);
        if (!"PUBLISHED".equalsIgnoreCase(r.getPublishStatus())) {
            throw new BusinessException(400, "仅已发布资源可下线");
        }
        if ("PENDING".equalsIgnoreCase(r.getApprovalStatus())) {
            throw new BusinessException(400, "审批中不可下线，请先处理待审申请");
        }
        String comment = str(body == null ? null : body.get("comment"), "管理员即时下线");
        r.setPublishStatus("OFFLINE");
        // 下线后回到编目草稿，须重新「提交」才会再出现在注册发布页
        r.setApprovalStatus("DRAFT");
        offlinePortal(r);
        touch(r, operator);
        resourceMapper.updateById(r);
        insertApprovedAudit(operator, r, "OFFLINE", comment);
        return r;
    }

    /**
     * 平台/系统管理员即时删除目录（审批页「已通过」操作列）。
     * 已发布资源会先下线门户再删除；草稿/已下线直接删除。
     */
    @Transactional
    public void adminForceDelete(UserPrincipal operator, Long id, Map<String, Object> body) {
        assertCatalogAdmin(operator);
        GovCatalogResource r = require(id);
        String comment = str(body == null ? null : body.get("comment"), "管理员即时删除");
        // 待审核也可删除：先撤销待审单，再删资源
        if ("PENDING".equalsIgnoreCase(r.getApprovalStatus())) {
            List<GovCatalogApproval> pending = approvalMapper.selectList(new LambdaQueryWrapper<GovCatalogApproval>()
                    .eq(GovCatalogApproval::getResourceId, r.getId())
                    .eq(GovCatalogApproval::getStatus, "PENDING"));
            for (GovCatalogApproval a : pending) {
                a.setStatus("WITHDRAWN");
                a.setReviewedAt(LocalDateTime.now());
                if (operator != null) {
                    a.setReviewedBy(operator.getUsername());
                }
                a.setReviewComment(comment);
                approvalMapper.updateById(a);
            }
        }
        if ("PUBLISHED".equalsIgnoreCase(r.getPublishStatus())) {
            offlinePortal(r);
        }
        insertApprovedAudit(operator, r, "DELETE", comment);
        resourceMapper.deleteById(r.getId());
    }

    private void assertCatalogAdmin(UserPrincipal operator) {
        if (operator == null || !(operator.isSystemAdmin() || operator.isPlatformAdmin())) {
            throw new BusinessException(403, "仅平台管理员可执行此操作");
        }
    }

    /** 审批须填写审批人、联系方式；驳回时意见另校验。 */
    private void applyReviewerFields(GovCatalogApproval a, Map<String, Object> body) {
        String reviewerName = str(body.get("reviewerName"), null);
        if (reviewerName == null || reviewerName.isBlank()) {
            reviewerName = str(body.get("reviewedBy"), null);
        }
        if (reviewerName == null || reviewerName.isBlank()) {
            throw new BusinessException(400, "请填写审批人");
        }
        String contact = str(body.get("reviewerContact"), null);
        if (contact == null || contact.isBlank()) {
            contact = str(body.get("contact"), null);
        }
        if (contact == null || contact.isBlank()) {
            throw new BusinessException(400, "请填写联系方式");
        }
        a.setReviewedBy(reviewerName.trim());
        a.setReviewerContact(contact.trim());
    }

    private void insertApprovedAudit(UserPrincipal operator, GovCatalogResource r, String actionType, String comment) {
        GovCatalogApproval a = insertApproval(operator, r.getId(), r.getCategoryId(), r.getCatalogOrigin(),
                actionType, comment, null);
        a.setStatus("APPROVED");
        a.setReviewComment(comment);
        if (operator != null) {
            a.setReviewedBy(operator.getUsername());
        }
        a.setReviewedAt(LocalDateTime.now());
        approvalMapper.updateById(a);
    }

    @Transactional
    public Map<String, Object> batchApprove(UserPrincipal operator, List<Long> approvalIds, Map<String, Object> body) {
        if (approvalIds == null || approvalIds.isEmpty()) {
            throw new BusinessException(400, "ids 不能为空");
        }
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (Long aid : approvalIds) {
            try {
                approve(operator, aid, body == null ? Map.of() : body);
                ok++;
            } catch (Exception e) {
                errors.add(aid + ": " + e.getMessage());
            }
        }
        return Map.of("approved", ok, "errors", errors);
    }

    @Transactional
    public Map<String, Object> batchReject(UserPrincipal operator, List<Long> approvalIds, Map<String, Object> body) {
        if (approvalIds == null || approvalIds.isEmpty()) {
            throw new BusinessException(400, "ids 不能为空");
        }
        if (body == null || str(body.get("comment"), null) == null) {
            throw new BusinessException(400, "批量驳回须填写审批意见");
        }
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (Long aid : approvalIds) {
            try {
                reject(operator, aid, body);
                ok++;
            } catch (Exception e) {
                errors.add(aid + ": " + e.getMessage());
            }
        }
        return Map.of("rejected", ok, "errors", errors);
    }

    @Transactional
    public Map<String, Object> bindCategory(UserPrincipal operator, Long categoryId, List<Long> resourceIds) {
        if (categoryId == null) {
            throw new BusinessException(400, "categoryId 不能为空");
        }
        GovCatalogCategory cat = categoryMapper.selectById(categoryId);
        if (cat == null) {
            throw new BusinessException(400, "分类不存在");
        }
        if (resourceIds == null || resourceIds.isEmpty()) {
            throw new BusinessException(400, "resourceIds 不能为空");
        }
        int n = 0;
        List<String> errors = new ArrayList<>();
        for (Long rid : resourceIds) {
            if (rid == null) {
                continue;
            }
            try {
                GovCatalogResource r = require(rid);
                if ("PUBLISHED".equalsIgnoreCase(r.getPublishStatus())) {
                    errors.add("「" + r.getResourceName() + "」已发布，不可改挂分类，请先下线");
                    continue;
                }
                if (hasBlockingPendingApproval(rid, r)) {
                    errors.add("「" + r.getResourceName() + "」已有发布/下线等待审批，不可改挂分类");
                    continue;
                }
                if (r.getCategoryId() != null && r.getCategoryId() > 0) {
                    errors.add("「" + r.getResourceName() + "」已关联分类，请先解除");
                    continue;
                }
                // 关联分类立即生效，不走审批；仅「发布」才进入目录审批
                withdrawPendingBindUnbind(operator, rid);
                applyBindImmediate(operator, r, categoryId, cat.getCategoryPath());
                if ("PENDING".equalsIgnoreCase(r.getApprovalStatus())) {
                    r.setApprovalStatus("DRAFT");
                }
                touch(r, operator);
                resourceMapper.updateById(r);
                n++;
            } catch (BusinessException ex) {
                errors.add(ex.getMessage());
            }
        }
        if (n == 0 && !errors.isEmpty()) {
            throw new BusinessException(400, String.join("；", errors));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submitted", n);
        result.put("bound", n);
        result.put("categoryId", categoryId);
        result.put("errors", errors);
        return result;
    }

    @Transactional
    public Map<String, Object> unbindCategory(UserPrincipal operator, List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            throw new BusinessException(400, "resourceIds 不能为空");
        }
        int n = 0;
        List<String> errors = new ArrayList<>();
        for (Long rid : resourceIds) {
            if (rid == null) {
                continue;
            }
            try {
                GovCatalogResource r = require(rid);
                if ("PUBLISHED".equalsIgnoreCase(r.getPublishStatus())) {
                    errors.add("「" + r.getResourceName() + "」已发布，不可解绑，请先下线");
                    continue;
                }
                if (hasBlockingPendingApproval(rid, r)) {
                    errors.add("「" + r.getResourceName() + "」已有发布/下线等待审批，不可解绑");
                    continue;
                }
                if (r.getCategoryId() == null || r.getCategoryId() <= 0) {
                    errors.add("「" + r.getResourceName() + "」未关联分类");
                    continue;
                }
                // 解除关联立即生效，不走审批
                withdrawPendingBindUnbind(operator, rid);
                applyUnbindImmediate(operator, r);
                if ("PENDING".equalsIgnoreCase(r.getApprovalStatus())) {
                    r.setApprovalStatus("DRAFT");
                }
                touch(r, operator);
                resourceMapper.updateById(r);
                n++;
            } catch (BusinessException ex) {
                errors.add(ex.getMessage());
            }
        }
        if (n == 0 && !errors.isEmpty()) {
            throw new BusinessException(400, String.join("；", errors));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submitted", n);
        result.put("unbound", n);
        result.put("errors", errors);
        return result;
    }

    /** 是否存在会阻塞改挂/解绑的待审（发布/下线/删除/变更等，不含 BIND/UNBIND） */
    private boolean hasBlockingPendingApproval(Long resourceId, GovCatalogResource r) {
        if (!"PENDING".equalsIgnoreCase(r.getApprovalStatus())) {
            return false;
        }
        Long cnt = approvalMapper.selectCount(new LambdaQueryWrapper<GovCatalogApproval>()
                .eq(GovCatalogApproval::getResourceId, resourceId)
                .eq(GovCatalogApproval::getStatus, "PENDING")
                .notIn(GovCatalogApproval::getActionType, "BIND", "UNBIND"));
        return cnt != null && cnt > 0;
    }

    /** 撤回历史遗留的关联/解绑待审单（流程已改为即时生效） */
    private void withdrawPendingBindUnbind(UserPrincipal operator, Long resourceId) {
        List<GovCatalogApproval> pending = approvalMapper.selectList(new LambdaQueryWrapper<GovCatalogApproval>()
                .eq(GovCatalogApproval::getResourceId, resourceId)
                .eq(GovCatalogApproval::getStatus, "PENDING")
                .in(GovCatalogApproval::getActionType, "BIND", "UNBIND"));
        for (GovCatalogApproval a : pending) {
            a.setStatus("WITHDRAWN");
            a.setReviewComment("关联/解绑已改为即时生效，自动撤回历史待审");
            a.setReviewedAt(LocalDateTime.now());
            if (operator != null) {
                a.setReviewedBy(operator.getUsername());
            }
            approvalMapper.updateById(a);
        }
    }

    public List<Map<String, Object>> listApprovals(Long resourceId, String status) {
        return listApprovals(resourceId, status, null, null);
    }

    public List<Map<String, Object>> listApprovals(Long resourceId, String status, String catalogOrigin) {
        return listApprovals(resourceId, status, catalogOrigin, null);
    }

    /**
     * @param scope RESOURCE=资源目录审批；CATEGORY=资源分类审批；空=全部（仍排除 BIND/UNBIND）
     */
    public List<Map<String, Object>> listApprovals(Long resourceId, String status, String catalogOrigin, String scope) {
        return listApprovals(resourceId, status, catalogOrigin, scope, null);
    }

    public List<Map<String, Object>> listApprovals(Long resourceId, String status, String catalogOrigin, String scope,
                                                   UserPrincipal operator) {
        LambdaQueryWrapper<GovCatalogApproval> q = new LambdaQueryWrapper<GovCatalogApproval>()
                .orderByDesc(GovCatalogApproval::getId);
        if (resourceId != null) {
            q.eq(GovCatalogApproval::getResourceId, resourceId);
        }
        if (status != null && !status.isBlank()) {
            q.eq(GovCatalogApproval::getStatus, status);
        }
        if (catalogOrigin != null && !catalogOrigin.isBlank()) {
            q.eq(GovCatalogApproval::getCatalogOrigin, catalogOrigin.trim().toUpperCase(Locale.ROOT));
        }
        // 关联/解绑已改为即时生效，不再展示历史 BIND/UNBIND 审批
        q.notIn(GovCatalogApproval::getActionType, "BIND", "UNBIND");
        String scopeKey = scope == null ? "" : scope.trim().toUpperCase(Locale.ROOT);
        if ("CATEGORY".equals(scopeKey)) {
            q.in(GovCatalogApproval::getActionType, "CAT_CREATE", "CAT_UPDATE", "CAT_DELETE");
        } else if ("RESOURCE".equals(scopeKey)) {
            q.notIn(GovCatalogApproval::getActionType, "CAT_CREATE", "CAT_UPDATE", "CAT_DELETE");
        }
        List<GovCatalogApproval> rows = approvalMapper.selectList(q);
        // 组织隔离：非管理员仅看本部门资源相关审批
        if (operator != null && !operator.isSystemAdmin() && !operator.isPlatformAdmin()) {
            String orgName = resolveOrgName(operator.getOrgId());
            if (orgName == null || orgName.isBlank()) {
                rows = List.of();
            } else {
                final String scopeOrg = orgName;
                rows = rows.stream().filter(a -> {
                    if (a.getResourceId() == null) return false;
                    GovCatalogResource r = resourceMapper.selectById(a.getResourceId());
                    return r != null && scopeOrg.equals(r.getProviderOrg());
                }).collect(Collectors.toList());
            }
        }
        Set<String> usernames = new HashSet<>();
        for (GovCatalogApproval a : rows) {
            if (a.getSubmittedBy() != null && !a.getSubmittedBy().isBlank()) {
                usernames.add(a.getSubmittedBy());
            }
            if (a.getReviewedBy() != null && !a.getReviewedBy().isBlank()) {
                usernames.add(a.getReviewedBy());
            }
        }
        Map<String, String> userDisplayNames = resolveUserDisplayNames(usernames);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovCatalogApproval a : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("resourceId", a.getResourceId());
            m.put("categoryId", a.getCategoryId());
            m.put("catalogOrigin", a.getCatalogOrigin());
            m.put("actionType", a.getActionType());
            m.put("status", a.getStatus());
            m.put("submitComment", a.getSubmitComment());
            m.put("reviewComment", a.getReviewComment());
            m.put("submittedBy", userDisplayNames.getOrDefault(a.getSubmittedBy(), a.getSubmittedBy()));
            m.put("submittedAt", a.getSubmittedAt());
            m.put("reviewedBy", a.getReviewedBy());
            m.put("reviewerContact", a.getReviewerContact());
            m.put("reviewedAt", a.getReviewedAt());
            m.put("payloadJson", a.getPayloadJson());
            if (a.getResourceId() != null) {
                GovCatalogResource r = resourceMapper.selectById(a.getResourceId());
                if (r != null) {
                    m.put("resourceAlive", true);
                    m.put("resourceCode", r.getResourceCode());
                    m.put("resourceName", r.getResourceName());
                    m.put("resourceType", r.getResourceType());
                    m.put("publishStatus", r.getPublishStatus());
                    m.put("approvalStatus", r.getApprovalStatus());
                } else {
                    m.put("resourceAlive", false);
                }
            }
            if (a.getCategoryId() != null) {
                GovCatalogCategory cat = categoryMapper.selectById(a.getCategoryId());
                if (cat != null) {
                    m.put("categoryName", cat.getCategoryName());
                    m.put("categoryCode", cat.getCategoryCode());
                }
            }
            Map<String, Object> payload = parsePayload(a.getPayloadJson());
            if (m.get("categoryName") == null && payload.get("categoryName") != null) {
                m.put("categoryName", payload.get("categoryName"));
            }
            if (m.get("categoryCode") == null && payload.get("categoryCode") != null) {
                m.put("categoryCode", payload.get("categoryCode"));
            }
            if (m.get("resourceName") == null && payload.get("categoryName") != null) {
                m.put("resourceName", String.valueOf(payload.get("categoryName")));
            }
            if (m.get("resourceCode") == null && payload.get("categoryCode") != null) {
                m.put("resourceCode", String.valueOf(payload.get("categoryCode")));
            }
            out.add(m);
        }
        return out;
    }

    public List<GovCatalogResourceVersion> listVersions(Long resourceId) {
        require(resourceId);
        return versionMapper.selectList(new LambdaQueryWrapper<GovCatalogResourceVersion>()
                .eq(GovCatalogResourceVersion::getResourceId, resourceId)
                .orderByDesc(GovCatalogResourceVersion::getVersionNo));
    }

    public Map<String, Object> diffVersions(Long resourceId, Integer leftNo, Integer rightNo) {
        require(resourceId);
        if (leftNo == null || rightNo == null) {
            throw new BusinessException(400, "leftNo/rightNo 不能为空");
        }
        GovCatalogResourceVersion left = versionMapper.selectOne(new LambdaQueryWrapper<GovCatalogResourceVersion>()
                .eq(GovCatalogResourceVersion::getResourceId, resourceId)
                .eq(GovCatalogResourceVersion::getVersionNo, leftNo));
        GovCatalogResourceVersion right = versionMapper.selectOne(new LambdaQueryWrapper<GovCatalogResourceVersion>()
                .eq(GovCatalogResourceVersion::getResourceId, resourceId)
                .eq(GovCatalogResourceVersion::getVersionNo, rightNo));
        if (left == null || right == null) {
            throw new BusinessException(404, "版本不存在");
        }
        Map<String, Object> leftSnap = parseSnapshot(left.getSnapshotJson());
        Map<String, Object> rightSnap = parseSnapshot(right.getSnapshotJson());
        List<Map<String, Object>> basicDiff = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        keys.addAll(leftSnap.keySet());
        keys.addAll(rightSnap.keySet());
        for (String key : keys) {
            String lv = nvl(String.valueOf(leftSnap.getOrDefault(key, "")));
            String rv = nvl(String.valueOf(rightSnap.getOrDefault(key, "")));
            if (!lv.equals(rv)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("field", key);
                row.put("left", lv);
                row.put("right", rv);
                basicDiff.add(row);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("leftNo", leftNo);
        out.put("rightNo", rightNo);
        out.put("sameSnapshot", String.valueOf(left.getSnapshotJson()).equals(String.valueOf(right.getSnapshotJson())));
        out.put("basicDiff", basicDiff);
        return out;
    }

    @Transactional
    public Map<String, Object> importResources(UserPrincipal operator, Map<String, Object> body) {
        List<Map<String, Object>> items = parseImportItems(body);
        if (items.isEmpty()) {
            throw new BusinessException(400, "导入数据为空");
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> row = items.get(i);
            try {
                String code = str(row.get("resourceCode"), null);
                GovCatalogResource existing = null;
                if (code != null && !code.isBlank()) {
                    existing = resourceMapper.selectOne(new LambdaQueryWrapper<GovCatalogResource>()
                            .eq(GovCatalogResource::getResourceCode, code));
                }
                if (existing != null) {
                    if ("PUBLISHED".equalsIgnoreCase(existing.getPublishStatus())) {
                        skipped++;
                        continue;
                    }
                    applyBody(existing, row, false);
                    touch(existing, operator);
                    resourceMapper.updateById(existing);
                    updated++;
                } else {
                    create(operator, row);
                    created++;
                }
            } catch (Exception e) {
                errors.add("行" + (i + 1) + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

    public Object exportResources(Long categoryId, String format) {
        LambdaQueryWrapper<GovCatalogResource> q = new LambdaQueryWrapper<GovCatalogResource>()
                .orderByAsc(GovCatalogResource::getId);
        if (categoryId != null) {
            q.eq(GovCatalogResource::getCategoryId, categoryId);
        }
        List<GovCatalogResource> rows = resourceMapper.selectList(q);
        String fmt = format == null ? "json" : format.trim().toLowerCase();
        if ("csv".equals(fmt)) {
            return toCsv(rows);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovCatalogResource r : rows) {
            out.add(exportRow(r));
        }
        return out;
    }

    private void snapshotOnPublish(GovCatalogResource r, UserPrincipal operator, String summaryPrefix) {
        long versionCount = versionMapper.selectCount(new LambdaQueryWrapper<GovCatalogResourceVersion>()
                .eq(GovCatalogResourceVersion::getResourceId, r.getId()));
        int nextVersion;
        if (versionCount == 0) {
            nextVersion = r.getVersionNo() == null || r.getVersionNo() < 1 ? 1 : r.getVersionNo();
        } else {
            nextVersion = (r.getVersionNo() == null ? 1 : r.getVersionNo()) + 1;
        }
        r.setVersionNo(nextVersion);
        String publisher = operator != null ? operator.getUsername() : r.getUpdatedBy();
        LocalDateTime now = LocalDateTime.now();

        GovCatalogResourceVersion ver = new GovCatalogResourceVersion();
        ver.setResourceId(r.getId());
        ver.setVersionNo(nextVersion);
        ver.setSnapshotJson(toSnapshotJson(r));
        ver.setChangeSummary(summaryPrefix + nextVersion);
        ver.setPublishedBy(publisher);
        ver.setPublishedAt(now);
        versionMapper.insert(ver);
        log.info("catalog resource {} published as v{}", r.getId(), nextVersion);
    }

    private String toSnapshotJson(GovCatalogResource r) {
        Map<String, Object> snap = exportRow(r);
        snap.put("publishStatus", r.getPublishStatus());
        snap.put("approvalStatus", r.getApprovalStatus());
        snap.put("versionNo", r.getVersionNo());
        try {
            return OM.writeValueAsString(snap);
        } catch (Exception e) {
            throw new BusinessException(500, "版本快照序列化失败");
        }
    }

    private Map<String, Object> exportRow(GovCatalogResource r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resourceCode", r.getResourceCode());
        m.put("resourceName", r.getResourceName());
        m.put("resourceType", r.getResourceType());
        m.put("metadataEntryCode", r.getMetadataEntryCode());
        m.put("dataSourceId", r.getDataSourceId());
        m.put("physicalTableName", r.getPhysicalTableName());
        m.put("sourcePathType", r.getSourcePathType());
        m.put("qualityScore", r.getQualityScore());
        m.put("categoryId", r.getCategoryId());
        m.put("categoryPath", r.getCategoryPath());
        m.put("providerOrg", r.getProviderOrg());
        m.put("resourceFormat", r.getResourceFormat());
        m.put("shareType", r.getShareType());
        m.put("updateCycle", r.getUpdateCycle());
        m.put("description", r.getDescription());
        m.put("secretFlag", r.getSecretFlag());
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseImportItems(Map<String, Object> body) {
        Object itemsObj = body.get("items");
        if (itemsObj instanceof List<?> list) {
            return castItemList(list);
        }
        String format = str(body.get("format"), "json").toLowerCase();
        String content = str(body.get("content"), null);
        if (content != null && !content.isBlank()) {
            if ("csv".equals(format)) {
                return parseCsv(content);
            }
            try {
                Object parsed = OM.readValue(content, Object.class);
                if (parsed instanceof List<?> list) {
                    return castItemList(list);
                }
                throw new BusinessException(400, "JSON 内容须为数组");
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(400, "JSON 解析失败: " + e.getMessage());
            }
        }
        throw new BusinessException(400, "请提供 items 数组或 content 文本");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castItemList(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            } else {
                throw new BusinessException(400, "导入项须为对象");
            }
        }
        return out;
    }

    private List<Map<String, Object>> parseCsv(String content) {
        String[] lines = content.split("\\r?\\n");
        if (lines.length < 2) {
            throw new BusinessException(400, "CSV 至少包含表头与一行数据");
        }
        String[] headers = splitCsvLine(lines[0]);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            String[] cols = splitCsvLine(lines[i]);
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.length && c < cols.length; c++) {
                row.put(headers[c].trim(), cols[c].trim());
            }
            out.add(row);
        }
        return out;
    }

    private String[] splitCsvLine(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuote = !inQuote;
            } else if (ch == ',' && !inQuote) {
                cols.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        cols.add(cur.toString());
        return cols.toArray(new String[0]);
    }

    private String toCsv(List<GovCatalogResource> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", EXPORT_HEADERS)).append('\n');
        for (GovCatalogResource r : rows) {
            Map<String, Object> m = exportRow(r);
            List<String> cols = new ArrayList<>();
            for (String h : EXPORT_HEADERS) {
                cols.add(escapeCsv(String.valueOf(m.getOrDefault(h, ""))));
            }
            sb.append(String.join(",", cols)).append('\n');
        }
        return sb.toString();
    }

    private static String escapeCsv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private Map<String, Object> parseSnapshot(String json) {
        try {
            return OM.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String nvl(String s) {
        return "null".equalsIgnoreCase(s) ? "" : s;
    }

    private void applyBody(GovCatalogResource r, Map<String, Object> body, boolean creating) {
        if (creating || body.containsKey("resourceCode")) {
            r.setResourceCode(str(body.get("resourceCode"), creating ? "RES_" + System.currentTimeMillis() : r.getResourceCode()));
        }
        if (creating || body.containsKey("resourceName")) {
            r.setResourceName(required(body.get("resourceName"), "resourceName").toString());
        }
        if (creating || body.containsKey("resourceType")) {
            r.setResourceType(str(body.get("resourceType"), creating ? "DATA" : r.getResourceType()));
        }
        if (creating || body.containsKey("catalogOrigin")) {
            String origin = str(body.get("catalogOrigin"), creating ? "GOVERNANCE" : r.getCatalogOrigin());
            if (origin == null || origin.isBlank()) {
                origin = "GOVERNANCE";
            }
            origin = origin.trim().toUpperCase(Locale.ROOT);
            if (!"INGEST".equals(origin) && !"GOVERNANCE".equals(origin)) {
                throw new BusinessException(400, "catalogOrigin 仅支持 INGEST / GOVERNANCE");
            }
            r.setCatalogOrigin(origin);
        }
        if (body.containsKey("metadataEntryCode") || creating) {
            r.setMetadataEntryCode(str(body.get("metadataEntryCode"), creating ? null : r.getMetadataEntryCode()));
        }
        if (body.containsKey("dataSourceId")) {
            r.setDataSourceId(longVal(body.get("dataSourceId"), r.getDataSourceId()));
        }
        if (body.containsKey("physicalTableName") || creating) {
            r.setPhysicalTableName(str(body.get("physicalTableName"), creating ? null : r.getPhysicalTableName()));
        }
        if (body.containsKey("sourcePathType") || creating) {
            r.setSourcePathType(str(body.get("sourcePathType"), creating ? "DIRECT" : r.getSourcePathType()));
        }
        if (body.containsKey("qualityScore")) {
            r.setQualityScore(decimalVal(body.get("qualityScore"), r.getQualityScore()));
        }
        if (body.containsKey("categoryId") || creating) {
            Long categoryId = longVal(body.get("categoryId"), null);
            r.setCategoryId(categoryId);
            if (categoryId != null) {
                GovCatalogCategory cat = categoryMapper.selectById(categoryId);
                if (cat == null) {
                    throw new BusinessException(400, "分类不存在");
                }
                r.setCategoryPath(cat.getCategoryPath());
                // 信息资源分类即基础资源目录下一级 → 同步基础库名称
                String bodyBase = body.containsKey("baseCatalogName")
                        ? str(body.get("baseCatalogName"), null) : null;
                if (bodyBase == null || bodyBase.isBlank()) {
                    r.setBaseCatalogName(cat.getCategoryName());
                }
            } else if (body.containsKey("categoryPath")) {
                r.setCategoryPath(str(body.get("categoryPath"), null));
            }
        } else if (body.containsKey("categoryPath")) {
            r.setCategoryPath(str(body.get("categoryPath"), r.getCategoryPath()));
        }
        if (body.containsKey("providerOrg") || creating) {
            r.setProviderOrg(str(body.get("providerOrg"), creating ? null : r.getProviderOrg()));
        }
        if (body.containsKey("resourceFormat") || creating) {
            r.setResourceFormat(str(body.get("resourceFormat"), creating ? "DATABASE" : r.getResourceFormat()));
        }
        if (body.containsKey("shareType") || creating) {
            r.setShareType(str(body.get("shareType"), creating ? "OPEN" : r.getShareType()));
        }
        if (body.containsKey("shareCondition") || creating) {
            r.setShareCondition(str(body.get("shareCondition"), creating ? null : r.getShareCondition()));
        }
        if (body.containsKey("notShareReason") || creating) {
            r.setNotShareReason(str(body.get("notShareReason"), creating ? null : r.getNotShareReason()));
        }
        if (body.containsKey("openType") || creating) {
            r.setOpenType(str(body.get("openType"), creating ? "SOCIAL_OPEN" : r.getOpenType()));
        }
        if (body.containsKey("openCondition") || creating) {
            r.setOpenCondition(str(body.get("openCondition"), creating ? null : r.getOpenCondition()));
        }
        if (body.containsKey("notOpenReason") || creating) {
            r.setNotOpenReason(str(body.get("notOpenReason"), creating ? null : r.getNotOpenReason()));
        }
        if (body.containsKey("contactName") || creating) {
            r.setContactName(str(body.get("contactName"), creating ? null : r.getContactName()));
        }
        if (body.containsKey("contactPhone") || creating) {
            r.setContactPhone(str(body.get("contactPhone"), creating ? null : r.getContactPhone()));
        }
        if (body.containsKey("contactEmail") || creating) {
            r.setContactEmail(str(body.get("contactEmail"), creating ? null : r.getContactEmail()));
        }
        if (body.containsKey("themeName") || creating) {
            r.setThemeName(str(body.get("themeName"), creating ? null : r.getThemeName()));
        }
        if (body.containsKey("baseCatalogName") || creating) {
            r.setBaseCatalogName(str(body.get("baseCatalogName"), creating ? null : r.getBaseCatalogName()));
        }
        if (body.containsKey("tags") || creating) {
            Object tagsRaw = body.get("tags");
            if (tagsRaw instanceof List<?> list) {
                String joined = list.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining(","));
                r.setTags(joined.isEmpty() ? null : joined);
            } else {
                r.setTags(str(tagsRaw, creating ? null : r.getTags()));
            }
        }
        if (body.containsKey("extJson") || creating) {
            Object ext = body.get("extJson");
            if (ext == null) {
                r.setExtJson(creating ? null : r.getExtJson());
            } else if (ext instanceof String s) {
                r.setExtJson(s.isBlank() ? null : s);
            } else {
                try {
                    r.setExtJson(OM.writeValueAsString(ext));
                } catch (Exception e) {
                    throw new BusinessException(400, "extJson 无法序列化");
                }
            }
        }
        if (body.containsKey("updateCycle") || creating) {
            r.setUpdateCycle(str(body.get("updateCycle"), creating ? "DAILY" : r.getUpdateCycle()));
        }
        if (body.containsKey("description") || creating) {
            r.setDescription(str(body.get("description"), creating ? null : r.getDescription()));
        }
        if (body.containsKey("secretFlag")) {
            r.setSecretFlag(intVal(body.get("secretFlag"), 0));
        }
    }

    private void validateShareOpen(GovCatalogResource r) {
        String share = r.getShareType() == null ? "" : r.getShareType().trim().toUpperCase(Locale.ROOT);
        if ("CONDITIONAL".equals(share)) {
            if (r.getShareCondition() == null || r.getShareCondition().isBlank()) {
                throw new BusinessException(400, "有条件共享须填写共享条件");
            }
        } else if ("NOT_SHARE".equals(share)) {
            if (r.getNotShareReason() == null || r.getNotShareReason().isBlank()) {
                throw new BusinessException(400, "不予共享须填写不共享理由");
            }
        }
        String open = r.getOpenType() == null ? "" : r.getOpenType().trim().toUpperCase(Locale.ROOT);
        if ("NOT_OPEN".equals(open)) {
            if (r.getNotOpenReason() == null || r.getNotOpenReason().isBlank()) {
                throw new BusinessException(400, "不对社会开放须填写不开放理由");
            }
        } else if ("SOCIAL_OPEN".equals(open) || "OPEN".equals(open)) {
            if (r.getOpenCondition() == null || r.getOpenCondition().isBlank()) {
                r.setOpenCondition("无条件开放");
            }
        }
        String format = r.getResourceFormat() == null ? "" : r.getResourceFormat().trim().toUpperCase(Locale.ROOT);
        if ("API".equals(format)) {
            String ext = r.getExtJson();
            if (ext == null || ext.isBlank()) {
                throw new BusinessException(400, "接口资源须填写接口关联信息");
            }
        }
        if ("FILE".equals(format)) {
            String ext = r.getExtJson();
            if (ext == null || ext.isBlank()) {
                throw new BusinessException(400, "文件资源须填写文件关联信息");
            }
        }
    }

    /** 按元数据条目回填物理表、来源路径、提供方等 */
    private void enrichFromMetadata(GovCatalogResource r, boolean creating) {
        String code = r.getMetadataEntryCode();
        if (code == null || code.isBlank()) {
            return;
        }
        GovMetadataRegistry entry = findMetadata(code);
        if (entry == null) {
            throw new BusinessException(400, "元数据条目不存在或已下线：" + code);
        }
        String layer = resolveLayer(entry);
        if (r.getPhysicalTableName() == null || r.getPhysicalTableName().isBlank()) {
            r.setPhysicalTableName(entry.getPhysicalTableName());
        }
        if (r.getDataSourceId() == null) {
            r.setDataSourceId(entry.getDataSourceId());
        }
        if (creating || r.getSourcePathType() == null || r.getSourcePathType().isBlank()) {
            r.setSourcePathType(DataLayerSupport.sourcePathTypeForLayer(layer));
        }
        if ((r.getProviderOrg() == null || r.getProviderOrg().isBlank()) && entry.getOwnerName() != null) {
            r.setProviderOrg(entry.getOwnerName());
        }
        if ((r.getResourceName() == null || r.getResourceName().isBlank()) && entry.getEntryName() != null) {
            r.setResourceName(entry.getEntryName());
        }
    }

    private void assertCatalogable(GovCatalogResource r, boolean requireEntry) {
        String code = r.getMetadataEntryCode();
        if (requireEntry && (code == null || code.isBlank())) {
            throw new BusinessException(400, "须选择已登记的元数据条目（entry_code），禁止手填空挂载");
        }
        if (code == null || code.isBlank()) {
            return;
        }
        GovMetadataRegistry entry = findMetadata(code);
        if (entry == null) {
            throw new BusinessException(400, "元数据条目不存在或已下线：" + code);
        }
        String layer = resolveLayer(entry);
        if (DataLayerSupport.isProcessLayer(layer)) {
            throw new BusinessException(400, "过程层（DWD）不可编目进资源目录，仅源层或主题/专题资源可挂载");
        }
        if (!DataLayerSupport.isCatalogableLayer(layer) && layer != null && !layer.isBlank()
                && !"CONTROL".equalsIgnoreCase(layer)) {
            throw new BusinessException(400, "当前分层「" + layer + "」不可进资源目录");
        }
        String path = r.getSourcePathType();
        if (path != null && !path.isBlank()
                && !"DIRECT".equalsIgnoreCase(path) && !"PROCESSED".equalsIgnoreCase(path)) {
            throw new BusinessException(400, "sourcePathType 须为 DIRECT 或 PROCESSED");
        }
        if ("DWS".equalsIgnoreCase(layer) || "ADS".equalsIgnoreCase(layer)) {
            r.setSourcePathType("PROCESSED");
        } else if ("ODS".equalsIgnoreCase(layer) || "SOURCE".equalsIgnoreCase(layer)) {
            r.setSourcePathType("DIRECT");
        }
    }

    private void assertQualityGate(GovCatalogResource r) {
        if (r.getQualityScore() != null && r.getQualityScore().compareTo(MIN_QUALITY_SCORE) < 0) {
            throw new BusinessException(400, "质量分低于 " + MIN_QUALITY_SCORE + "，不可提交发布审批");
        }
    }

    private GovMetadataRegistry findMetadata(String entryCode) {
        return metadataRegistryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryCode, entryCode.trim())
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .last("limit 1"));
    }

    private static String resolveLayer(GovMetadataRegistry e) {
        if (e.getDataLayer() != null && !e.getDataLayer().isBlank()) {
            return e.getDataLayer().trim().toUpperCase(Locale.ROOT);
        }
        if (e.getDatabaseName() != null && !e.getDatabaseName().isBlank()) {
            return DataLayerSupport.layerForDatabase(e.getDatabaseName());
        }
        return DataLayerSupport.layerForTableName(e.getPhysicalTableName());
    }

    private void touch(GovCatalogResource r, UserPrincipal operator) {
        if (operator != null) {
            r.setUpdatedBy(operator.getUsername());
        }
        r.setUpdatedAt(LocalDateTime.now());
    }

    private void syncPortal(GovCatalogResource r) {
        if (r.getSecretFlag() != null && r.getSecretFlag() == 1) {
            return;
        }
        BizCatalogItem item = null;
        if (r.getPortalCatalogId() != null) {
            item = portalCatalogMapper.selectById(r.getPortalCatalogId());
        }
        if (item == null && r.getId() != null) {
            item = portalCatalogMapper.selectOne(new LambdaQueryWrapper<BizCatalogItem>()
                    .eq(BizCatalogItem::getGovResourceId, r.getId()).last("LIMIT 1"));
        }
        if (item == null) {
            String code = "GOV_" + (r.getResourceCode() != null ? r.getResourceCode() : r.getId());
            item = portalCatalogMapper.selectOne(new LambdaQueryWrapper<BizCatalogItem>()
                    .eq(BizCatalogItem::getCatalogCode, code).last("LIMIT 1"));
            if (item == null) {
                item = new BizCatalogItem();
                item.setCatalogCode(code);
                item.setCreatedBy(r.getCreatedBy());
                item.setCreatedAt(LocalDateTime.now());
            }
        }
        item.setTitle(r.getResourceName());
        item.setDescription(r.getDescription());
        item.setCatalogKind("SERVICE".equalsIgnoreCase(r.getResourceType()) ? "SERVICE" : "DATA");
        item.setCatalogOrigin(r.getCatalogOrigin() == null ? "GOVERNANCE" : r.getCatalogOrigin());
        item.setGovResourceId(r.getId());
        // 主题：主题资源目录下一级；库：信息资源分类（基础资源目录下一级）；部门：提供方组织机构
        applyPortalTheme(item, r);
        applyPortalBase(item, r);
        item.setProviderOrg(r.getProviderOrg());
        item.setShareModes(mapShareModes(r.getResourceFormat(), r.getShareType()));
        item.setResourceCount(1);
        item.setHotScore(item.getHotScore() == null ? 1 : item.getHotScore());
        item.setPublishStatus("PUBLISHED");
        item.setPublishedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        if (item.getId() == null) {
            portalCatalogMapper.insert(item);
        } else {
            portalCatalogMapper.updateById(item);
        }
        r.setPortalCatalogId(item.getId());
    }

    /**
     * 门户主题字段：来自资源上的 themeName（主题资源目录下一级），
     * 尽量回填为分类表中的 category_code，便于门户按主题聚合。
     */
    private void applyPortalTheme(BizCatalogItem item, GovCatalogResource r) {
        String themeName = r.getThemeName() == null ? null : r.getThemeName().trim();
        if (themeName == null || themeName.isEmpty()) {
            item.setThemeCode(null);
            item.setThemeName(null);
            return;
        }
        GovCatalogCategory matched = findThemeCategoryByName(themeName, r.getCatalogOrigin());
        if (matched != null) {
            item.setThemeCode(matched.getCategoryCode());
            item.setThemeName(matched.getCategoryName());
            return;
        }
        item.setThemeCode("THEME_" + Integer.toHexString(themeName.hashCode()));
        item.setThemeName(themeName);
    }

    /**
     * 门户基础库字段：来自信息资源分类 categoryId（基础资源目录下一级）或 baseCatalogName。
     */
    private void applyPortalBase(BizCatalogItem item, GovCatalogResource r) {
        if (r.getCategoryId() != null) {
            GovCatalogCategory cat = categoryMapper.selectById(r.getCategoryId());
            if (cat != null) {
                item.setBaseCatalogCode(cat.getCategoryCode());
                item.setBaseCatalogName(cat.getCategoryName());
                return;
            }
        }
        String baseName = r.getBaseCatalogName() == null ? null : r.getBaseCatalogName().trim();
        if (baseName == null || baseName.isEmpty()) {
            item.setBaseCatalogCode(null);
            item.setBaseCatalogName(null);
            return;
        }
        GovCatalogCategory matched = findBaseCategoryByName(baseName, r.getCatalogOrigin());
        if (matched != null) {
            item.setBaseCatalogCode(matched.getCategoryCode());
            item.setBaseCatalogName(matched.getCategoryName());
            return;
        }
        item.setBaseCatalogCode("BASE_" + Integer.toHexString(baseName.hashCode()));
        item.setBaseCatalogName(baseName);
    }

    private GovCatalogCategory findBaseCategoryByName(String baseName, String catalogOrigin) {
        LambdaQueryWrapper<GovCatalogCategory> q = new LambdaQueryWrapper<GovCatalogCategory>()
                .eq(GovCatalogCategory::getCategoryName, baseName)
                .and(w -> w.isNull(GovCatalogCategory::getStatus)
                        .or().ne(GovCatalogCategory::getStatus, "OFFLINE"));
        if (catalogOrigin != null && !catalogOrigin.isBlank()) {
            q.eq(GovCatalogCategory::getCatalogOrigin, catalogOrigin.trim().toUpperCase(Locale.ROOT));
        }
        List<GovCatalogCategory> list = categoryMapper.selectList(q);
        for (GovCatalogCategory c : list) {
            if (isUnderBaseRoot(c)) {
                return c;
            }
        }
        if (catalogOrigin != null && !catalogOrigin.isBlank()) {
            return findBaseCategoryByName(baseName, null);
        }
        return list.isEmpty() ? null : list.get(0);
    }

    private boolean isUnderBaseRoot(GovCatalogCategory c) {
        if (c == null) {
            return false;
        }
        String path = c.getCategoryPath();
        if (path != null && path.contains("基础资源目录")) {
            return true;
        }
        Long parentId = c.getParentId();
        int guard = 0;
        while (parentId != null && parentId > 0 && guard++ < 8) {
            GovCatalogCategory p = categoryMapper.selectById(parentId);
            if (p == null) {
                break;
            }
            if ("基础资源目录".equals(p.getCategoryName())
                    || (p.getCategoryCode() != null && p.getCategoryCode().toUpperCase(Locale.ROOT).endsWith("_BASE"))) {
                return true;
            }
            parentId = p.getParentId();
        }
        return false;
    }

    private GovCatalogCategory findThemeCategoryByName(String themeName, String catalogOrigin) {
        LambdaQueryWrapper<GovCatalogCategory> q = new LambdaQueryWrapper<GovCatalogCategory>()
                .eq(GovCatalogCategory::getCategoryName, themeName)
                .and(w -> w.isNull(GovCatalogCategory::getStatus)
                        .or().ne(GovCatalogCategory::getStatus, "OFFLINE"));
        if (catalogOrigin != null && !catalogOrigin.isBlank()) {
            q.eq(GovCatalogCategory::getCatalogOrigin, catalogOrigin.trim().toUpperCase(Locale.ROOT));
        }
        List<GovCatalogCategory> list = categoryMapper.selectList(q);
        for (GovCatalogCategory c : list) {
            if (isUnderThemeRoot(c)) {
                return c;
            }
        }
        if (catalogOrigin != null && !catalogOrigin.isBlank() && !list.isEmpty()) {
            // 名称命中但未挂到主题根时仍可用（历史脏数据）
            return list.get(0);
        }
        if (catalogOrigin != null && !catalogOrigin.isBlank()) {
            return findThemeCategoryByName(themeName, null);
        }
        return list.isEmpty() ? null : list.get(0);
    }

    private boolean isUnderThemeRoot(GovCatalogCategory c) {
        if (c == null) {
            return false;
        }
        String path = c.getCategoryPath();
        if (path != null && path.contains("主题资源目录")) {
            return true;
        }
        Long parentId = c.getParentId();
        int guard = 0;
        while (parentId != null && parentId > 0 && guard++ < 8) {
            GovCatalogCategory p = categoryMapper.selectById(parentId);
            if (p == null) {
                break;
            }
            if ("主题资源目录".equals(p.getCategoryName())
                    || (p.getCategoryCode() != null && p.getCategoryCode().toUpperCase(Locale.ROOT).endsWith("_THEME"))) {
                return true;
            }
            parentId = p.getParentId();
        }
        return false;
    }

    private void offlinePortal(GovCatalogResource r) {
        BizCatalogItem item = null;
        if (r.getPortalCatalogId() != null) {
            item = portalCatalogMapper.selectById(r.getPortalCatalogId());
        }
        if (item == null && r.getId() != null) {
            item = portalCatalogMapper.selectOne(new LambdaQueryWrapper<BizCatalogItem>()
                    .eq(BizCatalogItem::getGovResourceId, r.getId()).last("LIMIT 1"));
        }
        if (item == null) {
            return;
        }
        item.setPublishStatus("OFFLINE");
        item.setUpdatedAt(LocalDateTime.now());
        portalCatalogMapper.updateById(item);
    }

    private static String mapShareModes(String format, String shareType) {
        if ("NOT_SHARE".equalsIgnoreCase(shareType)) {
            return "TABLE";
        }
        if ("API".equalsIgnoreCase(format)) {
            return "API";
        }
        if ("FILE".equalsIgnoreCase(format)) {
            return "FILE";
        }
        return "TABLE,FILE,API";
    }

    private GovCatalogApproval insertApproval(UserPrincipal operator, Long resourceId, Long categoryId,
                                              String catalogOrigin, String actionType, String comment,
                                              Map<String, Object> payload) {
        GovCatalogApproval a = new GovCatalogApproval();
        a.setResourceId(resourceId);
        a.setCategoryId(categoryId);
        a.setCatalogOrigin(catalogOrigin == null ? null : catalogOrigin.toUpperCase(Locale.ROOT));
        a.setActionType(actionType);
        a.setStatus("PENDING");
        a.setSubmitComment(comment);
        if (operator != null) {
            a.setSubmittedBy(operator.getUsername());
        }
        a.setSubmittedAt(LocalDateTime.now());
        if (payload != null) {
            try {
                a.setPayloadJson(OM.writeValueAsString(payload));
            } catch (Exception e) {
                throw new BusinessException(500, "审批载荷序列化失败");
            }
        }
        approvalMapper.insert(a);
        return a;
    }

    private void applyCategoryApproval(UserPrincipal operator, GovCatalogApproval a, String action) {
        Map<String, Object> payload = parsePayload(a.getPayloadJson());
        if ("CAT_CREATE".equals(action)) {
            Long newId = categoryService.applyCreate(operator, payload);
            a.setCategoryId(newId);
            approvalMapper.updateById(a);
        } else if ("CAT_UPDATE".equals(action)) {
            Long cid = a.getCategoryId() != null ? a.getCategoryId() : longVal(payload.get("categoryId"), null);
            if (cid == null) {
                throw new BusinessException(400, "分类编辑审批缺少 categoryId");
            }
            categoryService.applyUpdate(operator, cid, payload);
        } else if ("CAT_DELETE".equals(action)) {
            Long cid = a.getCategoryId() != null ? a.getCategoryId() : longVal(payload.get("categoryId"), null);
            if (cid == null) {
                throw new BusinessException(400, "分类删除审批缺少 categoryId");
            }
            categoryService.applyDelete(operator, cid);
        }
    }

    private void applyBindFromApproval(UserPrincipal operator, GovCatalogApproval a, GovCatalogResource r) {
        Map<String, Object> payload = parsePayload(a.getPayloadJson());
        Long categoryId = a.getCategoryId() != null ? a.getCategoryId() : longVal(payload.get("categoryId"), null);
        if (categoryId == null) {
            throw new BusinessException(400, "关联审批缺少 categoryId");
        }
        GovCatalogCategory cat = categoryMapper.selectById(categoryId);
        if (cat == null) {
            throw new BusinessException(400, "分类不存在");
        }
        String path = str(payload.get("categoryPath"), cat.getCategoryPath());
        applyBindImmediate(operator, r, categoryId, path);
    }

    private void applyBindImmediate(UserPrincipal operator, GovCatalogResource r, Long categoryId, String categoryPath) {
        GovCatalogCategory cat = categoryMapper.selectById(categoryId);
        String baseName = cat != null ? cat.getCategoryName() : null;
        LambdaUpdateWrapper<GovCatalogResource> uw = new LambdaUpdateWrapper<GovCatalogResource>()
                .eq(GovCatalogResource::getId, r.getId())
                .set(GovCatalogResource::getCategoryId, categoryId)
                .set(GovCatalogResource::getCategoryPath, categoryPath)
                .set(GovCatalogResource::getBaseCatalogName, baseName)
                .set(GovCatalogResource::getUpdatedAt, LocalDateTime.now());
        if (operator != null) {
            uw.set(GovCatalogResource::getUpdatedBy, operator.getUsername());
        }
        resourceMapper.update(null, uw);
        r.setCategoryId(categoryId);
        r.setCategoryPath(categoryPath);
        r.setBaseCatalogName(baseName);
    }

    private void applyUnbindImmediate(UserPrincipal operator, GovCatalogResource r) {
        LambdaUpdateWrapper<GovCatalogResource> uw = new LambdaUpdateWrapper<GovCatalogResource>()
                .eq(GovCatalogResource::getId, r.getId())
                .set(GovCatalogResource::getCategoryId, null)
                .set(GovCatalogResource::getCategoryPath, null)
                .set(GovCatalogResource::getUpdatedAt, LocalDateTime.now());
        if (operator != null) {
            uw.set(GovCatalogResource::getUpdatedBy, operator.getUsername());
        }
        resourceMapper.update(null, uw);
        r.setCategoryId(null);
        r.setCategoryPath(null);
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OM.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("parse approval payload failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private GovCatalogResource require(Long id) {
        GovCatalogResource r = resourceMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(404, "资源不存在");
        }
        return r;
    }

    private GovCatalogApproval requireApproval(Long id) {
        GovCatalogApproval a = approvalMapper.selectById(id);
        if (a == null) {
            throw new BusinessException(404, "审批单不存在");
        }
        return a;
    }

    private static Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " 不能为空");
        }
        return v;
    }

    private static Object firstNonBlank(Object a, Object b) {
        if (a != null && !String.valueOf(a).isBlank()) {
            return a;
        }
        if (b != null && !String.valueOf(b).isBlank()) {
            return b;
        }
        return "";
    }

    private static Long longOrNull(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object v, String def) {
        if (v == null || String.valueOf(v).isBlank()) {
            return def;
        }
        return String.valueOf(v).trim();
    }

    private static Long longVal(Object v, Long def) {
        if (v == null || String.valueOf(v).isBlank()) {
            return def;
        }
        return Long.valueOf(String.valueOf(v));
    }

    private static Integer intVal(Object v, Integer def) {
        if (v == null || String.valueOf(v).isBlank()) {
            return def;
        }
        return Integer.valueOf(String.valueOf(v));
    }

    private static BigDecimal decimalVal(Object v, BigDecimal def) {
        if (v == null || String.valueOf(v).isBlank()) {
            return def;
        }
        return new BigDecimal(String.valueOf(v));
    }

    /** username → displayName；无匹配时回落原值 */
    private Map<String, String> resolveUserDisplayNames(Set<String> usernames) {
        Map<String, String> out = new HashMap<>();
        if (usernames == null || usernames.isEmpty()) {
            return out;
        }
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getUsername, usernames));
        for (SysUser u : users) {
            if (u.getUsername() == null) {
                continue;
            }
            String label = u.getDisplayName();
            out.put(u.getUsername(),
                    (label != null && !label.isBlank()) ? label.trim() : u.getUsername());
        }
        return out;
    }
}
