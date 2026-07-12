package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.BizCatalogObjection;
import com.chengde.smartcity.exchange.entity.BizDataDemand;
import com.chengde.smartcity.exchange.entity.BizDemandSupplyTask;
import com.chengde.smartcity.exchange.entity.BizDemandTemplate;
import com.chengde.smartcity.exchange.entity.BizEsbFlow;
import com.chengde.smartcity.exchange.entity.BizSupplyManifest;
import com.chengde.smartcity.exchange.mapper.BizCatalogItemMapper;
import com.chengde.smartcity.exchange.mapper.BizCatalogObjectionMapper;
import com.chengde.smartcity.exchange.mapper.BizDataDemandMapper;
import com.chengde.smartcity.exchange.mapper.BizDemandSupplyTaskMapper;
import com.chengde.smartcity.exchange.mapper.BizDemandTemplateMapper;
import com.chengde.smartcity.exchange.mapper.BizEsbFlowMapper;
import com.chengde.smartcity.exchange.mapper.BizSupplyManifestMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplyDemandService {

    private final BizDemandTemplateMapper templateMapper;
    private final BizDataDemandMapper demandMapper;
    private final BizCatalogItemMapper catalogMapper;
    private final BizDemandSupplyTaskMapper supplyTaskMapper;
    private final BizCatalogObjectionMapper objectionMapper;
    private final BizSupplyManifestMapper manifestMapper;
    private final BizEsbFlowMapper esbFlowMapper;
    private final AuditService auditService;

    public SupplyDemandService(BizDemandTemplateMapper templateMapper, BizDataDemandMapper demandMapper,
                               BizCatalogItemMapper catalogMapper, BizDemandSupplyTaskMapper supplyTaskMapper,
                               BizCatalogObjectionMapper objectionMapper, BizSupplyManifestMapper manifestMapper,
                               BizEsbFlowMapper esbFlowMapper, AuditService auditService) {
        this.templateMapper = templateMapper;
        this.demandMapper = demandMapper;
        this.catalogMapper = catalogMapper;
        this.supplyTaskMapper = supplyTaskMapper;
        this.objectionMapper = objectionMapper;
        this.manifestMapper = manifestMapper;
        this.esbFlowMapper = esbFlowMapper;
        this.auditService = auditService;
    }

    public List<BizDemandTemplate> listTemplates() {
        return templateMapper.selectList(new LambdaQueryWrapper<BizDemandTemplate>()
                .eq(BizDemandTemplate::getStatus, "ACTIVE")
                .orderByAsc(BizDemandTemplate::getId));
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

    @Transactional
    public Long createDemand(UserPrincipal operator, Map<String, Object> body) {
        BizDataDemand demand = new BizDataDemand();
        demand.setDemandTitle(required(body.get("demandTitle"), "需求标题").toString());
        demand.setRequesterOrg(str(body.get("requesterOrg"), "机构" + operator.getOrgId()));
        demand.setDemandType(str(body.get("demandType"), "STRUCTURED"));
        demand.setTemplateCode(str(body.get("templateCode"), null));
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
        if (!"SUBMITTED".equals(demand.getStatus()) && !"ANALYZING".equals(demand.getStatus())) {
            throw new BusinessException(400, "当前状态不可分析");
        }
        List<BizCatalogItem> catalogs = catalogMapper.selectList(new LambdaQueryWrapper<BizCatalogItem>()
                .eq(BizCatalogItem::getPublishStatus, "PUBLISHED"));
        BizCatalogItem best = null;
        double bestScore = 0;
        String keyword = demand.getDemandTitle().toLowerCase();
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (BizCatalogItem c : catalogs) {
            double score = matchScore(keyword, c.getTitle(), c.getDescription());
            Map<String, Object> row = new HashMap<>();
            row.put("catalogId", c.getId());
            row.put("catalogCode", c.getCatalogCode());
            row.put("title", c.getTitle());
            row.put("score", score);
            candidates.add(row);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        demand.setStage("ANALYZE");
        demand.setStatus("ANALYZING");
        if (best != null && bestScore >= 30) {
            demand.setMatchedCatalogId(best.getId());
            demand.setMatchScore(BigDecimal.valueOf(bestScore).setScale(2, RoundingMode.HALF_UP));
            demand.setAnalysisNote("智能匹配目录：" + best.getTitle() + "，相关度 " + bestScore + "%");
        } else {
            demand.setAnalysisNote("未找到高相关目录，建议人工分发");
            demand.setMatchScore(BigDecimal.ZERO);
        }
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_ANALYZE", "biz_data_demand", String.valueOf(id), demand.getAnalysisNote());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("demandId", id);
        out.put("matchedCatalogId", demand.getMatchedCatalogId());
        out.put("matchScore", demand.getMatchScore());
        out.put("analysisNote", demand.getAnalysisNote());
        out.put("candidates", candidates.stream().sorted((a, b) ->
                Double.compare((Double) b.get("score"), (Double) a.get("score"))).limit(5).toList());
        out.put("relationGraph", buildRelationGraph(demand, best));
        return out;
    }

    @Transactional
    public void dispatchDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizDataDemand demand = getDemand(id);
        demand.setAssigneeOrg(required(body.get("assigneeOrg"), "assigneeOrg").toString());
        demand.setStatus("DISPATCHED");
        demand.setStage("ANALYZE");
        demand.setAnalysisNote(str(body.get("analysisNote"), "已分发至 " + demand.getAssigneeOrg()));
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_DISPATCH", "biz_data_demand", String.valueOf(id), demand.getAssigneeOrg());
    }

    @Transactional
    public void returnDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizDataDemand demand = getDemand(id);
        demand.setStatus("RETURNED");
        demand.setStage("MANAGE");
        demand.setAnalysisNote(str(body.get("analysisNote"), "分析退回，需补充材料"));
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_RETURN", "biz_data_demand", String.valueOf(id), demand.getAnalysisNote());
    }

    @Transactional
    public Map<String, Object> confirmDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizDataDemand demand = getDemand(id);
        demand.setStatus("CONFIRMED");
        demand.setStage("CONFIRM");
        demand.setConfirmNote(str(body.get("confirmNote"), "需求已确认，生成供给任务"));
        demand.setSupplyMode(str(body.get("supplyMode"), "EXCHANGE"));
        demandMapper.updateById(demand);
        List<BizDemandSupplyTask> tasks = createSupplyTasks(demand);
        BizSupplyManifest manifest = new BizSupplyManifest();
        manifest.setManifestType("SUPPLY_DEMAND");
        manifest.setRefId(demand.getId());
        manifest.setTitle("供需台账-" + demand.getDemandTitle());
        manifest.setStatus("ACTIVE");
        manifest.setAuthLevel(str(body.get("authLevel"), "DEPT"));
        manifest.setCascadeFlag(intVal(body.get("cascadeFlag"), 0));
        manifest.setExportPayload("demandId=" + demand.getId() + ",tasks=" + tasks.size());
        manifestMapper.insert(manifest);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_CONFIRM", "biz_data_demand", String.valueOf(id), demand.getConfirmNote());
        return Map.of("demandId", id, "tasks", tasks, "manifestId", manifest.getId());
    }

    @Transactional
    public void rejectDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizDataDemand demand = getDemand(id);
        demand.setStatus("REJECTED");
        demand.setStage("CONFIRM");
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
        BizCatalogItem catalog = demand.getMatchedCatalogId() != null
                ? catalogMapper.selectById(demand.getMatchedCatalogId()) : null;
        List<BizEsbFlow> flows = esbFlowMapper.selectList(new LambdaQueryWrapper<BizEsbFlow>().last("LIMIT 3"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("demand", demand);
        out.put("tasks", tasks);
        out.put("catalog", catalog);
        out.put("exchangeFlows", flows);
        out.put("sharePageUrl", catalog != null ? "/exchange/portal?tab=catalog" : null);
        out.put("apiEndpoint", catalog != null ? "/api/v1/exchange/catalog/" + catalog.getId() : null);
        return out;
    }

    public List<BizCatalogItem> catalogManifest() {
        return catalogMapper.selectList(new LambdaQueryWrapper<BizCatalogItem>()
                .in(BizCatalogItem::getPublishStatus, "PUBLISHED", "OFFLINE")
                .orderByDesc(BizCatalogItem::getId));
    }

    @Transactional
    public Long createCatalog(UserPrincipal operator, Map<String, Object> body) {
        BizCatalogItem item = new BizCatalogItem();
        item.setCatalogCode(str(body.get("catalogCode"), "CAT_" + UUID.randomUUID().toString().substring(0, 8)));
        item.setTitle(required(body.get("title"), "目录标题").toString());
        item.setDescription(str(body.get("description"), ""));
        item.setPublishStatus("DRAFT");
        item.setCreatedBy(operator.getUsername());
        catalogMapper.insert(item);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "CATALOG_CREATE", "biz_catalog_item", String.valueOf(item.getId()), item.getTitle());
        return item.getId();
    }

    @Transactional
    public void publishCatalog(UserPrincipal operator, Long id) {
        BizCatalogItem item = catalogMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "目录不存在");
        }
        item.setPublishStatus("PUBLISHED");
        catalogMapper.updateById(item);
        upsertCatalogManifest(item);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "CATALOG_PUBLISH", "biz_catalog_item", String.valueOf(id), item.getTitle());
    }

    @Transactional
    public void offlineCatalog(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizCatalogItem item = catalogMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "目录不存在");
        }
        item.setPublishStatus("OFFLINE");
        item.setDescription(item.getDescription() + " [下线:" + str(body.get("reason"), "维护") + "]");
        catalogMapper.updateById(item);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "CATALOG_OFFLINE", "biz_catalog_item", String.valueOf(id), item.getTitle());
    }

    public Map<String, Object> exportCatalogManifest() {
        List<BizCatalogItem> items = catalogManifest();
        StringBuilder csv = new StringBuilder("catalogCode,title,publishStatus\n");
        for (BizCatalogItem i : items) {
            csv.append(i.getCatalogCode()).append(',')
                    .append(i.getTitle()).append(',')
                    .append(i.getPublishStatus()).append('\n');
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
        obj.setCatalogId(Long.valueOf(String.valueOf(required(body.get("catalogId"), "catalogId"))));
        obj.setObjectionType(str(body.get("objectionType"), "QUALITY"));
        obj.setContent(required(body.get("content"), "content").toString());
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
        obj.setStatus("CLOSE".equals(action) || "CLOSED".equals(action) ? "CLOSED" : "PROCESSING");
        obj.setHandlerNote(str(body.get("handlerNote"), ""));
        objectionMapper.updateById(obj);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "OBJECTION_PROCESS", "biz_catalog_objection", String.valueOf(id), obj.getStatus());
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

    private List<BizDemandSupplyTask> createSupplyTasks(BizDataDemand demand) {
        List<BizDemandSupplyTask> tasks = new ArrayList<>();
        tasks.add(insertTask(demand.getId(), "COLLECT", "归集任务-" + demand.getDemandTitle(), "PENDING", null));
        tasks.add(insertTask(demand.getId(), "SHARE", "共享页面-" + demand.getDemandTitle(), "PENDING", null));
        BizEsbFlow flow = esbFlowMapper.selectOne(new LambdaQueryWrapper<BizEsbFlow>().last("LIMIT 1"));
        String flowCode = flow != null ? flow.getFlowCode() : "MF_DEMO_001";
        tasks.add(insertTask(demand.getId(), "EXCHANGE", "交换作业-" + demand.getDemandTitle(), "PENDING", flowCode));
        demand.setStage("SUPPLY");
        demandMapper.updateById(demand);
        return tasks;
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
        m.setTitle("异议-" + obj.getId());
        m.setStatus("ACTIVE");
        m.setAuthLevel("DEPT");
        m.setCascadeFlag(0);
        manifestMapper.insert(m);
    }

    private Map<String, Object> buildRelationGraph(BizDataDemand demand, BizCatalogItem catalog) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        nodes.add(Map.of("id", "demand-" + demand.getId(), "label", demand.getDemandTitle(), "type", "DEMAND"));
        if (catalog != null) {
            nodes.add(Map.of("id", "catalog-" + catalog.getId(), "label", catalog.getTitle(), "type", "CATALOG"));
            edges.add(Map.of("from", "demand-" + demand.getId(), "to", "catalog-" + catalog.getId(), "label", "匹配"));
        }
        if (demand.getTargetCatalogId() != null && (catalog == null || !demand.getTargetCatalogId().equals(catalog.getId()))) {
            BizCatalogItem target = catalogMapper.selectById(demand.getTargetCatalogId());
            if (target != null) {
                nodes.add(Map.of("id", "catalog-" + target.getId(), "label", target.getTitle(), "type", "CATALOG"));
                edges.add(Map.of("from", "demand-" + demand.getId(), "to", "catalog-" + target.getId(), "label", "申请"));
            }
        }
        return Map.of("nodes", nodes, "edges", edges);
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
