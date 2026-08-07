package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.BizDemandSupplyTask;
import com.chengde.smartcity.exchange.entity.BizPortalSituation;
import com.chengde.smartcity.exchange.entity.BizPortalSubscription;
import com.chengde.smartcity.exchange.mapper.BizCatalogItemMapper;
import com.chengde.smartcity.exchange.mapper.BizDemandSupplyTaskMapper;
import com.chengde.smartcity.exchange.mapper.BizPortalSituationMapper;
import com.chengde.smartcity.exchange.mapper.BizPortalSubscriptionMapper;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.storage.StorageIntegrationClient;
import com.chengde.smartcity.masterdata.entity.GovCatalogCategory;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.mapper.GovCatalogCategoryMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.service.CatalogSubscriptionService;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
public class PortalService {

    private static final Logger log = LoggerFactory.getLogger(PortalService.class);
    private static final String ES_INDEX = "smartcity_catalog";
    private static final ObjectMapper OM = new ObjectMapper();
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BizCatalogItemMapper catalogMapper;
    private final BizPortalSubscriptionMapper subscriptionMapper;
    private final BizPortalSituationMapper situationMapper;
    private final BizDemandSupplyTaskMapper supplyTaskMapper;
    private final GovCatalogResourceMapper govResourceMapper;
    private final GovCatalogCategoryMapper govCategoryMapper;
    private final SysOrgMapper orgMapper;
    private final AuditService auditService;
    private final IntegrationProperties integrationProperties;
    private final StorageIntegrationClient storageClient;
    private final CatalogSubscriptionService catalogSubscriptionService;

    public PortalService(BizCatalogItemMapper catalogMapper,
                         BizPortalSubscriptionMapper subscriptionMapper,
                         BizPortalSituationMapper situationMapper,
                         BizDemandSupplyTaskMapper supplyTaskMapper,
                         GovCatalogResourceMapper govResourceMapper,
                         GovCatalogCategoryMapper govCategoryMapper,
                         SysOrgMapper orgMapper,
                         AuditService auditService,
                         IntegrationProperties integrationProperties,
                         StorageIntegrationClient storageClient,
                         CatalogSubscriptionService catalogSubscriptionService) {
        this.catalogMapper = catalogMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.situationMapper = situationMapper;
        this.supplyTaskMapper = supplyTaskMapper;
        this.govResourceMapper = govResourceMapper;
        this.govCategoryMapper = govCategoryMapper;
        this.orgMapper = orgMapper;
        this.auditService = auditService;
        this.integrationProperties = integrationProperties;
        this.storageClient = storageClient;
        this.catalogSubscriptionService = catalogSubscriptionService;
    }

