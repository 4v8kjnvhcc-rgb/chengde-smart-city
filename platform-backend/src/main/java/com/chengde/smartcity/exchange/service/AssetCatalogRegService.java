package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngAssetCatalogReg;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.mapper.IngAssetCatalogRegMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.entity.SysUser;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import com.chengde.smartcity.system.mapper.SysUserMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetCatalogRegService {

    private static final Logger log = LoggerFactory.getLogger(AssetCatalogRegService.class);

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    /** @deprecated 历史状态，等同待审核 */
    public static final String STATUS_PENDING_ARCHIVE = "PENDING_ARCHIVE";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_APPROVED = "APPROVED";
    /** @deprecated 历史状态，等同审核通过 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private static final Set<String> EDITABLE = Set.of(STATUS_DRAFT, STATUS_REJECTED);
    private static final Set<String> SUBMITTABLE = Set.of(STATUS_DRAFT, STATUS_REJECTED);
    private static final Set<String> AUDITABLE = Set.of(STATUS_PENDING_REVIEW, STATUS_PENDING_ARCHIVE);

    private final IngAssetCatalogRegMapper catalogMapper;
    private final IngProjectMapper projectMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final IngDataTableMapper tableMapper;
    private final SysUserMapper userMapper;
    private final SysOrgMapper orgMapper;
    private final Path uploadRoot;

    public AssetCatalogRegService(IngAssetCatalogRegMapper catalogMapper,
                                  IngProjectMapper projectMapper,
                                  IngDataSourceMapper dataSourceMapper,
                                  IngDataTableMapper tableMapper,
                                  SysUserMapper userMapper,
                                  SysOrgMapper orgMapper,
                                  @Value("${app.upload.dir:./data/uploads}") String uploadDir) {
        this.catalogMapper = catalogMapper;
        this.projectMapper = projectMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.tableMapper = tableMapper;
        this.userMapper = userMapper;
        this.orgMapper = orgMapper;
        this.uploadRoot = Paths.get(uploadDir, "asset-catalog").toAbsolutePath().normalize();
    }

    public Map<String, Object> defaults(UserPrincipal operator) {
        // 新增表单所属机构、联系方式默认留空，由用户选择
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orgId", null);
        m.put("orgName", "");
        m.put("contactInfo", "");
        return m;
    }

    /** 组织机构选项：仅叶子单位（如数据对接、高新区民政局），不含「高新区/机关单位」等上级分组 */
    public List<Map<String, Object>> orgOptions() {
        List<SysOrg> orgs = orgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                .eq(SysOrg::getStatus, 1)
                .orderByAsc(SysOrg::getSortOrder)
                .orderByAsc(SysOrg::getId));
        java.util.Set<Long> parentIds = new java.util.HashSet<>();
        for (SysOrg o : orgs) {
            if (o.getParentId() != null && o.getParentId() > 0) {
                parentIds.add(o.getParentId());
            }
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (SysOrg o : orgs) {
            if (o.getId() != null && parentIds.contains(o.getId())) {
                continue; // 有下级，跳过上级分组
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", o.getId());
            row.put("orgCode", o.getOrgCode());
            row.put("orgName", o.getOrgName());
            row.put("parentId", o.getParentId());
            row.put("label", o.getOrgName() == null ? "" : o.getOrgName());
            out.add(row);
        }
        return out;
    }

    /** 某机构下账号联系方式（来自为本单位新建账号时填写的 phone） */
    public List<Map<String, Object>> contactsByOrg(Long orgId) {
        if (orgId == null) {
            return List.of();
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getOrgId, orgId)
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (SysUser u : users) {
            String phone = u.getPhone();
            if (phone == null || phone.isBlank()) {
                continue;
            }
            phone = phone.trim();
            if (!seen.add(phone)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("phone", phone);
            String name = u.getDisplayName();
            if (name == null || name.isBlank()) {
                name = u.getUsername();
            }
            row.put("displayName", name);
            // 下拉只展示电话号码
            row.put("label", phone);
            out.add(row);
        }
        return out;
    }

    public List<IngAssetCatalogReg> list(String assetName, String orgName, String projectName, String status) {
        LambdaQueryWrapper<IngAssetCatalogReg> q = new LambdaQueryWrapper<IngAssetCatalogReg>()
                .orderByDesc(IngAssetCatalogReg::getId);
        if (assetName != null && !assetName.isBlank()) {
            q.like(IngAssetCatalogReg::getAssetName, assetName.trim());
        }
        if (orgName != null && !orgName.isBlank()) {
            q.like(IngAssetCatalogReg::getOrgName, orgName.trim());
        }
        if (projectName != null && !projectName.isBlank()) {
            q.like(IngAssetCatalogReg::getProjectName, projectName.trim());
        }
        if (status != null && !status.isBlank()) {
            String st = status.trim().toUpperCase(Locale.ROOT);
            if ("PENDING_REVIEW".equals(st) || "PENDING".equals(st)) {
                q.in(IngAssetCatalogReg::getStatus, STATUS_PENDING_REVIEW, STATUS_PENDING_ARCHIVE, "PENDING");
            } else if ("APPROVED".equals(st)) {
                q.in(IngAssetCatalogReg::getStatus, STATUS_APPROVED, STATUS_ARCHIVED);
            } else {
                q.eq(IngAssetCatalogReg::getStatus, st);
            }
        }
        return catalogMapper.selectList(q);
    }

    public IngAssetCatalogReg get(Long id) {
        IngAssetCatalogReg e = catalogMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(404, "资产目录登记不存在");
        }
        return e;
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        IngAssetCatalogReg e = new IngAssetCatalogReg();
        applyBody(e, body, true);
        e.setStatus(STATUS_DRAFT);
        e.setCreatedBy(operator.getUsername());
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        catalogMapper.insert(e);
        log.info("asset catalog reg created id={} by={}", e.getId(), operator.getUsername());
        return e.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngAssetCatalogReg e = get(id);
        if (!EDITABLE.contains(norm(e.getStatus()))) {
            throw new BusinessException(400, "仅草稿或已驳回状态可编辑");
        }
        applyBody(e, body, false);
        if (STATUS_REJECTED.equals(norm(e.getStatus()))) {
            e.setStatus(STATUS_DRAFT);
            e.setRejectReason(null);
        }
        e.setUpdatedAt(LocalDateTime.now());
        catalogMapper.updateById(e);
        log.info("asset catalog reg updated id={} by={}", id, operator.getUsername());
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        IngAssetCatalogReg e = get(id);
        String st = norm(e.getStatus());
        boolean approved = STATUS_APPROVED.equals(st) || STATUS_ARCHIVED.equals(st);
        if (approved) {
            if (operator == null || !operator.isSystemAdmin()) {
                throw new BusinessException(403, "审核通过的资产目录仅超级管理员可删除");
            }
        } else if (!STATUS_DRAFT.equals(st) && !STATUS_REJECTED.equals(st)) {
            throw new BusinessException(400, "仅草稿、驳回待提交可删除；审核通过仅超级管理员可删");
        }
        catalogMapper.deleteById(id);
        log.info("asset catalog reg deleted id={} status={} by={}", id, st, operator.getUsername());
    }

    /** 提交审核（兼容旧 /report 接口） */
    @Transactional
    public void report(UserPrincipal operator, Long id) {
        IngAssetCatalogReg e = get(id);
        if (!SUBMITTABLE.contains(norm(e.getStatus()))) {
            throw new BusinessException(400, "仅草稿或驳回待提交可提交审核");
        }
        if (blank(e.getAssetName()) || e.getProjectId() == null || e.getSourceId() == null || e.getTableId() == null) {
            throw new BusinessException(400, "提交前请完善资产名称、项目、数据源与数据表");
        }
        e.setStatus(STATUS_PENDING_REVIEW);
        e.setRejectReason(null);
        e.setReportedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        catalogMapper.updateById(e);
        log.info("asset catalog reg submitted id={} by={}", id, operator.getUsername());
    }

    @Transactional
    public void reject(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngAssetCatalogReg e = get(id);
        if (!AUDITABLE.contains(norm(e.getStatus()))) {
            throw new BusinessException(400, "仅待审核状态可驳回");
        }
        String reason = str(body != null ? body.get("reason") : null, "");
        if (reason.isBlank()) {
            throw new BusinessException(400, "驳回须填写原因");
        }
        e.setStatus(STATUS_REJECTED);
        e.setRejectReason(reason.trim());
        e.setUpdatedAt(LocalDateTime.now());
        catalogMapper.updateById(e);
        log.info("asset catalog reg rejected id={} by={}", id, operator.getUsername());
    }

    /** 审核通过（兼容旧 /archive 接口） */
    @Transactional
    public void archive(UserPrincipal operator, Long id) {
        IngAssetCatalogReg e = get(id);
        if (!AUDITABLE.contains(norm(e.getStatus()))) {
            throw new BusinessException(400, "仅待审核状态可审核通过");
        }
        e.setStatus(STATUS_APPROVED);
        e.setRejectReason(null);
        e.setArchivedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        catalogMapper.updateById(e);
        log.info("asset catalog reg approved id={} by={}", id, operator.getUsername());
    }

    public Map<String, Object> upload(UserPrincipal operator, MultipartFile file, String kind) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择附件文件");
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            original = "file.bin";
        }
        String safeName = original.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safeName.length() > 180) {
            safeName = safeName.substring(safeName.length() - 180);
        }
        String prefix = "quality".equalsIgnoreCase(kind) ? "quality" : "risk";
        try {
            Files.createDirectories(uploadRoot);
            String stored = prefix + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;
            Path target = uploadRoot.resolve(stored).normalize();
            if (!target.startsWith(uploadRoot)) {
                throw new BusinessException(400, "非法文件路径");
            }
            file.transferTo(target);
            Map<String, Object> r = new HashMap<>();
            r.put("fileName", safeName);
            r.put("filePath", target.toString());
            r.put("kind", prefix);
            r.put("uploadedBy", operator.getUsername());
            return r;
        } catch (IOException ex) {
            log.warn("upload asset catalog attachment failed: {}", ex.getMessage());
            throw new BusinessException(500, "附件上传失败");
        }
    }

    private void applyBody(IngAssetCatalogReg e, Map<String, Object> body, boolean creating) {
        String assetName = str(body.get("assetName"), creating ? null : e.getAssetName());
        if (blank(assetName)) {
            throw new BusinessException(400, "资产名称不能为空");
        }
        e.setAssetName(assetName.trim());
        e.setAssetDesc(str(body.get("assetDesc"), null));
        e.setOwnerName(str(body.get("ownerName"), null));
        e.setContactInfo(str(body.get("contactInfo"), null));
        e.setDataTags(str(body.get("dataTags"), null));
        e.setBizPurpose(str(body.get("bizPurpose"), null));
        e.setBizScenario(str(body.get("bizScenario"), null));
        e.setAccessScope(str(body.get("accessScope"), null));
        e.setControlReq(str(body.get("controlReq"), null));
        e.setOtherInfo(str(body.get("otherInfo"), null));
        e.setAccessMode(str(body.get("accessMode"), null));
        e.setTransferMode(str(body.get("transferMode"), null));

        if (body.containsKey("qualityFilePath")) {
            e.setQualityFilePath(str(body.get("qualityFilePath"), null));
            e.setQualityFileName(str(body.get("qualityFileName"), null));
        }
        if (body.containsKey("riskFilePath")) {
            e.setRiskFilePath(str(body.get("riskFilePath"), null));
            e.setRiskFileName(str(body.get("riskFileName"), null));
        }

        Long projectId = longVal(body.get("projectId"));
        if (projectId == null) {
            throw new BusinessException(400, "请选择来源项目");
        }
        IngProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(400, "来源项目不存在");
        }
        e.setProjectId(projectId);
        e.setProjectName(project.getProjectName());

        // 所属机构：优先登记页选择/自定义，不再强制覆盖为项目绑定机构
        Long orgId = longVal(body.get("orgId"));
        String orgName = str(body.get("orgName"), null);
        if (orgId != null) {
            SysOrg org = orgMapper.selectById(orgId);
            if (org != null) {
                e.setOrgId(orgId);
                e.setOrgName(org.getOrgName());
            } else if (!blank(orgName)) {
                e.setOrgId(null);
                e.setOrgName(orgName.trim());
            } else {
                e.setOrgId(null);
                e.setOrgName(null);
            }
        } else if (!blank(orgName)) {
            e.setOrgId(null);
            e.setOrgName(orgName.trim());
        } else if (project.getBoundOrgId() != null) {
            e.setOrgId(project.getBoundOrgId());
            SysOrg org = orgMapper.selectById(project.getBoundOrgId());
            e.setOrgName(org != null ? org.getOrgName() : null);
        } else {
            e.setOrgId(null);
            e.setOrgName(null);
        }

        Long sourceId = longVal(body.get("sourceId"));
        if (sourceId == null) {
            throw new BusinessException(400, "请选择数据源/系统");
        }
        IngDataSource ds = dataSourceMapper.selectById(sourceId);
        if (ds == null) {
            throw new BusinessException(400, "数据源不存在");
        }
        if (ds.getProjectId() != null && !ds.getProjectId().equals(projectId)) {
            throw new BusinessException(400, "数据源不属于所选项目");
        }
        e.setSourceId(sourceId);
        String systemName = ds.getSystemName();
        if (blank(systemName)) {
            systemName = ds.getSourceName();
        }
        e.setSystemName(systemName);

        String mappedFormat = mapFormatType(ds.getSourceType());
        if (mappedFormat != null) {
            e.setFormatType(mappedFormat);
            e.setFormatLocked(1);
        } else {
            e.setFormatType(str(body.get("formatType"), null));
            e.setFormatLocked(0);
        }

        Long tableId = longVal(body.get("tableId"));
        if (tableId == null) {
            throw new BusinessException(400, "请选择数据表");
        }
        IngDataTable table = tableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException(400, "数据表不存在，请先在库表登记中完成登记");
        }
        if (table.getSourceId() != null && !table.getSourceId().equals(sourceId)) {
            throw new BusinessException(400, "数据表不属于所选数据源");
        }
        e.setTableId(tableId);
        e.setTableName(blank(table.getTableName()) ? table.getTableCode() : table.getTableName());
    }

    private static String mapFormatType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return null;
        }
        String t = sourceType.trim().toUpperCase(Locale.ROOT);
        if (t.contains("FILE") || t.equals("CSV") || t.equals("EXCEL") || t.equals("FTP") || t.equals("SFTP")) {
            return "FILE";
        }
        if (t.contains("API") || t.equals("HTTP") || t.equals("REST") || t.equals("WS")) {
            return "API";
        }
        if (t.contains("DB") || t.contains("JDBC") || t.contains("MYSQL") || t.contains("ORACLE")
                || t.contains("POSTGRES") || t.equals("DATABASE") || t.equals("TABLE")) {
            return "DATABASE";
        }
        return null;
    }

    private static String norm(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String str(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? def : s;
    }

    private static Long longVal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
