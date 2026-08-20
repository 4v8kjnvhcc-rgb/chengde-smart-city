package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovCatalogDistributeLog;
import com.chengde.smartcity.masterdata.entity.GovCatalogDistributeTarget;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovCatalogSubNotice;
import com.chengde.smartcity.masterdata.entity.GovCatalogSubscription;
import com.chengde.smartcity.masterdata.mapper.GovCatalogDistributeLogMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogDistributeTargetMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogSubNoticeMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogSubscriptionMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

/**
 * 目录订阅变更通知 + 多目标数据分发。
 * 门户已发布资源发生变更/增补时通知订阅方，并按申请订阅内容推送至内部系统/上级/大数据中心等。
 */
@Service
public class CatalogSubscribeDistributeService {

    private static final Logger log = LoggerFactory.getLogger(CatalogSubscribeDistributeService.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private static final Set<String> CHANGE_TYPES = Set.of(
            "DATA_UPDATE", "DATA_INSERT", "META_UPDATE", "SCHEMA_CHANGE", "REPUBLISH");
    private static final Set<String> TARGET_TYPES = Set.of(
            "INTERNAL_SYSTEM", "SUPERIOR", "CITY_BIGDATA", "NATIONAL_LOCAL_BIGDATA", "THIRD_PARTY");
    private static final Set<String> ACTIVE_SUB_STATUS = Set.of("APPROVED", "DISTRIBUTED");

    private final GovCatalogSubscriptionMapper subscriptionMapper;
    private final GovCatalogResourceMapper resourceMapper;
    private final GovCatalogSubNoticeMapper noticeMapper;
    private final GovCatalogDistributeTargetMapper targetMapper;
    private final GovCatalogDistributeLogMapper logMapper;
    private final SysOrgMapper orgMapper;
    private final AuditService auditService;

    public CatalogSubscribeDistributeService(GovCatalogSubscriptionMapper subscriptionMapper,
                                             GovCatalogResourceMapper resourceMapper,
                                             GovCatalogSubNoticeMapper noticeMapper,
                                             GovCatalogDistributeTargetMapper targetMapper,
                                             GovCatalogDistributeLogMapper logMapper,
                                             SysOrgMapper orgMapper,
                                             AuditService auditService) {
        this.subscriptionMapper = subscriptionMapper;
        this.resourceMapper = resourceMapper;
        this.noticeMapper = noticeMapper;
        this.targetMapper = targetMapper;
        this.logMapper = logMapper;
        this.orgMapper = orgMapper;
        this.auditService = auditService;
    }

    /** 资源发布/再发布后：通知活跃订阅方，并对开启 auto_push 的目标执行分发。 */
    @Transactional
    public Map<String, Object> onResourceChange(Long resourceId, String changeType, String detail) {
        return onResourceChange(null, resourceId, changeType, detail);
    }

    @Transactional
    public Map<String, Object> onResourceChange(UserPrincipal operator, Long resourceId,
                                                String changeType, String detail) {
        if (resourceId == null) {
            throw new BusinessException(400, "resourceId 不能为空");
        }
        String ct = normalizeChangeType(changeType);
        GovCatalogResource resource = requireResource(resourceId);
        List<GovCatalogSubscription> subs = subscriptionMapper.selectList(
                new LambdaQueryWrapper<GovCatalogSubscription>()
                        .eq(GovCatalogSubscription::getResourceId, resourceId)
                        .in(GovCatalogSubscription::getStatus, ACTIVE_SUB_STATUS));

        int noticeCount = 0;
        int distributeCount = 0;
        String title = buildNoticeTitle(resource, ct);
        String body = detail == null || detail.isBlank()
                ? defaultDetail(resource, ct)
                : detail.trim();

        for (GovCatalogSubscription sub : subs) {
            GovCatalogSubNotice notice = new GovCatalogSubNotice();
            notice.setSubscriptionId(sub.getId());
            notice.setResourceId(resourceId);
            notice.setChangeType(ct);
            notice.setTitle(title);
            notice.setDetail(body);
            notice.setNotifyUser(sub.getApplicantUser());
            notice.setNotifyOrg(sub.getApplicantOrg());
            notice.setStatus("UNREAD");
            notice.setCreatedAt(LocalDateTime.now());
            noticeMapper.insert(notice);
            noticeCount++;

            List<GovCatalogDistributeLog> pushed = pushTargetsForSubscription(
                    operator, sub, resource, "CHANGE", ct, true);
            distributeCount += pushed.size();
        }

        if (operator != null) {
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "CATALOG_SUB_CHANGE_NOTIFY", "gov_catalog_resource",
                    String.valueOf(resourceId),
                    "changeType=" + ct + " notices=" + noticeCount + " distributes=" + distributeCount);
        }
        log.info("catalog change notify resourceId={} type={} notices={} distributes={}",
                resourceId, ct, noticeCount, distributeCount);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resourceId", resourceId);
        out.put("changeType", ct);
        out.put("noticeCount", noticeCount);
        out.put("distributeCount", distributeCount);
        out.put("activeSubscriptions", subs.size());
        return out;
    }

