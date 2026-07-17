package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovCatalogAuthorization;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovCatalogSubscription;
import com.chengde.smartcity.masterdata.mapper.GovCatalogAuthorizationMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogSubscriptionMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(CatalogSubscriptionService.class);
    private static final Set<String> SHARE_MODES = Set.of("DB_SYNC", "FILE_SYNC", "API");

    private final GovCatalogSubscriptionMapper subscriptionMapper;
    private final GovCatalogResourceMapper resourceMapper;
    private final GovCatalogAuthorizationMapper authorizationMapper;
    private final AuditService auditService;

    public CatalogSubscriptionService(GovCatalogSubscriptionMapper subscriptionMapper,
                                      GovCatalogResourceMapper resourceMapper,
                                      GovCatalogAuthorizationMapper authorizationMapper,
                                      AuditService auditService) {
        this.subscriptionMapper = subscriptionMapper;
        this.resourceMapper = resourceMapper;
        this.authorizationMapper = authorizationMapper;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> listMine(UserPrincipal operator, String status) {
        LambdaQueryWrapper<GovCatalogSubscription> q = new LambdaQueryWrapper<GovCatalogSubscription>()
                .orderByDesc(GovCatalogSubscription::getId);
        if (operator != null && operator.getUsername() != null) {
            q.eq(GovCatalogSubscription::getApplicantUser, operator.getUsername());
        }
        if (status != null && !status.isBlank()) {
            q.eq(GovCatalogSubscription::getStatus, status);
        }
        return toRows(subscriptionMapper.selectList(q));
    }

    public List<Map<String, Object>> listPending() {
        List<GovCatalogSubscription> list = subscriptionMapper.selectList(
                new LambdaQueryWrapper<GovCatalogSubscription>()
                        .eq(GovCatalogSubscription::getStatus, "PENDING")
                        .orderByDesc(GovCatalogSubscription::getId));
        return toRows(list);
    }

    public Map<String, Object> get(Long id) {
        return toRow(require(id));
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        Long resourceId = Long.valueOf(String.valueOf(required(body.get("resourceId"), "resourceId")));
        GovCatalogResource resource = requireResource(resourceId);
        if (!"PUBLISHED".equalsIgnoreCase(resource.getPublishStatus())) {
            throw new BusinessException(400, "仅已发布资源可订阅");
        }
        String shareMode = str(body.get("shareMode"), "DB_SYNC").toUpperCase();
        if (!SHARE_MODES.contains(shareMode)) {
            throw new BusinessException(400, "shareMode 须为 DB_SYNC/FILE_SYNC/API");
        }

        long pending = subscriptionMapper.selectCount(new LambdaQueryWrapper<GovCatalogSubscription>()
                .eq(GovCatalogSubscription::getResourceId, resourceId)
                .eq(GovCatalogSubscription::getApplicantUser, operator.getUsername())
                .eq(GovCatalogSubscription::getStatus, "PENDING"));
        if (pending > 0) {
            throw new BusinessException(400, "该资源已有待审批申请");
        }

        GovCatalogSubscription sub = new GovCatalogSubscription();
        sub.setResourceId(resourceId);
        sub.setApplicantOrg(str(body.get("applicantOrg"), "机构" + operator.getOrgId()));
        sub.setApplicantUser(operator.getUsername());
        sub.setShareMode(shareMode);
        sub.setPurpose(str(body.get("purpose"), ""));
        sub.setStatus("PENDING");
        sub.setCreatedAt(LocalDateTime.now());
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.insert(sub);

        resource.setSubscriptionStatus("PENDING");
        resourceMapper.updateById(resource);

        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "CATALOG_SUBSCRIBE_APPLY", "gov_catalog_subscription",
                String.valueOf(sub.getId()), resource.getResourceName());
        log.info("catalog subscription created id={} resourceId={} mode={}", sub.getId(), resourceId, shareMode);
        return sub.getId();
    }

    @Transactional
    public Map<String, Object> approve(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovCatalogSubscription sub = require(id);
        if (!"PENDING".equalsIgnoreCase(sub.getStatus())) {
            throw new BusinessException(400, "仅待处理申请可通过");
        }
        sub.setStatus("APPROVED");
        sub.setReviewComment(str(body.get("comment"), "同意"));
        sub.setReviewedBy(operator.getUsername());
        sub.setReviewedAt(LocalDateTime.now());
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(sub);

        GovCatalogResource resource = requireResource(sub.getResourceId());
        resource.setSubscriptionStatus("SUBSCRIBED");
        resourceMapper.updateById(resource);

        GovCatalogAuthorization authorization = ensureAuthorization(operator, sub);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "CATALOG_SUBSCRIBE_APPROVE", "gov_catalog_subscription",
                String.valueOf(id), resource.getResourceName() + " auth=" + authorization.getAuthorizationCode());
        Map<String, Object> out = toRow(sub);
        out.put("authorization", toAuthorizationRow(authorization));
        return out;
    }

    @Transactional
    public Map<String, Object> reject(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovCatalogSubscription sub = require(id);
        if (!"PENDING".equalsIgnoreCase(sub.getStatus())) {
            throw new BusinessException(400, "仅待处理申请可驳回");
        }
        String comment = str(body.get("comment"), null);
        if (comment == null || comment.isBlank()) {
            throw new BusinessException(400, "驳回须填写审批意见");
        }
        sub.setStatus("REJECTED");
        sub.setReviewComment(comment);
        sub.setReviewedBy(operator.getUsername());
        sub.setReviewedAt(LocalDateTime.now());
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(sub);

        GovCatalogResource resource = requireResource(sub.getResourceId());
        resource.setSubscriptionStatus("REJECTED");
        resourceMapper.updateById(resource);

        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "CATALOG_SUBSCRIBE_REJECT", "gov_catalog_subscription",
                String.valueOf(id), resource.getResourceName());
        return toRow(sub);
    }

    @Transactional
    public Map<String, Object> cancel(UserPrincipal operator, Long id) {
        GovCatalogSubscription sub = require(id);
        if (!"PENDING".equalsIgnoreCase(sub.getStatus())) {
            throw new BusinessException(400, "仅待处理申请可取消");
        }
        if (operator != null && !operator.getUsername().equals(sub.getApplicantUser())) {
            throw new BusinessException(403, "仅申请人可取消");
        }
        sub.setStatus("CANCELLED");
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(sub);

        GovCatalogResource resource = requireResource(sub.getResourceId());
        resource.setSubscriptionStatus("CANCELLED");
        resourceMapper.updateById(resource);
        return toRow(sub);
    }

    @Transactional
    public Map<String, Object> distribute(UserPrincipal operator, Long id) {
        GovCatalogSubscription sub = require(id);
        if (!"APPROVED".equalsIgnoreCase(sub.getStatus()) && !"DISTRIBUTED".equalsIgnoreCase(sub.getStatus())) {
            throw new BusinessException(400, "仅已通过或已分发的订阅可触发分发");
        }
        GovCatalogResource resource = requireResource(sub.getResourceId());
        String mode = sub.getShareMode() == null ? "DB_SYNC" : sub.getShareMode().toUpperCase();
        String traceId = "GOV-" + UUID.randomUUID().toString().substring(0, 8);
        String result;
        if ("API".equals(mode)) {
            String debugEntry = "/api/v1/governance/catalog/subscriptions/" + id + "/test-api";
            result = "API 调试入口已就绪 entry=" + debugEntry + " traceId=" + traceId;
            log.info("catalog distribute API-only id={} entry={}", id, debugEntry);
        } else if ("FILE_SYNC".equals(mode)) {
            // L1：仅写分发台账，不真实搬文件
            result = "FILE_SYNC 已记分发台账（未真实传文件） resource=" + resource.getResourceCode()
                    + " traceId=" + traceId;
            log.info("catalog distribute FILE_SYNC log-only id={} resource={}", id, resource.getResourceCode());
        } else {
            // DB_SYNC：写日志不真搬数
            result = "DB_SYNC 已记分发台账（未真实搬数） resource=" + resource.getResourceCode()
                    + " traceId=" + traceId;
            log.info("catalog distribute DB_SYNC log-only id={} resource={}", id, resource.getResourceCode());
        }

        sub.setStatus("DISTRIBUTED");
        sub.setDistributeResult(result);
        sub.setDistributeAt(LocalDateTime.now());
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(sub);

        resource.setSubscriptionStatus("DISTRIBUTED");
        resourceMapper.updateById(resource);

        if (operator != null) {
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "CATALOG_DISTRIBUTE", "gov_catalog_subscription", String.valueOf(id), result);
        }

        Map<String, Object> out = toRow(sub);
        out.put("traceId", traceId);
        if ("API".equals(mode)) {
            out.put("testApi", "/api/v1/governance/catalog/subscriptions/" + id + "/test-api");
        }
        return out;
    }

    public Map<String, Object> distributeResult(Long id) {
        GovCatalogSubscription sub = require(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", sub.getId());
        out.put("status", sub.getStatus());
        out.put("shareMode", sub.getShareMode());
        out.put("distributeResult", sub.getDistributeResult());
        out.put("distributeAt", sub.getDistributeAt());
        GovCatalogAuthorization authorization = findAuthorization(id);
        out.put("authorization", authorization == null ? null : toAuthorizationRow(authorization));
        return out;
    }

    public Map<String, Object> authorization(Long subscriptionId) {
        require(subscriptionId);
        GovCatalogAuthorization authorization = findAuthorization(subscriptionId);
        if (authorization == null) {
            throw new BusinessException(404, "该订阅尚未生成授权记录");
        }
        return toAuthorizationRow(authorization);
    }

    public Map<String, Object> testApi(Long id) {
        GovCatalogSubscription sub = require(id);
        if (!"API".equalsIgnoreCase(sub.getShareMode())) {
            throw new BusinessException(400, "仅 API 共享方式可调用测试接口");
        }
        if (!"APPROVED".equalsIgnoreCase(sub.getStatus()) && !"DISTRIBUTED".equalsIgnoreCase(sub.getStatus())) {
            throw new BusinessException(400, "订阅须已通过或已分发");
        }
        GovCatalogResource resource = requireResource(sub.getResourceId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("subscriptionId", id);
        out.put("resourceCode", resource.getResourceCode());
        out.put("resourceName", resource.getResourceName());
        out.put("message", "API 调试入口响应（L1 模拟，无真实下游调用）");
        out.put("sampledAt", LocalDateTime.now().toString());
        out.put("sampleRows", List.of(
                Map.of("id", 1, "name", "示例记录A"),
                Map.of("id", 2, "name", "示例记录B")
        ));
        return out;
    }

    private List<Map<String, Object>> toRows(List<GovCatalogSubscription> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovCatalogSubscription sub : list) {
            out.add(toRow(sub));
        }
        return out;
    }

    private Map<String, Object> toRow(GovCatalogSubscription sub) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", sub.getId());
        row.put("resourceId", sub.getResourceId());
        row.put("applicantOrg", sub.getApplicantOrg());
        row.put("applicantUser", sub.getApplicantUser());
        row.put("shareMode", sub.getShareMode());
        row.put("purpose", sub.getPurpose());
        row.put("status", sub.getStatus());
        row.put("reviewComment", sub.getReviewComment());
        row.put("reviewedBy", sub.getReviewedBy());
        row.put("reviewedAt", sub.getReviewedAt());
        row.put("distributeResult", sub.getDistributeResult());
        row.put("distributeAt", sub.getDistributeAt());
        row.put("createdAt", sub.getCreatedAt());
        row.put("updatedAt", sub.getUpdatedAt());
        GovCatalogResource resource = resourceMapper.selectById(sub.getResourceId());
        if (resource != null) {
            row.put("resourceCode", resource.getResourceCode());
            row.put("resourceName", resource.getResourceName());
            row.put("resourceType", resource.getResourceType());
            row.put("providerOrg", resource.getProviderOrg());
            row.put("publishStatus", resource.getPublishStatus());
        }
        GovCatalogAuthorization authorization = findAuthorization(sub.getId());
        row.put("authorization", authorization == null ? null : toAuthorizationRow(authorization));
        return row;
    }

    private GovCatalogAuthorization ensureAuthorization(UserPrincipal operator, GovCatalogSubscription sub) {
        GovCatalogAuthorization authorization = findAuthorization(sub.getId());
        if (authorization == null) {
            LocalDateTime now = LocalDateTime.now();
            authorization = new GovCatalogAuthorization();
            authorization.setAuthorizationCode("AUTH-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
            authorization.setSubscriptionId(sub.getId());
            authorization.setResourceId(sub.getResourceId());
            authorization.setGranteeOrg(sub.getApplicantOrg());
            authorization.setGranteeUser(sub.getApplicantUser());
            authorization.setShareMode(sub.getShareMode());
            authorization.setStatus("ACTIVE");
            authorization.setCredentialRef("LOCAL://" + authorization.getAuthorizationCode());
            authorization.setValidFrom(now);
            authorization.setCreatedBy(operator == null ? "system" : operator.getUsername());
            authorization.setCreatedAt(now);
            authorization.setUpdatedAt(now);
            authorizationMapper.insert(authorization);
        } else if (!"ACTIVE".equalsIgnoreCase(authorization.getStatus())) {
            authorization.setStatus("ACTIVE");
            authorization.setUpdatedAt(LocalDateTime.now());
            authorizationMapper.updateById(authorization);
        }
        return authorization;
    }

    private GovCatalogAuthorization findAuthorization(Long subscriptionId) {
        return authorizationMapper.selectOne(new LambdaQueryWrapper<GovCatalogAuthorization>()
                .eq(GovCatalogAuthorization::getSubscriptionId, subscriptionId)
                .last("LIMIT 1"));
    }

    private Map<String, Object> toAuthorizationRow(GovCatalogAuthorization authorization) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", authorization.getId());
        row.put("authorizationCode", authorization.getAuthorizationCode());
        row.put("subscriptionId", authorization.getSubscriptionId());
        row.put("resourceId", authorization.getResourceId());
        row.put("granteeOrg", authorization.getGranteeOrg());
        row.put("granteeUser", authorization.getGranteeUser());
        row.put("shareMode", authorization.getShareMode());
        row.put("status", authorization.getStatus());
        row.put("credentialRef", authorization.getCredentialRef());
        row.put("validFrom", authorization.getValidFrom());
        row.put("validUntil", authorization.getValidUntil());
        row.put("createdAt", authorization.getCreatedAt());
        return row;
    }

    private GovCatalogSubscription require(Long id) {
        GovCatalogSubscription sub = subscriptionMapper.selectById(id);
        if (sub == null) {
            throw new BusinessException(404, "订阅申请不存在");
        }
        return sub;
    }

    private GovCatalogResource requireResource(Long id) {
        GovCatalogResource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException(404, "资源不存在");
        }
        return resource;
    }

    private Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " required");
        }
        return v;
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }
}
