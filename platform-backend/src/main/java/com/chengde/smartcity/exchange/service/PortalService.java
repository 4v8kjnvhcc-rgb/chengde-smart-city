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
import com.chengde.smartcity.security.UserPrincipal;
import java.util.ArrayList;
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

    private final BizCatalogItemMapper catalogMapper;
    private final BizPortalSubscriptionMapper subscriptionMapper;
    private final BizPortalSituationMapper situationMapper;
    private final BizDemandSupplyTaskMapper supplyTaskMapper;
    private final AuditService auditService;
    private final IntegrationProperties integrationProperties;
    private final StorageIntegrationClient storageClient;

    public PortalService(BizCatalogItemMapper catalogMapper,
                         BizPortalSubscriptionMapper subscriptionMapper,
                         BizPortalSituationMapper situationMapper,
                         BizDemandSupplyTaskMapper supplyTaskMapper,
                         AuditService auditService,
                         IntegrationProperties integrationProperties,
                         StorageIntegrationClient storageClient) {
        this.catalogMapper = catalogMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.situationMapper = situationMapper;
        this.supplyTaskMapper = supplyTaskMapper;
        this.auditService = auditService;
        this.integrationProperties = integrationProperties;
        this.storageClient = storageClient;
    }

    public Map<String, Object> home(String keyword) {
        List<BizCatalogItem> published = publishedCatalogs(keyword, null, null, null, null);
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
                .limit(6)
                .map(this::catalogView)
                .toList();
        List<Map<String, Object>> hot = published.stream()
                .sorted(Comparator
                        .comparing((BizCatalogItem c) -> c.getHotScore() == null ? 0 : c.getHotScore()).reversed()
                        .thenComparing(BizCatalogItem::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .map(this::catalogView)
                .toList();

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
        return publishedCatalogs(keyword, themeCode, providerOrg, catalogKind, shareMode).stream()
                .map(c -> {
                    Map<String, Object> row = catalogView(c);
                    row.put("source", "database");
                    row.put("score", 1.0);
                    return row;
                }).toList();
    }

    public List<Map<String, Object>> catalogBrowse(String keyword, String themeCode, String providerOrg,
                                                   String catalogKind, String shareMode) {
        return search(keyword, themeCode, providerOrg, catalogKind, shareMode);
    }

    public List<Map<String, Object>> listSubscriptions(String status) {
        LambdaQueryWrapper<BizPortalSubscription> q = new LambdaQueryWrapper<BizPortalSubscription>()
                .orderByDesc(BizPortalSubscription::getId);
        if (status != null && !status.isBlank()) {
            q.eq(BizPortalSubscription::getStatus, status);
        }
        List<BizPortalSubscription> list = subscriptionMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (BizPortalSubscription sub : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", sub.getId());
            row.put("catalogId", sub.getCatalogId());
            row.put("applicantOrg", sub.getApplicantOrg());
            row.put("resourceType", sub.getResourceType());
            row.put("purpose", sub.getPurpose());
            row.put("status", sub.getStatus());
            row.put("approverNote", sub.getApproverNote());
            row.put("createdBy", sub.getCreatedBy());
            row.put("createdAt", sub.getCreatedAt());
            BizCatalogItem cat = catalogMapper.selectById(sub.getCatalogId());
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
        BizPortalSubscription sub = new BizPortalSubscription();
        sub.setCatalogId(catalogId);
        sub.setApplicantOrg(str(body.get("applicantOrg"), "机构" + operator.getOrgId()));
        sub.setResourceType(resourceType);
        sub.setPurpose(str(body.get("purpose"), ""));
        sub.setStatus("PENDING");
        sub.setCreatedBy(operator.getUsername());
        subscriptionMapper.insert(sub);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "PORTAL_SUBSCRIBE", "biz_portal_subscription", String.valueOf(sub.getId()), catalog.getTitle());
        return sub.getId();
    }

    @Transactional
    public Map<String, Object> reviewSubscription(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizPortalSubscription sub = subscriptionMapper.selectById(id);
        if (sub == null) {
            throw new BusinessException(404, "订阅申请不存在");
        }
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

    public List<BizPortalSituation> listSituations() {
        return situationMapper.selectList(new LambdaQueryWrapper<BizPortalSituation>()
                .orderByAsc(BizPortalSituation::getSortOrder));
    }

    @Transactional
    public Map<String, Object> syncSearchIndex(UserPrincipal operator) {
        List<BizCatalogItem> items = publishedCatalogs(null, null, null, null, null);
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

    private List<BizCatalogItem> publishedCatalogs(String keyword, String themeCode, String providerOrg,
                                                   String catalogKind, String shareMode) {
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
                    .or().like(BizCatalogItem::getProviderOrg, keyword));
        }
        if (!blank(themeCode)) {
            q.eq(BizCatalogItem::getThemeCode, themeCode);
        }
        if (!blank(providerOrg)) {
            q.eq(BizCatalogItem::getProviderOrg, providerOrg);
        }
        if (!blank(catalogKind)) {
            q.eq(BizCatalogItem::getCatalogKind, catalogKind.toUpperCase(Locale.ROOT));
        }
        if (!blank(shareMode)) {
            q.like(BizCatalogItem::getShareModes, shareMode.toUpperCase(Locale.ROOT));
        }
        return catalogMapper.selectList(q);
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
        row.put("providerOrg", c.getProviderOrg());
        row.put("shareModes", c.getShareModes());
        row.put("resourceCount", c.getResourceCount() == null ? 0 : c.getResourceCount());
        row.put("hotScore", c.getHotScore() == null ? 0 : c.getHotScore());
        row.put("publishedAt", c.getPublishedAt());
        row.put("publishStatus", c.getPublishStatus());
        row.put("source", "database");
        row.put("previewItems", previewItems(c));
        return row;
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

    private List<Map<String, Object>> buildThemes(List<BizCatalogItem> published) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (BizCatalogItem c : published) {
            if (blank(c.getThemeCode())) continue;
            map.computeIfAbsent(c.getThemeCode(), code -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", code);
                m.put("name", nz(c.getThemeName(), code));
                m.put("route", "/exchange/application?system=portal&section=catalog&themeCode=" + code);
                m.put("count", 0);
                m.put("apiCount", 0);
                m.put("dataCount", 0);
                return m;
            });
            Map<String, Object> m = map.get(c.getThemeCode());
            m.put("count", ((Number) m.get("count")).intValue() + 1);
            if (isApiResource(c)) {
                m.put("apiCount", ((Number) m.get("apiCount")).intValue() + 1);
            } else {
                m.put("dataCount", ((Number) m.get("dataCount")).intValue() + 1);
            }
        }
        return new ArrayList<>(map.values());
    }

    private List<Map<String, Object>> buildProviders(List<BizCatalogItem> published) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (BizCatalogItem c : published) {
            if (blank(c.getProviderOrg())) continue;
            map.computeIfAbsent(c.getProviderOrg(), name -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", name);
                m.put("count", 0);
                m.put("apiCount", 0);
                m.put("dataCount", 0);
                return m;
            });
            Map<String, Object> m = map.get(c.getProviderOrg());
            m.put("count", ((Number) m.get("count")).intValue() + 1);
            if (isApiResource(c)) {
                m.put("apiCount", ((Number) m.get("apiCount")).intValue() + 1);
            } else {
                m.put("dataCount", ((Number) m.get("dataCount")).intValue() + 1);
            }
        }
        return new ArrayList<>(map.values());
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