    public Map<String, Object> home(String keyword) {
        List<BizCatalogItem> published = publishedCatalogs(keyword, null, null, null, null, null);
        long openResourceTotal = published.stream().filter(c -> "DATA".equalsIgnoreCase(nz(c.getCatalogKind(), "DATA"))).count();
        long apiServiceTotal = published.stream().filter(c ->
                "SERVICE".equalsIgnoreCase(nz(c.getCatalogKind(), ""))
                        || (c.getShareModes() != null && c.getShareModes().toUpperCase(Locale.ROOT).contains("API"))
        ).count();
        long shareOrgTotal = published.stream()
                .map(BizCatalogItem::getProviderOrg)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .distinct()
                .count();
        long approvedSubs = subscriptionMapper.selectCount(new LambdaQueryWrapper<BizPortalSubscription>()
                .eq(BizPortalSubscription::getStatus, "APPROVED"));
        long exchangeTaskTotal = supplyTaskMapper.selectCount(new LambdaQueryWrapper<BizDemandSupplyTask>()
                .likeRight(BizDemandSupplyTask::getRefFlowCode, "PORTAL_SUB_"));
        long exchangeVolumeTotal = Math.max(approvedSubs, exchangeTaskTotal);

        long subTotal = subscriptionMapper.selectCount(null);
        long subPending = subscriptionMapper.selectCount(new LambdaQueryWrapper<BizPortalSubscription>()
                .eq(BizPortalSubscription::getStatus, "PENDING"));

        List<Map<String, Object>> latest = published.stream()
                .sorted(Comparator
                        .comparing(BizCatalogItem::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(BizCatalogItem::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(this::catalogView)
                .collect(Collectors.toCollection(ArrayList::new));
        List<Map<String, Object>> hot = published.stream()
                .sorted(Comparator
                        .comparing((BizCatalogItem c) -> c.getHotScore() == null ? 0 : c.getHotScore()).reversed()
                        .thenComparing(BizCatalogItem::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(this::catalogView)
                .collect(Collectors.toCollection(ArrayList::new));
        enrichShareOpenStats(latest);
        enrichShareOpenStats(hot);

        List<BizPortalSubscription> recentSubs = subscriptionMapper.selectList(new LambdaQueryWrapper<BizPortalSubscription>()
                .orderByDesc(BizPortalSubscription::getId)
                .last("LIMIT 6"));
        List<Map<String, Object>> latestApplications = new ArrayList<>();
        for (BizPortalSubscription sub : recentSubs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", sub.getId());
            row.put("applicantOrg", sub.getApplicantOrg());
            row.put("status", sub.getStatus());
            row.put("createdAt", sub.getCreatedAt());
            BizCatalogItem cat = catalogMapper.selectById(sub.getCatalogId());
            row.put("catalogTitle", cat != null ? cat.getTitle() : ("目录#" + sub.getCatalogId()));
            latestApplications.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("openResourceTotal", openResourceTotal);
        out.put("apiServiceTotal", apiServiceTotal);
        out.put("shareOrgTotal", shareOrgTotal);
        out.put("exchangeVolumeTotal", exchangeVolumeTotal);
        out.put("publishedCount", published.size());
        out.put("subscriptionTotal", subTotal);
        out.put("subscriptionPending", subPending);
        out.put("situationCount", situationMapper.selectCount(null));
        out.put("searchKeyword", keyword == null ? "" : keyword);
        out.put("hotKeywords", buildHotKeywords(published));
        out.put("themes", buildThemes(published));
        out.put("baseLibraries", buildBaseLibraries(published));
        out.put("providers", buildProviders(published));
        out.put("latestResources", latest);
        out.put("latestApplications", latestApplications);
        out.put("hotResources", hot);
        out.put("recommendations", hot.isEmpty() ? latest : hot);
        return out;
    }

    public List<Map<String, Object>> search(String keyword, String themeCode, String providerOrg,
                                            String catalogKind, String shareMode) {
        if (keyword != null && !keyword.isBlank()
                && blank(themeCode) && blank(providerOrg) && blank(catalogKind) && blank(shareMode)) {
            List<Map<String, Object>> esHits = storageClient.searchCatalog(keyword, 50);
            if (!esHits.isEmpty()) {
                return enrichEsHits(esHits, themeCode, providerOrg, catalogKind, shareMode);
            }
        }
        return publishedCatalogs(keyword, themeCode, null, providerOrg, catalogKind, shareMode).stream()
                .map(c -> {
                    Map<String, Object> row = catalogView(c);
                    row.put("source", "database");
                    row.put("score", 1.0);
                    return row;
                }).toList();
    }

    public List<Map<String, Object>> catalogBrowse(String keyword, String themeCode, String providerOrg,
                                                   String catalogKind, String shareMode) {
        return catalogBrowse(keyword, themeCode, null, providerOrg, catalogKind, shareMode,
                null, null, null, null, null);
    }

    public List<Map<String, Object>> catalogBrowse(String keyword, String themeCode, String providerOrg,
                                                   String catalogKind, String shareMode,
                                                   String shareAttr, String openAttr, String resourceType,
                                                   String sortBy, String sortDir) {
        return catalogBrowse(keyword, themeCode, null, providerOrg, catalogKind, shareMode,
                shareAttr, openAttr, resourceType, sortBy, sortDir);
    }

    public List<Map<String, Object>> catalogBrowse(String keyword, String themeCode, String baseCode,
                                                   String providerOrg, String catalogKind, String shareMode,
                                                   String shareAttr, String openAttr, String resourceType,
                                                   String sortBy, String sortDir) {
        String mode = !blank(resourceType) ? resourceType : shareMode;
        List<Map<String, Object>> rows = publishedCatalogs(keyword, themeCode, baseCode, providerOrg, catalogKind, mode).stream()
                .map(this::catalogView)
                .collect(Collectors.toCollection(ArrayList::new));
        enrichShareOpenStats(rows);
        if (!blank(shareAttr)) {
            String want = shareAttr.trim().toUpperCase(Locale.ROOT);
            rows = rows.stream()
                    .filter(r -> want.equalsIgnoreCase(String.valueOf(r.getOrDefault("shareAttr", ""))))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (!blank(openAttr)) {
            String want = openAttr.trim().toUpperCase(Locale.ROOT);
            rows = rows.stream()
                    .filter(r -> want.equalsIgnoreCase(String.valueOf(r.getOrDefault("openAttr", ""))))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        sortCatalogRows(rows, sortBy, sortDir);
        return rows;
    }

    public Map<String, Object> catalogDetail(Long id) {
        BizCatalogItem catalog = catalogMapper.selectById(id);
        if (catalog == null || !"PUBLISHED".equals(catalog.getPublishStatus())) {
            throw new BusinessException(404, "资源不存在或未发布");
        }
        Map<String, Object> row = catalogView(catalog);
        enrichShareOpenStats(List.of(row));
        GovCatalogResource gov = catalog.getGovResourceId() == null
                ? null : govResourceMapper.selectById(catalog.getGovResourceId());
        String resourceType = resolveResourceType(catalog, gov);
        row.put("resourceType", resourceType);
        row.put("resourceTypeLabel", resourceTypeLabel(resourceType));
        if (gov != null) {
            row.put("updatedAt", formatDt(gov.getUpdatedAt() != null ? gov.getUpdatedAt() : catalog.getPublishedAt()));
            row.put("updateCycle", gov.getUpdateCycle());
            row.put("physicalTableName", gov.getPhysicalTableName());
            Map<String, Object> ext = parseExtJson(gov.getExtJson());
            if (ext != null) {
                if (ext.get("tables") != null) {
                    row.put("tables", ext.get("tables"));
                }
                if (ext.get("apis") != null) {
                    row.put("apis", ext.get("apis"));
                }
                if (ext.get("files") != null) {
                    row.put("files", ext.get("files"));
                }
                if (ext.get("resourceType") != null) {
                    row.put("resourceType", String.valueOf(ext.get("resourceType")).toUpperCase(Locale.ROOT));
                    row.put("resourceTypeLabel", resourceTypeLabel(String.valueOf(row.get("resourceType"))));
                }
            }
            // 编目保存的是 columnList/api/file，门户详情需要 tables/apis/files 结构
            enrichPortalDetailFromGovExt(row, catalog, gov, ext);
        } else {
            row.put("updatedAt", formatDt(catalog.getPublishedAt()));
        }
        // 访问量 +1（详情打开）
        int score = catalog.getHotScore() == null ? 0 : catalog.getHotScore();
        catalog.setHotScore(score + 1);
        catalogMapper.updateById(catalog);
        row.put("visitCount", score + 1);
        return row;
    }

    public List<Map<String, Object>> listSubscriptions(String status) {
        return listSubscriptions(null, status, null);
    }

    /**
     * @param scope mine=本部门申请；pending=待本部门审批（资源提供方为本机构）；空=仅本部门相关（申请或待审）
     */
    public List<Map<String, Object>> listSubscriptions(UserPrincipal operator, String status, String scope) {
        LambdaQueryWrapper<BizPortalSubscription> q = new LambdaQueryWrapper<BizPortalSubscription>()
                .orderByDesc(BizPortalSubscription::getId);
        if (status != null && !status.isBlank()) {
            q.eq(BizPortalSubscription::getStatus, status);
        }
        List<BizPortalSubscription> list = subscriptionMapper.selectList(q);
        String myOrg = resolveOrgName(operator);
        String username = operator == null ? null : operator.getUsername();
        boolean admin = operator != null && operator.isSystemAdmin();
        String scopeKey = nz(scope, "").trim().toLowerCase(Locale.ROOT);

        List<Map<String, Object>> out = new ArrayList<>();
        for (BizPortalSubscription sub : list) {
            BizCatalogItem cat = catalogMapper.selectById(sub.getCatalogId());
            String providerOrg = cat == null ? null : cat.getProviderOrg();

            if ("mine".equals(scopeKey)) {
                // 本部门提交的申请（按申请单位隔离；兼容历史仅按申请人账号）
                if (!admin) {
                    if (blank(myOrg)) {
                        if (username == null || !username.equals(sub.getCreatedBy())) {
                            continue;
                        }
                    } else if (!orgNameEquals(myOrg, sub.getApplicantOrg())
                            && (username == null || !username.equals(sub.getCreatedBy()))) {
                        continue;
                    }
                }
            } else if ("pending".equals(scopeKey)) {
                // 本部门作为资源提供方的待审申请
                if (!"PENDING".equalsIgnoreCase(sub.getStatus())) {
                    continue;
                }
                if (!admin) {
                    if (blank(myOrg) || !orgNameEquals(myOrg, providerOrg)) {
                        continue;
                    }
                    // 本部门自己提的申请不算「待我审批」
                    if (orgNameEquals(myOrg, sub.getApplicantOrg())
                            || (username != null && username.equals(sub.getCreatedBy()))) {
                        continue;
                    }
                }
            } else if (!admin) {
                // 未指定 scope：禁止返回全量，仅本部门相关
                boolean asApplicant = !blank(myOrg) && orgNameEquals(myOrg, sub.getApplicantOrg());
                boolean asProvider = !blank(myOrg) && orgNameEquals(myOrg, providerOrg);
                boolean asSelf = username != null && username.equals(sub.getCreatedBy());
                if (!asApplicant && !asProvider && !asSelf) {
                    continue;
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", sub.getId());
            row.put("catalogId", sub.getCatalogId());
            row.put("applicantOrg", sub.getApplicantOrg());
            row.put("resourceType", sub.getResourceType());
            row.put("purpose", sub.getPurpose());
            row.put("applyPayload", parseJsonSafe(sub.getApplyPayload()));
            row.put("status", sub.getStatus());
            row.put("approverNote", sub.getApproverNote());
            row.put("createdBy", sub.getCreatedBy());
            row.put("createdAt", sub.getCreatedAt());
            row.put("providerOrg", providerOrg);
            if (cat != null) {
                row.put("catalogTitle", cat.getTitle());
                row.put("catalogCode", cat.getCatalogCode());
            }
            String ref = "PORTAL_SUB_" + sub.getId();
            List<BizDemandSupplyTask> tasks = supplyTaskMapper.selectList(new LambdaQueryWrapper<BizDemandSupplyTask>()
                    .eq(BizDemandSupplyTask::getRefFlowCode, ref)
                    .orderByDesc(BizDemandSupplyTask::getId)
                    .last("LIMIT 1"));
            if (!tasks.isEmpty()) {
                BizDemandSupplyTask t = tasks.get(0);
                row.put("taskId", t.getId());
                row.put("taskType", t.getTaskType());
                row.put("taskName", t.getTaskName());
                row.put("taskStatus", t.getStatus());
            }
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Long createSubscription(UserPrincipal operator, Map<String, Object> body) {
        Long catalogId = Long.valueOf(String.valueOf(required(body.get("catalogId"), "catalogId")));
        BizCatalogItem catalog = catalogMapper.selectById(catalogId);
        if (catalog == null || !"PUBLISHED".equals(catalog.getPublishStatus())) {
            throw new BusinessException(400, "仅可订阅已发布目录");
        }
        String resourceType = str(body.get("resourceType"), "TABLE").toUpperCase(Locale.ROOT);
        if (!Set.of("TABLE", "FILE", "API").contains(resourceType)) {
            throw new BusinessException(400, "resourceType 须为 TABLE/FILE/API");
        }
        String myOrg = resolveOrgName(operator);
        if (blank(myOrg) && (operator == null || !operator.isSystemAdmin())) {
            throw new BusinessException(400, "当前账号未绑定所属部门，无法提交申请");
        }
        // 申请单位强制取登录用户部门，保证「我的申请」按部门隔离
        String applicantOrg = blank(myOrg)
                ? str(body.get("applicantOrg"), "系统管理员")
                : myOrg;
        String purpose = str(body.get("purpose"), str(body.get("scene"), ""));
        if (purpose.length() > 500) {
            purpose = purpose.substring(0, 500);
        }
        String payloadJson = null;
        try {
            Map<String, Object> payload = new LinkedHashMap<>(body);
            payload.remove("catalogId");
            payload.put("applicantOrg", applicantOrg);
            payloadJson = OM.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("serialize apply payload failed: {}", e.getMessage());
        }

        BizPortalSubscription sub = new BizPortalSubscription();
        sub.setCatalogId(catalogId);
        sub.setApplicantOrg(applicantOrg);
        sub.setResourceType(resourceType);
        sub.setPurpose(purpose);
        sub.setApplyPayload(payloadJson);
        sub.setStatus("PENDING");
        sub.setCreatedBy(operator.getUsername());
        subscriptionMapper.insert(sub);

        // 同步到数据融合治理平台「资源申请订阅」
        if (catalog.getGovResourceId() != null) {
            try {
                Map<String, Object> govBody = new LinkedHashMap<>();
                govBody.put("resourceId", catalog.getGovResourceId());
                govBody.put("shareMode", toGovShareMode(resourceType));
                govBody.put("applicantOrg", applicantOrg);
                govBody.put("purpose", purpose);
                govBody.put("applyPayload", payloadJson);
                catalogSubscriptionService.create(operator, govBody);
            } catch (BusinessException ex) {
                // 已有待审等业务冲突：门户记录仍保留，治理侧跳过
                log.warn("sync gov subscription skipped: {}", ex.getMessage());
            }
        }

        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "PORTAL_SUBSCRIBE", "biz_portal_subscription", String.valueOf(sub.getId()), catalog.getTitle());
        return sub.getId();
    }

    private String toGovShareMode(String resourceType) {
        return switch (resourceType) {
            case "FILE" -> "FILE_SYNC";
            case "API" -> "API";
            default -> "DB_SYNC";
        };
    }

    private String resolveOrgName(UserPrincipal operator) {
        if (operator == null || operator.getOrgId() == null) {
            return null;
        }
        SysOrg org = orgMapper.selectById(operator.getOrgId());
        return org == null ? null : org.getOrgName();
    }

    /** 部门名比对：去首尾空白后全等（与资源提供单位 / 申请单位对齐） */
    private boolean orgNameEquals(String a, String b) {
        if (blank(a) || blank(b)) {
            return false;
        }
        return a.trim().equals(b.trim());
    }

    /** 仅资源提供方部门（或系统管理员）可审批 */
    private void assertProviderCanReview(UserPrincipal operator, BizPortalSubscription sub) {
        if (operator != null && operator.isSystemAdmin()) {
            return;
        }
        if (operator == null) {
            throw new BusinessException(403, "未登录，无法审批");
        }
        String myOrg = resolveOrgName(operator);
        BizCatalogItem catalog = catalogMapper.selectById(sub.getCatalogId());
        String providerOrg = catalog == null ? null : catalog.getProviderOrg();
        if (blank(myOrg) || !orgNameEquals(myOrg, providerOrg)) {
            throw new BusinessException(403, "仅资源提供方部门可审批该申请");
        }
        if (orgNameEquals(myOrg, sub.getApplicantOrg())
                || Objects.equals(operator.getUsername(), sub.getCreatedBy())) {
            throw new BusinessException(403, "不能审批本部门自己提交的申请");
        }
    }

    @SuppressWarnings("unchecked")
    private Object parseJsonSafe(String json) {
        if (blank(json)) {
            return null;
        }
        try {
            return OM.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return json;
        }
    }

    @Transactional
    public Map<String, Object> reviewSubscription(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizPortalSubscription sub = subscriptionMapper.selectById(id);
        if (sub == null) {
            throw new BusinessException(404, "订阅申请不存在");
        }
        if (!"PENDING".equalsIgnoreCase(sub.getStatus())) {
            throw new BusinessException(400, "仅待审批申请可审核");
        }
        assertProviderCanReview(operator, sub);

        String action = str(body.get("action"), "APPROVE").toUpperCase(Locale.ROOT);
        boolean approved = "APPROVE".equals(action) || "APPROVED".equals(action);
        sub.setStatus(approved ? "APPROVED" : "REJECTED");
        sub.setApproverNote(str(body.get("approverNote"), ""));
        subscriptionMapper.updateById(sub);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subscriptionId", id);
        out.put("status", sub.getStatus());

        if (approved) {
            BizCatalogItem catalog = catalogMapper.selectById(sub.getCatalogId());
            BizDemandSupplyTask task = createPortalExchangeTask(sub, catalog);
            out.put("taskId", task.getId());
            out.put("taskType", task.getTaskType());
            out.put("taskStatus", task.getStatus());
            if (catalog != null) {
                int score = catalog.getHotScore() == null ? 0 : catalog.getHotScore();
                catalog.setHotScore(score + 1);
                catalogMapper.updateById(catalog);
            }
            log.info("portal subscription {} approved, task {}", id, task.getId());
        }

        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "PORTAL_SUB_REVIEW", "biz_portal_subscription", String.valueOf(id), sub.getStatus());
        return out;
    }

    @Transactional
    public void cancelSubscription(UserPrincipal operator, Long id) {
        BizPortalSubscription sub = subscriptionMapper.selectById(id);
        if (sub == null) {
            throw new BusinessException(404, "订阅申请不存在");
        }
        boolean admin = operator != null && operator.isSystemAdmin();
        if (!admin) {
            if (operator == null) {
                throw new BusinessException(403, "仅申请方可取消订阅");
            }
            String myOrg = resolveOrgName(operator);
            boolean self = Objects.equals(operator.getUsername(), sub.getCreatedBy());
            boolean sameDept = orgNameEquals(myOrg, sub.getApplicantOrg());
            if (!self && !sameDept) {
                throw new BusinessException(403, "仅本部门申请方可取消订阅");
            }
        }
        if (!"PENDING".equalsIgnoreCase(sub.getStatus())) {
            throw new BusinessException(400, "仅待审批申请可取消");
        }
        sub.setStatus("CANCELLED");
        subscriptionMapper.updateById(sub);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "PORTAL_SUB_CANCEL", "biz_portal_subscription", String.valueOf(id), null);
    }

    public List<BizPortalSituation> listSituations() {
        return situationMapper.selectList(new LambdaQueryWrapper<BizPortalSituation>()
                .orderByAsc(BizPortalSituation::getSortOrder));
    }

    @Transactional
    public Map<String, Object> syncSearchIndex(UserPrincipal operator) {
        List<BizCatalogItem> items = publishedCatalogs(null, null, null, null, null, null);
        int indexed = 0;
        for (BizCatalogItem item : items) {
            storageClient.indexCatalog(String.valueOf(item.getId()), item.getCatalogCode(),
                    item.getTitle(), item.getDescription());
            indexed++;
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "PORTAL_INDEX_SYNC", "smartcity_catalog", String.valueOf(indexed), "catalog-index");
        return Map.of("indexed", indexed, "index", ES_INDEX,
                "esHealthy", storageClient.isElasticsearchHealthy());
    }

    private BizDemandSupplyTask createPortalExchangeTask(BizPortalSubscription sub, BizCatalogItem catalog) {
        String type = mapShareModeToTaskType(sub.getResourceType());
        String title = catalog != null ? catalog.getTitle() : ("目录#" + sub.getCatalogId());
        BizDemandSupplyTask task = new BizDemandSupplyTask();
        task.setDemandId(0L);
        task.setTaskType(type);
        task.setTaskName("门户订阅·" + sub.getResourceType() + "·" + title);
        task.setStatus("READY");
        task.setRefFlowCode("PORTAL_SUB_" + sub.getId());
        supplyTaskMapper.insert(task);
        return task;
    }

    private String mapShareModeToTaskType(String resourceType) {
        return switch (nz(resourceType, "TABLE").toUpperCase(Locale.ROOT)) {
            case "FILE" -> "FILE";
            case "API" -> "SHARE";
            default -> "EXCHANGE";
        };
    }

    private List<BizCatalogItem> publishedCatalogs(String keyword, String themeCode, String baseCode,
                                                   String providerOrg, String catalogKind, String shareMode) {
        LambdaQueryWrapper<BizCatalogItem> q = new LambdaQueryWrapper<BizCatalogItem>()
                .eq(BizCatalogItem::getPublishStatus, "PUBLISHED")
                .isNotNull(BizCatalogItem::getGovResourceId)
                .orderByDesc(BizCatalogItem::getHotScore)
                .orderByDesc(BizCatalogItem::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(BizCatalogItem::getTitle, keyword)
                    .or().like(BizCatalogItem::getDescription, keyword)
                    .or().like(BizCatalogItem::getCatalogCode, keyword)
                    .or().like(BizCatalogItem::getThemeName, keyword)
                    .or().like(BizCatalogItem::getBaseCatalogName, keyword)
                    .or().like(BizCatalogItem::getProviderOrg, keyword));
        }
        // themeCode / baseCode / providerOrg 在 enrich 后再过滤
        if (!blank(catalogKind)) {
            q.eq(BizCatalogItem::getCatalogKind, catalogKind.toUpperCase(Locale.ROOT));
        }
        if (!blank(shareMode)) {
            q.like(BizCatalogItem::getShareModes, shareMode.toUpperCase(Locale.ROOT));
        }
        List<BizCatalogItem> list = catalogMapper.selectList(q);
        enrichPortalFieldsFromGov(list);
        if (!blank(themeCode)) {
            list = list.stream()
                    .filter(c -> themeCode.equalsIgnoreCase(nz(c.getThemeCode(), ""))
                            || themeCode.equals(nz(c.getThemeName(), "")))
                    .toList();
        }
        if (!blank(baseCode)) {
            list = list.stream()
                    .filter(c -> baseCode.equalsIgnoreCase(nz(c.getBaseCatalogCode(), ""))
                            || baseCode.equals(nz(c.getBaseCatalogName(), "")))
                    .toList();
        }
        if (!blank(providerOrg)) {
            list = list.stream()
                    .filter(c -> providerOrg.equals(nz(c.getProviderOrg(), "")))
                    .toList();
        }
        return list;
    }

    /**
     * 用统一编目表回填主题（主题资源目录）、基础库（信息资源分类）与提供方（组织机构），
     * 兼容历史 sync 把 themeName 写成分类路径、未写 themeCode 的脏数据。
     */
    private void enrichPortalFieldsFromGov(List<BizCatalogItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<Long> govIds = items.stream()
                .map(BizCatalogItem::getGovResourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (govIds.isEmpty()) {
            return;
        }
        List<GovCatalogResource> govs = govResourceMapper.selectBatchIds(govIds);
        Map<Long, GovCatalogResource> byId = govs.stream()
                .collect(Collectors.toMap(GovCatalogResource::getId, g -> g, (a, b) -> a));
        Map<String, String> themeCodeCache = new HashMap<>();
        Map<Long, GovCatalogCategory> catCache = new HashMap<>();
        for (BizCatalogItem c : items) {
            GovCatalogResource g = c.getGovResourceId() == null ? null : byId.get(c.getGovResourceId());
            if (g == null) {
                continue;
            }
            String themeName = blank(g.getThemeName()) ? null : g.getThemeName().trim();
            if (themeName != null) {
                c.setThemeName(themeName);
                String code = themeCodeCache.computeIfAbsent(themeName + "|" + nz(g.getCatalogOrigin(), ""),
                        k -> resolveThemeCode(themeName, g.getCatalogOrigin()));
                c.setThemeCode(code);
            } else if (looksLikeCategoryPath(c.getThemeName())) {
                c.setThemeCode(null);
                c.setThemeName(null);
            }
            if (!blank(g.getProviderOrg())) {
                c.setProviderOrg(g.getProviderOrg().trim());
            }
            // 基础库：优先 categoryId，其次 baseCatalogName
            if (blank(c.getBaseCatalogName()) || blank(c.getBaseCatalogCode())) {
                if (g.getCategoryId() != null) {
                    GovCatalogCategory cat = catCache.computeIfAbsent(g.getCategoryId(),
                            id -> govCategoryMapper.selectById(id));
                    if (cat != null) {
                        c.setBaseCatalogCode(cat.getCategoryCode());
                        c.setBaseCatalogName(cat.getCategoryName());
                    }
                } else if (!blank(g.getBaseCatalogName())) {
                    c.setBaseCatalogName(g.getBaseCatalogName().trim());
                    if (blank(c.getBaseCatalogCode())) {
                        c.setBaseCatalogCode(resolveBaseCode(g.getBaseCatalogName().trim(), g.getCatalogOrigin()));
                    }
                }
            }
        }
    }

    private String resolveBaseCode(String baseName, String catalogOrigin) {
        LambdaQueryWrapper<GovCatalogCategory> q = new LambdaQueryWrapper<GovCatalogCategory>()
                .eq(GovCatalogCategory::getCategoryName, baseName)
                .and(w -> w.isNull(GovCatalogCategory::getStatus)
                        .or().ne(GovCatalogCategory::getStatus, "OFFLINE"));
        if (!blank(catalogOrigin)) {
            q.eq(GovCatalogCategory::getCatalogOrigin, catalogOrigin.trim().toUpperCase(Locale.ROOT));
        }
        List<GovCatalogCategory> list = govCategoryMapper.selectList(q);
        for (GovCatalogCategory cat : list) {
            String path = cat.getCategoryPath();
            String code = cat.getCategoryCode();
            if ((path != null && path.contains("基础资源目录"))
                    || (code != null && code.toUpperCase(Locale.ROOT).contains("BASE"))) {
                return cat.getCategoryCode();
            }
        }
        if (!list.isEmpty()) {
            return list.get(0).getCategoryCode();
        }
        return "BASE_" + Integer.toHexString(baseName.hashCode());
    }

    private String resolveThemeCode(String themeName, String catalogOrigin) {
        LambdaQueryWrapper<GovCatalogCategory> q = new LambdaQueryWrapper<GovCatalogCategory>()
                .eq(GovCatalogCategory::getCategoryName, themeName)
                .and(w -> w.isNull(GovCatalogCategory::getStatus)
                        .or().ne(GovCatalogCategory::getStatus, "OFFLINE"));
        if (!blank(catalogOrigin)) {
            q.eq(GovCatalogCategory::getCatalogOrigin, catalogOrigin.trim().toUpperCase(Locale.ROOT));
        }
        List<GovCatalogCategory> list = govCategoryMapper.selectList(q);
        for (GovCatalogCategory cat : list) {
            String path = cat.getCategoryPath();
            String code = cat.getCategoryCode();
            if ((path != null && path.contains("主题资源目录"))
                    || (code != null && code.toUpperCase(Locale.ROOT).contains("THEME"))) {
                return cat.getCategoryCode();
            }
        }
        if (!list.isEmpty()) {
            return list.get(0).getCategoryCode();
        }
        return "THEME_" + Integer.toHexString(themeName.hashCode());
    }

    private boolean looksLikeCategoryPath(String themeName) {
        if (blank(themeName)) {
            return false;
        }
        return themeName.contains("/")
                && (themeName.startsWith("基础资源目录")
                || themeName.startsWith("部门资源目录")
                || themeName.startsWith("主题资源目录"));
    }

    private List<Map<String, Object>> enrichEsHits(List<Map<String, Object>> esHits,
                                                   String themeCode, String providerOrg,
                                                   String catalogKind, String shareMode) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> hit : esHits) {
            Object idObj = hit.get("id");
            BizCatalogItem c = null;
            if (idObj != null) {
                try {
                    c = catalogMapper.selectById(Long.valueOf(String.valueOf(idObj)));
                } catch (NumberFormatException ignored) {
                    // keep ES hit
                }
            }
            Map<String, Object> row = c != null ? catalogView(c) : new HashMap<>(hit);
            row.put("source", hit.getOrDefault("source", "elasticsearch"));
            if (hit.get("score") != null) {
                row.put("score", hit.get("score"));
            }
            if (matchFilter(c, row, themeCode, providerOrg, catalogKind, shareMode)) {
                out.add(row);
            }
        }
        return out;
    }

    private boolean matchFilter(BizCatalogItem c, Map<String, Object> row,
                                String themeCode, String providerOrg,
                                String catalogKind, String shareMode) {
        String kind = c != null ? nz(c.getCatalogKind(), "") : String.valueOf(row.getOrDefault("catalogKind", ""));
        String theme = c != null ? nz(c.getThemeCode(), "") : String.valueOf(row.getOrDefault("themeCode", ""));
        String provider = c != null ? nz(c.getProviderOrg(), "") : String.valueOf(row.getOrDefault("providerOrg", ""));
        String modes = c != null ? nz(c.getShareModes(), "") : String.valueOf(row.getOrDefault("shareModes", ""));
        if (!blank(catalogKind) && !catalogKind.equalsIgnoreCase(kind)) return false;
        if (!blank(themeCode) && !themeCode.equalsIgnoreCase(theme)) return false;
        if (!blank(providerOrg) && !providerOrg.equals(provider)) return false;
        if (!blank(shareMode) && !modes.toUpperCase(Locale.ROOT).contains(shareMode.toUpperCase(Locale.ROOT))) return false;
        return true;
    }

    private Map<String, Object> catalogView(BizCatalogItem c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.getId());
        row.put("catalogCode", c.getCatalogCode());
        row.put("title", c.getTitle());
        row.put("description", c.getDescription());
        row.put("catalogKind", nz(c.getCatalogKind(), "DATA"));
        row.put("catalogOrigin", c.getCatalogOrigin());
        row.put("govResourceId", c.getGovResourceId());
        row.put("themeCode", c.getThemeCode());
        row.put("themeName", c.getThemeName());
        row.put("baseCatalogCode", c.getBaseCatalogCode());
        row.put("baseCatalogName", c.getBaseCatalogName());
        row.put("providerOrg", c.getProviderOrg());
        row.put("shareModes", c.getShareModes());
        row.put("resourceCount", c.getResourceCount() == null ? 0 : c.getResourceCount());
        row.put("hotScore", c.getHotScore() == null ? 0 : c.getHotScore());
        row.put("visitCount", c.getHotScore() == null ? 0 : c.getHotScore());
        row.put("applyCount", 0);
        row.put("publishedAt", c.getPublishedAt());
        row.put("updatedAt", formatDt(c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getPublishedAt()));
        row.put("publishStatus", c.getPublishStatus());
        row.put("source", "database");
        row.put("resourceType", resolveResourceType(c, null));
        row.put("resourceTypeLabel", resourceTypeLabel(String.valueOf(row.get("resourceType"))));
        row.put("shareAttr", "CONDITIONAL");
        row.put("openAttr", "SOCIAL_OPEN");
        row.put("previewItems", previewItems(c));
        return row;
    }

    private void enrichShareOpenStats(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> govIds = new LinkedHashSet<>();
        Set<Long> catalogIds = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object gid = row.get("govResourceId");
            if (gid != null) {
                try {
                    govIds.add(Long.valueOf(String.valueOf(gid)));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
            Object cid = row.get("id");
            if (cid != null) {
                try {
                    catalogIds.add(Long.valueOf(String.valueOf(cid)));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
        Map<Long, GovCatalogResource> govById = new HashMap<>();
        if (!govIds.isEmpty()) {
            for (GovCatalogResource g : govResourceMapper.selectBatchIds(govIds)) {
                govById.put(g.getId(), g);
            }
        }
        Map<Long, Integer> applyByCatalog = new HashMap<>();
        if (!catalogIds.isEmpty()) {
            List<BizPortalSubscription> subs = subscriptionMapper.selectList(new LambdaQueryWrapper<BizPortalSubscription>()
                    .in(BizPortalSubscription::getCatalogId, catalogIds));
            for (BizPortalSubscription sub : subs) {
                if (sub.getCatalogId() == null) {
                    continue;
                }
                applyByCatalog.merge(sub.getCatalogId(), 1, Integer::sum);
            }
        }
        for (Map<String, Object> row : rows) {
            Long govId = null;
            Long catalogId = null;
            try {
                if (row.get("govResourceId") != null) {
                    govId = Long.valueOf(String.valueOf(row.get("govResourceId")));
                }
                if (row.get("id") != null) {
                    catalogId = Long.valueOf(String.valueOf(row.get("id")));
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
            GovCatalogResource gov = govId == null ? null : govById.get(govId);
            if (gov != null) {
                String shareAttr = blank(gov.getShareType()) ? "CONDITIONAL" : gov.getShareType().trim().toUpperCase(Locale.ROOT);
                if ("OPEN".equals(shareAttr)) {
                    shareAttr = "UNCONDITIONAL";
                }
                String openAttr = blank(gov.getOpenType()) ? "SOCIAL_OPEN" : gov.getOpenType().trim().toUpperCase(Locale.ROOT);
                row.put("shareAttr", shareAttr);
                row.put("openAttr", openAttr);
                if (gov.getUpdatedAt() != null) {
                    row.put("updatedAt", formatDt(gov.getUpdatedAt()));
                }
                String rt = resolveResourceType(null, gov);
                if (row.get("shareModes") != null) {
                    rt = resolveResourceTypeFromModes(String.valueOf(row.get("shareModes")), gov);
                }
                row.put("resourceType", rt);
                row.put("resourceTypeLabel", resourceTypeLabel(rt));
            } else {
                String modes = String.valueOf(row.getOrDefault("shareModes", ""));
                String rt = resolveResourceTypeFromModes(modes, null);
                row.put("resourceType", rt);
                row.put("resourceTypeLabel", resourceTypeLabel(rt));
            }
            int apply = catalogId == null ? 0 : applyByCatalog.getOrDefault(catalogId, 0);
            row.put("applyCount", apply);
            Object visit = row.get("visitCount");
            if (visit == null) {
                row.put("visitCount", row.getOrDefault("hotScore", 0));
            }
        }
    }

    private void sortCatalogRows(List<Map<String, Object>> rows, String sortBy, String sortDir) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String by = blank(sortBy) ? "applyCount" : sortBy.trim();
        boolean asc = "asc".equalsIgnoreCase(nz(sortDir, "desc"));
        Comparator<Map<String, Object>> cmp;
        if ("hotScore".equalsIgnoreCase(by)) {
            cmp = Comparator.comparingInt(r -> toInt(r.get("hotScore")));
        } else if ("visitCount".equalsIgnoreCase(by)) {
            cmp = Comparator.comparingInt(r -> toInt(r.get("visitCount")));
        } else if ("updatedAt".equalsIgnoreCase(by) || "updateTime".equalsIgnoreCase(by)) {
            cmp = Comparator.comparing(r -> String.valueOf(r.getOrDefault("updatedAt", "")),
                    Comparator.nullsLast(String::compareTo));
        } else {
            cmp = Comparator.comparingInt(r -> toInt(r.get("applyCount")));
        }
        if (!asc) {
            cmp = cmp.reversed();
        }
        rows.sort(cmp);
    }

    private String resolveResourceType(BizCatalogItem c, GovCatalogResource gov) {
        if (gov != null) {
            String fmt = nz(gov.getResourceFormat(), "").toUpperCase(Locale.ROOT);
            if (fmt.contains("API") || "SERVICE".equalsIgnoreCase(nz(gov.getResourceType(), ""))) {
                return "API";
            }
            if (fmt.contains("FILE") || fmt.contains("FTP") || fmt.contains("XLS") || fmt.contains("CSV")) {
                return "FILE";
            }
            if (!blank(gov.getPhysicalTableName()) || fmt.contains("DATABASE") || fmt.contains("TABLE")) {
                return "TABLE";
            }
        }
        if (c != null) {
            return resolveResourceTypeFromModes(c.getShareModes(), gov);
        }
        return "TABLE";
    }

    private String resolveResourceTypeFromModes(String shareModes, GovCatalogResource gov) {
        String modes = nz(shareModes, "").toUpperCase(Locale.ROOT);
        if (modes.contains("API")) {
            return "API";
        }
        if (modes.contains("FILE")) {
            return "FILE";
        }
        if (modes.contains("TABLE") || modes.contains("DB")) {
            return "TABLE";
        }
        return resolveResourceType(null, gov);
    }

    private String resourceTypeLabel(String type) {
        return switch (nz(type, "TABLE").toUpperCase(Locale.ROOT)) {
            case "API" -> "接口";
            case "FILE" -> "文件";
            default -> "库表";
        };
    }

    private Map<String, Object> parseExtJson(String json) {
        if (blank(json)) {
            return null;
        }
        try {
            return OM.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("parse ext_json failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将编目 extJson（columnList / api / file）转换为门户详情使用的 tables / apis / files。
     */
    @SuppressWarnings("unchecked")
    private void enrichPortalDetailFromGovExt(Map<String, Object> row,
                                              BizCatalogItem catalog,
                                              GovCatalogResource gov,
                                              Map<String, Object> ext) {
        String type = String.valueOf(row.getOrDefault("resourceType", "TABLE")).toUpperCase(Locale.ROOT);
        if ("TABLE".equals(type)) {
            List<Map<String, Object>> columns = mapPortalColumnsFromExt(ext);
            Object tablesObj = row.get("tables");
            if (tablesObj instanceof List<?> rawList && !rawList.isEmpty()) {
                List<Map<String, Object>> tables = new ArrayList<>();
                for (Object o : rawList) {
                    if (!(o instanceof Map<?, ?> m)) {
                        continue;
                    }
                    Map<String, Object> t = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        t.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    Object cols = t.get("columns");
                    boolean emptyCols = !(cols instanceof List<?>) || ((List<?>) cols).isEmpty();
                    if (emptyCols && !columns.isEmpty()) {
                        t.put("columns", columns);
                    }
                    if (blank(str(t.get("summary"), "")) && !columns.isEmpty()) {
                        t.put("summary", "共 " + columns.size() + " 个字段");
                    }
                    tables.add(t);
                }
                if (!tables.isEmpty()) {
                    row.put("tables", tables);
                    return;
                }
            }
            String tableName = firstNonBlank(
                    gov.getPhysicalTableName(),
                    ext == null ? null : str(ext.get("bindTableName"), null),
                    null);
            if (blank(tableName) && columns.isEmpty()) {
                return;
            }
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("tableName", blank(tableName) ? "—" : tableName);
            t.put("catalogCode", nz(catalog.getCatalogCode(), ""));
            String summary = nz(gov.getDescription(), "");
            if (blank(summary) && !columns.isEmpty()) {
                summary = "共 " + columns.size() + " 个字段";
            }
            t.put("summary", summary);
            t.put("columns", columns);
            row.put("tables", List.of(t));
            return;
        }
        if ("API".equals(type) && row.get("apis") == null && ext != null && ext.get("api") instanceof Map<?, ?> apiMap) {
            Map<String, Object> api = new LinkedHashMap<>();
            api.put("apiName", str(apiMap.get("apiName"), catalog.getTitle()));
            api.put("apiCode", nz(catalog.getCatalogCode(), ""));
            api.put("catalogCode", nz(catalog.getCatalogCode(), ""));
            api.put("version", str(apiMap.get("apiVersion"), ""));
            api.put("targetAddressHint", "资源申请通过后前往个人中心查看");
            api.put("requestPath", firstNonBlank(str(apiMap.get("apiPath"), null), str(apiMap.get("apiUrl"), null), ""));
            api.put("httpMethod", str(apiMap.get("apiMethod"), "GET"));
            api.put("registeredAt", str(apiMap.get("registerAt"), ""));
            api.put("description", str(apiMap.get("apiDescription"), nz(gov.getDescription(), "")));
            api.put("expireAt", str(apiMap.get("expireAt"), ""));
            api.put("requestParams", mapPortalApiParams(apiMap.get("requestParams")));
            api.put("responseParams", mapPortalApiParams(apiMap.get("responseParams")));
            Object example = apiMap.get("apiResultJson");
            if (example instanceof String s && !s.isBlank()) {
                try {
                    api.put("successExample", OM.readValue(s, Object.class));
                } catch (Exception e) {
                    api.put("successExample", Map.of("raw", s));
                }
            } else if (example != null) {
                api.put("successExample", example);
            } else {
                api.put("successExample", Map.of());
            }
            row.put("apis", List.of(api));
            return;
        }
        if ("FILE".equals(type) && row.get("files") == null && ext != null && ext.get("file") instanceof Map<?, ?> fileMap) {
            Map<String, Object> file = new LinkedHashMap<>();
            String fileName = str(fileMap.get("fileName"), catalog.getTitle());
            file.put("fileName", fileName);
            file.put("fileCode", nz(catalog.getCatalogCode(), ""));
            file.put("catalogCode", nz(catalog.getCatalogCode(), ""));
            file.put("format", guessFileFormat(fileName));
            file.put("size", str(fileMap.get("fileSize"), ""));
            file.put("updateCycle", nz(gov.getUpdateCycle(), ""));
            file.put("storage", "FTP");
            file.put("addressHint", "资源申请通过后前往个人中心查看 FTP 地址");
            file.put("registeredAt", formatDt(gov.getUpdatedAt() != null ? gov.getUpdatedAt() : catalog.getPublishedAt()));
            file.put("description", firstNonBlank(str(fileMap.get("fileRemark"), null), nz(gov.getDescription(), ""), ""));
            row.put("files", List.of(file));
        }
    }

    private List<Map<String, Object>> mapPortalColumnsFromExt(Map<String, Object> ext) {
        if (ext == null) {
            return List.of();
        }
        Object raw = ext.get("columnList");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            String name = firstNonBlank(str(m.get("columnName"), null), str(m.get("name"), null), null);
            if (blank(name)) {
                continue;
            }
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("name", name);
            col.put("comment", firstNonBlank(
                    str(m.get("columnNameZh"), null),
                    str(m.get("remark"), null),
                    str(m.get("comment"), null),
                    name));
            col.put("type", firstNonBlank(
                    str(m.get("dataTypeZh"), null),
                    str(m.get("dataType"), null),
                    str(m.get("type"), null),
                    ""));
            Object len = m.get("length");
            if (len == null) {
                len = m.get("columnLength");
            }
            col.put("length", len == null || String.valueOf(len).isBlank() ? "" : String.valueOf(len));
            col.put("pk", boolish(m.get("pk")) || boolish(m.get("primaryKey")) || boolish(m.get("isPk")));
            if (m.containsKey("nullable")) {
                col.put("nullable", boolish(m.get("nullable")));
            } else if (m.containsKey("notNull") || m.containsKey("required")) {
                col.put("nullable", !(boolish(m.get("notNull")) || boolish(m.get("required"))));
            } else {
                col.put("nullable", true);
            }
            col.put("sensitivity", firstNonBlank(str(m.get("sensLevel"), null), str(m.get("sensitivity"), null), ""));
            out.add(col);
        }
        return out;
    }

    private List<Map<String, Object>> mapPortalApiParams(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", str(m.get("name"), ""));
            p.put("required", boolish(m.get("required")));
            p.put("dataType", firstNonBlank(str(m.get("dataType"), null), str(m.get("type"), null), ""));
            p.put("comment", firstNonBlank(str(m.get("comment"), null), str(m.get("description"), null), ""));
            out.add(p);
        }
        return out;
    }

    private String guessFileFormat(String fileName) {
        if (blank(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
    }

    private boolean boolish(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "Y".equalsIgnoreCase(s) || "是".equals(s);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (!blank(v)) {
                return v;
            }
        }
        return null;
    }

    private String formatDt(LocalDateTime dt) {
        return dt == null ? "" : DT_FMT.format(dt);
    }

    private int toInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<Map<String, String>> previewItems(BizCatalogItem c) {
        List<Map<String, String>> items = new ArrayList<>();
        items.add(Map.of("label", "编码", "value", nz(c.getCatalogCode(), "-")));
        items.add(Map.of("label", "类型", "value", "SERVICE".equalsIgnoreCase(nz(c.getCatalogKind(), "")) ? "服务目录" : "数据目录"));
        items.add(Map.of("label", "主题", "value", nz(c.getThemeName(), "-")));
        items.add(Map.of("label", "提供单位", "value", nz(c.getProviderOrg(), "-")));
        items.add(Map.of("label", "共享方式", "value", nz(c.getShareModes(), "-")));
        items.add(Map.of("label", "挂接资源", "value", String.valueOf(c.getResourceCount() == null ? 0 : c.getResourceCount())));
        return items;
    }

    private List<String> buildHotKeywords(List<BizCatalogItem> published) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add("人口");
        set.add("法人");
        set.add("经济");
        set.add("API");
        set.add("库表同步");
        for (BizCatalogItem c : published.stream()
                .sorted(Comparator.comparing((BizCatalogItem x) -> x.getHotScore() == null ? 0 : x.getHotScore()).reversed())
                .limit(8)
                .toList()) {
            if (c.getThemeName() != null && !c.getThemeName().isBlank()) {
                set.add(c.getThemeName().replace("主题", ""));
            }
            if (c.getTitle() != null) {
                String t = c.getTitle();
                if (t.length() > 4) {
                    set.add(t.substring(0, Math.min(4, t.length())));
                }
            }
        }
        return set.stream().limit(10).collect(Collectors.toList());
    }

    /**
     * 基础库图标条：列出「基础资源目录」下一级（人口库/法人库等），无资源也显示，计数为 0。
     * 计数按信息资源分类（baseCatalog*）聚合，不与主题混用。
     */
    private List<Map<String, Object>> buildBaseLibraries(List<BizCatalogItem> published) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        Map<String, String> nameToKey = new HashMap<>();

        for (GovCatalogCategory cat : listBaseL1Categories()) {
            String code = nz(cat.getCategoryCode(), "BASE_" + cat.getId());
            String name = nz(cat.getCategoryName(), code);
            map.put(code, newFacet(code, name,
                    "/exchange/analysis-portal/dept?section=subscribe&baseCode=" + code));
            nameToKey.put(name, code);
        }

        for (BizCatalogItem c : published) {
            if (blank(c.getBaseCatalogName()) && blank(c.getBaseCatalogCode())) {
                continue;
            }
            String name = !blank(c.getBaseCatalogName()) ? c.getBaseCatalogName() : c.getBaseCatalogCode();
            String code = !blank(c.getBaseCatalogCode()) ? c.getBaseCatalogCode() : null;
            String key = null;
            if (code != null && map.containsKey(code)) {
                key = code;
            } else if (name != null && nameToKey.containsKey(name)) {
                key = nameToKey.get(name);
            }
            // 不再按资源上的零散名称“发明”新图标：图标条只等于分类表一级节点
            if (key != null) {
                bumpFacet(map.get(key), c);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : map.values()) {
            if ("基础资源目录".equals(String.valueOf(m.getOrDefault("name", "")))) {
                continue;
            }
            out.add(m);
        }
        return out;
    }

    /**
     * 主题卡片：按「主题资源目录」一级分类排序，叠加审批通过已发布资源的接口数/库表数；
     * 仅返回已有已发布目录的主题（接口或库表计数 > 0）。
     */
    private List<Map<String, Object>> buildThemes(List<BizCatalogItem> published) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        Map<String, String> nameToKey = new HashMap<>();

        for (GovCatalogCategory cat : listThemeL1Categories()) {
            String code = nz(cat.getCategoryCode(), "THEME_" + cat.getId());
            String name = nz(cat.getCategoryName(), code);
            map.put(code, newFacet(code, name,
                    "/exchange/analysis-portal/dept?section=catalog&themeCode=" + code));
            nameToKey.put(name, code);
        }

        for (BizCatalogItem c : published) {
            if (blank(c.getThemeName()) && blank(c.getThemeCode())) {
                continue;
            }
            if (looksLikeCategoryPath(c.getThemeName())) {
                continue;
            }
            String name = !blank(c.getThemeName()) ? c.getThemeName() : c.getThemeCode();
            String code = !blank(c.getThemeCode()) ? c.getThemeCode() : null;
            String key = null;
            if (code != null && map.containsKey(code)) {
                key = code;
            } else if (name != null && nameToKey.containsKey(name)) {
                key = nameToKey.get(name);
            }
            // 主题卡片同样只展示分类表一级节点，不按资源名称发明新主题
            if (key != null) {
                bumpFacet(map.get(key), c);
            }
        }
        return filterFacetsWithCatalog(map.values());
    }

    /**
     * 部门卡片：按组织机构排序，叠加审批通过已发布资源的接口数/库表数；
     * 仅返回已有已发布目录的部门（接口或库表计数 > 0）。
     */
    private List<Map<String, Object>> buildProviders(List<BizCatalogItem> published) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();

        List<SysOrg> orgs = orgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                .orderByAsc(SysOrg::getSortOrder)
                .orderByAsc(SysOrg::getId));
        for (SysOrg org : orgs) {
            if (org.getStatus() != null && org.getStatus() == 0) {
                continue;
            }
            String name = org.getOrgName();
            if (blank(name)) {
                continue;
            }
            map.putIfAbsent(name, newFacet(name, name,
                    "/exchange/analysis-portal/dept?section=catalog&providerOrg=" + name));
        }

        for (BizCatalogItem c : published) {
            if (blank(c.getProviderOrg())) {
                continue;
            }
            String name = c.getProviderOrg().trim();
            map.putIfAbsent(name, newFacet(name, name,
                    "/exchange/analysis-portal/dept?section=catalog&providerOrg=" + name));
            bumpFacet(map.get(name), c);
        }
        return filterFacetsWithCatalog(map.values());
    }

    /** 仅保留至少有一条已发布目录的主题/部门分面。 */
    private List<Map<String, Object>> filterFacetsWithCatalog(Collection<Map<String, Object>> facets) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : facets) {
            Object raw = m.get("count");
            int count = raw instanceof Number ? ((Number) raw).intValue() : 0;
            if (count > 0) {
                out.add(m);
            }
        }
        return out;
    }

    private List<GovCatalogCategory> listThemeL1Categories() {
        return listRootL1Categories("主题资源目录", "_THEME");
    }

    private List<GovCatalogCategory> listBaseL1Categories() {
        return listRootL1Categories("基础资源目录", "_BASE");
    }

    private List<GovCatalogCategory> listRootL1Categories(String rootName, String codeSuffix) {
        List<GovCatalogCategory> all = govCategoryMapper.selectList(new LambdaQueryWrapper<GovCatalogCategory>()
                .and(w -> w.isNull(GovCatalogCategory::getStatus)
                        .or().ne(GovCatalogCategory::getStatus, "OFFLINE"))
                .orderByAsc(GovCatalogCategory::getSortOrder)
                .orderByAsc(GovCatalogCategory::getId));

        // 与归集「指标与目录体系构建 · 数据资源分类」对齐：优先只用 INGEST 根下的一级分类
        List<GovCatalogCategory> rootCandidates = new ArrayList<>();
        for (GovCatalogCategory c : all) {
            boolean isRoot = c.getParentId() == null || c.getParentId() == 0L;
            if (!isRoot) {
                continue;
            }
            String name = nz(c.getCategoryName(), "");
            String code = nz(c.getCategoryCode(), "").toUpperCase(Locale.ROOT);
            if (rootName.equals(name) || code.endsWith(codeSuffix)) {
                rootCandidates.add(c);
            }
        }
        Set<Long> rootIds = new LinkedHashSet<>();
        for (GovCatalogCategory c : rootCandidates) {
            if ("INGEST".equalsIgnoreCase(nz(c.getCatalogOrigin(), ""))) {
                rootIds.add(c.getId());
            }
        }
        if (rootIds.isEmpty()) {
            for (GovCatalogCategory c : rootCandidates) {
                rootIds.add(c.getId());
            }
        }

        List<GovCatalogCategory> l1 = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();
        for (GovCatalogCategory c : all) {
            if (c.getParentId() == null || !rootIds.contains(c.getParentId())) {
                continue;
            }
            String name = nz(c.getCategoryName(), "");
            // 根节点名称本身不作为一级库展示（避免图标条出现「基础资源目录」）
            if (name.isEmpty() || name.equals(rootName) || !seenNames.add(name)) {
                continue;
            }
            l1.add(c);
        }

        if (l1.isEmpty()) {
            String prefix = rootName + "/";
            for (GovCatalogCategory c : all) {
                if (!"INGEST".equalsIgnoreCase(nz(c.getCatalogOrigin(), "")) && !rootCandidates.isEmpty()) {
                    // 已有 INGEST 根时，路径回退也只认 INGEST
                    boolean ingestRootExists = rootCandidates.stream()
                            .anyMatch(r -> "INGEST".equalsIgnoreCase(nz(r.getCatalogOrigin(), "")));
                    if (ingestRootExists && !"INGEST".equalsIgnoreCase(nz(c.getCatalogOrigin(), ""))) {
                        continue;
                    }
                }
                String path = nz(c.getCategoryPath(), "");
                if (!path.startsWith(prefix)) {
                    continue;
                }
                String rest = path.substring(prefix.length());
                if (rest.isEmpty() || rest.contains("/")) {
                    continue;
                }
                String name = c.getCategoryName();
                if (name == null || name.equals(rootName) || !seenNames.add(name)) {
                    continue;
                }
                l1.add(c);
            }
        }
        return l1;
    }

    private Map<String, Object> newFacet(String code, String name, String route) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("route", route);
        m.put("count", 0);
        m.put("apiCount", 0);
        m.put("dataCount", 0);
        return m;
    }

    private void bumpFacet(Map<String, Object> m, BizCatalogItem c) {
        if (m == null) {
            return;
        }
        m.put("count", ((Number) m.get("count")).intValue() + 1);
        if (isApiResource(c)) {
            m.put("apiCount", ((Number) m.get("apiCount")).intValue() + 1);
        } else {
            m.put("dataCount", ((Number) m.get("dataCount")).intValue() + 1);
        }
    }

    private boolean isApiResource(BizCatalogItem c) {
        return "SERVICE".equalsIgnoreCase(nz(c.getCatalogKind(), ""))
                || (c.getShareModes() != null && c.getShareModes().toUpperCase(Locale.ROOT).contains("API"));
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private String nz(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private boolean blank(String v) {
        return v == null || v.isBlank();
    }

    private Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " required");
        }
        return v;
    }
}