    /** 审批通过后：按申请内容补默认分发目标，并记一次 APPROVE 分发台账。 */
    @Transactional
    public void onSubscriptionApproved(UserPrincipal operator, GovCatalogSubscription sub) {
        if (sub == null || sub.getId() == null) {
            return;
        }
        ensureDefaultTarget(operator, sub);
        GovCatalogResource resource = resourceMapper.selectById(sub.getResourceId());
        if (resource == null) {
            return;
        }
        pushTargetsForSubscription(operator, sub, resource, "APPROVE", null, false);
    }

    public List<Map<String, Object>> listNotices(UserPrincipal operator, String status) {
        LambdaQueryWrapper<GovCatalogSubNotice> q = new LambdaQueryWrapper<GovCatalogSubNotice>()
                .orderByDesc(GovCatalogSubNotice::getId);
        if (status != null && !status.isBlank()) {
            q.eq(GovCatalogSubNotice::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        List<GovCatalogSubNotice> list = noticeMapper.selectList(q);
        if (operator == null || operator.isSystemAdmin()) {
            return toNoticeRows(list);
        }
        String myOrg = resolveOrgName(operator);
        String username = operator.getUsername();
        List<GovCatalogSubNotice> filtered = new ArrayList<>();
        for (GovCatalogSubNotice n : list) {
            boolean self = username != null && username.equals(n.getNotifyUser());
            boolean sameDept = myOrg != null && !myOrg.isBlank()
                    && myOrg.trim().equals(nz(n.getNotifyOrg()).trim());
            if (self || sameDept) {
                filtered.add(n);
            }
        }
        return toNoticeRows(filtered);
    }

    @Transactional
    public Map<String, Object> markNoticeRead(UserPrincipal operator, Long id) {
        GovCatalogSubNotice n = requireNotice(id);
        assertCanAccessNotice(operator, n);
        if ("UNREAD".equalsIgnoreCase(n.getStatus())) {
            n.setStatus("READ");
            noticeMapper.updateById(n);
        }
        return toNoticeRow(n);
    }

    @Transactional
    public Map<String, Object> ackNotice(UserPrincipal operator, Long id) {
        GovCatalogSubNotice n = requireNotice(id);
        assertCanAccessNotice(operator, n);
        n.setStatus("ACKED");
        n.setAckedAt(LocalDateTime.now());
        noticeMapper.updateById(n);
        if (operator != null) {
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "CATALOG_SUB_NOTICE_ACK", "gov_catalog_sub_notice",
                    String.valueOf(id), n.getTitle());
        }
        return toNoticeRow(n);
    }

    public List<Map<String, Object>> listTargets(UserPrincipal operator, Long subscriptionId) {
        LambdaQueryWrapper<GovCatalogDistributeTarget> q = new LambdaQueryWrapper<GovCatalogDistributeTarget>()
                .orderByDesc(GovCatalogDistributeTarget::getId);
        if (subscriptionId != null) {
            q.eq(GovCatalogDistributeTarget::getSubscriptionId, subscriptionId);
        }
        List<GovCatalogDistributeTarget> list = targetMapper.selectList(q);
        List<GovCatalogDistributeTarget> filtered = filterTargetsByAccess(operator, list);
        return toTargetRows(filtered);
    }

    @Transactional
    public Map<String, Object> saveTarget(UserPrincipal operator, Map<String, Object> body) {
        Long subscriptionId = toLong(body.get("subscriptionId"));
        if (subscriptionId == null) {
            throw new BusinessException(400, "subscriptionId 不能为空");
        }
        GovCatalogSubscription sub = requireSubscription(subscriptionId);
        assertCanManageSubscription(operator, sub);
        if (!ACTIVE_SUB_STATUS.contains(sub.getStatus().toUpperCase(Locale.ROOT))
                && !"PENDING".equalsIgnoreCase(sub.getStatus())) {
            throw new BusinessException(400, "仅待审/已通过/已分发订阅可配置分发目标");
        }

        String targetType = str(body.get("targetType"), "INTERNAL_SYSTEM").toUpperCase(Locale.ROOT);
        if (!TARGET_TYPES.contains(targetType)) {
            throw new BusinessException(400, "不支持的目标类型: " + targetType);
        }
        String targetName = str(body.get("targetName"), null);
        if (targetName == null || targetName.isBlank()) {
            throw new BusinessException(400, "请填写目标名称");
        }

        Long id = toLong(body.get("id"));
        GovCatalogDistributeTarget t;
        if (id != null) {
            t = targetMapper.selectById(id);
            if (t == null || !Objects.equals(t.getSubscriptionId(), subscriptionId)) {
                throw new BusinessException(404, "分发目标不存在");
            }
        } else {
            t = new GovCatalogDistributeTarget();
            t.setSubscriptionId(subscriptionId);
            t.setResourceId(sub.getResourceId());
            t.setCreatedBy(operator == null ? null : operator.getUsername());
            t.setCreatedAt(LocalDateTime.now());
        }
        t.setTargetType(targetType);
        t.setTargetName(targetName.trim());
        t.setTargetOrg(str(body.get("targetOrg"), sub.getApplicantOrg()));
        t.setTargetEndpoint(str(body.get("targetEndpoint"), null));
        t.setShareMode(str(body.get("shareMode"), sub.getShareMode()));
        Object autoPush = body.get("autoPush");
        t.setAutoPush(autoPush == null || Boolean.parseBoolean(String.valueOf(autoPush))
                || "1".equals(String.valueOf(autoPush)) ? 1 : 0);
        t.setStatus(str(body.get("status"), "ACTIVE").toUpperCase(Locale.ROOT));
        t.setRemark(str(body.get("remark"), null));
        t.setUpdatedAt(LocalDateTime.now());
        if (id == null) {
            targetMapper.insert(t);
        } else {
            targetMapper.updateById(t);
        }
        return toTargetRow(t);
    }

    @Transactional
    public void deleteTarget(UserPrincipal operator, Long id) {
        GovCatalogDistributeTarget t = targetMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "分发目标不存在");
        }
        GovCatalogSubscription sub = requireSubscription(t.getSubscriptionId());
        assertCanManageSubscription(operator, sub);
        targetMapper.deleteById(id);
    }

    public List<Map<String, Object>> listDistributeLogs(UserPrincipal operator,
                                                        Long subscriptionId, Long resourceId) {
        LambdaQueryWrapper<GovCatalogDistributeLog> q = new LambdaQueryWrapper<GovCatalogDistributeLog>()
                .orderByDesc(GovCatalogDistributeLog::getId)
                .last("LIMIT 500");
        if (subscriptionId != null) {
            q.eq(GovCatalogDistributeLog::getSubscriptionId, subscriptionId);
        }
        if (resourceId != null) {
            q.eq(GovCatalogDistributeLog::getResourceId, resourceId);
        }
        List<GovCatalogDistributeLog> list = logMapper.selectList(q);
        if (operator == null || operator.isSystemAdmin()) {
            return toLogRows(list);
        }
        String myOrg = resolveOrgName(operator);
        String username = operator.getUsername();
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovCatalogDistributeLog row : list) {
            GovCatalogSubscription sub = subscriptionMapper.selectById(row.getSubscriptionId());
            if (sub == null) {
                continue;
            }
            boolean applicant = username != null && username.equals(sub.getApplicantUser());
            boolean sameDept = myOrg != null && !myOrg.isBlank()
                    && myOrg.trim().equals(nz(sub.getApplicantOrg()).trim());
            GovCatalogResource res = resourceMapper.selectById(row.getResourceId());
            boolean provider = res != null && myOrg != null && !myOrg.isBlank()
                    && myOrg.trim().equals(nz(res.getProviderOrg()).trim());
            if (applicant || sameDept || provider) {
                out.add(toLogRow(row));
            }
        }
        return out;
    }

    /** 手动按订阅触发分发（对全部 ACTIVE 目标）。 */
    @Transactional
    public Map<String, Object> distributeNow(UserPrincipal operator, Long subscriptionId, Long targetId) {
        GovCatalogSubscription sub = requireSubscription(subscriptionId);
        assertCanManageSubscription(operator, sub);
        if (!ACTIVE_SUB_STATUS.contains(sub.getStatus().toUpperCase(Locale.ROOT))) {
            throw new BusinessException(400, "仅已通过或已分发的订阅可触发分发");
        }
        GovCatalogResource resource = requireResource(sub.getResourceId());
        List<GovCatalogDistributeTarget> targets;
        if (targetId != null) {
            GovCatalogDistributeTarget one = targetMapper.selectById(targetId);
            if (one == null || !Objects.equals(one.getSubscriptionId(), subscriptionId)) {
                throw new BusinessException(404, "分发目标不存在");
            }
            targets = List.of(one);
        } else {
            ensureDefaultTarget(operator, sub);
            targets = targetMapper.selectList(new LambdaQueryWrapper<GovCatalogDistributeTarget>()
                    .eq(GovCatalogDistributeTarget::getSubscriptionId, subscriptionId)
                    .eq(GovCatalogDistributeTarget::getStatus, "ACTIVE"));
        }
        List<GovCatalogDistributeLog> logs = new ArrayList<>();
        for (GovCatalogDistributeTarget t : targets) {
            logs.add(executePush(operator, sub, resource, t, "MANUAL", null));
        }
        sub.setStatus("DISTRIBUTED");
        sub.setDistributeAt(LocalDateTime.now());
        sub.setDistributeResult("已按 " + logs.size() + " 个目标分发");
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(sub);
        resource.setSubscriptionStatus("DISTRIBUTED");
        resourceMapper.updateById(resource);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subscriptionId", subscriptionId);
        out.put("count", logs.size());
        out.put("logs", toLogRows(logs));
        return out;
    }

    private List<GovCatalogDistributeLog> pushTargetsForSubscription(UserPrincipal operator,
                                                                     GovCatalogSubscription sub,
                                                                     GovCatalogResource resource,
                                                                     String triggerType,
                                                                     String changeType,
                                                                     boolean autoOnly) {
        ensureDefaultTarget(operator, sub);
        LambdaQueryWrapper<GovCatalogDistributeTarget> q = new LambdaQueryWrapper<GovCatalogDistributeTarget>()
                .eq(GovCatalogDistributeTarget::getSubscriptionId, sub.getId())
                .eq(GovCatalogDistributeTarget::getStatus, "ACTIVE");
        if (autoOnly) {
            q.eq(GovCatalogDistributeTarget::getAutoPush, 1);
        }
        List<GovCatalogDistributeTarget> targets = targetMapper.selectList(q);
        List<GovCatalogDistributeLog> logs = new ArrayList<>();
        for (GovCatalogDistributeTarget t : targets) {
            logs.add(executePush(operator, sub, resource, t, triggerType, changeType));
        }
        if (!logs.isEmpty() && !"APPROVE".equalsIgnoreCase(triggerType)) {
            sub.setStatus("DISTRIBUTED");
            sub.setDistributeAt(LocalDateTime.now());
            sub.setDistributeResult("变更自动分发 " + logs.size() + " 目标");
            sub.setUpdatedAt(LocalDateTime.now());
            subscriptionMapper.updateById(sub);
            resource.setSubscriptionStatus("DISTRIBUTED");
            resourceMapper.updateById(resource);
        }
        return logs;
    }

    private GovCatalogDistributeLog executePush(UserPrincipal operator,
                                                GovCatalogSubscription sub,
                                                GovCatalogResource resource,
                                                GovCatalogDistributeTarget target,
                                                String triggerType,
                                                String changeType) {
        String shareMode = firstNonBlank(target.getShareMode(), sub.getShareMode(), "DB_SYNC")
                .toUpperCase(Locale.ROOT);
        String traceId = "DIST-" + UUID.randomUUID().toString().substring(0, 8);
        String digest = resource.getResourceCode() + "|" + shareMode + "|" + target.getTargetType()
                + "|" + (changeType == null ? triggerType : changeType);

        GovCatalogDistributeLog row = new GovCatalogDistributeLog();
        row.setSubscriptionId(sub.getId());
        row.setTargetId(target.getId());
        row.setResourceId(resource.getId());
        row.setTriggerType(triggerType);
        row.setChangeType(changeType);
        row.setTargetType(target.getTargetType());
        row.setTargetName(target.getTargetName());
        row.setShareMode(shareMode);
        row.setPayloadDigest(digest);
        row.setCreatedAt(LocalDateTime.now());
        row.setStatus("PENDING");
        logMapper.insert(row);

        String endpoint = nz(target.getTargetEndpoint()).trim();
        String summary;
        String status;
        if (!endpoint.isEmpty()) {
            try {
                postWebhook(endpoint, buildPushPayload(sub, resource, target, triggerType, changeType, traceId));
                status = "SUCCESS";
                summary = "已推送至 " + targetTypeLabel(target.getTargetType()) + "「" + target.getTargetName()
                        + "」 mode=" + shareMode + " traceId=" + traceId;
            } catch (Exception e) {
                status = "FAILED";
                summary = "推送失败: " + truncate(e.getMessage(), 200) + " traceId=" + traceId;
                log.warn("catalog distribute push failed targetId={} err={}", target.getId(), e.toString());
            }
        } else {
            // 无端点：诚实台账（按共享方式记分发意图，不假造成功外呼）
            status = "LEDGER";
            summary = targetTypeLabel(target.getTargetType()) + "「" + target.getTargetName()
                    + "」已记分发台账（未配置推送地址，未真实外呼） mode=" + shareMode
                    + " resource=" + resource.getResourceCode() + " traceId=" + traceId;
        }
        row.setStatus(status);
        row.setResultSummary(summary);
        row.setFinishedAt(LocalDateTime.now());
        logMapper.updateById(row);

        if (operator != null) {
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "CATALOG_DISTRIBUTE_PUSH", "gov_catalog_distribute_log",
                    String.valueOf(row.getId()), summary);
        }
        return row;
    }

    private void ensureDefaultTarget(UserPrincipal operator, GovCatalogSubscription sub) {
        Long cnt = targetMapper.selectCount(new LambdaQueryWrapper<GovCatalogDistributeTarget>()
                .eq(GovCatalogDistributeTarget::getSubscriptionId, sub.getId()));
        if (cnt != null && cnt > 0) {
            return;
        }
        Map<String, Object> payload = parseApplyPayload(sub.getApplyPayload());
        String systemName = str(payload.get("systemName"), null);
        String targetName = systemName != null && !systemName.isBlank()
                ? systemName.trim()
                : (nz(sub.getApplicantOrg()).isBlank() ? "申请方业务系统" : sub.getApplicantOrg() + "业务系统");
        GovCatalogDistributeTarget t = new GovCatalogDistributeTarget();
        t.setSubscriptionId(sub.getId());
        t.setResourceId(sub.getResourceId());
        t.setTargetType("INTERNAL_SYSTEM");
        t.setTargetName(targetName);
        t.setTargetOrg(sub.getApplicantOrg());
        t.setTargetEndpoint(str(payload.get("targetEndpoint"), str(payload.get("callbackUrl"), null)));
        t.setShareMode(sub.getShareMode());
        t.setAutoPush(1);
        t.setStatus("ACTIVE");
        t.setRemark("审批通过后按申请内容自动生成");
        t.setCreatedBy(operator == null ? "system" : operator.getUsername());
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        targetMapper.insert(t);
    }

    private Map<String, Object> buildPushPayload(GovCatalogSubscription sub,
                                                 GovCatalogResource resource,
                                                 GovCatalogDistributeTarget target,
                                                 String triggerType,
                                                 String changeType,
                                                 String traceId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("traceId", traceId);
        m.put("triggerType", triggerType);
        m.put("changeType", changeType);
        m.put("subscriptionId", sub.getId());
        m.put("shareMode", firstNonBlank(target.getShareMode(), sub.getShareMode(), "DB_SYNC"));
        m.put("applicantOrg", sub.getApplicantOrg());
        m.put("purpose", sub.getPurpose());
        m.put("targetType", target.getTargetType());
        m.put("targetName", target.getTargetName());
        m.put("resource", Map.of(
                "id", resource.getId(),
                "resourceCode", nz(resource.getResourceCode()),
                "resourceName", nz(resource.getResourceName()),
                "resourceType", nz(resource.getResourceType()),
                "physicalTableName", nz(resource.getPhysicalTableName()),
                "providerOrg", nz(resource.getProviderOrg()),
                "metadataEntryCode", nz(resource.getMetadataEntryCode()),
                "versionNo", resource.getVersionNo() == null ? 0 : resource.getVersionNo()
        ));
        m.put("applyPayload", parseApplyPayload(sub.getApplyPayload()));
        m.put("pushedAt", LocalDateTime.now().toString().replace('T', ' ').substring(0, 19));
        return m;
    }

    private void postWebhook(String endpoint, Map<String, Object> payload) throws Exception {
        byte[] body = OM.writeValueAsBytes(payload);
        HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(15_000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new BusinessException(502, "下游返回 HTTP " + code);
        }
    }

    private String buildNoticeTitle(GovCatalogResource resource, String changeType) {
        return "【" + changeTypeLabel(changeType) + "】" + nz(resource.getResourceName());
    }

    private String defaultDetail(GovCatalogResource resource, String changeType) {
        return "资源「" + nz(resource.getResourceName()) + "」（" + nz(resource.getResourceCode())
                + "）发生" + changeTypeLabel(changeType)
                + "，请及时了解最新情况并按申请订阅内容更新本地数据。"
                + " 物理表=" + nz(resource.getPhysicalTableName())
                + " 版本=v" + (resource.getVersionNo() == null ? 1 : resource.getVersionNo());
    }

    private String normalizeChangeType(String changeType) {
        String ct = changeType == null || changeType.isBlank()
                ? "DATA_UPDATE"
                : changeType.trim().toUpperCase(Locale.ROOT);
        if (!CHANGE_TYPES.contains(ct)) {
            throw new BusinessException(400, "不支持的变更类型: " + changeType);
        }
        return ct;
    }

    private List<GovCatalogDistributeTarget> filterTargetsByAccess(UserPrincipal operator,
                                                                   List<GovCatalogDistributeTarget> list) {
        if (operator == null || operator.isSystemAdmin()) {
            return list;
        }
        String myOrg = resolveOrgName(operator);
        String username = operator.getUsername();
        List<GovCatalogDistributeTarget> out = new ArrayList<>();
        for (GovCatalogDistributeTarget t : list) {
            GovCatalogSubscription sub = subscriptionMapper.selectById(t.getSubscriptionId());
            if (sub == null) {
                continue;
            }
            boolean applicant = username != null && username.equals(sub.getApplicantUser());
            boolean sameDept = myOrg != null && !myOrg.isBlank()
                    && myOrg.trim().equals(nz(sub.getApplicantOrg()).trim());
            GovCatalogResource res = resourceMapper.selectById(t.getResourceId());
            boolean provider = res != null && myOrg != null && !myOrg.isBlank()
                    && myOrg.trim().equals(nz(res.getProviderOrg()).trim());
            if (applicant || sameDept || provider) {
                out.add(t);
            }
        }
        return out;
    }

    private void assertCanAccessNotice(UserPrincipal operator, GovCatalogSubNotice n) {
        if (operator == null || operator.isSystemAdmin()) {
            return;
        }
        String myOrg = resolveOrgName(operator);
        boolean self = Objects.equals(operator.getUsername(), n.getNotifyUser());
        boolean sameDept = myOrg != null && !myOrg.isBlank()
                && myOrg.trim().equals(nz(n.getNotifyOrg()).trim());
        if (!self && !sameDept) {
            throw new BusinessException(403, "无权操作该通知");
        }
    }

    private void assertCanManageSubscription(UserPrincipal operator, GovCatalogSubscription sub) {
        if (operator == null || operator.isSystemAdmin()) {
            return;
        }
        String myOrg = resolveOrgName(operator);
        boolean applicant = Objects.equals(operator.getUsername(), sub.getApplicantUser());
        boolean sameDept = myOrg != null && !myOrg.isBlank()
                && myOrg.trim().equals(nz(sub.getApplicantOrg()).trim());
        GovCatalogResource res = resourceMapper.selectById(sub.getResourceId());
        boolean provider = res != null && myOrg != null && !myOrg.isBlank()
                && myOrg.trim().equals(nz(res.getProviderOrg()).trim());
        if (!applicant && !sameDept && !provider) {
            throw new BusinessException(403, "无权管理该订阅的分发配置");
        }
    }

    private GovCatalogSubNotice requireNotice(Long id) {
        GovCatalogSubNotice n = noticeMapper.selectById(id);
        if (n == null) {
            throw new BusinessException(404, "变更通知不存在");
        }
        return n;
    }

    private GovCatalogSubscription requireSubscription(Long id) {
        GovCatalogSubscription sub = subscriptionMapper.selectById(id);
        if (sub == null) {
            throw new BusinessException(404, "订阅不存在");
        }
        return sub;
    }

    private GovCatalogResource requireResource(Long id) {
        GovCatalogResource r = resourceMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(404, "目录资源不存在");
        }
        return r;
    }

    private List<Map<String, Object>> toNoticeRows(List<GovCatalogSubNotice> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovCatalogSubNotice n : list) {
            out.add(toNoticeRow(n));
        }
        return out;
    }

    private Map<String, Object> toNoticeRow(GovCatalogSubNotice n) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", n.getId());
        row.put("subscriptionId", n.getSubscriptionId());
        row.put("resourceId", n.getResourceId());
        row.put("changeType", n.getChangeType());
        row.put("title", n.getTitle());
        row.put("detail", n.getDetail());
        row.put("notifyUser", n.getNotifyUser());
        row.put("notifyOrg", n.getNotifyOrg());
        row.put("status", n.getStatus());
        row.put("ackedAt", n.getAckedAt());
        row.put("createdAt", n.getCreatedAt());
        GovCatalogResource r = resourceMapper.selectById(n.getResourceId());
        if (r != null) {
            row.put("resourceCode", r.getResourceCode());
            row.put("resourceName", r.getResourceName());
            row.put("providerOrg", r.getProviderOrg());
            row.put("physicalTableName", r.getPhysicalTableName());
            row.put("versionNo", r.getVersionNo());
        }
        return row;
    }

    private List<Map<String, Object>> toTargetRows(List<GovCatalogDistributeTarget> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovCatalogDistributeTarget t : list) {
            out.add(toTargetRow(t));
        }
        return out;
    }

    private Map<String, Object> toTargetRow(GovCatalogDistributeTarget t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.getId());
        row.put("subscriptionId", t.getSubscriptionId());
        row.put("resourceId", t.getResourceId());
        row.put("targetType", t.getTargetType());
        row.put("targetName", t.getTargetName());
        row.put("targetOrg", t.getTargetOrg());
        row.put("targetEndpoint", t.getTargetEndpoint());
        row.put("shareMode", t.getShareMode());
        row.put("autoPush", t.getAutoPush() != null && t.getAutoPush() == 1);
        row.put("status", t.getStatus());
        row.put("remark", t.getRemark());
        row.put("createdBy", t.getCreatedBy());
        row.put("createdAt", t.getCreatedAt());
        row.put("updatedAt", t.getUpdatedAt());
        GovCatalogResource r = resourceMapper.selectById(t.getResourceId());
        if (r != null) {
            row.put("resourceCode", r.getResourceCode());
            row.put("resourceName", r.getResourceName());
        }
        GovCatalogSubscription sub = subscriptionMapper.selectById(t.getSubscriptionId());
        if (sub != null) {
            row.put("applicantOrg", sub.getApplicantOrg());
            row.put("subscriptionStatus", sub.getStatus());
        }
        return row;
    }

    private List<Map<String, Object>> toLogRows(List<GovCatalogDistributeLog> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovCatalogDistributeLog row : list) {
            out.add(toLogRow(row));
        }
        return out;
    }

    private Map<String, Object> toLogRow(GovCatalogDistributeLog row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("subscriptionId", row.getSubscriptionId());
        m.put("targetId", row.getTargetId());
        m.put("resourceId", row.getResourceId());
        m.put("triggerType", row.getTriggerType());
        m.put("changeType", row.getChangeType());
        m.put("targetType", row.getTargetType());
        m.put("targetName", row.getTargetName());
        m.put("shareMode", row.getShareMode());
        m.put("status", row.getStatus());
        m.put("resultSummary", row.getResultSummary());
        m.put("payloadDigest", row.getPayloadDigest());
        m.put("createdAt", row.getCreatedAt());
        m.put("finishedAt", row.getFinishedAt());
        GovCatalogResource r = resourceMapper.selectById(row.getResourceId());
        if (r != null) {
            m.put("resourceCode", r.getResourceCode());
            m.put("resourceName", r.getResourceName());
        }
        return m;
    }

    private Map<String, Object> parseApplyPayload(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return OM.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String resolveOrgName(UserPrincipal operator) {
        if (operator == null || operator.getOrgId() == null) {
            return null;
        }
        SysOrg org = orgMapper.selectById(operator.getOrgId());
        return org == null ? null : org.getOrgName();
    }

    private static String changeTypeLabel(String ct) {
        if (ct == null) return "变更";
        return switch (ct.toUpperCase(Locale.ROOT)) {
            case "DATA_UPDATE" -> "数据变更";
            case "DATA_INSERT" -> "数据新增";
            case "META_UPDATE" -> "元数据变更";
            case "SCHEMA_CHANGE" -> "结构变更";
            case "REPUBLISH" -> "重新发布";
            default -> ct;
        };
    }

    private static String targetTypeLabel(String tt) {
        if (tt == null) return "目标系统";
        return switch (tt.toUpperCase(Locale.ROOT)) {
            case "INTERNAL_SYSTEM" -> "内部系统";
            case "SUPERIOR" -> "上级单位";
            case "CITY_BIGDATA" -> "市大数据中心";
            case "NATIONAL_LOCAL_BIGDATA" -> "国家/地方大数据中心";
            case "THIRD_PARTY" -> "第三方业务应用";
            default -> tt;
        };
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return "";
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
