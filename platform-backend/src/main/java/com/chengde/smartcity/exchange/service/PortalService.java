package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.BizPortalSituation;
import com.chengde.smartcity.exchange.entity.BizPortalSubscription;
import com.chengde.smartcity.exchange.mapper.BizCatalogItemMapper;
import com.chengde.smartcity.exchange.mapper.BizPortalSituationMapper;
import com.chengde.smartcity.exchange.mapper.BizPortalSubscriptionMapper;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.storage.StorageIntegrationClient;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalService {

    private static final String ES_INDEX = "smartcity_catalog";

    private final BizCatalogItemMapper catalogMapper;
    private final BizPortalSubscriptionMapper subscriptionMapper;
    private final BizPortalSituationMapper situationMapper;
    private final AuditService auditService;
    private final IntegrationProperties integrationProperties;
    private final StorageIntegrationClient storageClient;

    public PortalService(BizCatalogItemMapper catalogMapper, BizPortalSubscriptionMapper subscriptionMapper,
                         BizPortalSituationMapper situationMapper, AuditService auditService,
                         IntegrationProperties integrationProperties, StorageIntegrationClient storageClient) {
        this.catalogMapper = catalogMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.situationMapper = situationMapper;
        this.auditService = auditService;
        this.integrationProperties = integrationProperties;
        this.storageClient = storageClient;
    }

    public Map<String, Object> home(String keyword) {
        List<BizCatalogItem> published = publishedCatalogs(keyword);
        long subTotal = subscriptionMapper.selectCount(null);
        long subPending = subscriptionMapper.selectCount(new LambdaQueryWrapper<BizPortalSubscription>()
                .eq(BizPortalSubscription::getStatus, "PENDING"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("publishedCount", published.size());
        out.put("subscriptionTotal", subTotal);
        out.put("subscriptionPending", subPending);
        out.put("situationCount", situationMapper.selectCount(null));
        out.put("searchKeyword", keyword == null ? "" : keyword);
        out.put("recommendations", published.stream().limit(6).map(this::catalogView).toList());
        out.put("themes", List.of(
                Map.of("code", "POPULATION", "name", "人口主题", "route", "/analytics/population"),
                Map.of("code", "LEGAL", "name", "法人主题", "route", "/analytics/legal-entity"),
                Map.of("code", "ECONOMY", "name", "经济主题", "route", "/analytics/macro")
        ));
        return out;
    }

    public List<Map<String, Object>> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return publishedCatalogs(null).stream().map(this::catalogView).toList();
        }
        List<Map<String, Object>> esHits = storageClient.searchCatalog(keyword, 50);
        if (!esHits.isEmpty()) {
            return esHits;
        }
        return publishedCatalogs(keyword).stream().map(c -> {
            Map<String, Object> row = catalogView(c);
            row.put("source", "database");
            row.put("score", 1.0);
            return row;
        }).toList();
    }

    public List<Map<String, Object>> catalogBrowse(String keyword) {
        return search(keyword);
    }

    public List<BizPortalSubscription> listSubscriptions(String status) {
        LambdaQueryWrapper<BizPortalSubscription> q = new LambdaQueryWrapper<BizPortalSubscription>()
                .orderByDesc(BizPortalSubscription::getId);
        if (status != null && !status.isBlank()) {
            q.eq(BizPortalSubscription::getStatus, status);
        }
        return subscriptionMapper.selectList(q);
    }

    @Transactional
    public Long createSubscription(UserPrincipal operator, Map<String, Object> body) {
        Long catalogId = Long.valueOf(String.valueOf(required(body.get("catalogId"), "catalogId")));
        BizCatalogItem catalog = catalogMapper.selectById(catalogId);
        if (catalog == null || !"PUBLISHED".equals(catalog.getPublishStatus())) {
            throw new BusinessException(400, "仅可订阅已发布目录");
        }
        BizPortalSubscription sub = new BizPortalSubscription();
        sub.setCatalogId(catalogId);
        sub.setApplicantOrg(str(body.get("applicantOrg"), "机构" + operator.getOrgId()));
        sub.setResourceType(str(body.get("resourceType"), "TABLE"));
        sub.setPurpose(str(body.get("purpose"), ""));
        sub.setStatus("PENDING");
        sub.setCreatedBy(operator.getUsername());
        subscriptionMapper.insert(sub);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "PORTAL_SUBSCRIBE", "biz_portal_subscription", String.valueOf(sub.getId()), catalog.getTitle());
        return sub.getId();
    }

    @Transactional
    public void reviewSubscription(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizPortalSubscription sub = subscriptionMapper.selectById(id);
        if (sub == null) {
            throw new BusinessException(404, "订阅申请不存在");
        }
        String action = str(body.get("action"), "APPROVE").toUpperCase();
        sub.setStatus("APPROVE".equals(action) || "APPROVED".equals(action) ? "APPROVED" : "REJECTED");
        sub.setApproverNote(str(body.get("approverNote"), ""));
        subscriptionMapper.updateById(sub);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "PORTAL_SUB_REVIEW", "biz_portal_subscription", String.valueOf(id), sub.getStatus());
    }

    public List<BizPortalSituation> listSituations() {
        return situationMapper.selectList(new LambdaQueryWrapper<BizPortalSituation>()
                .orderByAsc(BizPortalSituation::getSortOrder));
    }

    @Transactional
    public Map<String, Object> syncSearchIndex(UserPrincipal operator) {
        List<BizCatalogItem> items = publishedCatalogs(null);
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

    private List<BizCatalogItem> publishedCatalogs(String keyword) {
        LambdaQueryWrapper<BizCatalogItem> q = new LambdaQueryWrapper<BizCatalogItem>()
                .eq(BizCatalogItem::getPublishStatus, "PUBLISHED")
                .orderByDesc(BizCatalogItem::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(BizCatalogItem::getTitle, keyword)
                    .or().like(BizCatalogItem::getDescription, keyword)
                    .or().like(BizCatalogItem::getCatalogCode, keyword));
        }
        return catalogMapper.selectList(q);
    }

    private Map<String, Object> catalogView(BizCatalogItem c) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", c.getId());
        row.put("catalogCode", c.getCatalogCode());
        row.put("title", c.getTitle());
        row.put("description", c.getDescription());
        row.put("publishStatus", c.getPublishStatus());
        row.put("source", "database");
        return row;
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
}
