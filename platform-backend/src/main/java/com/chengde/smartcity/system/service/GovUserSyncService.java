package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysGovSyncJob;
import com.chengde.smartcity.system.entity.SysGovSyncTarget;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.entity.SysRole;
import com.chengde.smartcity.system.entity.SysUser;
import com.chengde.smartcity.system.mapper.SysGovSyncJobMapper;
import com.chengde.smartcity.system.mapper.SysGovSyncTargetMapper;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import com.chengde.smartcity.system.mapper.SysRoleMapper;
import com.chengde.smartcity.system.mapper.SysUserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** 政务用户信息同步：组织/用户/角色及认证相关字段推送台账 */
@Service
public class GovUserSyncService {

    private static final Logger log = LoggerFactory.getLogger(GovUserSyncService.class);

    private final SysGovSyncTargetMapper targetMapper;
    private final SysGovSyncJobMapper jobMapper;
    private final SysOrgMapper orgMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public GovUserSyncService(SysGovSyncTargetMapper targetMapper,
                              SysGovSyncJobMapper jobMapper,
                              SysOrgMapper orgMapper,
                              SysUserMapper userMapper,
                              SysRoleMapper roleMapper,
                              AuditService auditService,
                              ObjectMapper objectMapper,
                              RestTemplate integrationRestTemplate) {
        this.targetMapper = targetMapper;
        this.jobMapper = jobMapper;
        this.orgMapper = orgMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.restTemplate = integrationRestTemplate;
    }

