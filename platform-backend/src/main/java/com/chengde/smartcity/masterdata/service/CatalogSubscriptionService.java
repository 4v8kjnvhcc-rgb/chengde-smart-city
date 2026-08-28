package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.BizPortalSubscription;
import com.chengde.smartcity.exchange.mapper.BizCatalogItemMapper;
import com.chengde.smartcity.exchange.mapper.BizPortalSubscriptionMapper;
import com.chengde.smartcity.integration.esb.EsbConsumerProvisionService;
import com.chengde.smartcity.masterdata.entity.GovCatalogAuthorization;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovCatalogSubscription;
import com.chengde.smartcity.masterdata.mapper.GovCatalogAuthorizationMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogSubscriptionMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(CatalogSubscriptionService.class);
    private static final ObjectMapper OM = new ObjectMapper();
    private static final Set<String> SHARE_MODES = Set.of("DB_SYNC", "FILE_SYNC", "API");

    private final GovCatalogSubscriptionMapper subscriptionMapper;
    private final GovCatalogResourceMapper resourceMapper;
    private final GovCatalogAuthorizationMapper authorizationMapper;
    private final BizPortalSubscriptionMapper portalSubscriptionMapper;
    private final BizCatalogItemMapper portalCatalogMapper;
    private final AuditService auditService;
    private final SysOrgMapper orgMapper;
    private final EsbConsumerProvisionService esbConsumerProvisionService;
    private final CatalogSubscribeDistributeService subscribeDistributeService;

    public CatalogSubscriptionService(GovCatalogSubscriptionMapper subscriptionMapper,
                                      GovCatalogResourceMapper resourceMapper,
                                      GovCatalogAuthorizationMapper authorizationMapper,
                                      BizPortalSubscriptionMapper portalSubscriptionMapper,
                                      BizCatalogItemMapper portalCatalogMapper,
                                      AuditService auditService,
                                      SysOrgMapper orgMapper,
                                      EsbConsumerProvisionService esbConsumerProvisionService,
                                      CatalogSubscribeDistributeService subscribeDistributeService) {
        this.subscriptionMapper = subscriptionMapper;
        this.resourceMapper = resourceMapper;
        this.authorizationMapper = authorizationMapper;
        this.portalSubscriptionMapper = portalSubscriptionMapper;
        this.portalCatalogMapper = portalCatalogMapper;
        this.auditService = auditService;
        this.orgMapper = orgMapper;
        this.esbConsumerProvisionService = esbConsumerProvisionService;
        this.subscribeDistributeService = subscribeDistributeService;
    }

    public List<Map<String, Object>> listMine(UserPrincipal operator, String status) {
        LambdaQueryWrapper<GovCatalogSubscription> q = new LambdaQueryWrapper<GovCatalogSubscription>()
                .orderByDesc(GovCatalogSubscription::getId);
        if (status != null && !status.isBlank()) {
            q.eq(GovCatalogSubscription::getStatus, status);
        }
        List<GovCatalogSubscription> list = subscriptionMapper.selectList(q);
        if (operator == null || operator.isSystemAdmin()) {
            return toRows(list);
        }
        String myOrg = resolveOrgName(operator);
        String username = operator.getUsername();
        List<GovCatalogSubscription> filtered = new ArrayList<>();
        for (GovCatalogSubscription sub : list) {
            boolean sameDept = myOrg != null && !myOrg.isBlank()
                    && myOrg.trim().equals(nz(sub.getApplicantOrg()).trim());
            boolean self = username != null && username.equals(sub.getApplicantUser());
            if (sameDept || self) {
                filtered.add(sub);
            }
        }
        return toRows(filtered);
    }

    public List<Map<String, Object>> listPending() {
        return listPending(null);
    }

    public List<Map<String, Object>> listPending(UserPrincipal operator) {
        List<GovCatalogSubscription> list = subscriptionMapper.selectList(
                new LambdaQueryWrapper<GovCatalogSubscription>()
                        .eq(GovCatalogSubscription::getStatus, "PENDING")
                        .orderByDesc(GovCatalogSubscription::getId));
        if (operator == null || operator.isSystemAdmin()) {
            return toRows(list);
        }
        String myOrg = resolveOrgName(operator);
        if (myOrg == null || myOrg.isBlank()) {
            return List.of();
        }
        List<GovCatalogSubscription> filtered = new ArrayList<>();
        for (GovCatalogSubscription sub : list) {
            if (operator.getUsername() != null && operator.getUsername().equals(sub.getApplicantUser())) {
                continue;
            }
            if (myOrg.trim().equals(nz(sub.getApplicantOrg()).trim())) {
                continue;
            }
            GovCatalogResource resource = resourceMapper.selectById(sub.getResourceId());
            if (resource != null && myOrg.trim().equals(nz(resource.getProviderOrg()).trim())) {
                filtered.add(sub);
            }
        }
        return toRows(filtered);
    }

    /**
     * 已审批：本部门作为提供方的历史审批，按审批时间倒序。
     */
    public List<Map<String, Object>> listReviewed(UserPrincipal operator) {
        List<GovCatalogSubscription> list = subscriptionMapper.selectList(
                new LambdaQueryWrapper<GovCatalogSubscription>()
                        .in(GovCatalogSubscription::getStatus, List.of("APPROVED", "REJECTED", "DISTRIBUTED"))
                        .orderByDesc(GovCatalogSubscription::getReviewedAt)
                        .orderByDesc(GovCatalogSubscription::getId));
        if (operator == null || operator.isSystemAdmin()) {
            return toRows(list);
        }
        String myOrg = resolveOrgName(operator);
        if (myOrg == null || myOrg.isBlank()) {
            return List.of();
        }
        List<GovCatalogSubscription> filtered = new ArrayList<>();
        for (GovCatalogSubscription sub : list) {
            GovCatalogResource resource = resourceMapper.selectById(sub.getResourceId());
            if (resource != null && myOrg.trim().equals(nz(resource.getProviderOrg()).trim())) {
                filtered.add(sub);
            }
        }
        filtered.sort(Comparator
                .comparing(GovCatalogSubscription::getReviewedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(GovCatalogSubscription::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return toRows(filtered);
    }

    private String resolveOrgName(UserPrincipal operator) {
        if (operator == null || operator.getOrgId() == null) {
            return null;
        }
        SysOrg org = orgMapper.selectById(operator.getOrgId());
        return org == null ? null : org.getOrgName();
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }

    private void assertProviderCanReview(UserPrincipal operator, GovCatalogSubscription sub) {
        if (operator != null && operator.isSystemAdmin()) {
            return;
        }
        if (operator == null) {
            throw new BusinessException(403, "未登录，无法审批");
        }
        String myOrg = resolveOrgName(operator);
        GovCatalogResource resource = resourceMapper.selectById(sub.getResourceId());
        String providerOrg = resource == null ? null : resource.getProviderOrg();
        if (myOrg == null || myOrg.isBlank()
                || providerOrg == null || !myOrg.trim().equals(providerOrg.trim())) {
            throw new BusinessException(403, "仅资源提供方部门可审批该申请");
        }
        if ((myOrg.trim().equals(nz(sub.getApplicantOrg()).trim()))
                || Objects.equals(operator.getUsername(), sub.getApplicantUser())) {
            throw new BusinessException(403, "不能审批本部门自己提交的申请");
        }
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

        Long portalSubscriptionId = body.get("portalSubscriptionId") == null
                ? null
                : Long.valueOf(String.valueOf(body.get("portalSubscriptionId")));

        String myOrg = resolveOrgName(operator);
        if ((myOrg == null || myOrg.isBlank()) && !operator.isSystemAdmin()) {
            throw new BusinessException(400, "当前账号未绑定所属部门，无法提交申请");
        }
        String applicantOrg = (myOrg == null || myOrg.isBlank())
                ? str(body.get("applicantOrg"), "系统管理员")
                : myOrg;

        // 已申请（待审/已通过/已分发）不可再次申请；门户同步入口可补链已有待审单
        final String orgKey = applicantOrg;
        final String userKey = operator.getUsername();
        GovCatalogSubscription existing = subscriptionMapper.selectOne(new LambdaQueryWrapper<GovCatalogSubscription>()
                .eq(GovCatalogSubscription::getResourceId, resourceId)
                .in(GovCatalogSubscription::getStatus, List.of("PENDING", "APPROVED", "DISTRIBUTED"))
                .and(w -> {
                    if (userKey != null && !userKey.isBlank()) {
                        w.eq(GovCatalogSubscription::getApplicantUser, userKey);
                    }
                    if (orgKey != null && !orgKey.isBlank()) {
                        if (userKey != null && !userKey.isBlank()) {
                            w.or().eq(GovCatalogSubscription::getApplicantOrg, orgKey);
                        } else {
                            w.eq(GovCatalogSubscription::getApplicantOrg, orgKey);
                        }
                    }
                })
                .orderByDesc(GovCatalogSubscription::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            if (portalSubscriptionId != null && "PENDING".equalsIgnoreCase(existing.getStatus())
                    && existing.getPortalSubscriptionId() == null) {
                existing.setPortalSubscriptionId(portalSubscriptionId);
                existing.setUpdatedAt(LocalDateTime.now());
                subscriptionMapper.updateById(existing);
                linkPortalGovIds(portalSubscriptionId, existing.getId());
                return existing.getId();
            }
            if (portalSubscriptionId != null && existing.getPortalSubscriptionId() != null) {
                return existing.getId();
            }
            throw new BusinessException(400, "该目录已申请，不能再次申请");
        }

        GovCatalogSubscription sub = new GovCatalogSubscription();
        sub.setResourceId(resourceId);
        sub.setApplicantOrg(applicantOrg);
        sub.setApplicantUser(operator.getUsername());
        sub.setShareMode(shareMode);
        sub.setPurpose(str(body.get("purpose"), ""));
        if (body.get("applyPayload") != null) {
            Object ap = body.get("applyPayload");
            sub.setApplyPayload(ap == null ? null : String.valueOf(ap));
        }
        sub.setPortalSubscriptionId(portalSubscriptionId);
        sub.setStatus("PENDING");
        sub.setCreatedAt(LocalDateTime.now());
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.insert(sub);

        resource.setSubscriptionStatus("PENDING");
        resourceMapper.updateById(resource);

        if (portalSubscriptionId != null) {
            linkPortalGovIds(portalSubscriptionId, sub.getId());
        } else {
            // 治理侧直申：同步写入门户申请表，保证双入口同数据
            // 库表/接口 ESB 仅在审核通过时调用（提交/拒绝不调用）
            ensurePortalMirror(operator, sub, resource, applicantOrg);
        }

        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "CATALOG_SUBSCRIBE_APPLY", "gov_catalog_subscription",
                String.valueOf(sub.getId()), resource.getResourceName());
        log.info("catalog subscription created id={} resourceId={} mode={}", sub.getId(), resourceId, shareMode);
        return sub.getId();
    }

    /** 门户审批结果回写治理侧（同一业务单）。 */
    @Transactional
    public void syncReviewFromPortal(Long portalSubscriptionId, String status, String comment,
                                     String reviewedBy, LocalDateTime reviewedAt) {
        syncReviewFromPortal(portalSubscriptionId, status, comment, reviewedBy, reviewedAt, null);
    }

    public void syncReviewFromPortal(Long portalSubscriptionId, String status, String comment,
                                     String reviewedBy, LocalDateTime reviewedAt, String reviewerContact) {
        if (portalSubscriptionId == null || status == null) {
            return;
        }
        GovCatalogSubscription sub = subscriptionMapper.selectOne(new LambdaQueryWrapper<GovCatalogSubscription>()
                .eq(GovCatalogSubscription::getPortalSubscriptionId, portalSubscriptionId)
                .last("LIMIT 1"));
        BizPortalSubscription portal = portalSubscriptionMapper.selectById(portalSubscriptionId);
        if (sub == null && portal != null && portal.getGovSubscriptionId() != null) {
            sub = subscriptionMapper.selectById(portal.getGovSubscriptionId());
        }
        if (sub == null && portal != null) {
            BizCatalogItem cat = portalCatalogMapper.selectById(portal.getCatalogId());
            if (cat != null && cat.getGovResourceId() != null) {
                sub = subscriptionMapper.selectOne(new LambdaQueryWrapper<GovCatalogSubscription>()
                        .eq(GovCatalogSubscription::getResourceId, cat.getGovResourceId())
                        .eq(GovCatalogSubscription::getApplicantUser, portal.getCreatedBy())
                        .eq(GovCatalogSubscription::getStatus, "PENDING")
                        .orderByDesc(GovCatalogSubscription::getId)
                        .last("LIMIT 1"));
            }
        }
        if (sub == null || !"PENDING".equalsIgnoreCase(sub.getStatus())) {
            return;
        }
        if (sub.getPortalSubscriptionId() == null) {
            sub.setPortalSubscriptionId(portalSubscriptionId);
        }
        String st = status.toUpperCase(Locale.ROOT);
        sub.setStatus(st);
        sub.setReviewComment(comment);
        sub.setReviewedBy(reviewedBy);
        if (reviewerContact != null && !reviewerContact.isBlank()) {
            sub.setReviewerContact(reviewerContact.trim());
        }
        sub.setReviewedAt(reviewedAt == null ? LocalDateTime.now() : reviewedAt);
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(sub);

        GovCatalogResource resource = resourceMapper.selectById(sub.getResourceId());
        if (resource != null) {
            if ("APPROVED".equals(st)) {
                resource.setSubscriptionStatus("SUBSCRIBED");
            } else if ("REJECTED".equals(st)) {
                resource.setSubscriptionStatus("REJECTED");
            } else if ("CANCELLED".equals(st)) {
                resource.setSubscriptionStatus("CANCELLED");
            }
            resourceMapper.updateById(resource);
        }
        if ("APPROVED".equals(st)) {
            ensureAuthorization(null, sub);
            subscribeDistributeService.onSubscriptionApproved(null, sub);
        }
    }

    @Transactional
    public Map<String, Object> approve(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovCatalogSubscription sub = require(id);
        if (!"PENDING".equalsIgnoreCase(sub.getStatus())) {
            throw new BusinessException(400, "仅待处理申请可通过");
        }
        assertProviderCanReview(operator, sub);
        String reviewerName = str(body.get("reviewerName"), str(body.get("reviewedBy"), null));
        if (reviewerName == null || reviewerName.isBlank()) {
            throw new BusinessException(400, "请填写审批人");
        }
        String reviewerContact = str(body.get("reviewerContact"), str(body.get("contact"), null));
        if (reviewerContact == null || reviewerContact.isBlank()) {
            throw new BusinessException(400, "请填写联系方式");
        }
        sub.setStatus("APPROVED");
        sub.setReviewComment(str(body.get("comment"), "同意"));
        sub.setReviewedBy(reviewerName.trim());
        sub.setReviewerContact(reviewerContact.trim());
        sub.setReviewedAt(LocalDateTime.now());
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(sub);

        GovCatalogResource resource = requireResource(sub.getResourceId());
        resource.setSubscriptionStatus("SUBSCRIBED");
        resourceMapper.updateById(resource);

        GovCatalogAuthorization authorization = ensureAuthorization(operator, sub);
        syncPortalReview(sub, "APPROVED", sub.getReviewComment(), sub.getReviewedBy(),
                sub.getReviewedAt(), sub.getReviewerContact());
        if (sub.getPortalSubscriptionId() != null) {
            BizPortalSubscription portal = portalSubscriptionMapper.selectById(sub.getPortalSubscriptionId());
            esbConsumerProvisionService.provisionOnApprove(portal);
        }
        subscribeDistributeService.onSubscriptionApproved(operator, sub);
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
        assertProviderCanReview(operator, sub);
        String reviewerName = str(body.get("reviewerName"), str(body.get("reviewedBy"), null));
        if (reviewerName == null || reviewerName.isBlank()) {
            throw new BusinessException(400, "请填写审批人");
        }
        String reviewerContact = str(body.get("reviewerContact"), str(body.get("contact"), null));
        if (reviewerContact == null || reviewerContact.isBlank()) {
            throw new BusinessException(400, "请填写联系方式");
        }
        String comment = str(body.get("comment"), null);
        if (comment == null || comment.isBlank()) {
            throw new BusinessException(400, "驳回须填写驳回意见");
        }
        sub.setStatus("REJECTED");
        sub.setReviewComment(comment);
        sub.setReviewedBy(reviewerName.trim());
        sub.setReviewerContact(reviewerContact.trim());
        sub.setReviewedAt(LocalDateTime.now());
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(sub);

        GovCatalogResource resource = requireResource(sub.getResourceId());
        resource.setSubscriptionStatus("REJECTED");
        resourceMapper.updateById(resource);

        syncPortalReview(sub, "REJECTED", comment, sub.getReviewedBy(),
                sub.getReviewedAt(), sub.getReviewerContact());
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
        boolean admin = operator != null && operator.isSystemAdmin();
        if (!admin) {
            if (operator == null) {
                throw new BusinessException(403, "仅申请人可取消");
            }
            String myOrg = resolveOrgName(operator);
            boolean self = Objects.equals(operator.getUsername(), sub.getApplicantUser());
            boolean sameDept = myOrg != null && !myOrg.isBlank()
                    && myOrg.trim().equals(nz(sub.getApplicantOrg()).trim());
            if (!self && !sameDept) {
                throw new BusinessException(403, "仅本部门申请方可取消");
            }
        }
        sub.setStatus("CANCELLED");
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(sub);

        GovCatalogResource resource = requireResource(sub.getResourceId());
        resource.setSubscriptionStatus("CANCELLED");
        resourceMapper.updateById(resource);
        syncPortalReview(sub, "CANCELLED", null, operator == null ? null : operator.getUsername(),
                LocalDateTime.now(), null);
        return toRow(sub);
    }

    @Transactional
    public Map<String, Object> distribute(UserPrincipal operator, Long id) {
        // 按订阅配置的多目标分发（内部系统/上级/大数据中心等）；保留原返回字段兼容前端
        Map<String, Object> pushed = subscribeDistributeService.distributeNow(operator, id, null);
        GovCatalogSubscription sub = require(id);
        Map<String, Object> out = toRow(sub);
        out.put("count", pushed.get("count"));
        out.put("logs", pushed.get("logs"));
        String mode = sub.getShareMode() == null ? "DB_SYNC" : sub.getShareMode().toUpperCase();
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
        row.put("reviewerContact", sub.getReviewerContact());
        row.put("reviewedAt", sub.getReviewedAt());
        row.put("distributeResult", sub.getDistributeResult());
        row.put("distributeAt", sub.getDistributeAt());
        row.put("createdAt", sub.getCreatedAt());
        row.put("updatedAt", sub.getUpdatedAt());
        row.put("applyPayload", parseApplyPayload(sub.getApplyPayload()));
        row.put("portalSubscriptionId", sub.getPortalSubscriptionId());
        if (sub.getPortalSubscriptionId() != null) {
            BizPortalSubscription portal = portalSubscriptionMapper.selectById(sub.getPortalSubscriptionId());
            if (portal != null) {
                row.put("oauthClientId", portal.getOauthClientId());
                row.put("oauthClientSecret", portal.getOauthClientSecret());
                row.put("apiUrl", portal.getApiUrl());
                row.put("apiMethod", portal.getApiMethod());
            }
        }
        GovCatalogResource resource = resourceMapper.selectById(sub.getResourceId());
        if (resource != null) {
            row.put("resourceCode", resource.getResourceCode());
            row.put("resourceName", resource.getResourceName());
            row.put("resourceType", resource.getResourceType());
            row.put("providerOrg", resource.getProviderOrg());
            row.put("publishStatus", resource.getPublishStatus());
            row.put("resourceFormat", resource.getResourceFormat());
            row.put("shareType", resource.getShareType());
            row.put("openType", resource.getOpenType());
            row.put("updateCycle", resource.getUpdateCycle());
            row.put("description", resource.getDescription());
            row.put("physicalTableName", resource.getPhysicalTableName());
            row.put("portalCatalogId", resource.getPortalCatalogId());
        }
        GovCatalogAuthorization authorization = findAuthorization(sub.getId());
        row.put("authorization", authorization == null ? null : toAuthorizationRow(authorization));
        row.put("approvalFlow", buildApprovalFlow(sub));
        return row;
    }

    private List<Map<String, Object>> buildApprovalFlow(GovCatalogSubscription sub) {
        List<Map<String, Object>> flow = new ArrayList<>();
        Map<String, Object> submit = new LinkedHashMap<>();
        submit.put("step", "提交申请");
        submit.put("status", "DONE");
        submit.put("result", "已提交");
        submit.put("actor", sub.getApplicantUser());
        submit.put("time", sub.getCreatedAt());
        submit.put("comment", "");
        flow.add(submit);

        Map<String, Object> review = new LinkedHashMap<>();
        review.put("step", "提供方审批");
        String st = nz(sub.getStatus()).toUpperCase(Locale.ROOT);
        if ("PENDING".equals(st)) {
            review.put("status", "PENDING");
            review.put("result", "待审批");
            review.put("actor", "");
            review.put("time", null);
            review.put("comment", "");
        } else if ("APPROVED".equals(st) || "DISTRIBUTED".equals(st)) {
            review.put("status", "APPROVED");
            review.put("result", "已通过");
            review.put("actor", sub.getReviewedBy());
            review.put("time", sub.getReviewedAt());
            review.put("comment", sub.getReviewComment());
        } else if ("REJECTED".equals(st)) {
            review.put("status", "REJECTED");
            review.put("result", "已驳回");
            review.put("actor", sub.getReviewedBy());
            review.put("time", sub.getReviewedAt());
            review.put("comment", sub.getReviewComment());
        } else if ("CANCELLED".equals(st)) {
            review.put("status", "CANCELLED");
            review.put("result", "已取消");
            review.put("actor", sub.getApplicantUser());
            review.put("time", sub.getUpdatedAt());
            review.put("comment", "");
        } else {
            review.put("status", st);
            review.put("result", st);
            review.put("actor", sub.getReviewedBy());
            review.put("time", sub.getReviewedAt());
            review.put("comment", sub.getReviewComment());
        }
        flow.add(review);
        return flow;
    }

    private void linkPortalGovIds(Long portalSubscriptionId, Long govSubscriptionId) {
        BizPortalSubscription portal = portalSubscriptionMapper.selectById(portalSubscriptionId);
        if (portal != null) {
            portal.setGovSubscriptionId(govSubscriptionId);
            portal.setUpdatedAt(LocalDateTime.now());
            portalSubscriptionMapper.updateById(portal);
        }
        GovCatalogSubscription gov = subscriptionMapper.selectById(govSubscriptionId);
        if (gov != null && (gov.getPortalSubscriptionId() == null
                || !Objects.equals(gov.getPortalSubscriptionId(), portalSubscriptionId))) {
            gov.setPortalSubscriptionId(portalSubscriptionId);
            gov.setUpdatedAt(LocalDateTime.now());
            subscriptionMapper.updateById(gov);
        }
    }

    private void ensurePortalMirror(UserPrincipal operator, GovCatalogSubscription sub,
                                    GovCatalogResource resource, String applicantOrg) {
        Long catalogId = resource.getPortalCatalogId();
        if (catalogId == null) {
            BizCatalogItem byGov = portalCatalogMapper.selectOne(new LambdaQueryWrapper<BizCatalogItem>()
                    .eq(BizCatalogItem::getGovResourceId, resource.getId())
                    .last("LIMIT 1"));
            if (byGov != null) {
                catalogId = byGov.getId();
            }
        }
        if (catalogId == null) {
            return;
        }
        BizPortalSubscription existing = portalSubscriptionMapper.selectOne(new LambdaQueryWrapper<BizPortalSubscription>()
                .eq(BizPortalSubscription::getGovSubscriptionId, sub.getId())
                .last("LIMIT 1"));
        if (existing != null) {
            sub.setPortalSubscriptionId(existing.getId());
            subscriptionMapper.updateById(sub);
            return;
        }
        BizPortalSubscription portal = new BizPortalSubscription();
        portal.setCatalogId(catalogId);
        portal.setApplicantOrg(applicantOrg);
        portal.setResourceType(fromGovShareMode(sub.getShareMode()));
        portal.setPurpose(sub.getPurpose());
        portal.setApplyPayload(sub.getApplyPayload());
        portal.setStatus("PENDING");
        portal.setGovSubscriptionId(sub.getId());
        portal.setCreatedBy(operator == null ? sub.getApplicantUser() : operator.getUsername());
        portal.setCreatedAt(LocalDateTime.now());
        portal.setUpdatedAt(LocalDateTime.now());
        portalSubscriptionMapper.insert(portal);
        sub.setPortalSubscriptionId(portal.getId());
        subscriptionMapper.updateById(sub);
    }

    private void syncPortalReview(GovCatalogSubscription sub, String status, String comment,
                                  String reviewedBy, LocalDateTime reviewedAt) {
        syncPortalReview(sub, status, comment, reviewedBy, reviewedAt, sub.getReviewerContact());
    }

    private void syncPortalReview(GovCatalogSubscription sub, String status, String comment,
                                  String reviewedBy, LocalDateTime reviewedAt, String reviewerContact) {
        BizPortalSubscription portal = null;
        if (sub.getPortalSubscriptionId() != null) {
            portal = portalSubscriptionMapper.selectById(sub.getPortalSubscriptionId());
        }
        if (portal == null) {
            portal = portalSubscriptionMapper.selectOne(new LambdaQueryWrapper<BizPortalSubscription>()
                    .eq(BizPortalSubscription::getGovSubscriptionId, sub.getId())
                    .last("LIMIT 1"));
        }
        if (portal == null) {
            GovCatalogResource resource = resourceMapper.selectById(sub.getResourceId());
            if (resource != null && resource.getPortalCatalogId() != null) {
                portal = portalSubscriptionMapper.selectOne(new LambdaQueryWrapper<BizPortalSubscription>()
                        .eq(BizPortalSubscription::getCatalogId, resource.getPortalCatalogId())
                        .eq(BizPortalSubscription::getCreatedBy, sub.getApplicantUser())
                        .eq(BizPortalSubscription::getStatus, "PENDING")
                        .orderByDesc(BizPortalSubscription::getId)
                        .last("LIMIT 1"));
            }
        }
        if (portal == null) {
            return;
        }
        portal.setStatus(status);
        portal.setApproverNote(comment);
        if (!"CANCELLED".equalsIgnoreCase(status)) {
            portal.setReviewedBy(reviewedBy);
            portal.setReviewedAt(reviewedAt);
            if (reviewerContact != null && !reviewerContact.isBlank()) {
                portal.setReviewerContact(reviewerContact.trim());
            }
        }
        portal.setGovSubscriptionId(sub.getId());
        portal.setUpdatedAt(LocalDateTime.now());
        portalSubscriptionMapper.updateById(portal);
        if (sub.getPortalSubscriptionId() == null) {
            sub.setPortalSubscriptionId(portal.getId());
            subscriptionMapper.updateById(sub);
        }
    }

    private String fromGovShareMode(String shareMode) {
        if (shareMode == null) {
            return "TABLE";
        }
        return switch (shareMode.toUpperCase(Locale.ROOT)) {
            case "FILE_SYNC", "FILE" -> "FILE";
            case "API" -> "API";
            default -> "TABLE";
        };
    }

    private Object parseApplyPayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OM.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return json;
        }
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
