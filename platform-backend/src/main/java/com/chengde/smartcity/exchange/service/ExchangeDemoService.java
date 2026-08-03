package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.BizCollectTask;
import com.chengde.smartcity.exchange.entity.BizDataAsset;
import com.chengde.smartcity.exchange.entity.BizDataDemand;
import com.chengde.smartcity.exchange.entity.BizEsbFlow;
import com.chengde.smartcity.exchange.entity.BizKettleJob;
import com.chengde.smartcity.exchange.mapper.BizCatalogItemMapper;
import com.chengde.smartcity.exchange.mapper.BizCollectTaskMapper;
import com.chengde.smartcity.exchange.mapper.BizDataAssetMapper;
import com.chengde.smartcity.exchange.mapper.BizDataDemandMapper;
import com.chengde.smartcity.exchange.mapper.BizEsbFlowMapper;
import com.chengde.smartcity.exchange.mapper.BizKettleJobMapper;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.kettle.KettleClient;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExchangeDemoService {

    private final BizDataAssetMapper assetMapper;
    private final BizCollectTaskMapper taskMapper;
    private final BizCatalogItemMapper catalogMapper;
    private final BizDataDemandMapper demandMapper;
    private final BizEsbFlowMapper esbFlowMapper;
    private final BizKettleJobMapper kettleJobMapper;
    private final AuditService auditService;
    private final IntegrationProperties integrationProperties;
    private final KettleClient kettleClient;

    public ExchangeDemoService(BizDataAssetMapper assetMapper, BizCollectTaskMapper taskMapper,
                               BizCatalogItemMapper catalogMapper, BizDataDemandMapper demandMapper,
                               BizEsbFlowMapper esbFlowMapper, BizKettleJobMapper kettleJobMapper,
                               AuditService auditService, IntegrationProperties integrationProperties,
                               KettleClient kettleClient) {
        this.assetMapper = assetMapper;
        this.taskMapper = taskMapper;
        this.catalogMapper = catalogMapper;
        this.demandMapper = demandMapper;
        this.esbFlowMapper = esbFlowMapper;
        this.kettleJobMapper = kettleJobMapper;
        this.auditService = auditService;
        this.integrationProperties = integrationProperties;
        this.kettleClient = kettleClient;
    }

    public List<BizDataAsset> listAssets() {
        return assetMapper.selectList(new LambdaQueryWrapper<BizDataAsset>().orderByDesc(BizDataAsset::getId));
    }

    @Transactional
    public Long createAsset(UserPrincipal operator, Map<String, Object> body) {
        BizDataAsset asset = new BizDataAsset();
        asset.setAssetCode(str(body.get("assetCode"), "AST_" + System.currentTimeMillis()));
        asset.setAssetName(required(body.get("assetName"), "资产名称"));
        asset.setSourceSystem(str(body.get("sourceSystem"), "演示数据源"));
        asset.setOwnerOrgId(operator.getOrgId());
        asset.setStatus("REGISTERED");
        asset.setCreatedBy(operator.getUsername());
        assetMapper.insert(asset);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ASSET_REGISTER", "biz_data_asset", String.valueOf(asset.getId()), asset.getAssetName());
        return asset.getId();
    }

    public List<BizCollectTask> listTasks() {
        return taskMapper.selectList(new LambdaQueryWrapper<BizCollectTask>().orderByDesc(BizCollectTask::getId));
    }

    @Transactional
    public Long createTask(UserPrincipal operator, Map<String, Object> body) {
        BizCollectTask task = new BizCollectTask();
        task.setTaskName(required(body.get("taskName"), "任务名称"));
        Object assetId = body.get("assetId");
        if (assetId != null) {
            task.setAssetId(Long.valueOf(String.valueOf(assetId)));
        }
        task.setScheduleCron(str(body.get("scheduleCron"), "0 0 * * * ?"));
        task.setStatus("READY");
        task.setCreatedBy(operator.getUsername());
        taskMapper.insert(task);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "COLLECT_TASK_CREATE", "biz_collect_task", String.valueOf(task.getId()), task.getTaskName());
        return task.getId();
    }

    @Transactional
    public void runTask(UserPrincipal operator, Long id) {
        BizCollectTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "采集任务不存在");
        }
        task.setStatus("SUCCESS");
        task.setLastRunAt(LocalDateTime.now());
        task.setLastMessage("demo collect success, rows=" + (10 + (int) (Math.random() * 90)));
        taskMapper.updateById(task);
        if (task.getAssetId() != null) {
            BizDataAsset asset = assetMapper.selectById(task.getAssetId());
            if (asset != null) {
                asset.setStatus("COLLECTED");
                assetMapper.updateById(asset);
            }
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "COLLECT_TASK_RUN", "biz_collect_task", String.valueOf(id), task.getLastMessage());
    }

    public List<BizCatalogItem> listCatalog() {
        return catalogMapper.selectList(new LambdaQueryWrapper<BizCatalogItem>()
                .isNotNull(BizCatalogItem::getGovResourceId)
                .orderByDesc(BizCatalogItem::getId));
    }

    @Transactional
    public Long createCatalog(UserPrincipal operator, Map<String, Object> body) {
        throw new BusinessException(400, "请勿通过演示接口新建目录；请在统一目录模块编目审批");
    }

    @Transactional
    public void publishCatalog(UserPrincipal operator, Long id) {
        throw new BusinessException(400, "请勿通过演示接口发布目录；请通过统一目录审批发布");
    }

    public List<BizCatalogItem> sharedPortal() {
        return catalogMapper.selectList(new LambdaQueryWrapper<BizCatalogItem>()
                .isNotNull(BizCatalogItem::getGovResourceId)
                .eq(BizCatalogItem::getPublishStatus, "PUBLISHED")
                .orderByDesc(BizCatalogItem::getId));
    }

    public List<BizDataDemand> listDemands() {
        return demandMapper.selectList(new LambdaQueryWrapper<BizDataDemand>().orderByDesc(BizDataDemand::getId));
    }

    @Transactional
    public Long createDemand(UserPrincipal operator, Map<String, Object> body) {
        BizDataDemand demand = new BizDataDemand();
        demand.setDemandTitle(required(body.get("demandTitle"), "需求标题"));
        demand.setRequesterOrg(str(body.get("requesterOrg"), "机构" + operator.getOrgId()));
        Object catalogId = body.get("targetCatalogId");
        if (catalogId != null) {
            demand.setTargetCatalogId(Long.valueOf(String.valueOf(catalogId)));
        }
        demand.setStatus("SUBMITTED");
        demand.setCreatedBy(operator.getUsername());
        demandMapper.insert(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_SUBMIT", "biz_data_demand", String.valueOf(demand.getId()), demand.getDemandTitle());
        return demand.getId();
    }

    @Transactional
    public void confirmDemand(UserPrincipal operator, Long id, Map<String, Object> body) {
        BizDataDemand demand = demandMapper.selectById(id);
        if (demand == null) {
            throw new BusinessException(404, "需求不存在");
        }
        demand.setStatus("CONFIRMED");
        demand.setConfirmNote(str(body.get("confirmNote"), "已确认对接，进入交换台账"));
        demandMapper.updateById(demand);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DEMAND_CONFIRM", "biz_data_demand", String.valueOf(id), demand.getConfirmNote());
    }

    public List<BizEsbFlow> listEsbFlows() {
        return esbFlowMapper.selectList(new LambdaQueryWrapper<BizEsbFlow>().orderByAsc(BizEsbFlow::getId));
    }

    @Transactional
    public Map<String, Object> invokeEsb(UserPrincipal operator, Long id) {
        BizEsbFlow flow = esbFlowMapper.selectById(id);
        if (flow == null) {
            throw new BusinessException(404, "MessageFlow 不存在");
        }
        String traceId = "ESB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        flow.setStatus("RUNNING");
        flow.setLastInvokeAt(LocalDateTime.now());
        flow.setLastResult("调用成功 traceId=" + traceId);
        esbFlowMapper.updateById(flow);
        flow.setStatus("SUCCESS");
        esbFlowMapper.updateById(flow);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ESB_INVOKE", "biz_esb_flow", String.valueOf(id), flow.getLastResult());
        return Map.of(
                "flowCode", flow.getFlowCode(),
                "traceId", traceId,
                "status", "SUCCESS",
                "message", flow.getLastResult()
        );
    }

    public List<BizKettleJob> listKettleJobs() {
        return kettleJobMapper.selectList(new LambdaQueryWrapper<BizKettleJob>().orderByAsc(BizKettleJob::getId));
    }

    @Transactional
    public Map<String, Object> runKettle(UserPrincipal operator, Long id) {
        // M215 遗留演示作业（非真实主链）：仅更新本地台账，真实 Carte 抽取走登记→汇聚链路。
        BizKettleJob job = kettleJobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(404, "Kettle 作业不存在");
        }
        job.setStatus("SUCCESS");
        job.setLastRunAt(LocalDateTime.now());
        job.setLastMessage("M215 演示作业执行完成");
        kettleJobMapper.updateById(job);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "KETTLE_RUN", "biz_kettle_job", String.valueOf(id), job.getLastMessage());
        return Map.of(
                "jobCode", job.getJobCode(),
                "status", job.getStatus(),
                "message", job.getLastMessage()
        );
    }

    private static String required(Object value, String label) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(400, label + "不能为空");
        }
        return String.valueOf(value);
    }

    private static String str(Object value, String defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(value);
    }
}
