package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.analysis.entity.AnaPlatformApp;
import com.chengde.smartcity.analysis.entity.AnaPlatformIntegration;
import com.chengde.smartcity.analysis.entity.AnaPlatformService;
import com.chengde.smartcity.analysis.mapper.AnaPlatformAppMapper;
import com.chengde.smartcity.analysis.mapper.AnaPlatformIntegrationMapper;
import com.chengde.smartcity.analysis.mapper.AnaPlatformServiceMapper;
import com.chengde.smartcity.analysis.service.AnalyticsPlatformService;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysAppGrant;
import com.chengde.smartcity.system.entity.SysRole;
import com.chengde.smartcity.system.entity.SysServiceApproval;
import com.chengde.smartcity.system.entity.SysServiceCallStat;
import com.chengde.smartcity.system.entity.SysUser;
import com.chengde.smartcity.system.mapper.SysAppGrantMapper;
import com.chengde.smartcity.system.mapper.SysRoleMapper;
import com.chengde.smartcity.system.mapper.SysServiceApprovalMapper;
import com.chengde.smartcity.system.mapper.SysServiceCallStatMapper;
import com.chengde.smartcity.system.mapper.SysUserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 统一用户管理系统（3.1.1）：在系统管理域聚合七中心能力 */
@Service
public class UnifiedUserManageService {

    private final AnalyticsPlatformService analyticsPlatformService;
    private final AnaPlatformAppMapper appMapper;
    private final AnaPlatformServiceMapper serviceMapper;
    private final AnaPlatformIntegrationMapper integrationMapper;
    private final SysAppGrantMapper appGrantMapper;
    private final SysServiceCallStatMapper callStatMapper;
    private final SysServiceApprovalMapper approvalMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final AuditService auditService;
    private final SysDictService dictService;

    public UnifiedUserManageService(AnalyticsPlatformService analyticsPlatformService,
                                    AnaPlatformAppMapper appMapper,
                                    AnaPlatformServiceMapper serviceMapper,
                                    AnaPlatformIntegrationMapper integrationMapper,
                                    SysAppGrantMapper appGrantMapper,
                                    SysServiceCallStatMapper callStatMapper,
                                    SysServiceApprovalMapper approvalMapper,
                                    SysUserMapper userMapper,
                                    SysRoleMapper roleMapper,
                                    AuditService auditService,
                                    SysDictService dictService) {
        this.analyticsPlatformService = analyticsPlatformService;
        this.appMapper = appMapper;
        this.serviceMapper = serviceMapper;
        this.integrationMapper = integrationMapper;
        this.appGrantMapper = appGrantMapper;
        this.callStatMapper = callStatMapper;
        this.approvalMapper = approvalMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.auditService = auditService;
        this.dictService = dictService;
    }

    public Map<String, Object> overview() {
        Map<String, Object> base = analyticsPlatformService.supportOverview();
        Map<String, Object> out = new LinkedHashMap<>(base);
        out.put("appGrantCount", appGrantMapper.selectCount(null));
        out.put("pendingApprovals", approvalMapper.selectCount(
                new LambdaQueryWrapper<SysServiceApproval>().eq(SysServiceApproval::getStatus, "PENDING")));
        out.put("sections", List.of(
                Map.of("key", "users", "title", "用户中心", "mCode", "M139"),
                Map.of("key", "apps", "title", "应用中心", "mCode", "M140"),
                Map.of("key", "auth", "title", "认证中心", "mCode", "M141"),
                Map.of("key", "services", "title", "服务中心", "mCode", "M142"),
                Map.of("key", "config", "title", "系统管理", "mCode", "M143"),
                Map.of("key", "audit", "title", "日志审计", "mCode", "M144"),
                Map.of("key", "integration", "title", "系统对接", "mCode", "M145")
        ));
        return out;
    }

    public List<Map<String, Object>> listAppGrants(Long appId) {
        LambdaQueryWrapper<SysAppGrant> q = new LambdaQueryWrapper<SysAppGrant>().orderByDesc(SysAppGrant::getId);
        if (appId != null) q.eq(SysAppGrant::getAppId, appId);
        return appGrantMapper.selectList(q).stream().map(this::appGrantView).collect(Collectors.toList());
    }

