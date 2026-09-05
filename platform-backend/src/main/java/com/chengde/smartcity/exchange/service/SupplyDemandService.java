package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.BizCatalogObjection;
import com.chengde.smartcity.exchange.entity.BizCollectTask;
import com.chengde.smartcity.exchange.entity.BizDataDemand;
import com.chengde.smartcity.exchange.entity.BizDataDuty;
import com.chengde.smartcity.exchange.entity.BizDemandSupplyTask;
import com.chengde.smartcity.exchange.entity.BizDemandTemplate;
import com.chengde.smartcity.exchange.entity.BizEsbFlow;
import com.chengde.smartcity.exchange.entity.BizGovMatter;
import com.chengde.smartcity.exchange.entity.BizSupplyManifest;
import com.chengde.smartcity.exchange.entity.BizSupplySetting;
import com.chengde.smartcity.exchange.mapper.BizCatalogItemMapper;
import com.chengde.smartcity.exchange.mapper.BizCatalogObjectionMapper;
import com.chengde.smartcity.exchange.mapper.BizCollectTaskMapper;
import com.chengde.smartcity.exchange.mapper.BizDataDemandMapper;
import com.chengde.smartcity.exchange.mapper.BizDataDutyMapper;
import com.chengde.smartcity.exchange.mapper.BizDemandSupplyTaskMapper;
import com.chengde.smartcity.exchange.mapper.BizDemandTemplateMapper;
import com.chengde.smartcity.exchange.mapper.BizEsbFlowMapper;
import com.chengde.smartcity.exchange.mapper.BizGovMatterMapper;
import com.chengde.smartcity.exchange.mapper.BizSupplyManifestMapper;
import com.chengde.smartcity.exchange.mapper.BizSupplySettingMapper;
import com.chengde.smartcity.masterdata.entity.GovCatalogApproval;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.mapper.GovCatalogApprovalMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.AuditLog;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SupplyDemandService {

    private static final Logger log = LoggerFactory.getLogger(SupplyDemandService.class);

    private static final String PATH_AUTHORIZE = "AUTHORIZE_EXISTING";
    private static final String PATH_COLLECT = "NEED_COLLECT";
    private static final String SETTING_RESPONSE_DAYS = "response_deadline_days";
    private static final String SETTING_MOUNT_DAYS = "mount_deadline_days";
    private static final int DEFAULT_DEADLINE_DAYS = 10;

    private final BizDemandTemplateMapper templateMapper;
    private final BizDataDemandMapper demandMapper;
    private final BizCatalogItemMapper catalogMapper;
    private final BizDemandSupplyTaskMapper supplyTaskMapper;
    private final BizCatalogObjectionMapper objectionMapper;
    private final BizSupplyManifestMapper manifestMapper;
    private final BizEsbFlowMapper esbFlowMapper;
    private final BizDataDutyMapper dutyMapper;
    private final BizCollectTaskMapper collectTaskMapper;
    private final GovCatalogApprovalMapper approvalMapper;
    private final GovCatalogResourceMapper govResourceMapper;
    private final BizGovMatterMapper matterMapper;
    private final BizSupplySettingMapper supplySettingMapper;
    private final SysOrgMapper orgMapper;
    private final AuditService auditService;

    /** 预审相关（兼容旧 ANALYZING，写入时收敛为 PRE_AUDITING） */
    private static final Set<String> PRE_AUDIT_STATUSES = Set.of(
            "PRE_AUDITING", "ANALYZING", "DISPATCHED", "SUPERVISING", "PROVIDER_RETURNED");
    /** 提供部门可确认：已分发/督办中/异议回流 */
    private static final Set<String> AUDIT_CONFIRMABLE = Set.of("DISPATCHED", "SUPERVISING", "CORRECTION");
    /** 需求部门可编辑填报：草稿 / 撤销待提交 / 已退回 */
    private static final Set<String> DEMAND_EDITABLE = Set.of("DRAFT", "WITHDRAW_PENDING", "RETURNED");

    private void requirePlatformAdmin(UserPrincipal operator) {
        if (operator == null) {
            throw new BusinessException(401, "未登录");
        }
        if (operator.isSystemAdmin() || operator.isPlatformAdmin()) {
            return;
        }
        if (operator.getPermissions() != null
                && (operator.getPermissions().contains("portal:supply:approve")
                || operator.getPermissions().contains("system:exchange:supply-config"))) {
            return;
        }
        throw new BusinessException(403, "仅平台管理员（数据主管部门）可操作");
    }

    private void requireDemandOperator(UserPrincipal operator) {
        if (operator == null) {
            throw new BusinessException(401, "未登录");
        }
        if (operator.isSystemAdmin() || operator.isPlatformAdmin() || operator.isDeptAdmin()) {
            return;
        }
        if (operator.getPermissions() != null
                && (operator.getPermissions().contains("portal:supply:create")
                || operator.getPermissions().contains("portal:supply:approve"))) {
            return;
        }
        throw new BusinessException(403, "无权操作数据需求");
    }

    private void requireProviderOperator(UserPrincipal operator) {
        requireDemandOperator(operator);
    }

    @Value("${app.exchange.supply.dispatch-downstream:false}")
    private boolean dispatchDownstream;

    private final Path attachmentUploadRoot;

    public SupplyDemandService(BizDemandTemplateMapper templateMapper, BizDataDemandMapper demandMapper,
                               BizCatalogItemMapper catalogMapper, BizDemandSupplyTaskMapper supplyTaskMapper,
                               BizCatalogObjectionMapper objectionMapper, BizSupplyManifestMapper manifestMapper,
                               BizEsbFlowMapper esbFlowMapper, BizDataDutyMapper dutyMapper,
                               BizCollectTaskMapper collectTaskMapper,
                               GovCatalogApprovalMapper approvalMapper, GovCatalogResourceMapper govResourceMapper,
                               BizGovMatterMapper matterMapper, BizSupplySettingMapper supplySettingMapper,
                               SysOrgMapper orgMapper, AuditService auditService,
                               @Value("${app.upload.dir:./data/uploads}") String uploadDir) {
        this.templateMapper = templateMapper;
        this.demandMapper = demandMapper;
        this.catalogMapper = catalogMapper;
        this.attachmentUploadRoot = Paths.get(uploadDir, "supply-demand").toAbsolutePath().normalize();
        this.supplyTaskMapper = supplyTaskMapper;
        this.objectionMapper = objectionMapper;
        this.manifestMapper = manifestMapper;
        this.esbFlowMapper = esbFlowMapper;
        this.dutyMapper = dutyMapper;
        this.collectTaskMapper = collectTaskMapper;
        this.approvalMapper = approvalMapper;
        this.govResourceMapper = govResourceMapper;
        this.matterMapper = matterMapper;
        this.supplySettingMapper = supplySettingMapper;
        this.orgMapper = orgMapper;
        this.auditService = auditService;
    }

    public List<BizDemandTemplate> listTemplates() {
        return templateMapper.selectList(new LambdaQueryWrapper<BizDemandTemplate>()
                .orderByAsc(BizDemandTemplate::getId));
    }

    public List<BizDemandTemplate> listActiveTemplates() {
        return templateMapper.selectList(new LambdaQueryWrapper<BizDemandTemplate>()
                .eq(BizDemandTemplate::getStatus, "ACTIVE")
                .orderByAsc(BizDemandTemplate::getId));
    }

    @Transactional
    public Long createTemplate(UserPrincipal operator, Map<String, Object> body) {
        requirePlatformAdmin(operator);
        BizDemandTemplate t = new BizDemandTemplate();
        t.setTemplateCode(str(body.get("templateCode"), "TPL_" + UUID.randomUUID().toString().substring(0, 8)));
        t.setTemplateName(required(body.get("templateName"), "模板名称").toString());
        t.setDemandType(str(body.get("demandType"), "STRUCTURED"));
        t.setFieldSchema(str(body.get("fieldSchema"), "{}"));
        t.setStatus(str(body.get("status"), "ACTIVE"));
        templateMapper.insert(t);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_TEMPLATE_CREATE", "biz_demand_template", String.valueOf(t.getId()), t.getTemplateName());
        return t.getId();
    }

    @Transactional
    public void updateTemplate(UserPrincipal operator, Long id, Map<String, Object> body) {
        requirePlatformAdmin(operator);
        BizDemandTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (body.get("templateName") != null) {
            t.setTemplateName(String.valueOf(body.get("templateName")));
        }
        if (body.get("demandType") != null) {
            t.setDemandType(String.valueOf(body.get("demandType")));
        }
        if (body.get("fieldSchema") != null) {
            t.setFieldSchema(String.valueOf(body.get("fieldSchema")));
        }
        if (body.get("status") != null) {
            t.setStatus(String.valueOf(body.get("status")));
        }
        templateMapper.updateById(t);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_TEMPLATE_UPDATE", "biz_demand_template", String.valueOf(id), t.getTemplateName());
    }

    @Transactional
    public void deleteTemplate(UserPrincipal operator, Long id) {
        requirePlatformAdmin(operator);
        BizDemandTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "模板不存在");
        }
        templateMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_TEMPLATE_DELETE", "biz_demand_template", String.valueOf(id), t.getTemplateName());
    }

    public List<BizDataDemand> listDemands(String stage, String status) {
        return listDemands(null, stage, status);
    }

    /**
     * 需求列表。部门管理员仅返回与本组织相关（需求方或提供方）的记录；平台/超管看全量。
     */
    public List<BizDataDemand> listDemands(UserPrincipal operator, String stage, String status) {
        LambdaQueryWrapper<BizDataDemand> q = new LambdaQueryWrapper<BizDataDemand>()
                .orderByDesc(BizDataDemand::getUpdatedAt)
                .orderByDesc(BizDataDemand::getId);
        if (stage != null && !stage.isBlank()) {
            q.eq(BizDataDemand::getStage, stage);
        }
        if (status != null && !status.isBlank()) {
            q.eq(BizDataDemand::getStatus, status);
        }
        if (operator != null && !isListCenterPlatform(operator)) {
            String orgName = resolveOrgName(operator);
            if (orgName != null && !orgName.isBlank()) {
                q.and(w -> w.eq(BizDataDemand::getRequesterOrg, orgName)
                        .or()
                        .eq(BizDataDemand::getAssigneeOrg, orgName));
            }
        }
        return demandMapper.selectList(q);
    }

    /**
     * 需求跟踪：摘要（名称/申请单位/目前状态）+ 各阶段流水（状态/结果/创建时间）。
     */
    public Map<String, Object> getDemandTrack(Long id) {
        BizDataDemand demand = getDemand(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", demand.getId());
        out.put("demandTitle", demand.getDemandTitle());
        out.put("requesterOrg", demand.getRequesterOrg());
        out.put("status", demand.getStatus());
        out.put("stage", demand.getStage());
        out.put("stages", buildDemandTrackStages(demand));
        return out;
    }

    private List<Map<String, Object>> buildDemandTrackStages(BizDataDemand demand) {
        List<AuditLog> logs = auditService.listByResource("biz_data_demand", String.valueOf(demand.getId()));
        List<Map<String, Object>> stages = new ArrayList<>();
        for (AuditLog a : logs) {
            if (a.getAction() != null && a.getAction().startsWith("DEMAND_TEMPLATE")) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", demandActionStatusLabel(a.getAction()));
            row.put("result", demandActionResult(a.getAction(), a.getDetail()));
            row.put("createdAt", a.getCreatedAt());
            stages.add(row);
        }
        if (!stages.isEmpty()) {
            return stages;
        }
        return synthesizeDemandTrackStages(demand);
    }

    private List<Map<String, Object>> synthesizeDemandTrackStages(BizDataDemand demand) {
        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(trackStage(
                statusZh(demand.getStatus() != null ? demand.getStatus() : "DRAFT"),
                firstNonBlank(demand.getAnalysisNote(), demand.getConfirmNote(), "需求已创建"),
                demand.getCreatedAt() != null ? demand.getCreatedAt() : demand.getUpdatedAt()));
        if (demand.getSuperviseAt() != null || (demand.getSuperviseNote() != null && !demand.getSuperviseNote().isBlank())) {
            stages.add(trackStage("督办中",
                    firstNonBlank(demand.getSuperviseNote(), "已发起督办"),
                    demand.getSuperviseAt() != null ? demand.getSuperviseAt() : demand.getUpdatedAt()));
        }
        if (demand.getConfirmNote() != null && !demand.getConfirmNote().isBlank()) {
            stages.add(trackStage(statusZh(demand.getStatus()),
                    demand.getConfirmNote(),
                    demand.getUpdatedAt()));
        }
        if (demand.getCatalogMountedAt() != null || "CATALOG_MOUNTED".equals(demand.getStatus())) {
            stages.add(trackStage("已挂载",
                    firstNonBlank(demand.getConfirmNote(), "目录已挂载至门户"),
                    demand.getCatalogMountedAt() != null ? demand.getCatalogMountedAt() : demand.getUpdatedAt()));
        }
        if ("COMPLETED".equals(demand.getStatus())) {
            stages.add(trackStage("已办结", "需求办结", demand.getUpdatedAt()));
        }
        return stages;
    }

    private Map<String, Object> trackStage(String status, String result, Object createdAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", status);
        row.put("result", result != null && !result.isBlank() ? result : "—");
        row.put("createdAt", createdAt);
        return row;
    }

    private String demandActionStatusLabel(String action) {
        if (action == null) {
            return "—";
        }
        return switch (action) {
            case "DEMAND_DRAFT" -> "草稿";
            case "DEMAND_SUBMIT" -> "待数据主管部门审核";
            case "DEMAND_WITHDRAW" -> "撤销待提交";
            case "DEMAND_UPDATE" -> "已修改";
            case "DEMAND_DELETE" -> "已删除";
            case "DEMAND_ANALYZE", "DEMAND_ANALYSIS_APPLY" -> "预审中";
            case "DEMAND_DISPATCH" -> "待确认";
            case "DEMAND_RETURN", "DEMAND_RETURN_PORTAL", "DEMAND_ADMIN_AGREE_RETURN" -> "已退回";
            case "DEMAND_SUPERVISE" -> "督办中";
            case "DEMAND_CONFIRM" -> "已确认";
            case "DEMAND_PROVIDER_RETURN" -> "提供方退回待裁决";
            case "DEMAND_ADMIN_REFUSE_RETURN" -> "待确认";
            case "DEMAND_CATALOG_MOUNTED" -> "目录已挂载";
            case "DEMAND_CONFIRM_FEEDBACK" -> "督查反馈";
            case "DEMAND_COMPLETE" -> "已办结";
            case "DEMAND_CANCEL" -> "已撤销";
            case "DEMAND_CORRECTION" -> "异议回流";
            default -> action.startsWith("DEMAND_") ? action.substring("DEMAND_".length()) : action;
        };
    }

    private String demandActionResult(String action, String detail) {
        if (detail != null && !detail.isBlank()) {
            return detail;
        }
        if (action == null) {
            return "—";
        }
        return switch (action) {
            case "DEMAND_DRAFT" -> "暂存草稿";
            case "DEMAND_SUBMIT" -> "提交成功";
            case "DEMAND_WITHDRAW" -> "已撤销待重新提交";
            case "DEMAND_DISPATCH" -> "已分发至提供部门";
            case "DEMAND_CONFIRM" -> "同意提供";
            case "DEMAND_COMPLETE" -> "办结";
            case "DEMAND_CANCEL" -> "已撤销";
            case "DEMAND_CATALOG_MOUNTED" -> "目录已挂载至门户";
            default -> "处理完成";
        };
    }

    private String statusZh(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "SUBMITTED" -> "待数据主管部门审核";
            case "WITHDRAW_PENDING" -> "撤销待提交";
            case "PRE_AUDITING", "ANALYZING" -> "预审中";
            case "DISPATCHED" -> "待确认";
            case "SUPERVISING" -> "督办中";
            case "PROVIDER_RETURNED" -> "提供方退回待裁决";
            case "RETURNED" -> "已退回";
            case "CONFIRMED" -> "已确认";
            case "CATALOG_MOUNTED" -> "已挂载";
            case "CORRECTION" -> "异议回流";
            case "COMPLETED" -> "已办结";
            case "CANCELLED" -> "已撤销";
            default -> status;
        };
    }

    /** 需求附件上传 */
    public Map<String, Object> uploadAttachment(UserPrincipal operator, MultipartFile file) {
        requireDemandOperator(operator);
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
        try {
            Files.createDirectories(attachmentUploadRoot);
            String stored = System.currentTimeMillis() + "_"
                    + UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;
            Path target = attachmentUploadRoot.resolve(stored).normalize();
            if (!target.startsWith(attachmentUploadRoot)) {
                throw new BusinessException(400, "非法文件路径");
            }
            file.transferTo(target);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("fileName", safeName);
            r.put("filePath", stored);
            r.put("url", "/api/v1/exchange/supply/attachments/" + stored);
            r.put("size", file.getSize());
            r.put("uploadedBy", operator.getUsername());
            return r;
        } catch (IOException ex) {
            log.warn("upload supply demand attachment failed: {}", ex.getMessage());
            throw new BusinessException(500, "附件上传失败");
        }
    }

    public Path resolveAttachment(String relative) {
        if (relative == null || relative.isBlank()) {
            throw new BusinessException(404, "附件不存在");
        }
        String name = relative.replace("\\", "/");
        if (name.contains("..") || name.contains("/")) {
            throw new BusinessException(400, "非法文件路径");
        }
        Path target = attachmentUploadRoot.resolve(name).normalize();
        if (!target.startsWith(attachmentUploadRoot) || !Files.isRegularFile(target)) {
            throw new BusinessException(404, "附件不存在");
        }
        return target;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "—";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "—";
    }

    public List<BizDataDuty> listDuties(Long demandId) {
        LambdaQueryWrapper<BizDataDuty> q = new LambdaQueryWrapper<BizDataDuty>().orderByDesc(BizDataDuty::getId);
        if (demandId != null) {
            q.eq(BizDataDuty::getDemandId, demandId);
        }
        return dutyMapper.selectList(q);
    }

    @Transactional
    public Long createDemand(UserPrincipal operator, Map<String, Object> body) {
        requireDemandOperator(operator);
        BizDataDemand demand = new BizDataDemand();
        applyDemandForm(demand, body, operator, true);
        boolean draft = Boolean.TRUE.equals(body.get("draft"))
                || "DRAFT".equalsIgnoreCase(str(body.get("status"), ""));
        demand.setStatus(draft ? "DRAFT" : "SUBMITTED");
        demand.setStage("MANAGE");
        demand.setCreatedBy(operator.getUsername());
        demandMapper.insert(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                draft ? "DEMAND_DRAFT" : "DEMAND_SUBMIT", "biz_data_demand",
                String.valueOf(demand.getId()), demand.getDemandTitle());
        return demand.getId();
    }

    @Transactional
    public void withdrawDemand(UserPrincipal operator, Long id) {
        requireDemandOperator(operator);
        BizDataDemand demand = getDemand(id);
        if (!"SUBMITTED".equals(demand.getStatus())) {
            throw new BusinessException(400, "仅待数据主管部门审核的需求可撤销");
        }
        demand.setStatus("WITHDRAW_PENDING");
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_WITHDRAW", "biz_data_demand", String.valueOf(id), demand.getDemandTitle());
    }

    @Transactional
    public void submitDemand(UserPrincipal operator, Long id) {
        requireDemandOperator(operator);
        BizDataDemand demand = getDemand(id);
        if (!DEMAND_EDITABLE.contains(demand.getStatus())) {
            throw new BusinessException(400, "仅草稿、撤销待提交或已退回状态可提交");
        }
        if (demand.getDemandTitle() == null || demand.getDemandTitle().isBlank()) {
            throw new BusinessException(400, "请填写数据名称");
        }
        demand.setStatus("SUBMITTED");
        demand.setStage("MANAGE");
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_SUBMIT", "biz_data_demand", String.valueOf(id), demand.getDemandTitle());
    }

    @Transactional
    public void deleteDemand(UserPrincipal operator, Long id) {
        requireDemandOperator(operator);
        BizDataDemand demand = getDemand(id);
        if (!DEMAND_EDITABLE.contains(demand.getStatus())) {
            throw new BusinessException(400, "仅草稿、撤销待提交或已退回状态可删除");
        }
        demandMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_DELETE", "biz_data_demand", String.valueOf(id), demand.getDemandTitle());
    }

    public List<BizGovMatter> listMatters(String keyword, String matterType, String status) {
        ensureBuiltinMatters();
        LambdaQueryWrapper<BizGovMatter> q = new LambdaQueryWrapper<BizGovMatter>()
                .orderByDesc(BizGovMatter::getUpdatedAt)
                .orderByDesc(BizGovMatter::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(BizGovMatter::getMatterName, keyword.trim())
                    .or().like(BizGovMatter::getMatterCode, keyword.trim()));
        }
        if (matterType != null && !matterType.isBlank()) {
            q.eq(BizGovMatter::getMatterType, matterType.trim());
        }
        if (status != null && !status.isBlank()) {
            q.eq(BizGovMatter::getStatus, status.trim());
        }
        return matterMapper.selectList(q);
    }

    /** 若库中无事项，兜底写入国家/省/市示例（Flyway 未执行时仍可用） */
    private void ensureBuiltinMatters() {
        Long cnt = matterMapper.selectCount(null);
        if (cnt != null && cnt > 0) {
            return;
        }
        String[][] seeds = {
                {"NATION-001", "结婚登记", "户籍婚姻", "NATIONAL", "1"},
                {"NATION-002", "出生医学证明签发", "户籍婚姻", "NATIONAL", "2"},
                {"NATION-003", "居民身份证申领", "证件证照", "NATIONAL", "3"},
                {"HEBEI-001", "居住证办理", "证件证照", "PROVINCE", "10"},
                {"HEBEI-002", "社保卡申领", "社会保障", "PROVINCE", "11"},
                {"HEBEI-003", "不动产登记查询", "不动产", "PROVINCE", "12"},
                {"CD-001", "高新技术企业认定", "企业服务", "CITY", "20"},
                {"CD-002", "建设项目规划许可", "工程建设", "CITY", "21"},
                {"CD-003", "公共场所卫生许可", "卫生健康", "CITY", "22"},
                {"CD-004", "社会救助证明开具", "社会救助", "CITY", "23"},
        };
        for (String[] s : seeds) {
            BizGovMatter m = new BizGovMatter();
            m.setMatterCode(s[0]);
            m.setMatterName(s[1]);
            m.setMatterType(s[2]);
            m.setRegionScope(s[3]);
            m.setStatus("ACTIVE");
            m.setSortOrder(Integer.parseInt(s[4]));
            m.setCreatedBy("sys_admin");
            try {
                matterMapper.insert(m);
            } catch (Exception ex) {
                log.debug("seed matter skip {}: {}", s[0], ex.getMessage());
            }
        }
    }

    @Transactional
    public Long createMatter(UserPrincipal operator, Map<String, Object> body) {
        requirePlatformAdmin(operator);
        BizGovMatter m = new BizGovMatter();
        m.setMatterCode(required(body.get("matterCode"), "事项编码").toString().trim());
        m.setMatterName(required(body.get("matterName"), "事项名称").toString().trim());
        m.setMatterType(str(body.get("matterType"), "OTHER"));
        m.setRegionScope(str(body.get("regionScope"), "CITY"));
        m.setStatus(str(body.get("status"), "ACTIVE"));
        m.setSortOrder(intVal(body.get("sortOrder"), 100));
        m.setCreatedBy(operator == null ? null : operator.getUsername());
        Long exists = matterMapper.selectCount(new LambdaQueryWrapper<BizGovMatter>()
                .eq(BizGovMatter::getMatterCode, m.getMatterCode()));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "事项编码已存在");
        }
        matterMapper.insert(m);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "GOV_MATTER_CREATE", "biz_gov_matter", String.valueOf(m.getId()), m.getMatterName());
        return m.getId();
    }

    @Transactional
    public void updateMatter(UserPrincipal operator, Long id, Map<String, Object> body) {
        requirePlatformAdmin(operator);
        BizGovMatter m = matterMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(404, "事项不存在");
        }
        if (body.get("matterCode") != null) {
            String code = String.valueOf(body.get("matterCode")).trim();
            Long dup = matterMapper.selectCount(new LambdaQueryWrapper<BizGovMatter>()
                    .eq(BizGovMatter::getMatterCode, code)
                    .ne(BizGovMatter::getId, id));
            if (dup != null && dup > 0) {
                throw new BusinessException(400, "事项编码已存在");
            }
            m.setMatterCode(code);
        }
        if (body.get("matterName") != null) {
            m.setMatterName(String.valueOf(body.get("matterName")).trim());
        }
        if (body.get("matterType") != null) {
            m.setMatterType(String.valueOf(body.get("matterType")));
        }
        if (body.get("regionScope") != null) {
            m.setRegionScope(String.valueOf(body.get("regionScope")));
        }
        if (body.get("status") != null) {
            m.setStatus(String.valueOf(body.get("status")));
        }
        if (body.get("sortOrder") != null) {
            m.setSortOrder(intVal(body.get("sortOrder"), m.getSortOrder() == null ? 0 : m.getSortOrder()));
        }
        matterMapper.updateById(m);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "GOV_MATTER_UPDATE", "biz_gov_matter", String.valueOf(id), m.getMatterName());
    }

    @Transactional
    public void deleteMatter(UserPrincipal operator, Long id) {
        requirePlatformAdmin(operator);
        BizGovMatter m = matterMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(404, "事项不存在");
        }
        matterMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "GOV_MATTER_DELETE", "biz_gov_matter", String.valueOf(id), m.getMatterName());
    }

    private void applyDemandForm(BizDataDemand demand, Map<String, Object> body, UserPrincipal operator, boolean creating) {
        Object titleObj = body.get("demandTitle");
        if (titleObj == null || String.valueOf(titleObj).isBlank()) {
            titleObj = body.get("dataName");
        }
        if (creating) {
            demand.setDemandTitle(required(titleObj, "数据名称").toString());
        } else if (titleObj != null && !String.valueOf(titleObj).isBlank()) {
            demand.setDemandTitle(String.valueOf(titleObj));
        }
        if (body.get("requesterOrg") != null || creating) {
            demand.setRequesterOrg(str(body.get("requesterOrg"),
                    operator == null ? "未知机构" : "机构" + operator.getOrgId()));
        }
        // demandType 保留 STRUCTURED/UNSTRUCTURED；政务类型写入 formPayload.serviceDemandType
        String dtype = str(body.get("demandType"), demand.getDemandType() == null ? "STRUCTURED" : demand.getDemandType());
        if ("GOV".equalsIgnoreCase(dtype) || "NON_GOV".equalsIgnoreCase(dtype)
                || "政务服务需求".equals(dtype) || "非政务服务需求".equals(dtype)) {
            dtype = demand.getDemandType() == null ? "STRUCTURED" : demand.getDemandType();
        }
        demand.setDemandType(dtype);
        if (body.get("templateCode") != null) {
            demand.setTemplateCode(str(body.get("templateCode"), null));
        }
        if (body.get("demandContent") != null) {
            demand.setDemandContent(str(body.get("demandContent"), null));
        }
        if (body.get("modelFields") != null) {
            demand.setModelFields(body.get("modelFields") instanceof String
                    ? String.valueOf(body.get("modelFields"))
                    : toJson(castMap(body.get("modelFields"))));
        }
        Object catalogId = body.get("targetCatalogId");
        if (catalogId != null && !String.valueOf(catalogId).isBlank()) {
            demand.setTargetCatalogId(Long.valueOf(String.valueOf(catalogId)));
        }
        if (body.get("formPayload") != null) {
            if (body.get("formPayload") instanceof String) {
                demand.setFormPayload(String.valueOf(body.get("formPayload")));
            } else {
                demand.setFormPayload(toJsonObject(body.get("formPayload")));
            }
        } else if (body.containsKey("providerOrg") || body.containsKey("dataName")
                || body.containsKey("dataItems") || body.containsKey("matterIds")) {
            Map<String, Object> payload = new LinkedHashMap<>();
            putIfPresent(payload, body, "providerOrg");
            putIfPresent(payload, body, "dataName");
            putIfPresent(payload, body, "systemNames");
            putIfPresent(payload, body, "dataItems");
            putIfPresent(payload, body, "serviceDemandType");
            putIfPresent(payload, body, "matterIds");
            putIfPresent(payload, body, "matterNames");
            putIfPresent(payload, body, "matterMaterials");
            putIfPresent(payload, body, "usageScenario");
            putIfPresent(payload, body, "demandBasis");
            putIfPresent(payload, body, "shareProvideMode");
            putIfPresent(payload, body, "updateFrequency");
            putIfPresent(payload, body, "attachments");
            putIfPresent(payload, body, "contactName");
            putIfPresent(payload, body, "contactPhone");
            putIfPresent(payload, body, "contactEmail");
            putIfPresent(payload, body, "catalogTitle");
            demand.setFormPayload(toJson(payload));
            if ((demand.getDemandTitle() == null || demand.getDemandTitle().isBlank())
                    && payload.get("dataName") != null) {
                demand.setDemandTitle(String.valueOf(payload.get("dataName")));
            }
        }
        if (body.get("assigneeOrg") != null) {
            demand.setAssigneeOrg(String.valueOf(body.get("assigneeOrg")));
        }
        if (body.get("supplyMode") != null) {
            demand.setSupplyMode(String.valueOf(body.get("supplyMode")));
        }
    }

    private void putIfPresent(Map<String, Object> target, Map<String, Object> body, String key) {
        if (body.containsKey(key) && body.get(key) != null) {
            target.put(key, body.get(key));
        }
    }

    @Transactional
    public Map<String, Object> analyzeDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        requirePlatformAdmin(operator);
        BizDataDemand demand = getDemand(id);
        if (!"SUBMITTED".equals(demand.getStatus()) && !"PRE_AUDITING".equals(demand.getStatus())
                && !"ANALYZING".equals(demand.getStatus()) && !"SUPERVISING".equals(demand.getStatus())) {
            throw new BusinessException(400, "当前状态不可预审分析（已退回请由需求部门修改后重新提交）");
        }
        String providerOrg = resolveAnalysisProviderOrg(demand, body);
        demand.setAssigneeOrg(providerOrg);

        DemandMatchSignals signals = buildDemandMatchSignals(demand);
        List<BizCatalogItem> orgCatalogs = publishedCatalogsByProvider(providerOrg);
        List<Map<String, Object>> candidates = scorePortalCatalogs(orgCatalogs, signals, null);

        Map<String, Object> best = candidates.isEmpty() ? null : candidates.get(0);
        double bestScore = best == null ? 0 : ((Number) best.get("score")).doubleValue();
        BizCatalogItem bestCatalog = null;
        if (best != null) {
            bestCatalog = catalogMapper.selectById(Long.valueOf(String.valueOf(best.get("resourceId"))));
        } else if (demand.getMatchedCatalogId() != null) {
            bestCatalog = catalogMapper.selectById(demand.getMatchedCatalogId());
        }

        demand.setStage("PRE_AUDIT");
        demand.setStatus("PRE_AUDITING");
        // 履约路径默认「未在中台·需归集补数」，由平台管理员人工选择
        demand.setFulfillPath(PATH_COLLECT);
        if (orgCatalogs.isEmpty()) {
            demand.setMatchedCatalogId(null);
            demand.setMatchScore(BigDecimal.ZERO);
            demand.setEvalStatus("UNMATCHED");
            demand.setShareAttr(str(demand.getShareAttr(), "INTERNAL"));
            demand.setAnalysisNote("组织「" + providerOrg + "」暂无已发布到部门数据共享门户的目录，建议分发后由数源部门补编目录或归集补数");
        } else if (best != null && bestScore >= 30) {
            demand.setMatchedCatalogId(Long.valueOf(String.valueOf(best.get("resourceId"))));
            demand.setMatchScore(BigDecimal.valueOf(bestScore).setScale(2, RoundingMode.HALF_UP));
            demand.setEvalStatus("MATCHED");
            demand.setShareAttr(str(demand.getShareAttr(), "CONDITIONAL"));
            demand.setAnalysisNote("组织「" + providerOrg + "」门户目录参考匹配：" + best.get("title")
                    + "，相关度 " + bestScore + "%（供管理员人工判定是否退回门户申请或分发）");
        } else if (best != null && bestScore > 0) {
            demand.setMatchedCatalogId(Long.valueOf(String.valueOf(best.get("resourceId"))));
            demand.setMatchScore(BigDecimal.valueOf(bestScore).setScale(2, RoundingMode.HALF_UP));
            demand.setEvalStatus("PARTIAL");
            demand.setShareAttr(str(demand.getShareAttr(), "RESTRICTED"));
            demand.setAnalysisNote("组织「" + providerOrg + "」门户目录弱匹配：" + best.get("title")
                    + "（" + bestScore + "%，供管理员人工判定分发或退回）");
        } else {
            demand.setMatchedCatalogId(null);
            demand.setMatchScore(BigDecimal.ZERO);
            demand.setEvalStatus("UNMATCHED");
            demand.setShareAttr(str(demand.getShareAttr(), "INTERNAL"));
            demand.setAnalysisNote("组织「" + providerOrg + "」有 "
                    + orgCatalogs.size() + " 条已发布门户目录，与需求未形成有效匹配（供管理员人工判定）");
        }
        List<Map<String, Object>> topCandidates = candidates.stream().limit(10).toList();
        Map<String, Object> graph = buildRelationGraph(demand, bestCatalog, topCandidates);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerOrg", providerOrg);
        payload.put("orgCatalogCount", orgCatalogs.size());
        payload.put("candidates", topCandidates);
        payload.put("relationGraph", graph);
        demand.setAnalysisPayload(toJson(payload));
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_ANALYZE", "biz_data_demand", String.valueOf(id), demand.getAnalysisNote());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("demandId", id);
        out.put("providerOrg", providerOrg);
        out.put("orgCatalogCount", orgCatalogs.size());
        out.put("catalogCount", topCandidates.size());
        out.put("matchedCatalogId", demand.getMatchedCatalogId());
        out.put("matchScore", demand.getMatchScore());
        out.put("fulfillPath", demand.getFulfillPath());
        out.put("evalStatus", demand.getEvalStatus());
        out.put("shareAttr", demand.getShareAttr());
        out.put("analysisNote", demand.getAnalysisNote());
        out.put("candidates", topCandidates);
        out.put("relationGraph", graph);
        return out;
    }

    /** 门户已发布目录快速查询（按可选提供方过滤；不再检索库表/服务总线） */
    public Map<String, Object> searchResources(String keyword, String resourceType, String providerOrg) {
        if (resourceType != null && !resourceType.isBlank()
                && !"ALL".equalsIgnoreCase(resourceType) && !"CATALOG".equalsIgnoreCase(resourceType)) {
            return Map.of("keyword", keyword == null ? "" : keyword, "total", 0, "items", List.of(),
                    "message", "需求分析仅支持门户目录（CATALOG）检索");
        }
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        List<BizCatalogItem> catalogs = providerOrg == null || providerOrg.isBlank()
                ? publishedCatalogs()
                : publishedCatalogsByProvider(providerOrg.trim());
        DemandMatchSignals signals = new DemandMatchSignals();
        signals.titleCorpus = kw.isBlank() || "*".equals(kw) ? "" : kw;
        signals.contentCorpus = signals.titleCorpus;
        signals.shareMode = "";
        List<Map<String, Object>> all = scorePortalCatalogs(catalogs, signals, kw.isBlank() || "*".equals(kw) ? "*" : kw);
        return Map.of("keyword", keyword == null ? "" : keyword, "total", all.size(),
                "providerOrg", providerOrg == null ? "" : providerOrg,
                "items", all.stream().limit(30).toList());
    }

    @Transactional
    public void superviseDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        requirePlatformAdmin(operator);
        BizDataDemand demand = getDemand(id);
        if (!PRE_AUDIT_STATUSES.contains(demand.getStatus())
                && !"CONFIRMED".equals(demand.getStatus())
                && !"PROVIDER_RETURNED".equals(demand.getStatus())) {
            throw new BusinessException(400, "仅预审中/已分发/已确认挂载期/提供方退回待裁决的需求可督办");
        }
        String note = required(body.get("superviseNote"), "督办说明").toString();
        demand.setStatus("SUPERVISING");
        demand.setStage("PRE_AUDIT");
        demand.setSuperviseNote(note);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        demand.setSuperviseAt(now);
        demand.setSuperviseBy(operator.getUsername());
        // 挂载超时督办保留挂载截止；确认前督办刷新确认/反馈时限
        if (demand.getCatalogMountDeadline() == null) {
            int responseDays = getSettingDays(SETTING_RESPONSE_DAYS);
            demand.setResponseDeadline(plusCalendarDays(now, responseDays));
        }
        String prev = demand.getAnalysisNote() == null ? "" : demand.getAnalysisNote() + " | ";
        demand.setAnalysisNote(prev + "督办：" + note);
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_SUPERVISE", "biz_data_demand", String.valueOf(id), note);
    }

    /**
     * 一键设置信息项评估状态 / 共享属性，并可绑定匹配资源。
     */
    @Transactional
    public Map<String, Object> applyAnalysisSettings(UserPrincipal operator, Long id, Map<String, Object> body) {
        requirePlatformAdmin(operator);
        BizDataDemand demand = getDemand(id);
        if (body.get("evalStatus") != null) {
            String eval = String.valueOf(body.get("evalStatus")).toUpperCase();
            if (!Set.of("PENDING", "MATCHED", "PARTIAL", "UNMATCHED").contains(eval)) {
                throw new BusinessException(400, "evalStatus 非法");
            }
            demand.setEvalStatus(eval);
        }
        if (body.get("shareAttr") != null) {
            String share = String.valueOf(body.get("shareAttr")).toUpperCase();
            if (!Set.of("OPEN", "CONDITIONAL", "RESTRICTED", "INTERNAL").contains(share)) {
                throw new BusinessException(400, "shareAttr 非法");
            }
            demand.setShareAttr(share);
        }
        if (body.get("fulfillPath") != null) {
            String path = String.valueOf(body.get("fulfillPath"));
            if (!PATH_AUTHORIZE.equals(path) && !PATH_COLLECT.equals(path)) {
                throw new BusinessException(400, "fulfillPath 非法");
            }
            demand.setFulfillPath(path);
        }
        if (body.get("matchedCatalogId") != null && !String.valueOf(body.get("matchedCatalogId")).isBlank()) {
            demand.setMatchedCatalogId(Long.valueOf(String.valueOf(body.get("matchedCatalogId"))));
        }
        if (body.get("resourceType") != null && body.get("resourceId") != null
                && "CATALOG".equalsIgnoreCase(String.valueOf(body.get("resourceType")))) {
            demand.setMatchedCatalogId(Long.valueOf(String.valueOf(body.get("resourceId"))));
        }
        if (body.get("matchScore") != null) {
            demand.setMatchScore(new BigDecimal(String.valueOf(body.get("matchScore"))));
        }
        if (demand.getStatus().equals("SUBMITTED")) {
            demand.setStatus("PRE_AUDITING");
            demand.setStage("PRE_AUDIT");
        }
        String tip = "一键设置：评估=" + demand.getEvalStatus() + "，共享=" + demand.getShareAttr();
        demand.setAnalysisNote(str(demand.getAnalysisNote(), tip));
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_ANALYSIS_APPLY", "biz_data_demand", String.valueOf(id), tip);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("demandId", id);
        out.put("evalStatus", demand.getEvalStatus());
        out.put("shareAttr", demand.getShareAttr());
        out.put("fulfillPath", demand.getFulfillPath());
        out.put("matchedCatalogId", demand.getMatchedCatalogId());
        out.put("matchScore", demand.getMatchScore());
        return out;
    }

    @Transactional
    public void dispatchDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        requirePlatformAdmin(operator);
        BizDataDemand demand = getDemand(id);
        if (!Set.of("PRE_AUDITING", "ANALYZING", "DISPATCHED", "SUBMITTED", "SUPERVISING").contains(demand.getStatus())) {
            throw new BusinessException(400, "当前状态不可分发");
        }
        demand.setAssigneeOrg(required(body.get("assigneeOrg"), "assigneeOrg").toString());
        String path = str(body.get("fulfillPath"), demand.getFulfillPath());
        if (path == null || path.isBlank()) {
            path = demand.getMatchedCatalogId() != null ? PATH_AUTHORIZE : PATH_COLLECT;
        }
        if (!PATH_AUTHORIZE.equals(path) && !PATH_COLLECT.equals(path)) {
            throw new BusinessException(400, "fulfillPath 须为 AUTHORIZE_EXISTING 或 NEED_COLLECT");
        }
        demand.setFulfillPath(path);
        demand.setStatus("DISPATCHED");
        demand.setStage("PRE_AUDIT");
        demand.setReturnKind(null);
        int responseDays = getSettingDays(SETTING_RESPONSE_DAYS);
        demand.setResponseDeadline(plusCalendarDays(java.time.LocalDateTime.now(), responseDays));
        String note = str(body.get("analysisNote"), null);
        if (note == null) {
            note = "预审通过，已分发至 " + demand.getAssigneeOrg() + "；履约路径=" + path
                    + "；请于 " + responseDays + " 天（自然日）内确认/反馈";
        }
        demand.setAnalysisNote(note);
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_DISPATCH", "biz_data_demand", String.valueOf(id), demand.getAssigneeOrg() + "/" + path);
    }

    /**
     * 管理员判定「门户已可满足」：退回需求部门，引导其直接去门户申请。
     * 是否高/低匹配由管理员人工判断，系统匹配分仅供参考。
     */
    @Transactional
    public void returnToPortalApply(UserPrincipal operator, Long id, Map<String, Object> body) {
        requirePlatformAdmin(operator);
        BizDataDemand demand = getDemand(id);
        if (!Set.of("SUBMITTED", "PRE_AUDITING", "ANALYZING", "SUPERVISING").contains(demand.getStatus())) {
            throw new BusinessException(400, "当前状态不可退回门户申请");
        }
        String note = str(body.get("analysisNote"), null);
        if (note == null || note.isBlank()) {
            note = "管理员判定门户目录已可满足，请到部门数据共享门户直接申请；无需再走供需分发";
        }
        demand.setStatus("RETURNED");
        demand.setStage("MANAGE");
        demand.setReturnKind("ADMIN_PORTAL");
        demand.setAnalysisNote(note);
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_RETURN_PORTAL", "biz_data_demand", String.valueOf(id), note);
    }

    @Transactional
    public void returnDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        requirePlatformAdmin(operator);
        BizDataDemand demand = getDemand(id);
        if (!Set.of("SUBMITTED", "PRE_AUDITING", "ANALYZING", "SUPERVISING", "PROVIDER_RETURNED")
                .contains(demand.getStatus())) {
            throw new BusinessException(400, "当前状态不可退回需求部门");
        }
        demand.setStatus("RETURNED");
        demand.setStage("MANAGE");
        demand.setReturnKind("ADMIN_MATERIAL");
        demand.setAnalysisNote(str(body.get("analysisNote"), "预审退回/督查督办，需补充材料"));
        int responseDays = getSettingDays(SETTING_RESPONSE_DAYS);
        demand.setResponseDeadline(plusCalendarDays(java.time.LocalDateTime.now(), responseDays));
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_RETURN", "biz_data_demand", String.valueOf(id), demand.getAnalysisNote());
    }

    @Transactional
    public Map<String, Object> confirmDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        requireProviderOperator(operator);
        BizDataDemand demand = getDemand(id);
        if (!AUDIT_CONFIRMABLE.contains(demand.getStatus())) {
            throw new BusinessException(400, "仅待确认/督办中的需求可同意并生成共享任务");
        }
        // 挂载超时督办：应标记挂载后办结，不可再次「同意确认」
        if ("SUPERVISING".equals(demand.getStatus()) && demand.getCatalogMountDeadline() != null) {
            throw new BusinessException(400, "当前为挂载超时督办，请先「标记目录已挂载」再办结，无需再次同意确认");
        }
        String path = str(body.get("fulfillPath"), demand.getFulfillPath());
        if (path == null || path.isBlank()) {
            path = demand.getMatchedCatalogId() != null ? PATH_AUTHORIZE : PATH_COLLECT;
        }
        demand.setFulfillPath(path);
        demand.setStatus("CONFIRMED");
        demand.setStage("AUDIT");
        demand.setReturnKind(null);
        int mountDays = getSettingDays(SETTING_MOUNT_DAYS);
        demand.setConfirmNote(str(body.get("confirmNote"),
                "数源部门确认可满足，转换为数据责任；须在 " + mountDays + " 天（自然日）内将目录挂载至部门数据共享门户"));
        demand.setSupplyMode(str(body.get("supplyMode"), PATH_COLLECT.equals(path) ? "COLLECT" : "EXCHANGE"));
        java.time.LocalDateTime confirmedAt = java.time.LocalDateTime.now();
        demand.setCatalogMountDeadline(plusCalendarDays(confirmedAt, mountDays));
        demand.setResponseDeadline(null);
        // 授权既有门户目录：视为已挂载
        if (PATH_AUTHORIZE.equals(path) && demand.getMatchedCatalogId() != null) {
            BizCatalogItem matched = catalogMapper.selectById(demand.getMatchedCatalogId());
            if (matched != null && "PUBLISHED".equalsIgnoreCase(matched.getPublishStatus())) {
                demand.setCatalogMountedAt(confirmedAt);
            }
        }
        demandMapper.updateById(demand);

        BizDataDuty duty = createDataDuty(operator, demand, path);
        List<BizDemandSupplyTask> tasks = createSupplyTasks(demand, path);
        if (dispatchDownstream) {
            dispatchDownstreamTasks(operator, demand, tasks);
        }

        BizSupplyManifest manifest = new BizSupplyManifest();
        manifest.setManifestType("SUPPLY_DEMAND");
        manifest.setRefId(demand.getId());
        manifest.setTitle("供需台账-" + demand.getDemandTitle());
        manifest.setStatus("ACTIVE");
        manifest.setAuthLevel(str(body.get("authLevel"), "DEPT"));
        manifest.setCascadeFlag(intVal(body.get("cascadeFlag"), 0));
        manifest.setExportPayload("demandId=" + demand.getId() + ",path=" + path + ",tasks=" + tasks.size()
                + ",dutyId=" + duty.getId());
        manifestMapper.insert(manifest);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_CONFIRM", "biz_data_demand", String.valueOf(id), demand.getConfirmNote());

        Map<String, Object> integrations = buildIntegrationSummary(demand, duty, tasks);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("demandId", id);
        out.put("fulfillPath", path);
        out.put("dutyId", duty.getId());
        out.put("duty", duty);
        out.put("tasks", tasks);
        out.put("manifestId", manifest.getId());
        out.put("dispatchDownstream", dispatchDownstream);
        out.put("integrations", integrations);
        out.put("catalogMountDeadline", demand.getCatalogMountDeadline());
        out.put("catalogMountedAt", demand.getCatalogMountedAt());
        return out;
    }

    /** 供数部门不同意提供：退回平台管理员裁决（非直接退回需求部门） */
    @Transactional
    public void confirmReturnDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        requireProviderOperator(operator);
        BizDataDemand demand = getDemand(id);
        if (!AUDIT_CONFIRMABLE.contains(demand.getStatus()) && !"CONFIRMED".equals(demand.getStatus())) {
            throw new BusinessException(400, "当前状态不可退回");
        }
        String note = str(body.get("confirmNote"), null);
        if (note == null || note.isBlank()) {
            throw new BusinessException(400, "不同意提供须填写原因");
        }
        demand.setStatus("PROVIDER_RETURNED");
        demand.setStage("PRE_AUDIT");
        demand.setReturnKind("PROVIDER_PENDING");
        demand.setConfirmNote(note);
        int responseDays = getSettingDays(SETTING_RESPONSE_DAYS);
        demand.setResponseDeadline(plusCalendarDays(java.time.LocalDateTime.now(), responseDays));
        demand.setConfirmFeedback(str(body.get("confirmFeedback"), note));
        demand.setCatalogMountDeadline(null);
        demand.setCatalogMountedAt(null);
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_PROVIDER_RETURN", "biz_data_demand", String.valueOf(id), note);
    }

    /** 管理员同意提供方退回 → 退回需求部门 */
    @Transactional
    public void adminAgreeProviderReturn(UserPrincipal operator, Long id, Map<String, Object> body) {
        requirePlatformAdmin(operator);
        BizDataDemand demand = getDemand(id);
        if (!"PROVIDER_RETURNED".equals(demand.getStatus())) {
            throw new BusinessException(400, "仅「提供方退回待裁决」需求可同意退回需求部门");
        }
        String note = str(body.get("analysisNote"), null);
        if (note == null || note.isBlank()) {
            note = "管理员同意提供方退回：" + str(demand.getConfirmNote(), "不同意提供");
        }
        demand.setStatus("RETURNED");
        demand.setStage("MANAGE");
        demand.setReturnKind("ADMIN_AGREE_RETURN");
        demand.setAnalysisNote(note);
        int responseDays = getSettingDays(SETTING_RESPONSE_DAYS);
        demand.setResponseDeadline(plusCalendarDays(java.time.LocalDateTime.now(), responseDays));
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_ADMIN_AGREE_RETURN", "biz_data_demand", String.valueOf(id), note);
    }

    /** 管理员拒绝提供方退回 → 打回提供部门重新确认 */
    @Transactional
    public void adminRefuseProviderReturn(UserPrincipal operator, Long id, Map<String, Object> body) {
        requirePlatformAdmin(operator);
        BizDataDemand demand = getDemand(id);
        if (!"PROVIDER_RETURNED".equals(demand.getStatus())) {
            throw new BusinessException(400, "仅「提供方退回待裁决」需求可拒绝退回并打回确认");
        }
        String note = str(body.get("analysisNote"), null);
        if (note == null || note.isBlank()) {
            note = "管理员拒绝退回，请提供部门重新确认是否可提供";
        }
        demand.setStatus("DISPATCHED");
        demand.setStage("PRE_AUDIT");
        demand.setReturnKind(null);
        demand.setAnalysisNote(note);
        String prev = demand.getConfirmNote() == null ? "" : demand.getConfirmNote() + " | ";
        demand.setConfirmNote(prev + "管理员已打回重新确认");
        int responseDays = getSettingDays(SETTING_RESPONSE_DAYS);
        demand.setResponseDeadline(plusCalendarDays(java.time.LocalDateTime.now(), responseDays));
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_ADMIN_REFUSE_RETURN", "biz_data_demand", String.valueOf(id), note);
    }

    /** 提供部门标记：目录已挂载至门户（须选择门户目录，支持多选） */
    @Transactional
    public void markCatalogMounted(UserPrincipal operator, Long id, Map<String, Object> body) {
        requireProviderOperator(operator);
        BizDataDemand demand = getDemand(id);
        if (!"CONFIRMED".equals(demand.getStatus()) && !"SUPERVISING".equals(demand.getStatus())
                && !"CATALOG_MOUNTED".equals(demand.getStatus())) {
            throw new BusinessException(400, "仅已确认（含挂载督办中）的需求可标记目录已挂载");
        }
        List<Long> catalogIds = parseMountedCatalogIds(body);
        if (catalogIds.isEmpty()) {
            throw new BusinessException(400, "请选择已挂载的目录名称");
        }
        String provider = demand.getAssigneeOrg() == null ? "" : demand.getAssigneeOrg().trim();
        List<String> titles = new ArrayList<>();
        for (Long catalogId : catalogIds) {
            BizCatalogItem catalog = catalogMapper.selectById(catalogId);
            if (catalog == null || !"PUBLISHED".equals(catalog.getPublishStatus())
                    || catalog.getGovResourceId() == null) {
                throw new BusinessException(400, "仅可选择部门数据共享门户已发布目录");
            }
            if (!provider.isBlank() && catalog.getProviderOrg() != null
                    && !provider.equalsIgnoreCase(catalog.getProviderOrg().trim())) {
                throw new BusinessException(400, "所选目录不属于当前数据提供部门");
            }
            if (catalog.getTitle() != null && !catalog.getTitle().isBlank()) {
                titles.add(catalog.getTitle().trim());
            }
        }
        demand.setMatchedCatalogId(catalogIds.get(0));
        demand.setMatchedCatalogIds(toJsonObject(catalogIds));
        demand.setCatalogMountedAt(java.time.LocalDateTime.now());
        demand.setStatus("CATALOG_MOUNTED");
        demand.setStage("AUDIT");
        String titleText = titles.isEmpty() ? "" : String.join("、", titles);
        String tip = str(body.get("confirmNote"),
                titleText.isBlank()
                        ? "目录已挂载至部门数据共享门户"
                        : "目录已挂载至部门数据共享门户：" + titleText);
        String prev = demand.getConfirmNote() == null ? "" : demand.getConfirmNote() + " | ";
        demand.setConfirmNote(prev + tip);
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_CATALOG_MOUNTED", "biz_data_demand", String.valueOf(id), tip);
    }

    private List<Long> parseMountedCatalogIds(Map<String, Object> body) {
        List<Long> out = new ArrayList<>();
        Object multi = body.get("matchedCatalogIds");
        if (multi == null) {
            multi = body.get("catalogIds");
        }
        if (multi instanceof List<?> list) {
            for (Object item : list) {
                if (item == null || String.valueOf(item).isBlank()) {
                    continue;
                }
                out.add(Long.valueOf(String.valueOf(item)));
            }
        } else if (multi instanceof String s && !s.isBlank()) {
            try {
                Object parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(s, Object.class);
                if (parsed instanceof List<?> list) {
                    for (Object item : list) {
                        if (item == null || String.valueOf(item).isBlank()) {
                            continue;
                        }
                        out.add(Long.valueOf(String.valueOf(item)));
                    }
                }
            } catch (Exception ignored) {
                // fall through to single id
            }
        }
        if (!out.isEmpty()) {
            return out.stream().distinct().collect(Collectors.toList());
        }
        Object catalogIdObj = body.get("matchedCatalogId");
        if (catalogIdObj == null || String.valueOf(catalogIdObj).isBlank()) {
            catalogIdObj = body.get("catalogId");
        }
        if (catalogIdObj == null || String.valueOf(catalogIdObj).isBlank()) {
            return List.of();
        }
        return List.of(Long.valueOf(String.valueOf(catalogIdObj)));
    }

    /** 定时：挂载超时自动督办 */
    @Transactional
    public int autoSuperviseMountOverdue() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<BizDataDemand> rows = demandMapper.selectList(new LambdaQueryWrapper<BizDataDemand>()
                .eq(BizDataDemand::getStatus, "CONFIRMED")
                .isNotNull(BizDataDemand::getCatalogMountDeadline)
                .isNull(BizDataDemand::getCatalogMountedAt)
                .lt(BizDataDemand::getCatalogMountDeadline, now));
        int n = 0;
        for (BizDataDemand demand : rows) {
            int mountDays = getSettingDays(SETTING_MOUNT_DAYS);
            demand.setStatus("SUPERVISING");
            demand.setStage("PRE_AUDIT");
            demand.setSuperviseNote("挂载超时自动督办：同意提供后超过 "
                    + mountDays + " 天（自然日）仍未将目录挂载至门户");
            demand.setSuperviseAt(now);
            demand.setSuperviseBy("system");
            String prev = demand.getAnalysisNote() == null ? "" : demand.getAnalysisNote() + " | ";
            demand.setAnalysisNote(prev + demand.getSuperviseNote());
            demandMapper.updateById(demand);
            n++;
            log.info("autoSuperviseMountOverdue demandId={}", demand.getId());
        }
        return n;
    }

    /** 督查反馈（供数部门在确认环节反馈） */
    @Transactional
    public void confirmFeedback(UserPrincipal operator, Long id, Map<String, Object> body) {
        requireProviderOperator(operator);
        BizDataDemand demand = getDemand(id);
        if (!Set.of("DISPATCHED", "SUPERVISING", "CONFIRMED", "CORRECTION")
                .contains(demand.getStatus())) {
            throw new BusinessException(400, "当前状态不可填写督查反馈");
        }
        String feedback = required(body.get("confirmFeedback"), "督查反馈").toString();
        demand.setConfirmFeedback(feedback);
        if ("SUPERVISING".equals(demand.getStatus()) || "DISPATCHED".equals(demand.getStatus())
                || "CORRECTION".equals(demand.getStatus())) {
            String prev = demand.getConfirmNote() == null ? "" : demand.getConfirmNote() + " | ";
            demand.setConfirmNote(prev + "已反馈督查");
        }
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_CONFIRM_FEEDBACK", "biz_data_demand", String.valueOf(id), feedback);
    }

    /** 整体办结：由数据需求部门在「已挂载」后确认办结 */
    @Transactional
    public void completeDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        requireDemandOperator(operator);
        BizDataDemand demand = getDemand(id);
        boolean mounted = "CATALOG_MOUNTED".equals(demand.getStatus())
                || (("CONFIRMED".equals(demand.getStatus()) || "SUPERVISING".equals(demand.getStatus()))
                && demand.getCatalogMountedAt() != null);
        if (!mounted) {
            throw new BusinessException(400, "仅「已挂载」的需求可由数据需求部门办结");
        }
        if (demand.getCatalogMountedAt() == null) {
            throw new BusinessException(400, "请先由数据提供部门完成目录挂载后再办结");
        }
        demand.setStatus("COMPLETED");
        demand.setStage("SUPPLY");
        String tip = str(body.get("confirmNote"), "数据需求部门确认办结");
        String prev = demand.getConfirmNote() == null ? "" : demand.getConfirmNote() + " | ";
        demand.setConfirmNote(prev + tip);
        if (body.get("confirmFeedback") != null) {
            demand.setConfirmFeedback(String.valueOf(body.get("confirmFeedback")));
        }
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_COMPLETE", "biz_data_demand", String.valueOf(id), "办结");
    }

    /** 整体撤销（非已办结） */
    @Transactional
    public void cancelDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        requireProviderOperator(operator);
        BizDataDemand demand = getDemand(id);
        if (Set.of("COMPLETED", "CANCELLED", "WITHDRAWN").contains(demand.getStatus())) {
            throw new BusinessException(400, "当前状态不可撤销");
        }
        demand.setStatus("CANCELLED");
        demand.setConfirmNote(str(body.get("confirmNote"), "需求已撤销"));
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_CANCEL", "biz_data_demand", String.valueOf(id), demand.getConfirmNote());
    }

    /** 整体修改（办结/撤销前） */
    @Transactional
    public void updateDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        requireDemandOperator(operator);
        BizDataDemand demand = getDemand(id);
        if (Set.of("COMPLETED", "CANCELLED", "WITHDRAWN").contains(demand.getStatus())) {
            throw new BusinessException(400, "当前状态不可修改");
        }
        // 需求部门完整表单编辑：草稿 / 撤销待提交 / 已退回
        if (body.containsKey("formPayload") || body.containsKey("dataName") || body.containsKey("dataItems")) {
            if (!DEMAND_EDITABLE.contains(demand.getStatus())) {
                throw new BusinessException(400, "仅草稿、撤销待提交或已退回状态可修改填报内容");
            }
            applyDemandForm(demand, body, operator, false);
            if (Boolean.TRUE.equals(body.get("submit"))) {
                demand.setStatus("SUBMITTED");
                demand.setStage("MANAGE");
            } else if (Boolean.TRUE.equals(body.get("draft"))) {
                demand.setStatus("DRAFT");
            }
            demandMapper.updateById(demand);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "DEMAND_UPDATE", "biz_data_demand", String.valueOf(id), demand.getDemandTitle());
            return;
        }
        if (body.get("demandTitle") != null && !String.valueOf(body.get("demandTitle")).isBlank()) {
            demand.setDemandTitle(String.valueOf(body.get("demandTitle")));
        }
        if (body.get("requesterOrg") != null) {
            demand.setRequesterOrg(String.valueOf(body.get("requesterOrg")));
        }
        if (body.get("assigneeOrg") != null) {
            demand.setAssigneeOrg(String.valueOf(body.get("assigneeOrg")));
        }
        if (body.get("demandType") != null) {
            demand.setDemandType(String.valueOf(body.get("demandType")));
        }
        if (body.get("demandContent") != null) {
            demand.setDemandContent(String.valueOf(body.get("demandContent")));
        }
        if (body.get("modelFields") != null) {
            demand.setModelFields(body.get("modelFields") instanceof String
                    ? String.valueOf(body.get("modelFields"))
                    : toJson(castMap(body.get("modelFields"))));
        }
        if (body.get("templateCode") != null) {
            demand.setTemplateCode(String.valueOf(body.get("templateCode")));
        }
        if (body.get("fulfillPath") != null) {
            String path = String.valueOf(body.get("fulfillPath"));
            if (!PATH_AUTHORIZE.equals(path) && !PATH_COLLECT.equals(path)) {
                throw new BusinessException(400, "fulfillPath 非法");
            }
            demand.setFulfillPath(path);
        }
        if (body.get("confirmNote") != null) {
            demand.setConfirmNote(String.valueOf(body.get("confirmNote")));
        }
        if (body.get("shareAttr") != null) {
            demand.setShareAttr(String.valueOf(body.get("shareAttr")));
        }
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_UPDATE", "biz_data_demand", String.valueOf(id), demand.getDemandTitle());
    }

    @Transactional
    public void rejectDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        // 与 confirm-return 同义：提供方不同意 → 待管理员裁决
        confirmReturnDemand(operator, id, body);
    }

    /** 自 start 起增加 n 个自然日 */
    static java.time.LocalDateTime plusCalendarDays(java.time.LocalDateTime start, int days) {
        return start.plusDays(Math.max(0, days));
    }

    /** 兼容旧调用：工作日累加（跳过周六日；不含法定节假日） */
    static java.time.LocalDateTime plusWorkdays(java.time.LocalDateTime start, int workdays) {
        java.time.LocalDateTime cursor = start;
        int added = 0;
        while (added < workdays) {
            cursor = cursor.plusDays(1);
            java.time.DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return cursor;
    }

    public Map<String, Object> getSuperviseSettings() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("responseDeadlineDays", getSettingDays(SETTING_RESPONSE_DAYS));
        out.put("mountDeadlineDays", getSettingDays(SETTING_MOUNT_DAYS));
        out.put("unit", "CALENDAR_DAY");
        out.put("unitLabel", "天（自然日）");
        return out;
    }

    @Transactional
    public Map<String, Object> saveSuperviseSettings(UserPrincipal operator, Map<String, Object> body) {
        requirePlatformAdmin(operator);
        Integer responseDays = body.get("responseDeadlineDays") != null
                ? Integer.valueOf(String.valueOf(body.get("responseDeadlineDays"))) : null;
        Integer mountDays = body.get("mountDeadlineDays") != null
                ? Integer.valueOf(String.valueOf(body.get("mountDeadlineDays"))) : null;
        if (responseDays != null) {
            upsertSetting(SETTING_RESPONSE_DAYS, normalizeDeadlineDays(responseDays),
                    "确认/反馈时限（自然日）：分发或督办后，数据提供/需求部门须在 N 天内确认或反馈");
        }
        if (mountDays != null) {
            upsertSetting(SETTING_MOUNT_DAYS, normalizeDeadlineDays(mountDays),
                    "挂载门户时限（自然日）：数源部门同意提供后，须在 N 天内将目录挂载到门户");
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "SUPPLY_SUPERVISE_SETTINGS", "biz_supply_setting", "supervise",
                "response=" + getSettingDays(SETTING_RESPONSE_DAYS)
                        + ",mount=" + getSettingDays(SETTING_MOUNT_DAYS));
        return getSuperviseSettings();
    }

    private int normalizeDeadlineDays(int days) {
        if (days < 1 || days > 365) {
            throw new BusinessException(400, "时限须为 1～365 的正整数（自然日）");
        }
        return days;
    }

    private int getSettingDays(String key) {
        BizSupplySetting row = supplySettingMapper.selectOne(new LambdaQueryWrapper<BizSupplySetting>()
                .eq(BizSupplySetting::getSettingKey, key)
                .last("LIMIT 1"));
        if (row == null || row.getSettingValue() == null || row.getSettingValue().isBlank()) {
            return DEFAULT_DEADLINE_DAYS;
        }
        try {
            int v = Integer.parseInt(row.getSettingValue().trim());
            return v >= 1 && v <= 365 ? v : DEFAULT_DEADLINE_DAYS;
        } catch (NumberFormatException e) {
            return DEFAULT_DEADLINE_DAYS;
        }
    }

    private void upsertSetting(String key, int value, String description) {
        BizSupplySetting row = supplySettingMapper.selectOne(new LambdaQueryWrapper<BizSupplySetting>()
                .eq(BizSupplySetting::getSettingKey, key)
                .last("LIMIT 1"));
        if (row == null) {
            row = new BizSupplySetting();
            row.setSettingKey(key);
            row.setSettingValue(String.valueOf(value));
            row.setDescription(description);
            row.setUpdatedAt(java.time.LocalDateTime.now());
            supplySettingMapper.insert(row);
        } else {
            row.setSettingValue(String.valueOf(value));
            row.setDescription(description);
            row.setUpdatedAt(java.time.LocalDateTime.now());
            supplySettingMapper.updateById(row);
        }
    }

    public List<BizDemandSupplyTask> listSupplyTasks(Long demandId) {
        LambdaQueryWrapper<BizDemandSupplyTask> q = new LambdaQueryWrapper<BizDemandSupplyTask>()
                .orderByDesc(BizDemandSupplyTask::getId);
        if (demandId != null) {
            q.eq(BizDemandSupplyTask::getDemandId, demandId);
        }
        return supplyTaskMapper.selectList(q);
    }

    public Map<String, Object> supplyView(Long demandId) {
        BizDataDemand demand = getDemand(demandId);
        List<BizDemandSupplyTask> tasks = listSupplyTasks(demandId);
        List<BizDataDuty> duties = listDuties(demandId);
        BizCatalogItem catalog = demand.getMatchedCatalogId() != null
                ? catalogMapper.selectById(demand.getMatchedCatalogId()) : null;
        List<BizEsbFlow> flows = esbFlowMapper.selectList(new LambdaQueryWrapper<BizEsbFlow>().last("LIMIT 20"));

        List<Map<String, Object>> exchangeJobs = tasks.stream()
                .filter(t -> "EXCHANGE".equals(t.getTaskType()) || "COLLECT".equals(t.getTaskType()))
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("taskId", t.getId());
                    m.put("taskType", t.getTaskType());
                    m.put("taskName", t.getTaskName());
                    m.put("status", t.getStatus());
                    m.put("flowCode", t.getRefFlowCode());
                    return m;
                }).toList();
        List<Map<String, Object>> apiEndpoints = new ArrayList<>();
        for (BizDemandSupplyTask t : tasks) {
            if ("API".equals(t.getTaskType()) || "SHARE".equals(t.getTaskType()) || "EXCHANGE".equals(t.getTaskType())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", t.getTaskName());
                m.put("endpoint", catalog != null
                        ? "/api/v1/exchange/catalog/" + catalog.getId()
                        : "/api/v1/exchange/supply/supply-view/" + demandId);
                m.put("method", "API".equals(t.getTaskType()) ? "POST" : "GET");
                m.put("status", t.getStatus());
                apiEndpoints.add(m);
            }
        }
        for (BizEsbFlow f : flows.stream().limit(5).toList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.getFlowName());
            m.put("endpoint", "/esb/flows/" + f.getFlowCode());
            m.put("method", "INVOKE");
            m.put("status", f.getStatus());
            apiEndpoints.add(m);
        }
        List<Map<String, Object>> sharePages = new ArrayList<>();
        Map<String, Object> portalPage = new LinkedHashMap<>();
        portalPage.put("title", "通用共享页面");
        portalPage.put("url", catalog != null ? "/exchange/portal?tab=catalog&id=" + catalog.getId() : "/exchange/portal?tab=catalog");
        portalPage.put("openMode", "same_tab");
        sharePages.add(portalPage);
        if (catalog != null) {
            Map<String, Object> catPage = new LinkedHashMap<>();
            catPage.put("title", "目录详情 · " + catalog.getTitle());
            catPage.put("url", "/exchange/portal?tab=catalog");
            catPage.put("openMode", "same_tab");
            sharePages.add(catPage);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("demand", demand);
        out.put("tasks", tasks);
        out.put("duties", duties);
        out.put("catalog", catalog);
        out.put("exchangeFlows", flows);
        out.put("exchangeJobs", exchangeJobs);
        out.put("apiEndpoints", apiEndpoints);
        out.put("sharePages", sharePages);
        out.put("sharePageUrl", catalog != null ? "/exchange/portal?tab=catalog" : null);
        out.put("apiEndpoint", catalog != null ? "/api/v1/exchange/catalog/" + catalog.getId() : null);
        return out;
    }

    /**
     * 清单中心：目录清单 / 供需清单 / 异议清单（及兼容旧 listType）。
     * 异议状态机独立，不改动需求确认/分析/分发等业务流。
     */
    public Map<String, Object> listCenter(String listType, UserPrincipal operator) {
        Map<String, Object> out = new LinkedHashMap<>();
        String type = str(listType, "catalog-published");
        out.put("listType", type);

        // 兼容旧四 Tab
        if ("dept-catalog".equals(type) || "catalog".equals(type)) {
            type = "catalog-published";
            out.put("listType", type);
        } else if ("service-list".equals(type) || "service".equals(type) || "open-list".equals(type) || "open".equals(type)) {
            type = "sd-history";
            out.put("listType", type);
        }

        String orgName = resolveOrgName(operator);
        boolean platform = isListCenterPlatform(operator);

        switch (type) {
            case "catalog-publish" -> {
                out.put("title", "目录发布清单");
                out.put("category", "目录清单");
                out.put("columns", catalogListColumns());
                out.put("items", filterCatalogRowsByOrg(catalogListByApproval("PUBLISH"), orgName, platform));
            }
            case "catalog-change" -> {
                out.put("title", "目录变更清单");
                out.put("category", "目录清单");
                out.put("columns", catalogListColumns());
                out.put("items", filterCatalogRowsByOrg(catalogChangeOrUpdateRows("UPDATE"), orgName, platform));
            }
            case "catalog-offline" -> {
                out.put("title", "目录下线清单");
                out.put("category", "目录清单");
                out.put("columns", catalogListColumns());
                out.put("items", filterCatalogRowsByOrg(unifiedCatalogRows("OFFLINE"), orgName, platform));
            }
            case "catalog-access" -> {
                out.put("title", "数据接入清单");
                out.put("category", "目录清单");
                out.put("columns", catalogListColumns());
                // 与门户/资源目录已发布目录同源（不再用归集任务冒充）
                out.put("items", filterCatalogRowsByOrg(unifiedCatalogRows("PUBLISHED"), orgName, platform));
            }
            case "catalog-update" -> {
                out.put("title", "数据更新清单");
                out.put("category", "目录清单");
                out.put("columns", catalogListColumns());
                out.put("items", filterCatalogRowsByOrg(catalogChangeOrUpdateRows("UPDATE"), orgName, platform));
            }
            case "catalog-published" -> {
                out.put("title", "已发布目录清单");
                out.put("category", "目录清单");
                out.put("columns", catalogListColumns());
                out.put("items", filterCatalogRowsByOrg(unifiedCatalogRows("PUBLISHED"), orgName, platform));
            }
            case "sd-demand-audit" -> {
                out.put("title", "需求审核清单");
                out.put("category", "供需清单");
                out.put("columns", supplyListColumns());
                // 数据提供部门或平台管理员
                out.put("items", demandManifestRows(Set.of(
                        "SUBMITTED", "PRE_AUDITING", "ANALYZING", "RETURNED", "SUPERVISING", "PROVIDER_RETURNED"),
                        platform ? null : "provider", orgName));
            }
            case "sd-supply-audit" -> {
                out.put("title", "供给审核清单");
                out.put("category", "供需清单");
                out.put("columns", supplyListColumns());
                // 数据需求部门或平台管理员
                out.put("items", demandManifestRows(Set.of("DISPATCHED", "CORRECTION", "CONFIRMED"),
                        platform ? null : "requester", orgName));
            }
            case "sd-joint-audit" -> {
                out.put("title", "供需审核清单");
                out.put("category", "供需清单");
                out.put("columns", supplyListColumns());
                out.put("items", demandManifestRows(Set.of(
                        "SUBMITTED", "DISPATCHED", "PRE_AUDITING", "ANALYZING", "SUPERVISING", "CORRECTION",
                        "CONFIRMED", "PROVIDER_RETURNED", "RETURNED"),
                        platform ? null : "related", orgName));
            }
            case "sd-auth-history" -> {
                out.put("title", "历史授权清单");
                out.put("category", "供需清单");
                out.put("columns", supplyListColumns());
                out.put("items", filterCatalogRowsByOrg(dutyHistoryRows(), orgName, platform));
            }
            case "sd-history" -> {
                out.put("title", "历史供需清单");
                out.put("category", "供需清单");
                out.put("columns", supplyListColumns());
                out.put("items", demandManifestRows(Set.of("CONFIRMED", "COMPLETED", "CANCELLED", "REJECTED"),
                        platform ? null : "related", orgName));
            }
            case "sd-cascade" -> {
                out.put("title", "级联下行清单");
                out.put("category", "供需清单");
                out.put("columns", supplyListColumns());
                out.put("items", cascadeManifestRows());
                out.put("hint", "展示标记为级联下行的供需台账；若为空表示暂无级联记录");
            }
            case "objection", "objection-apply" -> {
                out.put("title", "异议申请清单");
                out.put("category", "异议清单");
                out.put("columns", objectionListColumns());
                out.put("items", objectionRowsByStatuses(Set.of("DRAFT", "SUBMITTED", "REJECTED", "PROCESSED"),
                        platform ? null : "provider", orgName));
            }
            case "objection-audit" -> {
                out.put("title", "异议审核清单");
                out.put("category", "异议清单");
                out.put("columns", objectionListColumns());
                out.put("items", objectionRowsByStatuses(Set.of("SUBMITTED"),
                        platform ? null : "verify", orgName));
            }
            case "objection-process" -> {
                out.put("title", "异议处理清单");
                out.put("category", "异议清单");
                out.put("columns", objectionListColumns());
                out.put("items", objectionRowsByStatuses(Set.of("APPROVED"),
                        platform ? null : "verify", orgName));
            }
            case "objection-closed" -> {
                out.put("title", "异议办结清单");
                out.put("category", "异议清单");
                out.put("columns", objectionListColumns());
                out.put("items", objectionRowsByStatuses(Set.of("CLOSED"),
                        platform ? null : "related", orgName));
            }
            case "objection-history" -> {
                out.put("title", "历史异议清单");
                out.put("category", "异议清单");
                out.put("columns", objectionListColumns());
                out.put("items", objectionRowsByStatuses(Set.of("CLOSED"),
                        platform ? null : "related", orgName));
            }
            case "objection-stats" -> {
                out.put("title", "异议统计分析");
                out.put("category", "异议清单");
                out.put("columns", List.of());
                Map<String, Object> stats = objectionStats(platform ? null : "related", orgName);
                out.put("stats", stats);
                out.put("items", stats.getOrDefault("byStatus", List.of()));
            }
            default -> {
                out.put("title", type);
                out.put("items", List.of());
            }
        }
        return out;
    }

    /** 兼容旧调用 */
    public Map<String, Object> listCenter(String listType) {
        return listCenter(listType, null);
    }

    /**
     * 清单中心查看页：审核流程（状态 / 结果 / 时间）。
     * kind: catalog | demand | objection（可省略，按 listType 推断）
     */
    public Map<String, Object> listCenterAuditFlow(String listType, String kind, Long id) {
        if (id == null) {
            throw new BusinessException(400, "缺少记录 id");
        }
        String resolved = resolveListCenterKind(listType, kind);
        List<Map<String, Object>> stages = switch (resolved) {
            case "objection" -> buildObjectionTrackStages(id);
            case "demand" -> {
                BizDataDemand demand = demandMapper.selectById(id);
                if (demand == null) {
                    throw new BusinessException(404, "需求不存在");
                }
                yield buildDemandTrackStages(demand);
            }
            case "catalog" -> buildCatalogTrackStages(id);
            default -> List.of();
        };
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", resolved);
        out.put("id", id);
        out.put("stages", stages);
        return out;
    }

    private static String resolveListCenterKind(String listType, String kind) {
        if (kind != null && !kind.isBlank()) {
            String k = kind.trim().toLowerCase();
            if (k.startsWith("obj")) {
                return "objection";
            }
            if (k.startsWith("dem") || k.startsWith("sd") || "supply".equals(k)) {
                return "demand";
            }
            if (k.startsWith("cat")) {
                return "catalog";
            }
        }
        String type = listType == null ? "" : listType.trim();
        if (type.startsWith("objection")) {
            return "objection";
        }
        if (type.startsWith("sd-") || type.startsWith("demand")) {
            return "demand";
        }
        return "catalog";
    }

    private List<Map<String, Object>> buildObjectionTrackStages(Long id) {
        BizCatalogObjection obj = objectionMapper.selectById(id);
        if (obj == null) {
            throw new BusinessException(404, "异议不存在");
        }
        List<AuditLog> logs = auditService.listByResource("biz_catalog_objection", String.valueOf(id));
        List<Map<String, Object>> stages = new ArrayList<>();
        for (AuditLog a : logs) {
            stages.add(trackStage(
                    objectionActionStatusLabel(a.getAction(), a.getDetail()),
                    objectionActionResult(a.getAction(), a.getDetail(), obj),
                    a.getCreatedAt()));
        }
        if (!stages.isEmpty()) {
            return stages;
        }
        return synthesizeObjectionTrackStages(obj);
    }

    private List<Map<String, Object>> synthesizeObjectionTrackStages(BizCatalogObjection obj) {
        List<Map<String, Object>> stages = new ArrayList<>();
        String st = normalizeObjectionStatus(obj.getStatus());
        stages.add(trackStage("草稿", "创建异议", obj.getCreatedAt()));
        if (Set.of("SUBMITTED", "APPROVED", "REJECTED", "PROCESSED", "CLOSED").contains(st)) {
            stages.add(trackStage("待审核", "提出方已提交", obj.getUpdatedAt() != null ? obj.getUpdatedAt() : obj.getCreatedAt()));
        }
        if ("APPROVED".equals(st) || "PROCESSED".equals(st) || "CLOSED".equals(st)) {
            stages.add(trackStage("已审核待处理", "审核通过", obj.getUpdatedAt()));
        }
        if ("REJECTED".equals(st)) {
            stages.add(trackStage("驳回待提交",
                    firstNonBlank(obj.getHandlerNote(), "审核驳回"),
                    obj.getUpdatedAt()));
        }
        if ("PROCESSED".equals(st) || "CLOSED".equals(st)) {
            stages.add(trackStage("已处理",
                    firstNonBlank(obj.getHandlerNote(), "接收方已处理"),
                    obj.getUpdatedAt()));
        }
        if ("CLOSED".equals(st)) {
            stages.add(trackStage("已办结", "提出方办结", obj.getUpdatedAt()));
        }
        return stages;
    }

    private String objectionActionStatusLabel(String action, String detail) {
        if ("OBJECTION_CREATE".equals(action)) {
            if (detail != null && detail.toUpperCase().contains("DRAFT")) {
                return "草稿";
            }
            return "待审核";
        }
        if ("OBJECTION_UPDATE".equals(action)) {
            return "草稿";
        }
        if ("OBJECTION_DELETE".equals(action)) {
            return "已删除";
        }
        if ("OBJECTION_PROCESS".equals(action) && detail != null) {
            String d = detail.toUpperCase();
            if (d.contains("→DRAFT") || d.startsWith("WITHDRAW") || d.startsWith("REVOKE")) {
                return "草稿";
            }
            if (d.contains("→SUBMITTED") || d.startsWith("SUBMIT")) {
                return "待审核";
            }
            if (d.contains("→APPROVED") || d.startsWith("APPROVE")) {
                return "已审核待处理";
            }
            if (d.contains("→REJECTED") || d.startsWith("REJECT")) {
                return "驳回待提交";
            }
            if (d.contains("→PROCESSED") || d.startsWith("PROCESS")) {
                return "已处理";
            }
            if (d.contains("→CLOSED") || d.startsWith("CLOSE")) {
                return "已办结";
            }
        }
        return action != null ? action : "—";
    }

    private String objectionActionResult(String action, String detail, BizCatalogObjection obj) {
        if ("OBJECTION_CREATE".equals(action)) {
            return "创建异议";
        }
        if ("OBJECTION_UPDATE".equals(action)) {
            return "编辑异议";
        }
        if ("OBJECTION_DELETE".equals(action)) {
            return "删除异议";
        }
        if ("OBJECTION_PROCESS".equals(action) && detail != null) {
            String d = detail.toUpperCase();
            if (d.startsWith("SUBMIT") || d.contains("→SUBMITTED")) {
                return "提出方提交";
            }
            if (d.startsWith("WITHDRAW") || d.startsWith("REVOKE") || d.contains("→DRAFT")) {
                return "提出方撤销";
            }
            if (d.startsWith("APPROVE") || d.contains("→APPROVED")) {
                return "接收方审核通过";
            }
            if (d.startsWith("REJECT") || d.contains("→REJECTED")) {
                return firstNonBlank(obj.getHandlerNote(), "接收方驳回");
            }
            if (d.startsWith("PROCESS") || d.contains("→PROCESSED")) {
                return firstNonBlank(obj.getHandlerNote(), "接收方已处理");
            }
            if (d.startsWith("CLOSE") || d.contains("→CLOSED")) {
                return "提出方办结";
            }
            return detail;
        }
        return detail != null && !detail.isBlank() ? detail : "—";
    }

    private List<Map<String, Object>> buildCatalogTrackStages(Long catalogOrApprovalId) {
        // 入参优先按 govResourceId；否则门户目录 id / 审批 id
        Long govResourceId = catalogOrApprovalId;
        BizCatalogItem catalog = null;
        List<GovCatalogApproval> approvals = approvalMapper.selectList(new LambdaQueryWrapper<GovCatalogApproval>()
                .eq(GovCatalogApproval::getResourceId, catalogOrApprovalId)
                .orderByAsc(GovCatalogApproval::getId)
                .last("LIMIT 50"));
        if (approvals.isEmpty()) {
            catalog = catalogMapper.selectById(catalogOrApprovalId);
            if (catalog != null && catalog.getGovResourceId() != null) {
                govResourceId = catalog.getGovResourceId();
                approvals = approvalMapper.selectList(new LambdaQueryWrapper<GovCatalogApproval>()
                        .eq(GovCatalogApproval::getResourceId, govResourceId)
                        .orderByAsc(GovCatalogApproval::getId)
                        .last("LIMIT 50"));
            } else {
                GovCatalogApproval byId = approvalMapper.selectById(catalogOrApprovalId);
                if (byId != null) {
                    govResourceId = byId.getResourceId();
                    approvals = approvalMapper.selectList(new LambdaQueryWrapper<GovCatalogApproval>()
                            .eq(GovCatalogApproval::getResourceId, govResourceId)
                            .orderByAsc(GovCatalogApproval::getId)
                            .last("LIMIT 50"));
                }
            }
        } else {
            catalog = catalogMapper.selectOne(new LambdaQueryWrapper<BizCatalogItem>()
                    .eq(BizCatalogItem::getGovResourceId, govResourceId)
                    .last("LIMIT 1"));
        }
        List<Map<String, Object>> stages = new ArrayList<>();
        for (GovCatalogApproval a : approvals) {
            String actionZh = catalogApprovalActionLabel(a.getActionType());
            if (a.getSubmittedAt() != null || a.getSubmittedBy() != null) {
                stages.add(trackStage(
                        actionZh + "·提交",
                        firstNonBlank(a.getSubmitComment(), "提交人：" + str(a.getSubmittedBy(), "—")),
                        a.getSubmittedAt()));
            }
            if (a.getReviewedAt() != null || (a.getStatus() != null && !"PENDING".equalsIgnoreCase(a.getStatus()))) {
                stages.add(trackStage(
                        actionZh + "·" + catalogApprovalStatusLabel(a.getStatus()),
                        firstNonBlank(a.getReviewComment(), "审批人：" + str(a.getReviewedBy(), "—")),
                        a.getReviewedAt() != null ? a.getReviewedAt() : a.getSubmittedAt()));
            }
        }
        if (!stages.isEmpty()) {
            return stages;
        }
        if (catalog != null) {
            String pub = catalog.getPublishStatus() != null ? catalog.getPublishStatus() : "DRAFT";
            String pubZh = switch (pub.toUpperCase()) {
                case "PUBLISHED" -> "已发布";
                case "OFFLINE" -> "已下线";
                case "DRAFT" -> "草稿";
                case "PENDING_PUBLISH" -> "待发布";
                default -> pub;
            };
            stages.add(trackStage(
                    pubZh,
                    firstNonBlank(catalog.getDescription(), "目录记录"),
                    catalog.getUpdatedAt() != null ? catalog.getUpdatedAt() : catalog.getCreatedAt()));
        }
        return stages;
    }

    private static String catalogApprovalActionLabel(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            return "目录审批";
        }
        return switch (actionType.toUpperCase()) {
            case "PUBLISH" -> "发布";
            case "UPDATE" -> "变更";
            case "OFFLINE" -> "下线";
            case "BIND" -> "挂载";
            case "UNBIND" -> "解挂";
            default -> actionType;
        };
    }

    private static String catalogApprovalStatusLabel(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return switch (status.toUpperCase()) {
            case "PENDING" -> "待审";
            case "APPROVED" -> "通过";
            case "REJECTED" -> "驳回";
            case "WITHDRAWN" -> "已撤回";
            default -> status;
        };
    }

    private boolean isListCenterPlatform(UserPrincipal operator) {
        if (operator == null) {
            return true;
        }
        if (operator.isSystemAdmin() || operator.isPlatformAdmin()) {
            return true;
        }
        return operator.getPermissions() != null
                && (operator.getPermissions().contains("portal:supply:approve")
                || operator.getPermissions().contains("system:exchange:supply-config"));
    }

    private String resolveOrgName(UserPrincipal operator) {
        if (operator == null || operator.getOrgId() == null) {
            return "";
        }
        SysOrg org = orgMapper.selectById(operator.getOrgId());
        return org != null ? str(org.getOrgName(), "") : "";
    }

    /** 清单行按组织隔离：匹配提供方 / 需求方 / 核查方任一 */
    private List<Map<String, Object>> filterCatalogRowsByOrg(List<Map<String, Object>> rows,
                                                             String orgName, boolean platform) {
        if (platform || orgName == null || orgName.isBlank() || rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String provider = str(r.get("providerOrg"), "");
            String requester = str(r.get("requesterOrg"), "");
            String verify = str(r.get("verifyOrg"), "");
            if (orgName.equals(provider) || orgName.equals(requester) || orgName.equals(verify)) {
                out.add(r);
            }
        }
        return out;
    }

    private List<String> catalogListColumns() {
        return List.of("catalogCode", "catalogName", "providerOrg", "shareAttr", "catalogOriginLabel",
                "versionNo", "status", "createdAt", "actions");
    }

    private List<String> supplyListColumns() {
        return List.of("demandScene", "demandCatalog", "demandService", "requesterOrg", "providerOrg",
                "resourceLevel", "status", "createdAt", "actions");
    }

    private List<String> objectionListColumns() {
        return List.of("title", "objectName", "serviceName", "providerOrg", "verifyOrg", "status", "createdAt", "actions");
    }

    private List<Map<String, Object>> unifiedCatalogRows(String publishStatus) {
        List<BizCatalogItem> items = catalogMapper.selectList(new LambdaQueryWrapper<BizCatalogItem>()
                .isNotNull(BizCatalogItem::getGovResourceId)
                .eq(publishStatus != null, BizCatalogItem::getPublishStatus, publishStatus)
                .orderByDesc(BizCatalogItem::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizCatalogItem c : items) {
            rows.add(toCatalogRow(c));
        }
        return rows;
    }

    /** 变更/更新：审批记录 + 版本号 + 来源（指标与目录 / 数据目录） */
    private List<Map<String, Object>> catalogChangeOrUpdateRows(String actionType) {
        List<Map<String, Object>> rows = catalogListByApproval(actionType);
        if (!rows.isEmpty()) {
            return rows;
        }
        // 无审批记录时回退：已发布且发生过元数据更新的门户目录
        return catalogUpdateRows();
    }

    /** 已发布目录中近期有更新的记录（updatedAt 晚于 createdAt） */
    private List<Map<String, Object>> catalogUpdateRows() {
        List<BizCatalogItem> items = catalogMapper.selectList(new LambdaQueryWrapper<BizCatalogItem>()
                .isNotNull(BizCatalogItem::getGovResourceId)
                .eq(BizCatalogItem::getPublishStatus, "PUBLISHED")
                .orderByDesc(BizCatalogItem::getUpdatedAt)
                .last("LIMIT 200"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizCatalogItem c : items) {
            if (c.getUpdatedAt() == null || c.getCreatedAt() == null) {
                continue;
            }
            if (!c.getUpdatedAt().isAfter(c.getCreatedAt())) {
                continue;
            }
            Map<String, Object> row = toCatalogRow(c);
            row.put("description", "目录元数据/发布信息已更新");
            row.put("actionType", "UPDATE");
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> toCatalogRow(BizCatalogItem c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.getId());
        row.put("code", c.getCatalogCode());
        row.put("catalogCode", c.getCatalogCode());
        row.put("catalogName", c.getTitle());
        row.put("title", c.getTitle());
        row.put("providerOrg", c.getProviderOrg());
        row.put("shareAttr", c.getShareModes());
        String origin = c.getCatalogOrigin();
        Integer versionNo = null;
        if (c.getGovResourceId() != null) {
            GovCatalogResource gov = govResourceMapper.selectById(c.getGovResourceId());
            if (gov != null) {
                if (origin == null || origin.isBlank()) {
                    origin = gov.getCatalogOrigin();
                }
                versionNo = gov.getVersionNo();
            }
        }
        row.put("catalogOrigin", origin);
        row.put("catalogOriginLabel", catalogOriginLabel(origin));
        row.put("versionNo", versionNo != null ? versionNo : "—");
        row.put("govResourceId", c.getGovResourceId());
        row.put("status", c.getPublishStatus());
        row.put("publishStatus", c.getPublishStatus());
        row.put("createdAt", c.getCreatedAt());
        row.put("publishedAt", c.getPublishedAt());
        row.put("updatedAt", c.getUpdatedAt());
        row.put("description", c.getDescription());
        row.put("themeName", c.getThemeName());
        row.put("baseCatalogName", c.getBaseCatalogName());
        row.put("actions", List.of("view", "export"));
        return row;
    }

    private static String catalogOriginLabel(String origin) {
        if (origin == null || origin.isBlank()) {
            return "—";
        }
        return switch (origin.toUpperCase()) {
            case "INGEST" -> "指标与目录";
            case "GOVERNANCE" -> "数据目录";
            default -> origin;
        };
    }

    private List<Map<String, Object>> catalogListByApproval(String actionType) {
        List<GovCatalogApproval> approvals = approvalMapper.selectList(new LambdaQueryWrapper<GovCatalogApproval>()
                .eq(GovCatalogApproval::getActionType, actionType)
                .eq(GovCatalogApproval::getStatus, "APPROVED")
                .orderByDesc(GovCatalogApproval::getId)
                .last("LIMIT 200"));
        Map<Long, BizCatalogItem> byGov = publishedCatalogs().stream()
                .filter(c -> c.getGovResourceId() != null)
                .collect(Collectors.toMap(BizCatalogItem::getGovResourceId, c -> c, (a, b) -> a));
        List<BizCatalogItem> allLinked = catalogMapper.selectList(new LambdaQueryWrapper<BizCatalogItem>()
                .isNotNull(BizCatalogItem::getGovResourceId));
        Map<Long, BizCatalogItem> allByGov = allLinked.stream()
                .collect(Collectors.toMap(BizCatalogItem::getGovResourceId, c -> c, (a, b) -> a));
        allByGov.putAll(byGov);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (GovCatalogApproval a : approvals) {
            Map<String, Object> row = new LinkedHashMap<>();
            BizCatalogItem portal = allByGov.get(a.getResourceId());
            GovCatalogResource gov = govResourceMapper.selectById(a.getResourceId());
            if (portal != null) {
                row.putAll(toCatalogRow(portal));
            } else if (gov != null) {
                row.put("code", gov.getResourceCode());
                row.put("catalogCode", gov.getResourceCode());
                row.put("catalogName", gov.getResourceName());
                row.put("title", gov.getResourceName());
                row.put("providerOrg", gov.getProviderOrg());
                row.put("shareAttr", gov.getShareType());
                row.put("catalogOrigin", gov.getCatalogOrigin());
                row.put("catalogOriginLabel", catalogOriginLabel(gov.getCatalogOrigin()));
                row.put("versionNo", gov.getVersionNo() != null ? gov.getVersionNo() : "—");
                row.put("govResourceId", gov.getId());
                row.put("status", gov.getPublishStatus());
                row.put("actions", List.of("view", "export"));
            } else {
                row.put("code", "GOV-" + a.getResourceId());
                row.put("catalogCode", "GOV-" + a.getResourceId());
                row.put("catalogName", "资源#" + a.getResourceId());
                row.put("title", "资源#" + a.getResourceId());
                row.put("catalogOriginLabel", "—");
                row.put("versionNo", "—");
                row.put("status", a.getStatus());
                row.put("actions", List.of("view"));
            }
            row.put("approvalId", a.getId());
            row.put("actionType", a.getActionType());
            row.put("reviewedAt", a.getReviewedAt());
            row.put("reviewedBy", a.getReviewedBy());
            row.put("createdAt", a.getSubmittedAt() != null ? a.getSubmittedAt() : row.get("createdAt"));
            row.put("description", a.getReviewComment());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> demandManifestRows(Set<String> statuses, String orgRole, String orgName) {
        List<BizDataDemand> demands = demandMapper.selectList(new LambdaQueryWrapper<BizDataDemand>()
                .in(BizDataDemand::getStatus, statuses)
                .orderByDesc(BizDataDemand::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizDataDemand d : demands) {
            if (orgName != null && !orgName.isBlank() && orgRole != null) {
                if ("provider".equals(orgRole)) {
                    if (d.getAssigneeOrg() == null || !orgName.equals(d.getAssigneeOrg())) {
                        continue;
                    }
                } else if ("requester".equals(orgRole)) {
                    if (d.getRequesterOrg() == null || !orgName.equals(d.getRequesterOrg())) {
                        continue;
                    }
                } else if ("related".equals(orgRole)) {
                    boolean ok = orgName.equals(d.getRequesterOrg()) || orgName.equals(d.getAssigneeOrg());
                    if (!ok) {
                        continue;
                    }
                }
            }
            Map<String, Object> form = parseFormPayloadMap(d.getFormPayload());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId());
            row.put("code", "DEMAND-" + d.getId());
            row.put("title", d.getDemandTitle());
            row.put("demandScene", firstNonBlank(
                    str(form.get("scene"), null),
                    str(form.get("serviceDemandType"), null),
                    d.getDemandType()));
            String catalogTitle = firstNonBlank(
                    str(form.get("catalogTitle"), null),
                    str(form.get("dataName"), null));
            if ((catalogTitle == null || catalogTitle.isBlank()) && d.getMatchedCatalogId() != null) {
                BizCatalogItem cat = catalogMapper.selectById(d.getMatchedCatalogId());
                if (cat != null) {
                    catalogTitle = cat.getTitle();
                }
            }
            row.put("demandCatalog", catalogTitle != null ? catalogTitle : "—");
            row.put("demandService", firstNonBlank(
                    str(form.get("systemName"), null),
                    str(form.get("appName"), null),
                    str(form.get("serviceName"), null),
                    "—"));
            row.put("requesterOrg", d.getRequesterOrg());
            row.put("providerOrg", d.getAssigneeOrg());
            row.put("resourceLevel", firstNonBlank(
                    str(form.get("resourceLevel"), null),
                    str(form.get("authLevel"), null),
                    "DEPT"));
            row.put("matchedCatalogId", d.getMatchedCatalogId());
            row.put("status", d.getStatus());
            row.put("stage", d.getStage());
            row.put("fulfillPath", d.getFulfillPath());
            row.put("createdAt", d.getCreatedAt());
            row.put("description", d.getAnalysisNote() != null ? d.getAnalysisNote() : d.getConfirmNote());
            row.put("actions", List.of("view", "export", "track"));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> dutyHistoryRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizDataDuty d : dutyMapper.selectList(new LambdaQueryWrapper<BizDataDuty>().orderByDesc(BizDataDuty::getId))) {
            BizDataDemand demand = d.getDemandId() != null ? demandMapper.selectById(d.getDemandId()) : null;
            String catalogTitle = "—";
            if (d.getCatalogId() != null) {
                BizCatalogItem cat = catalogMapper.selectById(d.getCatalogId());
                if (cat != null) {
                    catalogTitle = cat.getTitle();
                } else {
                    catalogTitle = "目录#" + d.getCatalogId();
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId());
            row.put("code", "DUTY-" + d.getId());
            row.put("title", demand != null ? demand.getDemandTitle() : ("数据责任#" + d.getId()));
            row.put("demandId", d.getDemandId());
            row.put("demandScene", demand != null ? demand.getDemandType() : "授权");
            row.put("demandCatalog", catalogTitle);
            row.put("demandService", d.getFulfillPath());
            row.put("requesterOrg", demand != null ? demand.getRequesterOrg() : "—");
            row.put("providerOrg", d.getDutyOrg());
            row.put("resourceLevel", "DEPT");
            row.put("status", d.getStatus());
            row.put("fulfillPath", d.getFulfillPath());
            row.put("catalogId", d.getCatalogId());
            row.put("description", d.getRemark());
            row.put("createdAt", d.getCreatedAt());
            row.put("actions", List.of("view", "export"));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> cascadeManifestRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizSupplyManifest m : listManifests(null)) {
            if (m.getCascadeFlag() != null && m.getCascadeFlag() == 1) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", m.getId());
                row.put("code", m.getManifestType() + "-" + m.getId());
                row.put("title", m.getTitle());
                row.put("demandScene", m.getManifestType());
                row.put("demandCatalog", "—");
                row.put("demandService", "级联下行");
                row.put("requesterOrg", "—");
                row.put("providerOrg", "—");
                row.put("resourceLevel", str(m.getAuthLevel(), "DEPT"));
                row.put("status", m.getStatus());
                row.put("authLevel", m.getAuthLevel());
                row.put("description", m.getExportPayload());
                row.put("createdAt", m.getCreatedAt());
                row.put("actions", List.of("view", "export"));
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> objectionRowsByStatuses(Set<String> statuses, String orgRole, String orgName) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizCatalogObjection o : listObjections(null)) {
            String st = normalizeObjectionStatus(o.getStatus());
            if (statuses != null && !statuses.contains(st)) {
                continue;
            }
            if (orgName != null && !orgName.isBlank() && orgRole != null) {
                if ("provider".equals(orgRole)) {
                    // 异议提出单位（需求部门）
                    if (o.getProviderOrg() == null || !orgName.equals(o.getProviderOrg())) {
                        continue;
                    }
                } else if ("verify".equals(orgRole)) {
                    if (o.getVerifyOrg() == null || !orgName.equals(o.getVerifyOrg())) {
                        continue;
                    }
                } else if ("related".equals(orgRole)) {
                    boolean ok = orgName.equals(o.getProviderOrg()) || orgName.equals(o.getVerifyOrg());
                    if (!ok) {
                        continue;
                    }
                }
            }
            o.setStatus(st);
            rows.add(toObjectionRow(o));
        }
        return rows;
    }

    /** 兼容旧三态 */
    private static String normalizeObjectionStatus(String status) {
        if (status == null || status.isBlank()) {
            return "DRAFT";
        }
        return switch (status.toUpperCase()) {
            case "OPEN" -> "SUBMITTED";
            case "PROCESSING" -> "APPROVED";
            case "CLOSED" -> "CLOSED";
            default -> status.toUpperCase();
        };
    }

    private Map<String, Object> toObjectionRow(BizCatalogObjection o) {
        Map<String, Object> row = new LinkedHashMap<>();
        String st = normalizeObjectionStatus(o.getStatus());
        row.put("id", o.getId());
        row.put("title", o.getTitle() != null ? o.getTitle() : ("异议#" + o.getId()));
        row.put("catalogId", o.getCatalogId());
        row.put("demandId", o.getDemandId());
        row.put("objectionType", o.getObjectionType());
        row.put("content", o.getContent());
        String objectName = "—";
        String serviceName = "—";
        if (o.getCatalogId() != null) {
            BizCatalogItem cat = catalogMapper.selectById(o.getCatalogId());
            if (cat != null) {
                objectName = cat.getTitle();
                serviceName = firstNonBlank(cat.getBaseCatalogName(), cat.getThemeName(), cat.getCatalogKind(), "门户目录");
            } else {
                objectName = "目录#" + o.getCatalogId();
            }
        } else if (o.getDemandId() != null) {
            objectName = "需求#" + o.getDemandId();
            serviceName = "供需对接";
        }
        row.put("objectName", objectName);
        row.put("serviceName", serviceName);
        row.put("providerOrg", o.getProviderOrg());
        row.put("verifyOrg", o.getVerifyOrg());
        row.put("status", st);
        row.put("handlerNote", o.getHandlerNote());
        row.put("createdAt", o.getCreatedAt());
        row.put("createdBy", o.getCreatedBy());
        // 前端按提出方/接收方/超管裁剪；此处给出状态可用操作全集
        List<String> actions = new ArrayList<>();
        actions.add("view");
        if ("DRAFT".equals(st) || "REJECTED".equals(st)) {
            actions.add("edit");
            actions.add("submit");
            actions.add("delete");
        }
        if ("SUBMITTED".equals(st)) {
            actions.add("withdraw");
            actions.add("approve");
            actions.add("reject");
        }
        if ("APPROVED".equals(st)) {
            actions.add("process");
        }
        if ("PROCESSED".equals(st)) {
            actions.add("close");
        }
        actions.add("export");
        row.put("actions", actions);
        return row;
    }

    private Map<String, Object> objectionStats() {
        return objectionStats(null, null);
    }

    private Map<String, Object> objectionStats(String orgRole, String orgName) {
        List<BizCatalogObjection> all = listObjections(null);
        if (orgName != null && !orgName.isBlank() && orgRole != null) {
            all = all.stream().filter(o -> {
                if ("provider".equals(orgRole)) {
                    return orgName.equals(o.getProviderOrg());
                }
                if ("verify".equals(orgRole)) {
                    return orgName.equals(o.getVerifyOrg());
                }
                if ("related".equals(orgRole)) {
                    return orgName.equals(o.getProviderOrg()) || orgName.equals(o.getVerifyOrg());
                }
                return true;
            }).toList();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String s : List.of("DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "PROCESSED", "CLOSED")) {
            counts.put(s, 0L);
        }
        Map<String, Long> byType = new LinkedHashMap<>();
        for (BizCatalogObjection o : all) {
            String st = normalizeObjectionStatus(o.getStatus());
            counts.merge(st, 1L, Long::sum);
            String t = o.getObjectionType() == null ? "OTHER" : o.getObjectionType();
            byType.merge(t, 1L, Long::sum);
        }
        List<Map<String, Object>> byStatus = new ArrayList<>();
        byStatus.add(Map.of("code", "DRAFT", "title", "草稿", "status", "DRAFT", "count", counts.get("DRAFT")));
        byStatus.add(Map.of("code", "SUBMITTED", "title", "待审核", "status", "SUBMITTED", "count", counts.get("SUBMITTED")));
        byStatus.add(Map.of("code", "APPROVED", "title", "已审核待处理", "status", "APPROVED", "count", counts.get("APPROVED")));
        byStatus.add(Map.of("code", "REJECTED", "title", "驳回待提交", "status", "REJECTED", "count", counts.get("REJECTED")));
        byStatus.add(Map.of("code", "PROCESSED", "title", "已处理", "status", "PROCESSED", "count", counts.get("PROCESSED")));
        byStatus.add(Map.of("code", "CLOSED", "title", "已办结", "status", "CLOSED", "count", counts.get("CLOSED")));
        List<Map<String, Object>> typeRows = new ArrayList<>();
        byType.forEach((k, v) -> typeRows.add(Map.of("code", k, "title", k, "count", v)));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        stats.put("draft", counts.get("DRAFT"));
        stats.put("submitted", counts.get("SUBMITTED"));
        stats.put("approved", counts.get("APPROVED"));
        stats.put("rejected", counts.get("REJECTED"));
        stats.put("processed", counts.get("PROCESSED"));
        stats.put("closed", counts.get("CLOSED"));
        // 兼容旧前端 KPI 字段
        stats.put("open", counts.get("SUBMITTED"));
        stats.put("processing", counts.get("APPROVED") + counts.get("PROCESSED"));
        stats.put("byStatus", byStatus);
        stats.put("byType", typeRows);
        return stats;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFormPayloadMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            Object v = new com.fasterxml.jackson.databind.ObjectMapper().readValue(raw, Map.class);
            if (v instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
        } catch (Exception ignored) {
            // ignore
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object v) {
        if (v instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of("value", String.valueOf(v));
    }

    public List<BizCatalogItem> catalogManifest() {
        return catalogMapper.selectList(new LambdaQueryWrapper<BizCatalogItem>()
                .isNotNull(BizCatalogItem::getGovResourceId)
                .in(BizCatalogItem::getPublishStatus, "PUBLISHED", "OFFLINE", "DRAFT", "PENDING_PUBLISH")
                .orderByDesc(BizCatalogItem::getId));
    }

    public List<BizCatalogItem> publishedCatalogs() {
        return catalogMapper.selectList(new LambdaQueryWrapper<BizCatalogItem>()
                .isNotNull(BizCatalogItem::getGovResourceId)
                .eq(BizCatalogItem::getPublishStatus, "PUBLISHED")
                .orderByDesc(BizCatalogItem::getId));
    }

    @Transactional
    public Long createCatalog(UserPrincipal operator, Map<String, Object> body) {
        throw new BusinessException(400, "供需对接系统不再维护目录；请在指标与目录体系构建或数据目录管理系统编目并审批发布");
    }

    @Transactional
    public void publishCatalog(UserPrincipal operator, Long id) {
        throw new BusinessException(400, "供需对接系统不再发布目录；请通过统一目录审批发布后同步至门户");
    }

    @Transactional
    public void offlineCatalog(UserPrincipal operator, Long id, Map<String, Object> body) {
        throw new BusinessException(400, "供需对接系统不再下线目录；请通过统一目录提交下线审批");
    }

    public Map<String, Object> exportCatalogManifest() {
        List<BizCatalogItem> items = catalogManifest();
        StringBuilder csv = new StringBuilder("catalogCode,title,publishStatus,catalogOrigin,govResourceId,providerOrg\n");
        for (BizCatalogItem i : items) {
            csv.append(i.getCatalogCode()).append(',')
                    .append(i.getTitle()).append(',')
                    .append(i.getPublishStatus()).append(',')
                    .append(Objects.toString(i.getCatalogOrigin(), "")).append(',')
                    .append(Objects.toString(i.getGovResourceId(), "")).append(',')
                    .append(Objects.toString(i.getProviderOrg(), "")).append('\n');
        }
        return Map.of("format", "csv", "rowCount", items.size(), "content", csv.toString());
    }

    public List<BizCatalogObjection> listObjections(String status) {
        LambdaQueryWrapper<BizCatalogObjection> q = new LambdaQueryWrapper<BizCatalogObjection>()
                .orderByDesc(BizCatalogObjection::getUpdatedAt)
                .orderByDesc(BizCatalogObjection::getCreatedAt)
                .orderByDesc(BizCatalogObjection::getId);
        if (status != null && !status.isBlank()) {
            q.eq(BizCatalogObjection::getStatus, status);
        }
        return objectionMapper.selectList(q);
    }

    @Transactional
    public Long createObjection(UserPrincipal operator, Map<String, Object> body) {
        if (operator != null && operator.isSystemAdmin()) {
            throw new BusinessException(403, "超级管理员仅可查看/删除异议，不可提交异议");
        }
        // 仅操作异议表；要求挂已通过的需求，不改需求状态
        Long demandId = body.get("demandId") == null || String.valueOf(body.get("demandId")).isBlank()
                ? null : Long.valueOf(String.valueOf(body.get("demandId")));
        if (demandId == null) {
            throw new BusinessException(400, "请选择已通过的数据需求后再提出异议");
        }
        BizDataDemand demand = demandMapper.selectById(demandId);
        if (demand == null) {
            throw new BusinessException(404, "需求不存在");
        }
        if (!Set.of("CONFIRMED", "COMPLETED").contains(String.valueOf(demand.getStatus()))) {
            throw new BusinessException(400, "仅可对已确认/已办结的需求提出异议");
        }

        Long catalogId = null;
        if (body.get("catalogId") != null && !String.valueOf(body.get("catalogId")).isBlank()) {
            catalogId = Long.valueOf(String.valueOf(body.get("catalogId")));
        } else if (demand.getMatchedCatalogId() != null) {
            catalogId = demand.getMatchedCatalogId();
        } else if (demand.getTargetCatalogId() != null) {
            catalogId = demand.getTargetCatalogId();
        }
        if (catalogId == null) {
            throw new BusinessException(400, "该需求未关联目录，无法登记异议");
        }
        BizCatalogItem catalog = catalogMapper.selectById(catalogId);
        if (catalog == null) {
            throw new BusinessException(400, "关联目录不存在");
        }

        boolean draft = Boolean.TRUE.equals(body.get("draft"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("draft")));
        String orgName = resolveOrgName(operator);

        BizCatalogObjection obj = new BizCatalogObjection();
        obj.setCatalogId(catalogId);
        obj.setDemandId(demandId);
        obj.setTitle(str(body.get("title"), "数据异议-" + demand.getDemandTitle()));
        obj.setObjectionType(str(body.get("objectionType"), "QUALITY"));
        obj.setContent(required(body.get("content"), "content").toString());
        // 提出单位=需求部门；核查单位=供数部门
        obj.setProviderOrg(str(body.get("providerOrg"),
                firstNonBlank(orgName, demand.getRequesterOrg(), operator.getUsername())));
        obj.setVerifyOrg(str(body.get("verifyOrg"),
                firstNonBlank(demand.getAssigneeOrg(), catalog.getProviderOrg())));
        obj.setStatus(draft ? "DRAFT" : "SUBMITTED");
        obj.setCreatedBy(operator.getUsername());
        objectionMapper.insert(obj);
        upsertObjectionManifest(obj);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "OBJECTION_CREATE", "biz_catalog_objection", String.valueOf(obj.getId()), obj.getStatus());
        return obj.getId();
    }

    /**
     * 异议状态机（独立，不改需求/目录其它业务状态）：
     * DRAFT→SUBMITTED(待审核)→APPROVED(已审核待处理)|REJECTED(驳回待提交)→PROCESSED→CLOSED
     * SUBMITTED 可由提出方 WITHDRAW 回草稿。
     */
    @Transactional
    public void processObjection(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizCatalogObjection obj = objectionMapper.selectById(id);
        if (obj == null) {
            throw new BusinessException(404, "异议不存在");
        }
        String cur = normalizeObjectionStatus(obj.getStatus());
        String action = str(body.get("action"), "CLOSE").toUpperCase();
        String note = str(body.get("handlerNote"), "");

        // 停用「回流需求审核」，避免异议模块改动其它业务状态机
        if ("REOPEN_AUDIT".equals(action) || "CORRECT".equals(action) || "CORRECTION".equals(action)) {
            throw new BusinessException(400, "异议清单不再回流修改需求状态；请在异议流程内处理或驳回");
        }

        if (operator != null && operator.isSystemAdmin()
                && Set.of("SUBMIT", "APPROVE", "REJECT", "PROCESS", "PROCESSING", "CLOSE", "CLOSED", "WITHDRAW", "REVOKE")
                .contains(action)) {
            throw new BusinessException(403, "超级管理员仅可查看/删除异议，不可审批或提交");
        }

        String orgName = resolveOrgName(operator);
        boolean platform = isListCenterPlatform(operator) && (operator == null || !operator.isSystemAdmin());
        boolean raiser = orgName != null && !orgName.isBlank() && orgName.equals(obj.getProviderOrg());
        boolean receiver = orgName != null && !orgName.isBlank() && orgName.equals(obj.getVerifyOrg());

        String next;
        switch (action) {
            case "SUBMIT" -> {
                if (!"DRAFT".equals(cur) && !"REJECTED".equals(cur)) {
                    throw new BusinessException(400, "仅草稿或驳回待提交可提交");
                }
                if (!platform && !raiser) {
                    throw new BusinessException(403, "仅异议提出方可提交");
                }
                next = "SUBMITTED";
            }
            case "WITHDRAW", "REVOKE" -> {
                if (!"SUBMITTED".equals(cur)) {
                    throw new BusinessException(400, "仅待审核状态可撤销");
                }
                if (!platform && !raiser) {
                    throw new BusinessException(403, "仅异议提出方可撤销");
                }
                next = "DRAFT";
            }
            case "APPROVE" -> {
                if (!"SUBMITTED".equals(cur)) {
                    throw new BusinessException(400, "仅待审核可审核通过");
                }
                if (!platform && !receiver) {
                    throw new BusinessException(403, "仅异议接收方可审核");
                }
                next = "APPROVED";
            }
            case "REJECT" -> {
                if (!"SUBMITTED".equals(cur)) {
                    throw new BusinessException(400, "仅待审核可驳回");
                }
                if (!platform && !receiver) {
                    throw new BusinessException(403, "仅异议接收方可审核");
                }
                if (note.isBlank()) {
                    throw new BusinessException(400, "驳回请填写理由");
                }
                next = "REJECTED";
            }
            case "PROCESS", "PROCESSING" -> {
                if (!"APPROVED".equals(cur)) {
                    throw new BusinessException(400, "仅已审核待处理可转入处理完成");
                }
                if (!platform && !receiver) {
                    throw new BusinessException(403, "仅异议接收方可处理");
                }
                next = "PROCESSED";
            }
            case "CLOSE", "CLOSED" -> {
                if (!"PROCESSED".equals(cur)) {
                    throw new BusinessException(400, "仅已处理状态可由提出方办结");
                }
                if (!platform && !raiser) {
                    throw new BusinessException(403, "仅异议提出方可办结");
                }
                next = "CLOSED";
            }
            default -> throw new BusinessException(400, "不支持的操作: " + action);
        }

        obj.setStatus(next);
        if (!note.isBlank()) {
            obj.setHandlerNote(note);
        }
        objectionMapper.updateById(obj);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "OBJECTION_PROCESS", "biz_catalog_objection", String.valueOf(id), action + "→" + next);
    }

    @Transactional
    public void updateObjection(UserPrincipal operator, Long id, Map<String, Object> body) {
        if (operator != null && operator.isSystemAdmin()) {
            throw new BusinessException(403, "超级管理员不可编辑异议");
        }
        BizCatalogObjection obj = objectionMapper.selectById(id);
        if (obj == null) {
            throw new BusinessException(404, "异议不存在");
        }
        String cur = normalizeObjectionStatus(obj.getStatus());
        if (!"DRAFT".equals(cur) && !"REJECTED".equals(cur)) {
            throw new BusinessException(400, "仅草稿或驳回待提交可编辑");
        }
        String orgName = resolveOrgName(operator);
        boolean platform = isListCenterPlatform(operator)
                && (operator == null || !operator.isSystemAdmin());
        if (!platform && (orgName.isBlank() || !orgName.equals(obj.getProviderOrg()))) {
            throw new BusinessException(403, "仅异议提出方可编辑");
        }
        if (body.get("title") != null && !String.valueOf(body.get("title")).isBlank()) {
            obj.setTitle(String.valueOf(body.get("title")).trim());
        }
        if (body.get("objectionType") != null && !String.valueOf(body.get("objectionType")).isBlank()) {
            obj.setObjectionType(String.valueOf(body.get("objectionType")).trim());
        }
        if (body.get("content") != null) {
            String content = String.valueOf(body.get("content")).trim();
            if (content.isBlank()) {
                throw new BusinessException(400, "异议内容不能为空");
            }
            obj.setContent(content);
        }
        objectionMapper.updateById(obj);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "OBJECTION_UPDATE", "biz_catalog_objection", String.valueOf(id), cur);
    }

    @Transactional
    public void deleteObjection(UserPrincipal operator, Long id) {
        BizCatalogObjection obj = objectionMapper.selectById(id);
        if (obj == null) {
            throw new BusinessException(404, "异议不存在");
        }
        String cur = normalizeObjectionStatus(obj.getStatus());
        boolean superAdmin = operator != null && operator.isSystemAdmin();
        if (!superAdmin) {
            if (!"DRAFT".equals(cur) && !"REJECTED".equals(cur)) {
                throw new BusinessException(400, "仅草稿或驳回待提交可删除");
            }
            String orgName = resolveOrgName(operator);
            boolean platform = isListCenterPlatform(operator);
            if (!platform && (orgName.isBlank() || !orgName.equals(obj.getProviderOrg()))) {
                throw new BusinessException(403, "仅异议提出方可删除");
            }
        }
        objectionMapper.deleteById(id);
        manifestMapper.delete(new LambdaQueryWrapper<BizSupplyManifest>()
                .eq(BizSupplyManifest::getManifestType, "OBJECTION")
                .eq(BizSupplyManifest::getRefId, id));
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "OBJECTION_DELETE", "biz_catalog_objection", String.valueOf(id), cur);
    }

    public List<BizSupplyManifest> listManifests(String manifestType) {
        LambdaQueryWrapper<BizSupplyManifest> q = new LambdaQueryWrapper<BizSupplyManifest>()
                .orderByDesc(BizSupplyManifest::getCreatedAt)
                .orderByDesc(BizSupplyManifest::getId);
        if (manifestType != null && !manifestType.isBlank()) {
            q.eq(BizSupplyManifest::getManifestType, manifestType);
        }
        return manifestMapper.selectList(q);
    }

    public Map<String, Object> exportManifest(Long id) {
        BizSupplyManifest m = manifestMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(404, "清单不存在");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("manifestId", m.getId());
        payload.put("manifestType", m.getManifestType());
        payload.put("title", m.getTitle());
        payload.put("authLevel", m.getAuthLevel());
        payload.put("cascadeFlag", m.getCascadeFlag());
        payload.put("exportPayload", m.getExportPayload());
        payload.put("exportedAt", java.time.LocalDateTime.now().toString());
        m.setExportPayload(payload.toString());
        manifestMapper.updateById(m);
        return payload;
    }

    private Map<String, Object> buildIntegrationSummary(BizDataDemand demand, BizDataDuty duty,
                                                        List<BizDemandSupplyTask> tasks) {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("system", "目录系统");
        catalog.put("matchedCatalogId", demand.getMatchedCatalogId());
        catalog.put("dutyId", duty.getId());
        catalog.put("status", duty.getStatus());

        Map<String, Object> collect = new LinkedHashMap<>();
        collect.put("system", "数据归集系统");
        collect.put("tasks", tasks.stream().filter(t -> "COLLECT".equals(t.getTaskType())).map(t -> Map.of(
                "taskId", t.getId(), "taskName", t.getTaskName(), "status", t.getStatus(),
                "ref", t.getRefFlowCode() == null ? "" : t.getRefFlowCode()
        )).toList());

        Map<String, Object> exchange = new LinkedHashMap<>();
        exchange.put("system", "共享交换系统");
        exchange.put("tasks", tasks.stream().filter(t -> "EXCHANGE".equals(t.getTaskType()) || "SHARE".equals(t.getTaskType()))
                .map(t -> Map.of(
                        "taskId", t.getId(), "taskType", t.getTaskType(), "taskName", t.getTaskName(),
                        "status", t.getStatus(), "ref", t.getRefFlowCode() == null ? "" : t.getRefFlowCode()
                )).toList());

        Map<String, Object> integrations = new LinkedHashMap<>();
        integrations.put("catalog", catalog);
        integrations.put("collect", collect);
        integrations.put("exchange", exchange);
        integrations.put("message", "已转换为数据责任，并生成归集/共享/交换任务台账");
        return integrations;
    }

    private BizDataDuty createDataDuty(UserPrincipal operator, BizDataDemand demand, String path) {
        BizDataDuty duty = new BizDataDuty();
        duty.setDemandId(demand.getId());
        duty.setDutyOrg(str(demand.getAssigneeOrg(), demand.getRequesterOrg()));
        duty.setDutyType(PATH_COLLECT.equals(path) ? "COLLECT" : "AUTHORIZE");
        duty.setCatalogId(demand.getMatchedCatalogId() != null ? demand.getMatchedCatalogId() : demand.getTargetCatalogId());
        duty.setFulfillPath(path);
        duty.setStatus("ACTIVE");
        duty.setRemark("确认生成数据责任：" + demand.getDemandTitle());
        duty.setCreatedBy(operator.getUsername());
        dutyMapper.insert(duty);
        return duty;
    }

    private List<BizDemandSupplyTask> createSupplyTasks(BizDataDemand demand, String path) {
        List<BizDemandSupplyTask> tasks = new ArrayList<>();
        if (PATH_COLLECT.equals(path)) {
            tasks.add(insertTask(demand.getId(), "COLLECT", "归集任务-" + demand.getDemandTitle(), "PENDING", null));
        }
        tasks.add(insertTask(demand.getId(), "SHARE", "共享页面-" + demand.getDemandTitle(), "PENDING", null));
        tasks.add(insertTask(demand.getId(), "API", "接口服务-" + demand.getDemandTitle(), "PENDING", null));
        BizEsbFlow flow = esbFlowMapper.selectOne(new LambdaQueryWrapper<BizEsbFlow>().last("LIMIT 1"));
        String flowCode = flow != null ? flow.getFlowCode() : "MF_DEMO_001";
        tasks.add(insertTask(demand.getId(), "EXCHANGE", "交换作业-" + demand.getDemandTitle(), "PENDING", flowCode));
        demand.setStage("SUPPLY");
        demandMapper.updateById(demand);
        return tasks;
    }

    private void dispatchDownstreamTasks(UserPrincipal operator, BizDataDemand demand, List<BizDemandSupplyTask> tasks) {
        for (BizDemandSupplyTask t : tasks) {
            try {
                if ("COLLECT".equals(t.getTaskType())) {
                    BizCollectTask ct = new BizCollectTask();
                    ct.setTaskName(t.getTaskName());
                    ct.setAssetId(demand.getMatchedCatalogId());
                    ct.setScheduleCron(null);
                    ct.setStatus("PENDING");
                    ct.setLastMessage("由供需确认生成 demandId=" + demand.getId());
                    ct.setCreatedBy(operator.getUsername());
                    collectTaskMapper.insert(ct);
                    t.setStatus("DISPATCHED");
                    t.setRefFlowCode("COLLECT#" + ct.getId());
                    supplyTaskMapper.updateById(t);
                } else if ("EXCHANGE".equals(t.getTaskType()) && t.getRefFlowCode() != null) {
                    t.setStatus("LINKED");
                    supplyTaskMapper.updateById(t);
                } else if ("SHARE".equals(t.getTaskType())) {
                    t.setStatus("READY");
                    supplyTaskMapper.updateById(t);
                } else if ("API".equals(t.getTaskType())) {
                    t.setStatus("READY");
                    supplyTaskMapper.updateById(t);
                }
            } catch (Exception ex) {
                log.warn("downstream dispatch failed for task {}: {}", t.getId(), ex.getMessage());
            }
        }
    }

    private BizDemandSupplyTask insertTask(Long demandId, String type, String name, String status, String flowCode) {
        BizDemandSupplyTask t = new BizDemandSupplyTask();
        t.setDemandId(demandId);
        t.setTaskType(type);
        t.setTaskName(name);
        t.setStatus(status);
        t.setRefFlowCode(flowCode);
        supplyTaskMapper.insert(t);
        return t;
    }

    private void upsertCatalogManifest(BizCatalogItem item) {
        long count = manifestMapper.selectCount(new LambdaQueryWrapper<BizSupplyManifest>()
                .eq(BizSupplyManifest::getManifestType, "CATALOG")
                .eq(BizSupplyManifest::getRefId, item.getId()));
        if (count == 0) {
            BizSupplyManifest m = new BizSupplyManifest();
            m.setManifestType("CATALOG");
            m.setRefId(item.getId());
            m.setTitle(item.getTitle() + " 清单");
            m.setStatus("ACTIVE");
            m.setAuthLevel("CITY");
            m.setCascadeFlag(1);
            manifestMapper.insert(m);
        }
    }

    private void upsertObjectionManifest(BizCatalogObjection obj) {
        BizSupplyManifest m = new BizSupplyManifest();
        m.setManifestType("OBJECTION");
        m.setRefId(obj.getId());
        m.setTitle(obj.getTitle() != null ? obj.getTitle() : ("异议-" + obj.getId()));
        m.setStatus("ACTIVE");
        m.setAuthLevel("DEPT");
        m.setCascadeFlag(0);
        manifestMapper.insert(m);
    }

    private List<BizCatalogItem> publishedCatalogsByProvider(String providerOrg) {
        String target = providerOrg == null ? "" : providerOrg.trim();
        if (target.isBlank()) {
            return List.of();
        }
        return publishedCatalogs().stream()
                .filter(c -> c.getProviderOrg() != null && c.getProviderOrg().trim().equalsIgnoreCase(target))
                .toList();
    }

    private String resolveAnalysisProviderOrg(BizDataDemand demand, Map<String, Object> body) {
        if (body != null) {
            Object fromBody = body.get("providerOrg");
            if (fromBody == null || String.valueOf(fromBody).isBlank()) {
                fromBody = body.get("assigneeOrg");
            }
            if (fromBody != null && !String.valueOf(fromBody).isBlank()) {
                return String.valueOf(fromBody).trim();
            }
        }
        if (demand.getAssigneeOrg() != null && !demand.getAssigneeOrg().isBlank()) {
            return demand.getAssigneeOrg().trim();
        }
        Map<String, Object> form = parseFormPayload(demand.getFormPayload());
        Object fromForm = form.get("providerOrg");
        if (fromForm != null && !String.valueOf(fromForm).isBlank()) {
            return String.valueOf(fromForm).trim();
        }
        throw new BusinessException(400, "请先选择组织机构（分发部门），再分析该组织已发布到门户的目录");
    }

    private Map<String, Object> parseFormPayload(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = new com.fasterxml.jackson.databind.ObjectMapper().readValue(raw, Map.class);
            return map == null ? Map.of() : map;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static final class DemandMatchSignals {
        String titleCorpus = "";
        String contentCorpus = "";
        String shareMode = "";
    }

    private DemandMatchSignals buildDemandMatchSignals(BizDataDemand demand) {
        Map<String, Object> form = parseFormPayload(demand.getFormPayload());
        DemandMatchSignals s = new DemandMatchSignals();
        StringBuilder title = new StringBuilder();
        appendText(title, demand.getDemandTitle());
        appendText(title, form.get("dataName"));
        appendText(title, form.get("catalogTitle"));
        StringBuilder content = new StringBuilder();
        appendText(content, demand.getDemandContent());
        appendText(content, form.get("demandBasis"));
        appendText(content, form.get("usageScenario"));
        Object dataItems = form.get("dataItems");
        if (dataItems instanceof List<?> list) {
            for (Object item : list) {
                appendText(content, item);
            }
        } else {
            appendText(content, dataItems);
        }
        appendText(content, demand.getModelFields());
        s.titleCorpus = title.toString().trim().toLowerCase();
        s.contentCorpus = (title + " " + content).trim().toLowerCase();
        s.shareMode = str(form.get("shareProvideMode"), str(demand.getSupplyMode(), "")).trim().toLowerCase();
        return s;
    }

    private void appendText(StringBuilder sb, Object v) {
        if (v == null) {
            return;
        }
        String t = String.valueOf(v).trim();
        if (t.isBlank() || "null".equalsIgnoreCase(t)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(t);
    }

    /**
     * 仅对门户已发布目录打分。keywordOverride 非空时用于快速查询（通配 "*" 给基础分）。
     */
    private List<Map<String, Object>> scorePortalCatalogs(List<BizCatalogItem> catalogs,
                                                          DemandMatchSignals signals,
                                                          String keywordOverride) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        boolean wildcard = "*".equals(keywordOverride);
        for (BizCatalogItem c : catalogs) {
            double score;
            if (wildcard) {
                score = 40;
            } else if (keywordOverride != null && !keywordOverride.isBlank()) {
                score = matchScore(keywordOverride, c.getTitle(), c.getDescription());
                if (c.getThemeName() != null) {
                    score = Math.min(100, score + matchScore(keywordOverride, c.getThemeName(), null) * 0.2);
                }
            } else {
                score = scoreCatalogAgainstDemand(c, signals);
            }
            if (score <= 0 && !wildcard && keywordOverride != null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resourceType", "CATALOG");
            row.put("resourceId", c.getId());
            row.put("resourceCode", c.getCatalogCode());
            row.put("title", c.getTitle());
            row.put("subtitle", c.getDescription());
            row.put("providerOrg", c.getProviderOrg());
            row.put("shareModes", c.getShareModes());
            row.put("catalogOrigin", c.getCatalogOrigin());
            row.put("score", Math.round(score * 100.0) / 100.0);
            row.put("suggestedEvalStatus", score >= 30 ? "MATCHED" : (score > 0 ? "PARTIAL" : "UNMATCHED"));
            row.put("suggestedShareAttr", score >= 50 ? "OPEN" : (score >= 30 ? "CONDITIONAL" : "RESTRICTED"));
            row.put("portalHint", "已发布至部门数据共享门户，可跳转申请");
            candidates.add(row);
        }
        candidates.sort((a, b) -> Double.compare(((Number) b.get("score")).doubleValue(),
                ((Number) a.get("score")).doubleValue()));
        return candidates;
    }

    private double scoreCatalogAgainstDemand(BizCatalogItem c, DemandMatchSignals signals) {
        double score = 0;
        String title = c.getTitle() == null ? "" : c.getTitle();
        String desc = c.getDescription() == null ? "" : c.getDescription();
        String theme = c.getThemeName() == null ? "" : c.getThemeName();
        String code = c.getCatalogCode() == null ? "" : c.getCatalogCode();

        if (signals.titleCorpus != null && !signals.titleCorpus.isBlank()) {
            score += Math.min(50, matchScore(signals.titleCorpus, title, null) * 0.5);
        }
        if (signals.contentCorpus != null && !signals.contentCorpus.isBlank()) {
            score += Math.min(35, matchScore(signals.contentCorpus, title, desc) * 0.35);
        }
        if (signals.shareMode != null && !signals.shareMode.isBlank()
                && c.getShareModes() != null && !c.getShareModes().isBlank()) {
            String modes = c.getShareModes().toLowerCase();
            for (String token : signals.shareMode.split("[,，/;|\\s]+")) {
                if (token.length() >= 2 && modes.contains(token)) {
                    score += 15;
                    break;
                }
            }
        }
        if (signals.titleCorpus != null && !signals.titleCorpus.isBlank()) {
            score += Math.min(10, matchScore(signals.titleCorpus, theme, code) * 0.1);
        }
        // 无需求语料时给轻微基础分，避免空信号全零
        if ((signals.titleCorpus == null || signals.titleCorpus.isBlank())
                && (signals.contentCorpus == null || signals.contentCorpus.isBlank())) {
            score = Math.max(score, 5);
        }
        return Math.min(100, score);
    }

    private Map<String, Object> buildRelationGraph(BizDataDemand demand, BizCatalogItem catalog,
                                                   List<Map<String, Object>> candidates) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        String demandNode = "demand-" + demand.getId();
        nodes.add(Map.of("id", demandNode, "label", demand.getDemandTitle(), "type", "DEMAND"));
        if (catalog != null) {
            String cid = "catalog-" + catalog.getId();
            nodes.add(Map.of("id", cid, "label", catalog.getTitle(), "type", "CATALOG"));
            edges.add(Map.of("from", demandNode, "to", cid, "label", "目录匹配"));
        }
        if (demand.getTargetCatalogId() != null && (catalog == null || !demand.getTargetCatalogId().equals(catalog.getId()))) {
            BizCatalogItem target = catalogMapper.selectById(demand.getTargetCatalogId());
            if (target != null) {
                String tid = "catalog-" + target.getId();
                nodes.add(Map.of("id", tid, "label", target.getTitle(), "type", "CATALOG"));
                edges.add(Map.of("from", demandNode, "to", tid, "label", "申请目标"));
            }
        }
        int added = 0;
        for (Map<String, Object> c : candidates) {
            if (added >= 5) break;
            if (!"CATALOG".equals(String.valueOf(c.get("resourceType")))) {
                continue;
            }
            if (catalog != null && String.valueOf(catalog.getId()).equals(String.valueOf(c.get("resourceId")))) {
                continue;
            }
            String nid = "catalog-" + c.get("resourceId");
            nodes.add(Map.of("id", nid, "label", String.valueOf(c.get("title")), "type", "CATALOG"));
            edges.add(Map.of("from", demandNode, "to", nid, "label", "相关度 " + c.get("score") + "%"));
            added++;
        }
        return Map.of("nodes", nodes, "edges", edges);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return String.valueOf(map);
        }
    }

    /** 保证 formPayload 中 List（如 dataItems）完整序列化 */
    private String toJsonObject(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            if (value instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) m;
                return toJson(cast);
            }
            return String.valueOf(value);
        }
    }

    private double matchScore(String keyword, String title, String desc) {
        String hay = ((title == null ? "" : title) + " " + (desc == null ? "" : desc)).toLowerCase();
        if (hay.isBlank() || keyword == null || keyword.isBlank()) {
            return 0;
        }
        String kw = keyword.toLowerCase().trim();
        int hits = 0;
        for (String token : kw.split("\\s+")) {
            if (token.length() >= 2 && hay.contains(token)) {
                hits++;
            }
        }
        if (kw.length() >= 2 && hay.contains(kw)) {
            hits += 2;
        }
        // 中文无空格时：用 2 字滑动窗口增加部分命中
        String compact = kw.replaceAll("\\s+", "");
        if (compact.length() >= 4) {
            int windowHits = 0;
            for (int i = 0; i <= compact.length() - 2; i++) {
                String bi = compact.substring(i, i + 2);
                if (hay.contains(bi)) {
                    windowHits++;
                }
            }
            hits += Math.min(4, windowHits / 2);
        }
        return Math.min(100, hits * 25.0);
    }

    private BizDataDemand getDemand(Long id) {
        BizDataDemand demand = demandMapper.selectById(id);
        if (demand == null) {
            throw new BusinessException(404, "需求不存在");
        }
        return demand;
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

    private int intVal(Object v, int def) {
        if (v == null) {
            return def;
        }
        return Integer.parseInt(String.valueOf(v));
    }
}