    public Map<String, Object> authMethodsView(SecurityConfigService securityConfigService) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("password", securityConfigService.isAuthMethodEnabled("password", true));
        m.put("sms", securityConfigService.isAuthMethodEnabled("sms", false));
        m.put("totp", securityConfigService.isAuthMethodEnabled("totp", false));
        m.put("fingerprint", securityConfigService.isAuthMethodEnabled("fingerprint", false));
        m.put("twoFactorRequired", securityConfigService.isSecondFactorRequired());
        return m;
    }

    public void saveAuthMethods(SecurityConfigService securityConfigService, Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new BusinessException(400, "请求体不能为空");
        }
        Map<String, String> patch = new LinkedHashMap<>();
        putBool(patch, body, "password", "auth_method_password");
        putBool(patch, body, "sms", "auth_method_sms");
        putBool(patch, body, "totp", "auth_method_totp");
        putBool(patch, body, "fingerprint", "auth_method_fingerprint");
        if (patch.isEmpty()) {
            throw new BusinessException(400, "未包含可保存的认证方式字段");
        }
        boolean passwordOn = securityConfigService.isAuthMethodEnabled("password", true);
        if (body.containsKey("password")) {
            passwordOn = boolVal(body.get("password"));
        }
        boolean smsOn = securityConfigService.isAuthMethodEnabled("sms", false);
        if (body.containsKey("sms")) {
            smsOn = boolVal(body.get("sms"));
        }
        boolean totpOn = securityConfigService.isAuthMethodEnabled("totp", false);
        if (body.containsKey("totp")) {
            totpOn = boolVal(body.get("totp"));
        }
        boolean fpOn = securityConfigService.isAuthMethodEnabled("fingerprint", false);
        if (body.containsKey("fingerprint")) {
            fpOn = boolVal(body.get("fingerprint"));
        }
        if (!passwordOn && !smsOn && !totpOn && !fpOn) {
            throw new BusinessException(400, "至少启用一种身份验证方式");
        }
        if (!passwordOn) {
            throw new BusinessException(400, "当前门户登录以账号密码为主认证，不能关闭用户名密码");
        }
        securityConfigService.update(patch);
        // 与等保双因素开关对齐：短信/令牌任一开启则要求第二因子
        Map<String, String> twoFactor = new LinkedHashMap<>();
        twoFactor.put("two_factor_enabled", (smsOn || totpOn) ? "true" : "false");
        securityConfigService.update(twoFactor);
    }

    public List<SysGovSyncTarget> listTargets() {
        return targetMapper.selectList(new LambdaQueryWrapper<SysGovSyncTarget>()
                .orderByAsc(SysGovSyncTarget::getId));
    }

    @Transactional
    public Long saveTarget(UserPrincipal operator, Map<String, Object> body, Long id) {
        String code = str(body.get("targetCode"), "").trim();
        String name = str(body.get("targetName"), "").trim();
        if (name.isEmpty()) {
            throw new BusinessException(400, "对接系统名称不能为空");
        }
        if (id == null) {
            if (code.isEmpty()) {
                code = "GOV_" + System.currentTimeMillis();
            }
            Long dup = targetMapper.selectCount(new LambdaQueryWrapper<SysGovSyncTarget>()
                    .eq(SysGovSyncTarget::getTargetCode, code));
            if (dup != null && dup > 0) {
                throw new BusinessException(400, "对接编码已存在");
            }
            SysGovSyncTarget row = new SysGovSyncTarget();
            row.setTargetCode(code);
            row.setTargetName(name);
            row.setEndpoint(blankToNull(str(body.get("endpoint"), null)));
            row.setSyncDirection(str(body.get("syncDirection"), "PUSH").toUpperCase(Locale.ROOT));
            row.setStatus(str(body.get("status"), "ACTIVE").toUpperCase(Locale.ROOT));
            row.setRemark(blankToNull(str(body.get("remark"), null)));
            row.setCreatedBy(operator.getUserId());
            row.setCreatedAt(LocalDateTime.now());
            targetMapper.insert(row);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "GOV_SYNC_TARGET_CREATE", "sys_gov_sync_target", String.valueOf(row.getId()), name);
            return row.getId();
        }
        SysGovSyncTarget row = targetMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "对接目标不存在");
        }
        if (StringUtils.hasText(code) && !code.equals(row.getTargetCode())) {
            Long dup = targetMapper.selectCount(new LambdaQueryWrapper<SysGovSyncTarget>()
                    .eq(SysGovSyncTarget::getTargetCode, code)
                    .ne(SysGovSyncTarget::getId, id));
            if (dup != null && dup > 0) {
                throw new BusinessException(400, "对接编码已存在");
            }
            row.setTargetCode(code);
        }
        row.setTargetName(name);
        if (body.containsKey("endpoint")) {
            row.setEndpoint(blankToNull(str(body.get("endpoint"), null)));
        }
        if (body.containsKey("syncDirection")) {
            row.setSyncDirection(str(body.get("syncDirection"), "PUSH").toUpperCase(Locale.ROOT));
        }
        if (body.containsKey("status")) {
            row.setStatus(str(body.get("status"), "ACTIVE").toUpperCase(Locale.ROOT));
        }
        if (body.containsKey("remark")) {
            row.setRemark(blankToNull(str(body.get("remark"), null)));
        }
        row.setUpdatedAt(LocalDateTime.now());
        targetMapper.updateById(row);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "GOV_SYNC_TARGET_UPDATE", "sys_gov_sync_target", String.valueOf(id), name);
        return id;
    }

    @Transactional
    public void deleteTarget(UserPrincipal operator, Long id) {
        SysGovSyncTarget row = targetMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "对接目标不存在");
        }
        targetMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "GOV_SYNC_TARGET_DELETE", "sys_gov_sync_target", String.valueOf(id), row.getTargetName());
    }

    public List<Map<String, Object>> listJobs(Long targetId) {
        LambdaQueryWrapper<SysGovSyncJob> q = new LambdaQueryWrapper<SysGovSyncJob>()
                .orderByDesc(SysGovSyncJob::getId)
                .last("LIMIT 100");
        if (targetId != null) {
            q.eq(SysGovSyncJob::getTargetId, targetId);
        }
        return jobMapper.selectList(q).stream().map(this::jobView).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> runSync(UserPrincipal operator, Map<String, Object> body) {
        Long targetId = longVal(body.get("targetId"));
        if (targetId == null) {
            throw new BusinessException(400, "请选择对接系统");
        }
        SysGovSyncTarget target = targetMapper.selectById(targetId);
        if (target == null) {
            throw new BusinessException(404, "对接目标不存在");
        }
        if (!"ACTIVE".equalsIgnoreCase(target.getStatus())) {
            throw new BusinessException(400, "对接目标未启用");
        }

        boolean syncOrg = boolVal(body.get("syncOrg"));
        boolean syncUser = boolVal(body.get("syncUser"));
        boolean syncRole = boolVal(body.get("syncRole"));
        boolean syncPassword = boolVal(body.get("syncPassword"));
        boolean syncSms = boolVal(body.get("syncSms"));
        if (!syncOrg && !syncUser && !syncRole && !syncPassword && !syncSms) {
            throw new BusinessException(400, "请至少勾选一项同步内容（组织/用户/角色/账号密码/短信验证）");
        }

        List<Long> orgIds = longList(body.get("orgIds"));
        List<Long> userIds = longList(body.get("userIds"));
        List<Long> roleIds = longList(body.get("roleIds"));

        LocalDateTime now = LocalDateTime.now();
        SysGovSyncJob job = new SysGovSyncJob();
        job.setTargetId(target.getId());
        job.setTargetName(target.getTargetName());
        job.setSyncOrg(syncOrg ? 1 : 0);
        job.setSyncUser(syncUser ? 1 : 0);
        job.setSyncRole(syncRole ? 1 : 0);
        job.setSyncPassword(syncPassword ? 1 : 0);
        job.setSyncSms(syncSms ? 1 : 0);
        job.setOrgIdsJson(toJson(orgIds));
        job.setUserIdsJson(toJson(userIds));
        job.setRoleIdsJson(toJson(roleIds));
        job.setStatus("RUNNING");
        job.setStartedAt(now);
        job.setCreatedBy(operator.getUserId());
        job.setCreatedByName(operator.getUsername());
        job.setCreatedAt(now);
        jobMapper.insert(job);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetCode", target.getTargetCode());
        payload.put("targetName", target.getTargetName());
        payload.put("syncedAt", now.toString());
        payload.put("operator", operator.getUsername());

        int orgCount = 0;
        int userCount = 0;
        int roleCount = 0;

        if (syncOrg) {
            List<SysOrg> orgs = loadOrgs(orgIds);
            orgCount = orgs.size();
            payload.put("orgs", orgs.stream().map(this::orgPayload).collect(Collectors.toList()));
        }
        if (syncUser || syncPassword || syncSms) {
            List<SysUser> users = loadUsers(userIds, orgIds);
            userCount = users.size();
            List<Map<String, Object>> userPayload = new ArrayList<>();
            for (SysUser u : users) {
                userPayload.add(userPayload(u, syncPassword, syncSms, syncUser));
            }
            payload.put("users", userPayload);
        }
        if (syncRole) {
            List<SysRole> roles = loadRoles(roleIds);
            roleCount = roles.size();
            payload.put("roles", roles.stream().map(this::rolePayload).collect(Collectors.toList()));
        }

        job.setSyncedOrgCount(orgCount);
        job.setSyncedUserCount(userCount);
        job.setSyncedRoleCount(roleCount);
        job.setPayloadSummary(summarize(orgCount, userCount, roleCount, syncPassword, syncSms));

        String status;
        String message;
        String endpoint = target.getEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            status = "LEDGER";
            message = "未配置对接 endpoint：已按所选范围生成本地同步台账（组织 "
                    + orgCount + " / 用户 " + userCount + " / 角色 " + roleCount + "），未向外部推送";
        } else {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
                ResponseEntity<String> resp = restTemplate.postForEntity(endpoint.trim(), entity, String.class);
                if (resp.getStatusCode().is2xxSuccessful()) {
                    status = "SUCCESS";
                    message = "已推送至 " + endpoint.trim() + "，HTTP " + resp.getStatusCode().value();
                } else {
                    status = "FAILED";
                    message = "对接返回非成功状态：HTTP " + resp.getStatusCode().value();
                }
            } catch (RestClientException ex) {
                log.warn("gov sync push failed: {}", ex.getMessage());
                status = "FAILED";
                message = "推送失败：" + truncate(ex.getMessage(), 400);
            }
        }

        job.setStatus(status);
        job.setMessage(message);
        job.setFinishedAt(LocalDateTime.now());
        jobMapper.updateById(job);

        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "GOV_USER_SYNC", "sys_gov_sync_job", String.valueOf(job.getId()), status + " " + message);

        Map<String, Object> out = jobView(job);
        out.put("payloadPreview", previewPayload(payload));
        return out;
    }

    private List<SysOrg> loadOrgs(List<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return orgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                    .eq(SysOrg::getStatus, 1)
                    .orderByAsc(SysOrg::getSortOrder)
                    .orderByAsc(SysOrg::getId));
        }
        return orgMapper.selectBatchIds(orgIds);
    }

    private List<SysUser> loadUsers(List<Long> userIds, List<Long> orgIds) {
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId);
        if (userIds != null && !userIds.isEmpty()) {
            q.in(SysUser::getId, userIds);
        } else if (orgIds != null && !orgIds.isEmpty()) {
            q.in(SysUser::getOrgId, orgIds);
        }
        q.last("LIMIT 2000");
        return userMapper.selectList(q);
    }

    private List<SysRole> loadRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getStatus, 1)
                    .orderByAsc(SysRole::getId));
        }
        return roleMapper.selectBatchIds(roleIds);
    }

    private Map<String, Object> orgPayload(SysOrg o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("orgCode", o.getOrgCode());
        m.put("orgName", o.getOrgName());
        m.put("parentId", o.getParentId());
        m.put("status", o.getStatus());
        return m;
    }

    private Map<String, Object> rolePayload(SysRole r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("roleCode", r.getRoleCode());
        m.put("roleName", r.getRoleName());
        m.put("status", r.getStatus());
        return m;
    }

    private Map<String, Object> userPayload(SysUser u, boolean syncPassword, boolean syncSms, boolean syncUser) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        if (syncUser) {
            m.put("displayName", u.getDisplayName());
            m.put("orgId", u.getOrgId());
            m.put("email", u.getEmail());
            m.put("phone", u.getPhone());
            m.put("status", u.getStatus());
            m.put("roleCodes", userMapper.findRoleCodesByUserId(u.getId()));
        }
        if (syncPassword) {
            m.put("passwordHashSha256", sha256(u.getPasswordHash()));
            m.put("passwordChangedAt", u.getPasswordChangedAt());
        }
        if (syncSms) {
            m.put("phone", u.getPhone());
            m.put("smsAuthEnabled", StringUtils.hasText(u.getPhone()));
            m.put("totpEnabled", u.getTotpEnabled() != null && u.getTotpEnabled() == 1);
        }
        return m;
    }

    private Map<String, Object> jobView(SysGovSyncJob j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("targetId", j.getTargetId());
        m.put("targetName", j.getTargetName());
        m.put("syncOrg", j.getSyncOrg() != null && j.getSyncOrg() == 1);
        m.put("syncUser", j.getSyncUser() != null && j.getSyncUser() == 1);
        m.put("syncRole", j.getSyncRole() != null && j.getSyncRole() == 1);
        m.put("syncPassword", j.getSyncPassword() != null && j.getSyncPassword() == 1);
        m.put("syncSms", j.getSyncSms() != null && j.getSyncSms() == 1);
        m.put("orgIdsJson", j.getOrgIdsJson());
        m.put("userIdsJson", j.getUserIdsJson());
        m.put("roleIdsJson", j.getRoleIdsJson());
        m.put("payloadSummary", j.getPayloadSummary());
        m.put("syncedOrgCount", j.getSyncedOrgCount());
        m.put("syncedUserCount", j.getSyncedUserCount());
        m.put("syncedRoleCount", j.getSyncedRoleCount());
        m.put("status", j.getStatus());
        m.put("message", j.getMessage());
        m.put("startedAt", j.getStartedAt());
        m.put("finishedAt", j.getFinishedAt());
        m.put("createdBy", j.getCreatedBy());
        m.put("createdByName", j.getCreatedByName());
        m.put("createdAt", j.getCreatedAt());
        return m;
    }

    private Map<String, Object> previewPayload(Map<String, Object> payload) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("targetCode", payload.get("targetCode"));
        preview.put("orgCount", sizeOf(payload.get("orgs")));
        preview.put("userCount", sizeOf(payload.get("users")));
        preview.put("roleCount", sizeOf(payload.get("roles")));
        return preview;
    }

    private static int sizeOf(Object v) {
        if (v instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    private String summarize(int orgCount, int userCount, int roleCount, boolean syncPassword, boolean syncSms) {
        StringBuilder sb = new StringBuilder();
        sb.append("组织=").append(orgCount)
                .append("，用户=").append(userCount)
                .append("，角色=").append(roleCount);
        if (syncPassword) {
            sb.append("，含密码摘要");
        }
        if (syncSms) {
            sb.append("，含短信认证字段");
        }
        return sb.toString();
    }

    private String toJson(Object v) {
        try {
            return objectMapper.writeValueAsString(v == null ? List.of() : v);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static String sha256(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return null;
        }
    }

    private static void putBool(Map<String, String> patch, Map<String, Object> body, String field, String key) {
        if (!body.containsKey(field)) {
            return;
        }
        patch.put(key, boolVal(body.get(field)) ? "true" : "false");
    }

    private static boolean boolVal(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s);
    }

    private static Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(v));
    }

    private static List<Long> longList(Object v) {
        List<Long> out = new ArrayList<>();
        if (v == null) {
            return out;
        }
        if (v instanceof List<?> list) {
            for (Object item : list) {
                Long id = longVal(item);
                if (id != null) {
                    out.add(id);
                }
            }
            return out;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return out;
        }
        for (String part : s.split("[,;\\s]+")) {
            Long id = longVal(part);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    private static String str(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static String blankToNull(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
