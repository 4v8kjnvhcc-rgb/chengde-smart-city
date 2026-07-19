package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.integration.ds.DolphinSchedulerClient;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * DolphinScheduler 真实编排：为汇聚/加工两条技术链创建真实项目、流程定义与实例，
 * 记录 ds_project_code/definition_code/instance_id，并提供状态/停止/失败重跑。
 * DS 只把资源推进到"待编目/待审批"，审批发布仍为人工业务门禁，不由 DS 越权完成。
 */
@Service
public class DsOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(DsOrchestrationService.class);
    private static final String COLLECT_PROJECT = "chengde_collect";
    private static final String FUSION_PROJECT = "chengde_fusion";
    private static final String KETTLE_PROJECT = "chengde_kettle";

    private final DolphinSchedulerClient dsClient;
    private final IngIngestTaskMapper ingestTaskMapper;
    private final IngDataTableMapper dataTableMapper;
    private final AuditService auditService;
    private final IntegrationProperties integrationProperties;

    public DsOrchestrationService(DolphinSchedulerClient dsClient, IngIngestTaskMapper ingestTaskMapper,
                                  IngDataTableMapper dataTableMapper, AuditService auditService,
                                  IntegrationProperties integrationProperties) {
        this.dsClient = dsClient;
        this.ingestTaskMapper = ingestTaskMapper;
        this.dataTableMapper = dataTableMapper;
        this.auditService = auditService;
        this.integrationProperties = integrationProperties;
    }

    /** 为某登记表的汇聚链路创建真实 DS 流程实例并回写标识。 */
    public Map<String, Object> orchestrateCollect(UserPrincipal operator, Long tableId) {
        IngDataTable table = dataTableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException(404, "登记表不存在");
        }
        if (!dsClient.isHealthy()) {
            throw new BusinessException(502, "DolphinScheduler 不可用（请确认 sched profile 已启动）");
        }
        long projectCode = dsClient.ensureProject(COLLECT_PROJECT);
        String tenant = dsClient.resolveTenant();
        String defName = "汇聚_" + safeName(table.getTableName()) + "_" + tableId;
        List<String> steps = List.of("Kettle抽取到ODS", "平台回写台账", "OpenMetadata同步", "质量执行");
        long definitionCode = dsClient.createAndReleaseChain(projectCode, defName, steps, tenant);
        long instanceId = dsClient.startInstance(projectCode, definitionCode);

        IngIngestTask task = ingestTaskMapper.selectOne(new LambdaQueryWrapper<IngIngestTask>()
                .eq(IngIngestTask::getTableId, tableId).last("LIMIT 1"));
        if (task != null) {
            task.setDsProjectCode(projectCode);
            task.setDsDefinitionCode(definitionCode);
            task.setDsInstanceId(instanceId);
            ingestTaskMapper.updateById(task);
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DS_ORCHESTRATE_COLLECT", "ing_data_table", String.valueOf(tableId),
                "project=" + projectCode + " def=" + definitionCode + " instance=" + instanceId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tableId", tableId);
        out.put("projectCode", projectCode);
        out.put("definitionCode", definitionCode);
        out.put("instanceId", instanceId);
        out.put("steps", steps);
        out.put("status", dsClient.instanceStatus(projectCode, instanceId).get("state"));
        return out;
    }

    /** 为加工链路创建真实 DS 流程实例。 */
    public Map<String, Object> orchestrateFusion(UserPrincipal operator, Long tableId, String targetTable) {
        if (!dsClient.isHealthy()) {
            throw new BusinessException(502, "DolphinScheduler 不可用（请确认 sched profile 已启动）");
        }
        long projectCode = dsClient.ensureProject(FUSION_PROJECT);
        String tenant = dsClient.resolveTenant();
        String defName = "加工_" + safeName(targetTable) + "_" + tableId;
        List<String> steps = List.of("Kettle清洗融合到DWS", "产出元数据与血缘", "产出质量", "资源中心纳管");
        long definitionCode = dsClient.createAndReleaseChain(projectCode, defName, steps, tenant);
        long instanceId = dsClient.startInstance(projectCode, definitionCode);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DS_ORCHESTRATE_FUSION", "gov_governance_task", String.valueOf(tableId),
                "project=" + projectCode + " def=" + definitionCode + " instance=" + instanceId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tableId", tableId);
        out.put("projectCode", projectCode);
        out.put("definitionCode", definitionCode);
        out.put("instanceId", instanceId);
        out.put("steps", steps);
        out.put("status", dsClient.instanceStatus(projectCode, instanceId).get("state"));
        return out;
    }

    public Map<String, Object> status(Long projectCode, Long instanceId) {
        if (projectCode == null || instanceId == null) {
            throw new BusinessException(400, "projectCode 与 instanceId 必填");
        }
        return dsClient.instanceStatus(projectCode, instanceId);
    }

    /**
     * 用 DS 的 SHELL 串行链路启动并等待 Carte trans 完成。
     * DS 失败/超时即返回 FAILED/timeout，并抛出异常由上层决定回滚/重试。
     */
    public Map<String, Object> runKettleTrans(UserPrincipal operator, String transName, String label) {
        if (!dsClient.isHealthy()) {
            throw new BusinessException(502, "DolphinScheduler 不可用（请确认 sched profile 已启动）");
        }
        if (transName == null || transName.isBlank()) {
            throw new BusinessException(400, "transName 必填");
        }

        long projectCode = dsClient.ensureProject(KETTLE_PROJECT);
        String tenant = dsClient.resolveTenant();
        String defName = "KTR_" + safeName(label) + "_" + transName;

        String kettleUser = integrationProperties.getKettle().getUser();
        String kettlePass = integrationProperties.getKettle().getPassword();
        String carteBase = "http://" + integrationProperties.getKettle().getCarteHost() + ":"
                + integrationProperties.getKettle().getCartePort();

        // startTrans 仅 HTTP 200 不够，须校验 webresult.result=OK
        String startScript = "resp=$(curl -s -u '" + kettleUser + ":" + kettlePass + "' '"
                + carteBase + "/kettle/startTrans/?xml=Y&name=" + transName + "'); "
                + "echo \"$resp\" | grep -q '<result>OK</result>' || { echo \"$resp\"; exit 1; }";

        // 勿用 grep error（会误匹配 <errors>0</errors>）；按 status_desc / webresult 判定
        String waitScript = "i=0; while [ $i -lt 600 ]; do "
                + "xml=$(curl -s -u '" + kettleUser + ":" + kettlePass + "' '"
                + carteBase + "/kettle/transStatus/?xml=Y&name=" + transName + "'); "
                + "echo \"$xml\" | grep -q '>Finished<' && exit 0; "
                + "echo \"$xml\" | grep -q '<result>ERROR</result>' && exit 2; "
                + "echo \"$xml\" | grep -Eqi 'status_desc>(Stopped|Halting|Failed|Error)<' && exit 2; "
                + "i=$((i+1)); sleep 1; "
                + "done; exit 3";

        List<String> steps = List.of("start", "wait");
        List<String> scripts = List.of(startScript, waitScript);

        long definitionCode = dsClient.createAndReleaseShellChain(projectCode, defName, steps, scripts, tenant);
        long instanceId = dsClient.startInstance(projectCode, definitionCode);

        if (operator != null) {
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "DS_KETTLE_TRANS_RUN", "dolphinscheduler", String.valueOf(instanceId),
                    "trans=" + transName);
        } else {
            auditService.log(0L, "system", null,
                    "DS_KETTLE_TRANS_RUN", "dolphinscheduler", String.valueOf(instanceId),
                    "trans=" + transName);
        }

        String state = "UNKNOWN";
        for (int i = 0; i < 720; i++) {
            Map<String, Object> st = dsClient.instanceStatus(projectCode, instanceId);
            state = String.valueOf(st.get("state"));
            if (isTerminalDsState(state)) {
                break;
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectCode", projectCode);
        out.put("definitionCode", definitionCode);
        out.put("instanceId", instanceId);
        out.put("state", state);
        out.put("transName", transName);

        if (!"SUCCESS".equalsIgnoreCase(state)) {
            throw new BusinessException(502, "DS 执行 Kettle trans 失败: state=" + state + ", trans=" + transName);
        }
        return out;
    }

    /** DS 3.2 终态：SUCCESS / FAILURE / STOP / KILL 等。 */
    private static boolean isTerminalDsState(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        String s = state.toUpperCase();
        return s.contains("SUCCESS") || s.contains("FAIL") || s.contains("STOP") || s.contains("KILL");
    }

    public Map<String, Object> stop(UserPrincipal operator, Long projectCode, Long instanceId) {
        require(projectCode, instanceId);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DS_STOP", "dolphinscheduler", String.valueOf(instanceId), "stop");
        return dsClient.stopInstance(projectCode, instanceId);
    }

    public Map<String, Object> retry(UserPrincipal operator, Long projectCode, Long instanceId) {
        require(projectCode, instanceId);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "DS_RETRY", "dolphinscheduler", String.valueOf(instanceId), "retry");
        return dsClient.retryInstance(projectCode, instanceId);
    }

    private void require(Long projectCode, Long instanceId) {
        if (projectCode == null || instanceId == null) {
            throw new BusinessException(400, "projectCode 与 instanceId 必填");
        }
    }

    private String safeName(String s) {
        if (s == null || s.isBlank()) {
            return "chain";
        }
        return s.replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5_]", "").trim();
    }
}
