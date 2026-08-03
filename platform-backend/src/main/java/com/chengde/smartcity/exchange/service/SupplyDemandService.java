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
import com.chengde.smartcity.exchange.entity.BizSupplyManifest;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.mapper.BizCatalogItemMapper;
import com.chengde.smartcity.exchange.mapper.BizCatalogObjectionMapper;
import com.chengde.smartcity.exchange.mapper.BizCollectTaskMapper;
import com.chengde.smartcity.exchange.mapper.BizDataDemandMapper;
import com.chengde.smartcity.exchange.mapper.BizDataDutyMapper;
import com.chengde.smartcity.exchange.mapper.BizDemandSupplyTaskMapper;
import com.chengde.smartcity.exchange.mapper.BizDemandTemplateMapper;
import com.chengde.smartcity.exchange.mapper.BizEsbFlowMapper;
import com.chengde.smartcity.exchange.mapper.BizSupplyManifestMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
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

    private final BizDemandTemplateMapper templateMapper;
    private final BizDataDemandMapper demandMapper;
    private final BizCatalogItemMapper catalogMapper;
    private final BizDemandSupplyTaskMapper supplyTaskMapper;
    private final BizCatalogObjectionMapper objectionMapper;
    private final BizSupplyManifestMapper manifestMapper;
    private final BizEsbFlowMapper esbFlowMapper;
    private final BizDataDutyMapper dutyMapper;
    private final BizCollectTaskMapper collectTaskMapper;
    private final IngDataTableMapper dataTableMapper;
    private final GovCatalogApprovalMapper approvalMapper;
    private final GovCatalogResourceMapper govResourceMapper;
    private final AuditService auditService;

    /** 预审中（兼容旧 ANALYZING） */
    private static final Set<String> PRE_AUDIT_STATUSES = Set.of("PRE_AUDITING", "ANALYZING", "DISPATCHED", "SUPERVISING");
    /** 可进入审核确认的状态 */
    private static final Set<String> AUDIT_CONFIRMABLE = Set.of("DISPATCHED", "PRE_AUDITING", "ANALYZING", "SUPERVISING", "CORRECTION");

    @Value("${app.exchange.supply.dispatch-downstream:false}")
    private boolean dispatchDownstream;

    public SupplyDemandService(BizDemandTemplateMapper templateMapper, BizDataDemandMapper demandMapper,
                               BizCatalogItemMapper catalogMapper, BizDemandSupplyTaskMapper supplyTaskMapper,
                               BizCatalogObjectionMapper objectionMapper, BizSupplyManifestMapper manifestMapper,
                               BizEsbFlowMapper esbFlowMapper, BizDataDutyMapper dutyMapper,
                               BizCollectTaskMapper collectTaskMapper, IngDataTableMapper dataTableMapper,
                               GovCatalogApprovalMapper approvalMapper, GovCatalogResourceMapper govResourceMapper,
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
        this.dataTableMapper = dataTableMapper;
        this.approvalMapper = approvalMapper;
        this.govResourceMapper = govResourceMapper;
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
        BizDataDemand demand = new BizDataDemand();
        demand.setDemandTitle(required(body.get("demandTitle"), "需求标题").toString());
        demand.setRequesterOrg(str(body.get("requesterOrg"), "机构" + operator.getOrgId()));
        demand.setDemandType(str(body.get("demandType"), "STRUCTURED"));
        demand.setTemplateCode(str(body.get("templateCode"), null));
        demand.setDemandContent(str(body.get("demandContent"), null));
        if (body.get("modelFields") != null) {
            demand.setModelFields(body.get("modelFields") instanceof String
                    ? String.valueOf(body.get("modelFields"))
                    : toJson(castMap(body.get("modelFields"))));
        }
        Object catalogId = body.get("targetCatalogId");
        if (catalogId != null) {
            demand.setTargetCatalogId(Long.valueOf(String.valueOf(catalogId)));
        }
        demand.setStatus("SUBMITTED");
        demand.setStage("MANAGE");
        demand.setCreatedBy(operator.getUsername());
        demandMapper.insert(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_SUBMIT", "biz_data_demand", String.valueOf(demand.getId()), demand.getDemandTitle());
        return demand.getId();
    }

    @Transactional
    public void withdrawDemand(UserPrincipal operator, Long id) {
        BizDataDemand demand = getDemand(id);
        if (!"SUBMITTED".equals(demand.getStatus())) {
            throw new BusinessException(400, "仅已提交需求可撤销");
        }
        demand.setStatus("WITHDRAWN");
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_WITHDRAW", "biz_data_demand", String.valueOf(id), demand.getDemandTitle());
    }

    @Transactional
    public Map<String, Object> analyzeDemand(UserPrincipal operator, Long id) {
        BizDataDemand demand = getDemand(id);
        if (!"SUBMITTED".equals(demand.getStatus()) && !"PRE_AUDITING".equals(demand.getStatus())
                && !"ANALYZING".equals(demand.getStatus()) && !"RETURNED".equals(demand.getStatus())
                && !"SUPERVISING".equals(demand.getStatus())) {
            throw new BusinessException(400, "当前状态不可预审分析");
        }
        String keyword = demand.getDemandTitle().toLowerCase();
        List<Map<String, Object>> candidates = searchResourceCandidates(keyword);

        Map<String, Object> best = candidates.isEmpty() ? null : candidates.get(0);
        double bestScore = best == null ? 0 : ((Number) best.get("score")).doubleValue();
        BizCatalogItem bestCatalog = null;
        if (best != null && "CATALOG".equals(best.get("resourceType"))) {
            bestCatalog = catalogMapper.selectById(Long.valueOf(String.valueOf(best.get("resourceId"))));
        } else if (demand.getMatchedCatalogId() != null) {
            bestCatalog = catalogMapper.selectById(demand.getMatchedCatalogId());
        }

        demand.setStage("PRE_AUDIT");
        demand.setStatus("PRE_AUDITING");
        if (best != null && bestScore >= 30) {
            if ("CATALOG".equals(best.get("resourceType"))) {
                demand.setMatchedCatalogId(Long.valueOf(String.valueOf(best.get("resourceId"))));
            }
            demand.setMatchScore(BigDecimal.valueOf(bestScore).setScale(2, RoundingMode.HALF_UP));
            demand.setFulfillPath(PATH_AUTHORIZE);
            demand.setEvalStatus("MATCHED");
            demand.setShareAttr(str(demand.getShareAttr(), "CONDITIONAL"));
            demand.setAnalysisNote("智能匹配" + best.get("resourceType") + "：" + best.get("title")
                    + "，相关度 " + bestScore + "%；建议路径：已在中台授权共享");
        } else if (best != null && bestScore > 0) {
            demand.setMatchScore(BigDecimal.valueOf(bestScore).setScale(2, RoundingMode.HALF_UP));
            demand.setFulfillPath(PATH_COLLECT);
            demand.setEvalStatus("PARTIAL");
            demand.setShareAttr(str(demand.getShareAttr(), "RESTRICTED"));
            demand.setAnalysisNote("弱匹配" + best.get("resourceType") + "：" + best.get("title")
                    + "（" + bestScore + "%），建议人工确认或归集补数");
        } else {
            demand.setAnalysisNote("未找到高相关目录/库表/接口，建议路径：未在中台需归集补数");
            demand.setMatchScore(BigDecimal.ZERO);
            demand.setFulfillPath(PATH_COLLECT);
            demand.setEvalStatus("UNMATCHED");
            demand.setShareAttr(str(demand.getShareAttr(), "INTERNAL"));
        }
        Map<String, Object> graph = buildRelationGraph(demand, bestCatalog, candidates);
        demand.setAnalysisPayload(toJson(Map.of("candidates", candidates.stream().limit(10).toList(), "relationGraph", graph)));
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_ANALYZE", "biz_data_demand", String.valueOf(id), demand.getAnalysisNote());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("demandId", id);
        out.put("matchedCatalogId", demand.getMatchedCatalogId());
        out.put("matchScore", demand.getMatchScore());
        out.put("fulfillPath", demand.getFulfillPath());
        out.put("evalStatus", demand.getEvalStatus());
        out.put("shareAttr", demand.getShareAttr());
        out.put("analysisNote", demand.getAnalysisNote());
        out.put("candidates", candidates.stream().limit(10).toList());
        out.put("relationGraph", graph);
        return out;
    }

    /** 资源目录/库表/接口快速查询（数据管理员分析入口） */
    public Map<String, Object> searchResources(String keyword, String resourceType) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Map<String, Object>> all = searchResourceCandidates(kw.isBlank() ? "*" : kw);
        if (resourceType != null && !resourceType.isBlank() && !"ALL".equalsIgnoreCase(resourceType)) {
            all = all.stream().filter(r -> resourceType.equalsIgnoreCase(String.valueOf(r.get("resourceType")))).toList();
        }
        return Map.of("keyword", keyword == null ? "" : keyword, "total", all.size(), "items", all.stream().limit(30).toList());
    }

    @Transactional
    public void superviseDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizDataDemand demand = getDemand(id);
        if (!PRE_AUDIT_STATUSES.contains(demand.getStatus())) {
            throw new BusinessException(400, "仅预审中/已分发需求可督办");
        }
        String note = required(body.get("superviseNote"), "督办说明").toString();
        demand.setStatus("SUPERVISING");
        demand.setStage("PRE_AUDIT");
        demand.setSuperviseNote(note);
        demand.setSuperviseAt(java.time.LocalDateTime.now());
        demand.setSuperviseBy(operator.getUsername());
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
        if (demand.getStatus().equals("SUBMITTED") || demand.getStatus().equals("RETURNED")) {
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
        String note = str(body.get("analysisNote"), null);
        if (note == null) {
            note = "预审通过，已分发至 " + demand.getAssigneeOrg() + "；履约路径=" + path;
        }
        demand.setAnalysisNote(note);
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_DISPATCH", "biz_data_demand", String.valueOf(id), demand.getAssigneeOrg() + "/" + path);
    }

    @Transactional
    public void returnDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizDataDemand demand = getDemand(id);
        demand.setStatus("RETURNED");
        demand.setStage("MANAGE");
        demand.setAnalysisNote(str(body.get("analysisNote"), "预审退回/督查督办，需补充材料"));
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_RETURN", "biz_data_demand", String.valueOf(id), demand.getAnalysisNote());
    }

    @Transactional
    public Map<String, Object> confirmDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizDataDemand demand = getDemand(id);
        if (!AUDIT_CONFIRMABLE.contains(demand.getStatus())) {
            throw new BusinessException(400, "仅预审分发/督办/异议回流中的需求可审核确认");
        }
        String path = str(body.get("fulfillPath"), demand.getFulfillPath());
        if (path == null || path.isBlank()) {
            path = demand.getMatchedCatalogId() != null ? PATH_AUTHORIZE : PATH_COLLECT;
        }
        demand.setFulfillPath(path);
        demand.setStatus("CONFIRMED");
        demand.setStage("AUDIT");
        demand.setConfirmNote(str(body.get("confirmNote"), "数源部门确认可满足，转换为数据责任"));
        demand.setSupplyMode(str(body.get("supplyMode"), PATH_COLLECT.equals(path) ? "COLLECT" : "EXCHANGE"));
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
        return out;
    }

    /** 供数部门退回（审核环节） */
    @Transactional
    public void confirmReturnDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizDataDemand demand = getDemand(id);
        if (!AUDIT_CONFIRMABLE.contains(demand.getStatus()) && !"CONFIRMED".equals(demand.getStatus())) {
            throw new BusinessException(400, "当前状态不可退回");
        }
        String note = str(body.get("confirmNote"), "审核退回，请补充材料");
        demand.setStatus("RETURNED");
        demand.setStage("MANAGE");
        demand.setConfirmNote(note);
        demand.setConfirmFeedback(str(body.get("confirmFeedback"), note));
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_CONFIRM_RETURN", "biz_data_demand", String.valueOf(id), note);
    }

    /** 督查反馈（供数/主管部门在审核环节反馈） */
    @Transactional
    public void confirmFeedback(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizDataDemand demand = getDemand(id);
        if (!Set.of("DISPATCHED", "PRE_AUDITING", "ANALYZING", "SUPERVISING", "CONFIRMED", "CORRECTION")
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
        BizDataDemand demand = getDemand(id);
        if (!"CONFIRMED".equals(demand.getStatus())) {
            throw new BusinessException(400, "仅已确认需求可办结");
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
        BizDataDemand demand = getDemand(id);
        if (Set.of("COMPLETED", "CANCELLED", "WITHDRAWN", "REJECTED").contains(demand.getStatus())) {
            throw new BusinessException(400, "当前状态不可修改");
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
        BizDataDemand demand = getDemand(id);
        if (!AUDIT_CONFIRMABLE.contains(demand.getStatus())) {
            throw new BusinessException(400, "仅预审分发/督办/异议回流中的需求可驳回");
        }
        demand.setStatus("REJECTED");
        demand.setStage("AUDIT");
        demand.setConfirmNote(str(body.get("confirmNote"), "需求已驳回"));
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_REJECT", "biz_data_demand", String.valueOf(id), demand.getConfirmNote());
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
            if ("SHARE".equals(t.getTaskType()) || "EXCHANGE".equals(t.getTaskType())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", t.getTaskName());
                m.put("endpoint", catalog != null
                        ? "/api/v1/exchange/catalog/" + catalog.getId()
                        : "/api/v1/exchange/supply/supply-view/" + demandId);
                m.put("method", "GET");
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
                out.put("items", catalogListByApproval("PUBLISH"));
            }
            case "catalog-change" -> {
                out.put("title", "目录变更清单");
                out.put("category", "目录清单");
                out.put("items", catalogListByApproval("UPDATE"));
            }
            case "catalog-offline" -> {
                out.put("title", "目录下线清单");
                out.put("category", "目录清单");
                out.put("items", unifiedCatalogRows("OFFLINE"));
            }
            case "catalog-access" -> {
                out.put("title", "数据接入清单");
                out.put("category", "目录清单");
                out.put("items", dataAccessRows());
            }
            case "catalog-update" -> {
                out.put("title", "数据更新清单");
                out.put("category", "目录清单");
                out.put("items", catalogListByApproval("UPDATE"));
            }
            case "catalog-published" -> {
                out.put("title", "已发布目录清单");
                out.put("category", "目录清单");
                out.put("items", unifiedCatalogRows("PUBLISHED"));
            }
            case "sd-demand-audit" -> {
                out.put("title", "需求审核清单");
                out.put("category", "供需清单");
                out.put("items", demandManifestRows(Set.of("SUBMITTED", "PRE_AUDITING", "ANALYZING", "RETURNED", "SUPERVISING")));
            }
            case "sd-supply-audit" -> {
                out.put("title", "供给审核清单");
                out.put("category", "供需清单");
                out.put("items", demandManifestRows(Set.of("DISPATCHED", "CORRECTION")));
            }
            case "sd-joint-audit" -> {
                out.put("title", "供需审核清单");
                out.put("category", "供需清单");
                out.put("items", demandManifestRows(Set.of("DISPATCHED", "PRE_AUDITING", "ANALYZING", "SUPERVISING", "CORRECTION", "CONFIRMED")));
            }
            case "sd-auth-history" -> {
                out.put("title", "历史授权清单");
                out.put("category", "供需清单");
                out.put("items", dutyHistoryRows());
            }
            case "sd-history" -> {
                out.put("title", "历史供需清单");
                out.put("category", "供需清单");
                out.put("items", demandManifestRows(Set.of("CONFIRMED", "COMPLETED", "CANCELLED", "REJECTED")));
            }
            case "sd-cascade" -> {
                out.put("title", "级联下行清单");
                out.put("category", "供需清单");
                out.put("items", cascadeManifestRows());
            }
            case "objection" -> {
                out.put("title", "数据异议清单");
                out.put("category", "异议清单");
                out.put("items", objectionRows());
            }
            default -> {
                out.put("title", type);
                out.put("items", List.of());
            }
        }
        return out;
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

    private Map<String, Object> toCatalogRow(BizCatalogItem c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.getId());
        row.put("code", c.getCatalogCode());
        row.put("catalogCode", c.getCatalogCode());
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
                row.put("title", gov.getResourceName());
                row.put("providerOrg", gov.getProviderOrg());
                row.put("shareAttr", gov.getShareType());
                row.put("catalogOrigin", gov.getCatalogOrigin());
                row.put("govResourceId", gov.getId());
                row.put("status", gov.getPublishStatus());
            } else {
                row.put("code", "GOV-" + a.getResourceId());
                row.put("title", "资源#" + a.getResourceId());
                row.put("status", a.getStatus());
            }
            row.put("approvalId", a.getId());
            row.put("actionType", a.getActionType());
            row.put("reviewedAt", a.getReviewedAt());
            row.put("reviewedBy", a.getReviewedBy());
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
            row.put("code", "ACCESS-" + t.getId());
            row.put("title", t.getTaskName());
            row.put("status", t.getStatus());
            row.put("description", t.getLastMessage());
            row.put("createdAt", t.getCreatedAt());
            row.put("assetId", t.getAssetId());
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
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId());
            row.put("code", "DEMAND-" + d.getId());
            row.put("title", d.getDemandTitle());
            row.put("demandScene", d.getDemandType());
            row.put("requesterOrg", d.getRequesterOrg());
            row.put("providerOrg", d.getAssigneeOrg());
            row.put("matchedCatalogId", d.getMatchedCatalogId());
            row.put("status", d.getStatus());
            row.put("stage", d.getStage());
            row.put("fulfillPath", d.getFulfillPath());
            row.put("createdAt", d.getCreatedAt());
            row.put("description", d.getAnalysisNote() != null ? d.getAnalysisNote() : d.getConfirmNote());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> dutyHistoryRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizDataDuty d : dutyMapper.selectList(new LambdaQueryWrapper<BizDataDuty>().orderByDesc(BizDataDuty::getId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", "DUTY-" + d.getId());
            row.put("title", "数据责任#" + d.getId());
            row.put("demandId", d.getDemandId());
            row.put("providerOrg", d.getDutyOrg());
            row.put("status", d.getStatus());
            row.put("fulfillPath", d.getFulfillPath());
            row.put("catalogId", d.getCatalogId());
            row.put("description", d.getRemark());
            row.put("createdAt", d.getCreatedAt());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> cascadeManifestRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizSupplyManifest m : listManifests(null)) {
            if (m.getCascadeFlag() != null && m.getCascadeFlag() == 1) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", m.getManifestType() + "-" + m.getId());
                row.put("title", m.getTitle());
                row.put("status", m.getStatus());
                row.put("authLevel", m.getAuthLevel());
                row.put("description", m.getExportPayload());
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> objectionRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizCatalogObjection o : listObjections(null)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", o.getId());
            row.put("title", o.getTitle() != null ? o.getTitle() : ("异议#" + o.getId()));
            row.put("catalogId", o.getCatalogId());
            row.put("demandId", o.getDemandId());
            row.put("objectionType", o.getObjectionType());
            row.put("content", o.getContent());
            row.put("providerOrg", o.getProviderOrg());
            row.put("verifyOrg", o.getVerifyOrg());
            row.put("status", o.getStatus());
            row.put("createdAt", o.getCreatedAt());
            row.put("createdBy", o.getCreatedBy());
            rows.add(row);
        }
        return rows;
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

    private List<Map<String, Object>> searchResourceCandidates(String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        boolean wildcard = kw.isBlank() || "*".equals(kw);
        List<Map<String, Object>> candidates = new ArrayList<>();

        // 仅匹配统一编目同步到门户的已发布目录
        List<BizCatalogItem> catalogs = publishedCatalogs();
        for (BizCatalogItem c : catalogs) {
            double score = wildcard ? 40 : matchScore(kw, c.getTitle(), c.getDescription());
            if (score <= 0 && !wildcard) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resourceType", "CATALOG");
            row.put("resourceId", c.getId());
            row.put("resourceCode", c.getCatalogCode());
            row.put("title", c.getTitle());
            row.put("subtitle", c.getDescription());
            row.put("catalogOrigin", c.getCatalogOrigin());
            row.put("score", score);
            row.put("suggestedEvalStatus", score >= 30 ? "MATCHED" : "PARTIAL");
            row.put("suggestedShareAttr", score >= 50 ? "OPEN" : "CONDITIONAL");
            row.put("portalHint", "已发布至部门数据共享门户，可跳转申请");
            candidates.add(row);
        }

        List<IngDataTable> tables = dataTableMapper.selectList(new LambdaQueryWrapper<IngDataTable>().last("LIMIT 200"));
        for (IngDataTable t : tables) {
            double score = wildcard ? 25 : matchScore(kw, t.getTableName(), t.getTableCode());
            if (score <= 0 && !wildcard) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resourceType", "TABLE");
            row.put("resourceId", t.getId());
            row.put("resourceCode", t.getTableCode());
            row.put("title", t.getTableName());
            row.put("subtitle", "库表 · 列数=" + t.getColumnCount());
            row.put("score", score);
            row.put("suggestedEvalStatus", score >= 30 ? "MATCHED" : "PARTIAL");
            row.put("suggestedShareAttr", "RESTRICTED");
            candidates.add(row);
        }

        List<BizEsbFlow> flows = esbFlowMapper.selectList(new LambdaQueryWrapper<>());
        for (BizEsbFlow f : flows) {
            double score = wildcard ? 20 : matchScore(kw, f.getFlowName(), f.getFlowCode());
            if (score <= 0 && !wildcard) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resourceType", "API");
            row.put("resourceId", f.getId());
            row.put("resourceCode", f.getFlowCode());
            row.put("title", f.getFlowName());
            row.put("subtitle", "接口/交换流 · " + f.getStatus());
            row.put("score", score);
            row.put("suggestedEvalStatus", score >= 30 ? "MATCHED" : "PARTIAL");
            row.put("suggestedShareAttr", "CONDITIONAL");
            candidates.add(row);
        }

        candidates.sort((a, b) -> Double.compare(((Number) b.get("score")).doubleValue(), ((Number) a.get("score")).doubleValue()));
        return candidates;
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
            String type = String.valueOf(c.get("resourceType"));
            if ("CATALOG".equals(type) && catalog != null
                    && String.valueOf(catalog.getId()).equals(String.valueOf(c.get("resourceId")))) {
                continue;
            }
            String nid = type.toLowerCase() + "-" + c.get("resourceId");
            nodes.add(Map.of("id", nid, "label", String.valueOf(c.get("title")), "type", type));
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
        if (hay.isBlank()) {
            return 0;
        }
        int hits = 0;
        for (String token : keyword.split("\\s+")) {
            if (token.length() >= 2 && hay.contains(token)) {
                hits++;
            }
        }
        if (keyword.length() >= 2 && hay.contains(keyword)) {
            hits += 2;
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
