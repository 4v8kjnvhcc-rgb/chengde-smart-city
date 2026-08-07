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
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public SupplyDemandService(BizDemandTemplateMapper templateMapper, BizDataDemandMapper demandMapper,
                               BizCatalogItemMapper catalogMapper, BizDemandSupplyTaskMapper supplyTaskMapper,
                               BizCatalogObjectionMapper objectionMapper, BizSupplyManifestMapper manifestMapper,
                               BizEsbFlowMapper esbFlowMapper, BizDataDutyMapper dutyMapper,
                               BizCollectTaskMapper collectTaskMapper,
                               GovCatalogApprovalMapper approvalMapper, GovCatalogResourceMapper govResourceMapper,
                               BizGovMatterMapper matterMapper, BizSupplySettingMapper supplySettingMapper,
                               AuditService auditService) {
        this.templateMapper = templateMapper;
        this.demandMapper = demandMapper;
        this.catalogMapper = catalogMapper;
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
        LambdaQueryWrapper<BizDataDemand> q = new LambdaQueryWrapper<BizDataDemand>().orderByDesc(BizDataDemand::getId);
        if (stage != null && !stage.isBlank()) {
            q.eq(BizDataDemand::getStage, stage);
        }
        if (status != null && !status.isBlank()) {
            q.eq(BizDataDemand::getStatus, status);
        }
        return demandMapper.selectList(q);
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
                .orderByAsc(BizGovMatter::getSortOrder)
                .orderByAsc(BizGovMatter::getId);
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
                demand.setFormPayload(toJson(castMap(body.get("formPayload"))));
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
        if (orgCatalogs.isEmpty()) {
            demand.setMatchedCatalogId(null);
            demand.setMatchScore(BigDecimal.ZERO);
            demand.setFulfillPath(PATH_COLLECT);
            demand.setEvalStatus("UNMATCHED");
            demand.setShareAttr(str(demand.getShareAttr(), "INTERNAL"));
            demand.setAnalysisNote("组织「" + providerOrg + "」暂无已发布到部门数据共享门户的目录，建议分发后由数源部门补编目录或归集补数");
        } else if (best != null && bestScore >= 30) {
            demand.setMatchedCatalogId(Long.valueOf(String.valueOf(best.get("resourceId"))));
            demand.setMatchScore(BigDecimal.valueOf(bestScore).setScale(2, RoundingMode.HALF_UP));
            demand.setFulfillPath(PATH_AUTHORIZE);
            demand.setEvalStatus("MATCHED");
            demand.setShareAttr(str(demand.getShareAttr(), "CONDITIONAL"));
            demand.setAnalysisNote("组织「" + providerOrg + "」门户目录参考匹配：" + best.get("title")
                    + "，相关度 " + bestScore + "%（供管理员人工判定是否退回门户申请或分发）");
        } else if (best != null && bestScore > 0) {
            demand.setMatchedCatalogId(Long.valueOf(String.valueOf(best.get("resourceId"))));
            demand.setMatchScore(BigDecimal.valueOf(bestScore).setScale(2, RoundingMode.HALF_UP));
            demand.setFulfillPath(PATH_COLLECT);
            demand.setEvalStatus("PARTIAL");
            demand.setShareAttr(str(demand.getShareAttr(), "RESTRICTED"));
            demand.setAnalysisNote("组织「" + providerOrg + "」门户目录弱匹配：" + best.get("title")
                    + "（" + bestScore + "%，供管理员人工判定分发或退回）");
        } else {
            demand.setMatchedCatalogId(null);
            demand.setMatchScore(BigDecimal.ZERO);
            demand.setFulfillPath(PATH_COLLECT);
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

    /** 提供部门标记：目录已挂载至门户 */
    @Transactional
    public void markCatalogMounted(UserPrincipal operator, Long id, Map<String, Object> body) {
        requireProviderOperator(operator);
        BizDataDemand demand = getDemand(id);
        if (!"CONFIRMED".equals(demand.getStatus()) && !"SUPERVISING".equals(demand.getStatus())) {
            throw new BusinessException(400, "仅已确认（含挂载督办中）的需求可标记目录已挂载");
        }
        demand.setCatalogMountedAt(java.time.LocalDateTime.now());
        if ("SUPERVISING".equals(demand.getStatus()) && demand.getCatalogMountDeadline() != null) {
            // 挂载完成后若因超时进入督办，恢复为已确认待办结
            demand.setStatus("CONFIRMED");
            demand.setStage("AUDIT");
        }
        String tip = str(body.get("confirmNote"), "目录已挂载至部门数据共享门户");
        String prev = demand.getConfirmNote() == null ? "" : demand.getConfirmNote() + " | ";
        demand.setConfirmNote(prev + tip);
        if (body.get("matchedCatalogId") != null && !String.valueOf(body.get("matchedCatalogId")).isBlank()) {
            demand.setMatchedCatalogId(Long.valueOf(String.valueOf(body.get("matchedCatalogId"))));
        }
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_CATALOG_MOUNTED", "biz_data_demand", String.valueOf(id), tip);
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

    /** 整体办结 */
    @Transactional
    public void completeDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        requireProviderOperator(operator);
        BizDataDemand demand = getDemand(id);
        if (!"CONFIRMED".equals(demand.getStatus()) && !"SUPERVISING".equals(demand.getStatus())) {
            throw new BusinessException(400, "仅已确认（或挂载督办中且已挂载）的需求可办结");
        }
        if (demand.getCatalogMountedAt() == null
                && (demand.getCatalogMountDeadline() != null || PATH_COLLECT.equals(demand.getFulfillPath()))) {
            throw new BusinessException(400, "请先完成目录挂载至门户（标记已挂载）后再办结；需求部门方可从门户申请");
        }
        if ("SUPERVISING".equals(demand.getStatus()) && demand.getCatalogMountedAt() == null) {
            throw new BusinessException(400, "挂载督办中请先标记目录已挂载");
        }
        demand.setStatus("COMPLETED");
        demand.setStage("SUPPLY");
        demand.setConfirmNote(str(body.get("confirmNote"), demand.getConfirmNote()));
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
     */
    public Map<String, Object> listCenter(String listType) {
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

        switch (type) {
            case "catalog-publish" -> {
                out.put("title", "目录发布清单");
                out.put("category", "目录清单");
                out.put("columns", catalogListColumns());
                out.put("items", catalogListByApproval("PUBLISH"));
            }
            case "catalog-change" -> {
                out.put("title", "目录变更清单");
                out.put("category", "目录清单");
                out.put("columns", catalogListColumns());
                out.put("items", catalogListByApproval("UPDATE"));
            }
            case "catalog-offline" -> {
                out.put("title", "目录下线清单");
                out.put("category", "目录清单");
                out.put("columns", catalogListColumns());
                out.put("items", unifiedCatalogRows("OFFLINE"));
            }
            case "catalog-access" -> {
                out.put("title", "数据接入清单");
                out.put("category", "目录清单");
                out.put("columns", catalogListColumns());
                out.put("items", dataAccessRows());
            }
            case "catalog-update" -> {
                out.put("title", "数据更新清单");
                out.put("category", "目录清单");
                out.put("columns", catalogListColumns());
                out.put("items", catalogUpdateRows());
            }
            case "catalog-published" -> {
                out.put("title", "已发布目录清单");
                out.put("category", "目录清单");
                out.put("columns", catalogListColumns());
                out.put("items", unifiedCatalogRows("PUBLISHED"));
            }
            case "sd-demand-audit" -> {
                out.put("title", "需求审核清单");
                out.put("category", "供需清单");
                out.put("columns", supplyListColumns());
                out.put("items", demandManifestRows(Set.of(
                        "SUBMITTED", "PRE_AUDITING", "ANALYZING", "RETURNED", "SUPERVISING", "PROVIDER_RETURNED")));
            }
            case "sd-supply-audit" -> {
                out.put("title", "供给审核清单");
                out.put("category", "供需清单");
                out.put("columns", supplyListColumns());
                out.put("items", demandManifestRows(Set.of("DISPATCHED", "CORRECTION", "CONFIRMED")));
            }
            case "sd-joint-audit" -> {
                out.put("title", "供需审核清单");
                out.put("category", "供需清单");
                out.put("columns", supplyListColumns());
                out.put("items", demandManifestRows(Set.of(
                        "DISPATCHED", "PRE_AUDITING", "ANALYZING", "SUPERVISING", "CORRECTION",
                        "CONFIRMED", "PROVIDER_RETURNED")));
            }
            case "sd-auth-history" -> {
                out.put("title", "历史授权清单");
                out.put("category", "供需清单");
                out.put("columns", supplyListColumns());
                out.put("items", dutyHistoryRows());
            }
            case "sd-history" -> {
                out.put("title", "历史供需清单");
                out.put("category", "供需清单");
                out.put("columns", supplyListColumns());
                out.put("items", demandManifestRows(Set.of("CONFIRMED", "COMPLETED", "CANCELLED", "REJECTED", "RETURNED")));
            }
            case "sd-cascade" -> {
                out.put("title", "级联下行清单");
                out.put("category", "供需清单");
                out.put("columns", supplyListColumns());
                out.put("items", cascadeManifestRows());
            }
            case "objection", "objection-apply" -> {
                out.put("title", "异议申请清单");
                out.put("category", "异议清单");
                out.put("columns", objectionListColumns());
                out.put("items", objectionRows("OPEN"));
            }
            case "objection-audit" -> {
                out.put("title", "异议审核清单");
                out.put("category", "异议清单");
                out.put("columns", objectionListColumns());
                out.put("items", objectionRows("OPEN"));
            }
            case "objection-process" -> {
                out.put("title", "异议处理清单");
                out.put("category", "异议清单");
                out.put("columns", objectionListColumns());
                out.put("items", objectionRows("PROCESSING"));
            }
            case "objection-closed" -> {
                out.put("title", "异议办结清单");
                out.put("category", "异议清单");
                out.put("columns", objectionListColumns());
                out.put("items", objectionRows("CLOSED"));
            }
            case "objection-history" -> {
                out.put("title", "历史异议清单");
                out.put("category", "异议清单");
                out.put("columns", objectionListColumns());
                out.put("items", objectionRows(null));
            }
            case "objection-stats" -> {
                out.put("title", "异议统计分析");
                out.put("category", "异议清单");
                out.put("columns", List.of());
                Map<String, Object> stats = objectionStats();
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

    private List<String> catalogListColumns() {
        return List.of("catalogCode", "catalogName", "providerOrg", "shareAttr", "status", "createdAt", "actions");
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
        if (rows.isEmpty()) {
            // 无「有变更」记录时，仍展示近期发布审批变更，避免空白
            return catalogListByApproval("UPDATE");
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
        row.put("catalogOrigin", c.getCatalogOrigin());
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

    private List<Map<String, Object>> catalogListByApproval(String actionType) {
        List<GovCatalogApproval> approvals = approvalMapper.selectList(new LambdaQueryWrapper<GovCatalogApproval>()
                .eq(GovCatalogApproval::getActionType, actionType)
                .eq(GovCatalogApproval::getStatus, "APPROVED")
                .orderByDesc(GovCatalogApproval::getId)
                .last("LIMIT 200"));
        Map<Long, BizCatalogItem> byGov = publishedCatalogs().stream()
                .filter(c -> c.getGovResourceId() != null)
                .collect(Collectors.toMap(BizCatalogItem::getGovResourceId, c -> c, (a, b) -> a));
        // 也加载非 published 以便变更/发布历史可见
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
                row.put("govResourceId", gov.getId());
                row.put("status", gov.getPublishStatus());
                row.put("actions", List.of("view", "export"));
            } else {
                row.put("code", "GOV-" + a.getResourceId());
                row.put("catalogCode", "GOV-" + a.getResourceId());
                row.put("catalogName", "资源#" + a.getResourceId());
                row.put("title", "资源#" + a.getResourceId());
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

    private List<Map<String, Object>> dataAccessRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizCollectTask t : collectTaskMapper.selectList(new LambdaQueryWrapper<BizCollectTask>()
                .orderByDesc(BizCollectTask::getId).last("LIMIT 100"))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.getId());
            row.put("code", "ACCESS-" + t.getId());
            row.put("catalogCode", "ACCESS-" + t.getId());
            row.put("catalogName", t.getTaskName());
            row.put("title", t.getTaskName());
            row.put("providerOrg", t.getCreatedBy());
            row.put("shareAttr", "—");
            row.put("status", t.getStatus());
            row.put("description", t.getLastMessage());
            row.put("createdAt", t.getCreatedAt());
            row.put("updatedAt", t.getLastRunAt() != null ? t.getLastRunAt() : t.getUpdatedAt());
            row.put("assetId", t.getAssetId());
            row.put("actions", List.of("view", "export"));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> demandManifestRows(Set<String> statuses) {
        List<BizDataDemand> demands = demandMapper.selectList(new LambdaQueryWrapper<BizDataDemand>()
                .in(BizDataDemand::getStatus, statuses)
                .orderByDesc(BizDataDemand::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizDataDemand d : demands) {
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
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId());
            row.put("code", "DUTY-" + d.getId());
            row.put("title", demand != null ? demand.getDemandTitle() : ("数据责任#" + d.getId()));
            row.put("demandId", d.getDemandId());
            row.put("demandScene", demand != null ? demand.getDemandType() : "—");
            row.put("demandCatalog", d.getCatalogId() != null ? ("目录#" + d.getCatalogId()) : "—");
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
                row.put("demandService", "—");
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

    private List<Map<String, Object>> objectionRows(String status) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizCatalogObjection o : listObjections(status)) {
            rows.add(toObjectionRow(o));
        }
        return rows;
    }

    private Map<String, Object> toObjectionRow(BizCatalogObjection o) {
        Map<String, Object> row = new LinkedHashMap<>();
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
        row.put("status", o.getStatus());
        row.put("handlerNote", o.getHandlerNote());
        row.put("createdAt", o.getCreatedAt());
        row.put("createdBy", o.getCreatedBy());
        row.put("actions", List.of("view", "process", "export"));
        return row;
    }

    private Map<String, Object> objectionStats() {
        List<BizCatalogObjection> all = listObjections(null);
        long open = all.stream().filter(o -> "OPEN".equalsIgnoreCase(o.getStatus())).count();
        long processing = all.stream().filter(o -> "PROCESSING".equalsIgnoreCase(o.getStatus())).count();
        long closed = all.stream().filter(o -> "CLOSED".equalsIgnoreCase(o.getStatus())).count();
        Map<String, Long> byType = new LinkedHashMap<>();
        for (BizCatalogObjection o : all) {
            String t = o.getObjectionType() == null ? "OTHER" : o.getObjectionType();
            byType.merge(t, 1L, Long::sum);
        }
        List<Map<String, Object>> byStatus = new ArrayList<>();
        byStatus.add(Map.of("code", "OPEN", "title", "待核查/申请", "status", "OPEN", "count", open));
        byStatus.add(Map.of("code", "PROCESSING", "title", "处理中", "status", "PROCESSING", "count", processing));
        byStatus.add(Map.of("code", "CLOSED", "title", "已办结", "status", "CLOSED", "count", closed));
        List<Map<String, Object>> typeRows = new ArrayList<>();
        byType.forEach((k, v) -> typeRows.add(Map.of("code", k, "title", k, "count", v)));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        stats.put("open", open);
        stats.put("processing", processing);
        stats.put("closed", closed);
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

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return null;
        }
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private List<Map<String, Object>> objectionRows() {
        return objectionRows(null);
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
                .orderByDesc(BizCatalogObjection::getId);
        if (status != null && !status.isBlank()) {
            q.eq(BizCatalogObjection::getStatus, status);
        }
        return objectionMapper.selectList(q);
    }

    @Transactional
    public Long createObjection(UserPrincipal operator, Map<String, Object> body) {
        BizCatalogObjection obj = new BizCatalogObjection();
        Long catalogId = Long.valueOf(String.valueOf(required(body.get("catalogId"), "catalogId")));
        BizCatalogItem catalog = catalogMapper.selectById(catalogId);
        if (catalog == null || catalog.getGovResourceId() == null) {
            throw new BusinessException(400, "仅可对统一编目同步的已发布目录提出异议");
        }
        obj.setCatalogId(catalogId);
        obj.setTitle(str(body.get("title"), "数据异议-" + catalog.getTitle()));
        if (body.get("demandId") != null && !String.valueOf(body.get("demandId")).isBlank()) {
            obj.setDemandId(Long.valueOf(String.valueOf(body.get("demandId"))));
        }
        obj.setObjectionType(str(body.get("objectionType"), "QUALITY"));
        obj.setContent(required(body.get("content"), "content").toString());
        obj.setProviderOrg(str(body.get("providerOrg"), operator.getUsername()));
        obj.setVerifyOrg(str(body.get("verifyOrg"), catalog.getProviderOrg()));
        obj.setStatus("OPEN");
        obj.setCreatedBy(operator.getUsername());
        objectionMapper.insert(obj);
        upsertObjectionManifest(obj);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "OBJECTION_CREATE", "biz_catalog_objection", String.valueOf(obj.getId()), obj.getContent());
        return obj.getId();
    }

    @Transactional
    public void processObjection(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizCatalogObjection obj = objectionMapper.selectById(id);
        if (obj == null) {
            throw new BusinessException(404, "异议不存在");
        }
        String action = str(body.get("action"), "CLOSE").toUpperCase();
        if ("REOPEN_AUDIT".equals(action) || "CORRECT".equals(action) || "CORRECTION".equals(action)) {
            obj.setStatus("PROCESSING");
            obj.setHandlerNote(str(body.get("handlerNote"), "异议纠错，回流需求审核"));
            objectionMapper.updateById(obj);
            reopenDemandAuditFromObjection(operator, obj);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "OBJECTION_REOPEN_AUDIT", "biz_catalog_objection", String.valueOf(id), obj.getHandlerNote());
            return;
        }
        obj.setStatus("CLOSE".equals(action) || "CLOSED".equals(action) ? "CLOSED" : "PROCESSING");
        obj.setHandlerNote(str(body.get("handlerNote"), ""));
        objectionMapper.updateById(obj);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "OBJECTION_PROCESS", "biz_catalog_objection", String.valueOf(id), obj.getStatus());
    }

    /** 异议发现数据问题 → 回流需求审核 */
    private void reopenDemandAuditFromObjection(UserPrincipal operator, BizCatalogObjection obj) {
        BizDataDemand demand = null;
        if (obj.getDemandId() != null) {
            demand = demandMapper.selectById(obj.getDemandId());
        }
        if (demand == null && obj.getCatalogId() != null) {
            demand = demandMapper.selectOne(new LambdaQueryWrapper<BizDataDemand>()
                    .eq(BizDataDemand::getMatchedCatalogId, obj.getCatalogId())
                    .in(BizDataDemand::getStatus, "CONFIRMED", "COMPLETED", "DISPATCHED")
                    .orderByDesc(BizDataDemand::getId)
                    .last("LIMIT 1"));
        }
        if (demand == null) {
            return;
        }
        demand.setStatus("CORRECTION");
        demand.setStage("AUDIT");
        String tip = "异议#" + obj.getId() + "纠错回流：" + obj.getContent();
        demand.setConfirmNote(tip);
        demandMapper.updateById(demand);
        if (obj.getDemandId() == null) {
            obj.setDemandId(demand.getId());
            objectionMapper.updateById(obj);
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_CORRECTION", "biz_data_demand", String.valueOf(demand.getId()), tip);
    }

    public List<BizSupplyManifest> listManifests(String manifestType) {
        LambdaQueryWrapper<BizSupplyManifest> q = new LambdaQueryWrapper<BizSupplyManifest>()
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