    @Transactional
    public Long createAppGrant(UserPrincipal operator, Map<String, Object> body) {
        Long appId = longVal(body.get("appId"));
        String granteeType = str(body.get("granteeType"), "USER").toUpperCase(Locale.ROOT);
        Long granteeId = longVal(body.get("granteeId"));
        String perm = str(body.get("perm"), "ACCESS").toUpperCase(Locale.ROOT);
        if (appId == null || granteeId == null) throw new BusinessException(400, "appId/granteeId required");
        if (appMapper.selectById(appId) == null) throw new BusinessException(404, "应用不存在");
        SysAppGrant exist = appGrantMapper.selectOne(new LambdaQueryWrapper<SysAppGrant>()
                .eq(SysAppGrant::getAppId, appId)
                .eq(SysAppGrant::getGranteeType, granteeType)
                .eq(SysAppGrant::getGranteeId, granteeId));
        if (exist != null) {
            exist.setPerm(perm);
            exist.setGrantedBy(operator.getUserId());
            appGrantMapper.updateById(exist);
            return exist.getId();
        }
        SysAppGrant g = new SysAppGrant();
        g.setAppId(appId);
        g.setGranteeType(granteeType);
        g.setGranteeId(granteeId);
        g.setPerm(perm);
        g.setGrantedBy(operator.getUserId());
        g.setCreatedAt(LocalDateTime.now());
        appGrantMapper.insert(g);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "APP_GRANT", "sys_app_grant", String.valueOf(g.getId()), "app=" + appId);
        return g.getId();
    }

    @Transactional
    public void deleteAppGrant(UserPrincipal operator, Long id) {
        SysAppGrant g = appGrantMapper.selectById(id);
        if (g == null) throw new BusinessException(404, "授权不存在");
        appGrantMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "APP_GRANT_DELETE", "sys_app_grant", String.valueOf(id), null);
    }

    public List<Map<String, Object>> listServiceStats(Long serviceId) {
        LambdaQueryWrapper<SysServiceCallStat> q = new LambdaQueryWrapper<SysServiceCallStat>()
                .orderByDesc(SysServiceCallStat::getCallDate);
        if (serviceId != null) q.eq(SysServiceCallStat::getServiceId, serviceId);
        q.last("LIMIT 60");
        List<Map<String, Object>> out = new ArrayList<>();
        for (SysServiceCallStat st : callStatMapper.selectList(q)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", st.getId());
            m.put("serviceId", st.getServiceId());
            AnaPlatformService svc = serviceMapper.selectById(st.getServiceId());
            m.put("serviceName", svc == null ? null : svc.getServiceName());
            m.put("callDate", st.getCallDate());
            m.put("callCount", st.getCallCount());
            m.put("successCount", st.getSuccessCount());
            m.put("failCount", st.getFailCount());
            int total = st.getCallCount() == null ? 0 : st.getCallCount();
            int ok = st.getSuccessCount() == null ? 0 : st.getSuccessCount();
            m.put("successRate", total == 0 ? 0 : Math.round(ok * 1000.0 / total) / 10.0);
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> listApprovals(String status) {
        LambdaQueryWrapper<SysServiceApproval> q = new LambdaQueryWrapper<SysServiceApproval>()
                .orderByDesc(SysServiceApproval::getId);
        if (status != null && !status.isBlank()) q.eq(SysServiceApproval::getStatus, status.toUpperCase(Locale.ROOT));
        return approvalMapper.selectList(q).stream().map(this::approvalView).collect(Collectors.toList());
    }

    @Transactional
    public Long applyService(UserPrincipal operator, Map<String, Object> body) {
        Long serviceId = longVal(body.get("serviceId"));
        String reason = str(body.get("reason"), "");
        if (serviceId == null) throw new BusinessException(400, "serviceId required");
        if (serviceMapper.selectById(serviceId) == null) throw new BusinessException(404, "服务不存在");
        SysServiceApproval a = new SysServiceApproval();
        a.setServiceId(serviceId);
        a.setApplicantId(operator.getUserId());
        a.setReason(reason);
        a.setStatus("PENDING");
        a.setCreatedAt(LocalDateTime.now());
        approvalMapper.insert(a);
        return a.getId();
    }

    @Transactional
    public void decideApproval(UserPrincipal operator, Long id, boolean pass, String comment) {
        if (!operator.isSystemAdmin() && !operator.isDeptAdmin()) {
            throw new BusinessException(403, "仅管理员可审批服务调用申请");
        }
        SysServiceApproval a = approvalMapper.selectById(id);
        if (a == null) throw new BusinessException(404, "申请不存在");
        if (!"PENDING".equals(a.getStatus())) throw new BusinessException(400, "申请已处理");
        a.setStatus(pass ? "APPROVED" : "REJECTED");
        a.setApproverId(operator.getUserId());
        a.setApproveComment(comment);
        a.setApprovedAt(LocalDateTime.now());
        approvalMapper.updateById(a);
    }

    public List<Map<String, Object>> authConfigs() {
        return dictService.authConfigsLegacy();
    }

    public List<Map<String, Object>> systemConfigs() {
        return dictService.systemConfigsLegacy();
    }

    @Transactional
    public void updateAuthConfig(UserPrincipal operator, Long id, Map<String, Object> body) {
        Object v = body == null ? null : body.get("configValue");
        dictService.updateItemValue(operator, id, v == null ? "" : String.valueOf(v));
    }

    public List<AnaPlatformApp> apps() {
        return appMapper.selectList(new LambdaQueryWrapper<AnaPlatformApp>().orderByAsc(AnaPlatformApp::getId));
    }

    public List<AnaPlatformService> services() {
        return serviceMapper.selectList(new LambdaQueryWrapper<AnaPlatformService>().orderByAsc(AnaPlatformService::getId));
    }

    public List<AnaPlatformIntegration> integrations() {
        return integrationMapper.selectList(new LambdaQueryWrapper<AnaPlatformIntegration>().orderByAsc(AnaPlatformIntegration::getId));
    }

    private Map<String, Object> appGrantView(SysAppGrant g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("appId", g.getAppId());
        AnaPlatformApp app = appMapper.selectById(g.getAppId());
        m.put("appName", app == null ? null : app.getAppName());
        m.put("granteeType", g.getGranteeType());
        m.put("granteeId", g.getGranteeId());
        m.put("granteeName", resolveGrantee(g.getGranteeType(), g.getGranteeId()));
        m.put("perm", g.getPerm());
        m.put("createdAt", g.getCreatedAt());
        return m;
    }

    private Map<String, Object> approvalView(SysServiceApproval a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("serviceId", a.getServiceId());
        AnaPlatformService svc = serviceMapper.selectById(a.getServiceId());
        m.put("serviceName", svc == null ? null : svc.getServiceName());
        m.put("applicantId", a.getApplicantId());
        SysUser u = userMapper.selectById(a.getApplicantId());
        m.put("applicantName", u == null ? null : u.getDisplayName());
        m.put("reason", a.getReason());
        m.put("status", a.getStatus());
        m.put("approverId", a.getApproverId());
        m.put("approveComment", a.getApproveComment());
        m.put("createdAt", a.getCreatedAt());
        m.put("approvedAt", a.getApprovedAt());
        return m;
    }

    private String resolveGrantee(String type, Long id) {
        if ("USER".equalsIgnoreCase(type)) {
            SysUser u = userMapper.selectById(id);
            return u == null ? String.valueOf(id) : u.getDisplayName();
        }
        if ("ROLE".equalsIgnoreCase(type)) {
            SysRole r = roleMapper.selectById(id);
            return r == null ? String.valueOf(id) : r.getRoleName();
        }
        return String.valueOf(id);
    }

    private static Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        return Long.valueOf(String.valueOf(v));
    }

    private static String str(Object v, String def) {
        if (v == null || String.valueOf(v).isBlank()) return def;
        return String.valueOf(v);
    }
}
