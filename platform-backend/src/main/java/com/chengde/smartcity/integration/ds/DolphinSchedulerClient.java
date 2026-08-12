package com.chengde.smartcity.integration.ds;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationConfig;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * DolphinScheduler 3.2 REST 客户端：真实登录/令牌鉴权，真实项目/租户/流程定义/实例生命周期。
 * 不再固定 project=1，不再伪造 session；鉴权或业务失败直接抛出真实原因。
 */
@Component
public class DolphinSchedulerClient {

    private static final Logger log = LoggerFactory.getLogger(DolphinSchedulerClient.class);

    private final IntegrationProperties props;
    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile String sessionId;

    public DolphinSchedulerClient(IntegrationProperties props, RestTemplate integrationRestTemplate) {
        this.props = props;
        this.rest = integrationRestTemplate;
    }

    public boolean isHealthy() {
        if (!props.isEnabled()) {
            return false;
        }
        String base = base();
        try {
            ResponseEntity<String> res = rest.getForEntity(base + "/actuator/health", String.class);
            if (res.getStatusCode().is2xxSuccessful()) {
                return true;
            }
        } catch (Exception e) {
            log.debug("DS actuator health failed url={}: {}", base, e.getMessage());
        }
        // 部分环境未暴露 actuator：再试登录接口可达性
        try {
            rest.exchange(base + "/login", HttpMethod.POST,
                    new HttpEntity<>(new HttpHeaders()), String.class);
            return true;
        } catch (Exception e) {
            // 405/400 也说明服务已起来
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("405") || msg.contains("400") || msg.contains("401") || msg.contains("403")) {
                return true;
            }
            log.warn("DS health check failed url={}: {}", base, msg);
            return false;
        }
    }

    // ---------- 项目 / 租户 ----------

    /** 按名称查找或创建项目，返回 projectCode。 */
    public long ensureProject(String projectName) {
        IntegrationConfig.requireIntegration(props, "DolphinScheduler");
        Long code = findProjectCode(projectName);
        if (code != null) {
            return code;
        }
        String url = base() + "/projects";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("projectName", projectName);
        form.add("description", "chengde auto project");
        JsonNode root = requirePost(url, form, "创建 DS 项目");
        long projectCode = extractCode(root.path("data"));
        if (projectCode <= 0) {
            code = findProjectCode(projectName);
            if (code != null) {
                return code;
            }
            throw new BusinessException(502, "创建 DS 项目成功但未返回 projectCode: " + trim(root.toString()));
        }
        return projectCode;
    }

    private Long findProjectCode(String projectName) {
        String url = base() + "/projects?pageNo=1&pageSize=100&searchVal=" + enc(projectName);
        JsonNode root = requireGet(url, "查询 DS 项目");
        JsonNode list = root.path("data").path("totalList");
        if (list.isArray()) {
            for (JsonNode n : list) {
                if (projectName.equals(n.path("name").asText())) {
                    return n.path("code").asLong();
                }
            }
        }
        return null;
    }

    /** 返回可用租户 code；优先配置值，否则取第一个已存在租户。 */
    public String resolveTenant() {
        String configured = props.getDs().getTenantCode();
        String url = base() + "/tenants?pageNo=1&pageSize=100";
        try {
            JsonNode root = requireGet(url, "查询 DS 租户");
            JsonNode list = root.path("data").path("totalList");
            if (list.isArray() && list.size() > 0) {
                for (JsonNode n : list) {
                    if (n.path("tenantCode").asText().equals(configured)) {
                        return configured;
                    }
                }
                return list.get(0).path("tenantCode").asText(configured);
            }
        } catch (Exception e) {
            log.warn("查询 DS 租户失败，回退配置 {}: {}", configured, e.getMessage());
        }
        return configured;
    }

    // ---------- 流程定义 ----------

    /**
     * 创建并上线一个由若干 SHELL 节点串行组成的流程定义，返回 processDefinitionCode。
     * 每个节点执行 echo，代表链路环节，产生真实的 DS 实例与节点状态。
     */
    public long createAndReleaseChain(long projectCode, String name, List<String> steps, String tenantCode) {
        List<String> scripts = new ArrayList<>();
        for (String stepName : steps) {
            scripts.add("echo \"[chengde] " + stepName.replace("\"", "") + " @ $(date)\"");
        }
        return createAndReleaseShellChain(projectCode, name, steps, scripts, tenantCode);
    }

    /**
     * 创建并上线一个由若干 SHELL 节点串行组成的流程定义（脚本版）。
     * scripts[i] 对应 steps[i] 的 rawScript；失败即以退出码非 0 让实例失败。
     * DS 3.2.2 要求 task 含 isCache，否则 transformTask NPE。
     */
    public long createAndReleaseShellChain(long projectCode, String name,
                                            List<String> steps, List<String> scripts, String tenantCode) {
        if (steps == null || scripts == null || steps.size() != scripts.size()) {
            throw new BusinessException(400, "steps/scripts 长度必须一致");
        }
        long[] taskCodes = genTaskCodes(projectCode, steps.size());
        StringBuilder taskDefs = new StringBuilder("[");
        StringBuilder relations = new StringBuilder("[");
        StringBuilder locations = new StringBuilder("[");
        for (int i = 0; i < steps.size(); i++) {
            long taskCode = taskCodes[i];
            String stepName = steps.get(i);
            String script = scripts.get(i);
            if (script == null) script = "";
            if (i > 0) {
                taskDefs.append(',');
                relations.append(',');
                locations.append(',');
            }
            taskDefs.append("{\"code\":").append(taskCode)
                    .append(",\"name\":\"").append(escapeJson(stepName)).append("\"")
                    .append(",\"version\":1,\"description\":\"\",\"taskType\":\"SHELL\"")
                    .append(",\"taskParams\":{\"localParams\":[],\"rawScript\":\"")
                    .append(escapeJson(script))
                    .append("\",\"resourceList\":[],\"dependence\":{},")
                    .append("\"conditionResult\":{\"successNode\":[],\"failedNode\":[]},")
                    .append("\"waitStartTimeout\":{},\"switchResult\":{}}")
                    .append(",\"flag\":\"YES\",\"isCache\":\"NO\",\"taskPriority\":\"MEDIUM\"")
                    .append(",\"workerGroup\":\"default\",\"taskExecuteType\":\"BATCH\"")
                    .append(",\"failRetryTimes\":0,\"failRetryInterval\":1")
                    .append(",\"timeoutFlag\":\"CLOSE\",\"timeoutNotifyStrategy\":\"\",\"timeout\":0")
                    .append(",\"delayTime\":0,\"environmentCode\":-1}");

            long pre = i == 0 ? 0 : taskCodes[i - 1];
            relations.append("{\"name\":\"\",\"preTaskCode\":").append(pre)
                    .append(",\"preTaskVersion\":").append(i == 0 ? 0 : 1)
                    .append(",\"postTaskCode\":").append(taskCode)
                    .append(",\"postTaskVersion\":1,\"conditionType\":\"NONE\",\"conditionParams\":{}}");
            locations.append("{\"taskCode\":").append(taskCode)
                    .append(",\"x\":").append(100 + i * 200).append(",\"y\":200}");
        }
        taskDefs.append(']');
        relations.append(']');
        locations.append(']');

        String url = base() + "/projects/" + projectCode + "/process-definition";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", name);
        form.add("description", "chengde kettle chain");
        form.add("globalParams", "[]");
        form.add("locations", locations.toString());
        form.add("timeout", "0");
        form.add("tenantCode", tenantCode);
        form.add("taskRelationJson", relations.toString());
        form.add("taskDefinitionJson", taskDefs.toString());
        form.add("executionType", "PARALLEL");
        JsonNode root = requirePost(url, form, "创建 DS 流程定义");
        long code = extractCode(root.path("data"));
        if (code <= 0) {
            throw new BusinessException(502, "创建 DS 流程定义未返回 code: " + trim(root.toString()));
        }
        releaseDefinition(projectCode, code, "ONLINE");
        return code;
    }

    public void releaseDefinition(long projectCode, long definitionCode, String state) {
        releaseDefinition(projectCode, definitionCode, state, null);
    }

    /**
     * @param state ONLINE / OFFLINE
     * @param name  流程名；下线时 DS 常要求传 name，为空则按 code 反查
     */
    public void releaseDefinition(long projectCode, long definitionCode, String state, String name) {
        String releaseName = name;
        if (releaseName == null || releaseName.isBlank()) {
            releaseName = findDefinitionName(projectCode, definitionCode);
        }
        String url = base() + "/projects/" + projectCode + "/process-definition/" + definitionCode + "/release";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", releaseName == null ? "" : releaseName);
        form.add("releaseState", state);
        String action = "OFFLINE".equalsIgnoreCase(state) ? "下线 DS 流程定义" : "上线 DS 流程定义";
        requirePost(url, form, action);
    }

    /** 分页列出项目下流程定义（最多 200 条）。 */
    public List<Map<String, Object>> listProcessDefinitions(long projectCode) {
        List<Map<String, Object>> out = new ArrayList<>();
        String url = base() + "/projects/" + projectCode + "/process-definition?pageNo=1&pageSize=200";
        JsonNode root = requireGet(url, "查询 DS 流程定义");
        JsonNode defs = root.path("data").path("totalList");
        if (!defs.isArray()) {
            return out;
        }
        for (JsonNode n : defs) {
            Map<String, Object> row = new HashMap<>();
            row.put("code", n.path("code").asLong());
            row.put("name", n.path("name").asText(""));
            row.put("releaseState", n.path("releaseState").asText(""));
            out.add(row);
        }
        return out;
    }

    private String findDefinitionName(long projectCode, long definitionCode) {
        try {
            for (Map<String, Object> row : listProcessDefinitions(projectCode)) {
                Object code = row.get("code");
                if (code != null && Long.parseLong(String.valueOf(code)) == definitionCode) {
                    Object name = row.get("name");
                    return name == null ? null : String.valueOf(name);
                }
            }
        } catch (Exception e) {
            log.warn("反查 DS 流程名失败 project={} def={}: {}", projectCode, definitionCode, e.getMessage());
        }
        return null;
    }

    // ---------- 实例 ----------

    public long startInstance(long projectCode, long definitionCode) {
        return startInstance(projectCode, definitionCode, "MEDIUM");
    }

    public long startInstance(long projectCode, long definitionCode, String priority) {
        String url = base() + "/projects/" + projectCode + "/executors/start-process-instance";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("processDefinitionCode", String.valueOf(definitionCode));
        form.add("scheduleTime", "");
        form.add("failureStrategy", "END");
        form.add("warningType", "NONE");
        form.add("warningGroupId", "0");
        form.add("execType", "START_PROCESS");
        form.add("startNodeList", "");
        form.add("taskDependType", "TASK_POST");
        form.add("runMode", "RUN_MODE_SERIAL");
        form.add("processInstancePriority", normalizePriority(priority));
        form.add("workerGroup", "default");
        form.add("tenantCode", resolveTenant());
        form.add("environmentCode", "-1");
        form.add("dryRun", "0");
        form.add("testFlag", "0");
        requirePost(url, form, "启动 DS 流程实例");
        return waitForLatestInstanceId(projectCode, definitionCode);
    }

    /** 查询流程定义最近一次实例 ID。 */
    public long latestInstanceId(long projectCode, long definitionCode) {
        return latestInstanceIdInternal(projectCode, definitionCode);
    }

    /** DS start 返回的 data 是 triggerCode，不是 processInstanceId；轮询直到实例出现。 */
    private long waitForLatestInstanceId(long projectCode, long definitionCode) {
        for (int i = 0; i < 30; i++) {
            try {
                return latestInstanceIdInternal(projectCode, definitionCode);
            } catch (BusinessException be) {
                if (i >= 29) {
                    throw be;
                }
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw be;
                }
            }
        }
        throw new BusinessException(502, "未查询到刚启动的 DS 实例");
    }

    private long latestInstanceIdInternal(long projectCode, long definitionCode) {
        String url = base() + "/projects/" + projectCode + "/process-instances?pageNo=1&pageSize=1"
                + "&processDefineCode=" + definitionCode;
        JsonNode root = requireGet(url, "查询 DS 实例");
        JsonNode list = root.path("data").path("totalList");
        if (list.isArray() && list.size() > 0) {
            return list.get(0).path("id").asLong();
        }
        throw new BusinessException(502, "未查询到刚启动的 DS 实例");
    }

    /** 查询实例状态与节点。 */
    public Map<String, Object> instanceStatus(long projectCode, long instanceId) {
        String url = base() + "/projects/" + projectCode + "/process-instances/" + instanceId;
        JsonNode root = requireGet(url, "查询 DS 实例状态");
        JsonNode data = root.path("data");
        Map<String, Object> out = new HashMap<>();
        out.put("instanceId", instanceId);
        out.put("name", data.path("name").asText());
        out.put("state", data.path("state").asText());
        out.put("startTime", data.path("startTime").asText());
        out.put("endTime", data.path("endTime").asText());
        return out;
    }

    public Map<String, Object> stopInstance(long projectCode, long instanceId) {
        return execute(projectCode, instanceId, "STOP", "停止 DS 实例");
    }

    public Map<String, Object> retryInstance(long projectCode, long instanceId) {
        return execute(projectCode, instanceId, "START_FAILURE_TASK_PROCESS", "重跑 DS 实例");
    }

    public Map<String, Object> pauseInstance(long projectCode, long instanceId) {
        return execute(projectCode, instanceId, "PAUSE", "暂停 DS 实例");
    }

    public Map<String, Object> resumeInstance(long projectCode, long instanceId) {
        return execute(projectCode, instanceId, "RECOVER_SUSPENDED_PROCESS", "恢复 DS 实例");
    }

    public Map<String, Object> listProcessInstances(long projectCode, int pageNo, int pageSize,
                                                    String startDate, String endDate,
                                                    String searchVal, String stateType) {
        StringBuilder url = new StringBuilder(base())
                .append("/projects/").append(projectCode)
                .append("/process-instances?pageNo=").append(Math.max(1, pageNo))
                .append("&pageSize=").append(Math.min(Math.max(pageSize, 1), 100));
        if (startDate != null && !startDate.isBlank()) {
            url.append("&startDate=").append(enc(startDate));
        }
        if (endDate != null && !endDate.isBlank()) {
            url.append("&endDate=").append(enc(endDate));
        }
        if (searchVal != null && !searchVal.isBlank()) {
            url.append("&searchVal=").append(enc(searchVal));
        }
        if (stateType != null && !stateType.isBlank()) {
            url.append("&stateType=").append(enc(stateType));
        }
        JsonNode root = requireGet(url.toString(), "查询 DS 流程实例");
        JsonNode data = root.path("data");
        List<Map<String, Object>> rows = new ArrayList<>();
        JsonNode list = data.path("totalList");
        if (list.isArray()) {
            for (JsonNode n : list) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", n.path("id").asLong());
                row.put("name", n.path("name").asText(""));
                row.put("state", n.path("state").asText(""));
                row.put("startTime", n.path("startTime").asText(null));
                row.put("endTime", n.path("endTime").asText(null));
                row.put("duration", n.path("duration").asText(null));
                row.put("processDefinitionCode", n.path("processDefinitionCode").asLong(0L));
                row.put("commandType", n.path("commandType").asText(null));
                row.put("host", n.path("host").asText(null));
                row.put("processInstancePriority", n.path("processInstancePriority").asText(null));
                rows.add(row);
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("total", data.path("total").asInt(rows.size()));
        out.put("totalList", rows);
        return out;
    }

    public List<Map<String, Object>> listTaskInstances(long projectCode, long processInstanceId) {
        String url = base() + "/projects/" + projectCode
                + "/task-instances?pageNo=1&pageSize=100&processInstanceId=" + processInstanceId;
        try {
            JsonNode root = requireGet(url, "查询 DS 任务实例");
            JsonNode list = root.path("data").path("totalList");
            List<Map<String, Object>> out = new ArrayList<>();
            if (list.isArray()) {
                for (JsonNode n : list) {
                    out.add(taskNodeToMap(n));
                }
                return out;
            }
        } catch (Exception e) {
            log.warn("task-instances 分页查询失败，尝试 process-instances/tasks: {}", e.getMessage());
        }
        String alt = base() + "/projects/" + projectCode + "/process-instances/" + processInstanceId + "/tasks";
        JsonNode root = requireGet(alt, "查询 DS 任务实例");
        JsonNode data = root.path("data");
        List<Map<String, Object>> out = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode n : data) {
                out.add(taskNodeToMap(n));
            }
        }
        return out;
    }

    private Map<String, Object> taskNodeToMap(JsonNode n) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", n.path("id").asLong());
        row.put("name", n.path("name").asText(""));
        row.put("state", n.path("state").asText(""));
        row.put("startTime", n.path("startTime").asText(null));
        row.put("endTime", n.path("endTime").asText(null));
        row.put("host", n.path("host").asText(null));
        row.put("logPath", n.path("logPath").asText(null));
        return row;
    }

    public String queryTaskLog(long taskInstanceId, int skipLineNum, int limit) {
        String url = base() + "/log/detail?taskInstanceId=" + taskInstanceId
                + "&skipLineNum=" + Math.max(0, skipLineNum)
                + "&limit=" + Math.min(Math.max(limit, 1), 5000);
        JsonNode root = requireGet(url, "查询 DS 任务日志");
        JsonNode data = root.path("data");
        if (data.isTextual()) {
            return data.asText("");
        }
        String message = data.path("message").asText(null);
        if (message != null) {
            return message;
        }
        return data.toString();
    }

    public Map<String, Object> clusterSnapshot() {
        Map<String, Object> out = new HashMap<>();
        List<Map<String, Object>> masters = new ArrayList<>();
        List<Map<String, Object>> workers = new ArrayList<>();
        try {
            JsonNode root = requireGet(base() + "/monitor/masters", "查询 DS Master");
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode n : data) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("host", n.path("host").asText(n.path("server").path("host").asText("")));
                    row.put("port", n.path("port").asInt(n.path("server").path("port").asInt(0)));
                    row.put("resInfo", n.path("resInfo").asText(n.path("server").path("resInfo").asText("")));
                    row.put("createTime", n.path("createTime").asText(null));
                    row.put("lastHeartbeatTime", n.path("lastHeartbeatTime").asText(null));
                    masters.add(row);
                }
            }
        } catch (Exception e) {
            log.warn("查询 DS Master 失败: {}", e.getMessage());
        }
        try {
            JsonNode root = requireGet(base() + "/monitor/workers", "查询 DS Worker");
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode n : data) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("host", n.path("host").asText(n.path("server").path("host").asText("")));
                    row.put("port", n.path("port").asInt(n.path("server").path("port").asInt(0)));
                    row.put("resInfo", n.path("resInfo").asText(n.path("server").path("resInfo").asText("")));
                    row.put("workerGroups", n.path("workerGroups").asText(null));
                    row.put("lastHeartbeatTime", n.path("lastHeartbeatTime").asText(null));
                    workers.add(row);
                }
            }
        } catch (Exception e) {
            log.warn("查询 DS Worker 失败: {}", e.getMessage());
        }
        out.put("masters", masters);
        out.put("workers", workers);
        StringBuilder text = new StringBuilder();
        text.append("=== Master ===\n");
        if (masters.isEmpty()) {
            text.append("(无)\n");
        } else {
            for (Map<String, Object> m : masters) {
                text.append(m.get("host")).append(':').append(m.get("port"))
                        .append(" res=").append(m.get("resInfo"))
                        .append(" heartbeat=").append(m.get("lastHeartbeatTime")).append('\n');
            }
        }
        text.append("=== Worker ===\n");
        if (workers.isEmpty()) {
            text.append("(无)\n");
        } else {
            for (Map<String, Object> w : workers) {
                text.append(w.get("host")).append(':').append(w.get("port"))
                        .append(" res=").append(w.get("resInfo"))
                        .append(" groups=").append(w.get("workerGroups"))
                        .append(" heartbeat=").append(w.get("lastHeartbeatTime")).append('\n');
            }
        }
        out.put("text", text.toString());
        return out;
    }

    public static String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "MEDIUM";
        }
        String p = priority.trim().toUpperCase(Locale.ROOT);
        return switch (p) {
            case "HIGHEST", "HIGH", "MEDIUM", "LOW" -> p;
            default -> "MEDIUM";
        };
    }

    private Map<String, Object> execute(long projectCode, long instanceId, String executeType, String action) {
        String url = base() + "/projects/" + projectCode + "/executors/execute";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("processInstanceId", String.valueOf(instanceId));
        form.add("executeType", executeType);
        requirePost(url, form, action);
        Map<String, Object> out = new HashMap<>();
        out.put("instanceId", instanceId);
        out.put("executeType", executeType);
        out.put("status", "SUCCESS");
        return out;
    }

    // ---------- 定时调度 ----------

    public int createAndOnlineSchedule(long projectCode, long definitionCode, String cronExpr) {
        return createAndOnlineSchedule(projectCode, definitionCode, cronExpr, "MEDIUM");
    }

    public int createAndOnlineSchedule(long projectCode, long definitionCode, String cronExpr, String priority) {
        IntegrationConfig.requireIntegration(props, "DolphinScheduler");
        String crontab = toDsCrontab(cronExpr);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String start = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end = now.plusYears(10).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String scheduleJson = "{\"startTime\":\"" + start + "\",\"endTime\":\"" + end
                + "\",\"timezoneId\":\"Asia/Shanghai\",\"crontab\":\"" + escapeJson(crontab) + "\"}";
        String url = base() + "/projects/" + projectCode + "/schedules";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("processDefinitionCode", String.valueOf(definitionCode));
        form.add("schedule", scheduleJson);
        form.add("warningType", "NONE");
        form.add("warningGroupId", "0");
        form.add("failureStrategy", "END");
        form.add("processInstancePriority", normalizePriority(priority));
        form.add("workerGroup", "default");
        form.add("tenantCode", resolveTenant());
        form.add("environmentCode", "-1");
        JsonNode root = requirePost(url, form, "创建 DS 调度");
        int scheduleId = root.path("data").path("id").asInt();
        if (scheduleId <= 0) {
            scheduleId = root.path("data").asInt();
        }
        if (scheduleId <= 0) {
            throw new BusinessException(502, "创建 DS 调度未返回 id: " + trim(root.toString()));
        }
        onlineSchedule(projectCode, scheduleId);
        return scheduleId;
    }

    public void onlineSchedule(long projectCode, int scheduleId) {
        String url = base() + "/projects/" + projectCode + "/schedules/" + scheduleId + "/online";
        requirePost(url, new LinkedMultiValueMap<>(), "上线 DS 调度");
    }

    public void offlineSchedule(long projectCode, int scheduleId) {
        String url = base() + "/projects/" + projectCode + "/schedules/" + scheduleId + "/offline";
        requirePost(url, new LinkedMultiValueMap<>(), "下线 DS 调度");
    }

    /** 删除流程定义（下线后）。 */
    public void deleteDefinition(long projectCode, long definitionCode) {
        String url = base() + "/projects/" + projectCode + "/process-definition/" + definitionCode;
        requireDelete(url, "删除 DS 流程定义");
    }

    /** 将平台 Cron（常见 6 段）转为 DS Quartz 7 段。 */
    public static String toDsCrontab(String cronExpr) {
        if (cronExpr == null || cronExpr.isBlank()) {
            throw new BusinessException(400, "Cron 表达式不能为空");
        }
        String[] parts = cronExpr.trim().split("\\s+");
        if (parts.length == 6) {
            return cronExpr.trim() + " *";
        }
        if (parts.length == 7) {
            return cronExpr.trim();
        }
        if (parts.length == 5) {
            return "0 " + cronExpr.trim() + " *";
        }
        throw new BusinessException(400, "不支持的 Cron 格式: " + cronExpr);
    }

    /** DS 实例 state 映射为平台采集 run 状态。 */
    public static String mapDsStateToRunStatus(String dsState) {
        if (dsState == null) return "RUNNING";
        return switch (dsState.toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "FORCED_SUCCESS" -> "SUCCESS";
            case "FAILURE", "FAILED" -> "FAILED";
            case "STOP", "KILL", "READY_STOP" -> "STOPPED";
            case "RUNNING_EXECUTION", "SUBMITTED_SUCCESS", "DELAY_EXECUTION" -> "RUNNING";
            default -> "RUNNING";
        };
    }

    // ---------- 兼容旧接口（分析域调度台账） ----------

    public List<Map<String, Object>> listWorkflows() {
        IntegrationConfig.requireIntegration(props, "DolphinScheduler");
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            String url = base() + "/projects?pageNo=1&pageSize=100";
            JsonNode root = requireGet(url, "查询 DS 项目");
            JsonNode projects = root.path("data").path("totalList");
            if (projects.isArray()) {
                for (JsonNode p : projects) {
                    long code = p.path("code").asLong();
                    String defUrl = base() + "/projects/" + code + "/process-definition?pageNo=1&pageSize=50";
                    JsonNode defRoot = requireGet(defUrl, "查询 DS 流程定义");
                    JsonNode defs = defRoot.path("data").path("totalList");
                    if (defs.isArray()) {
                        for (JsonNode n : defs) {
                            Map<String, Object> row = new HashMap<>();
                            row.put("id", n.path("code").asLong());
                            row.put("workflowCode", n.path("code").asText());
                            row.put("workflowName", n.path("name").asText());
                            row.put("projectCode", code);
                            row.put("status", n.path("releaseState").asText());
                            out.add(row);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("listWorkflows 失败: {}", e.getMessage());
        }
        return out;
    }

    /** 兼容旧接口：definitionCode 已知时在其所属项目启动。需 projectCode，通过 listWorkflows 反查。 */
    public Map<String, Object> startWorkflow(Long definitionCode) {
        IntegrationConfig.requireIntegration(props, "DolphinScheduler");
        Long projectCode = null;
        for (Map<String, Object> wf : listWorkflows()) {
            if (String.valueOf(wf.get("id")).equals(String.valueOf(definitionCode))) {
                projectCode = Long.valueOf(String.valueOf(wf.get("projectCode")));
                break;
            }
        }
        if (projectCode == null) {
            throw new BusinessException(404, "未找到流程定义 " + definitionCode + " 所属项目");
        }
        long instanceId = startInstance(projectCode, definitionCode);
        Map<String, Object> out = new HashMap<>();
        out.put("definitionCode", definitionCode);
        out.put("projectCode", projectCode);
        out.put("instanceId", instanceId);
        out.put("status", "SUCCESS");
        return out;
    }

    // ---------- 底层 HTTP / 鉴权 ----------

    private long[] genTaskCodes(long projectCode, int num) {
        String url = base() + "/projects/" + projectCode + "/task-definition/gen-task-codes?genNum=" + num;
        JsonNode root = requireGet(url, "生成 DS 任务码");
        JsonNode data = root.path("data");
        if (!data.isArray() || data.size() < num) {
            throw new BusinessException(502, "生成 DS 任务码数量不足");
        }
        long[] codes = new long[num];
        for (int i = 0; i < num; i++) {
            codes[i] = data.get(i).asLong();
        }
        return codes;
    }

    private String base() {
        String url = props.getDs().getUrl();
        if (url == null || url.isBlank()) {
            return "http://127.0.0.1:12345/dolphinscheduler";
        }
        url = url.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        // Windows 上 localhost 可能走 IPv6，统一成 127.0.0.1 更稳
        if (url.contains("://localhost:") || url.contains("://localhost/")) {
            url = url.replace("://localhost", "://127.0.0.1");
        }
        return url;
    }

    private void ensureSession() {
        if (props.getDs().getToken() != null && !props.getDs().getToken().isBlank()) {
            return;
        }
        if (sessionId != null) {
            return;
        }
        loginSession();
    }

    private void loginSession() {
        String url = base() + "/login?userName=" + enc(props.getDs().getUser())
                + "&userPassword=" + enc(props.getDs().getPassword());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.POST, new HttpEntity<>(h), String.class);
            JsonNode node = mapper.readTree(res.getBody());
            String sid = node.path("data").path("sessionId").asText(null);
            if (sid == null || sid.isBlank()) {
                throw new BusinessException(502, "DS 登录未返回 sessionId：" + trim(res.getBody()));
            }
            sessionId = sid;
            log.info("DS login ok user={}", props.getDs().getUser());
        } catch (BusinessException be) {
            sessionId = null;
            throw be;
        } catch (Exception e) {
            sessionId = null;
            throw new BusinessException(502, "DS 登录失败: " + e.getMessage());
        }
    }

    private void invalidateSession() {
        sessionId = null;
    }

    private HttpHeaders authHeaders(MediaType contentType) {
        ensureSession();
        HttpHeaders h = new HttpHeaders();
        if (contentType != null) {
            h.setContentType(contentType);
        }
        String token = props.getDs().getToken();
        if (token != null && !token.isBlank()) {
            h.add("token", token);
        } else if (sessionId != null) {
            h.add("sessionId", sessionId);
            h.add(HttpHeaders.COOKIE, "sessionId=" + sessionId);
        }
        return h;
    }

    private JsonNode requireGet(String url, String action) {
        try {
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(authHeaders(null)), String.class);
            return requireOk(res.getBody(), action);
        } catch (BusinessException be) {
            if (isSessionExpired(be) && (props.getDs().getToken() == null || props.getDs().getToken().isBlank())) {
                invalidateSession();
                try {
                    ResponseEntity<String> res = rest.exchange(url, HttpMethod.GET,
                            new HttpEntity<>(authHeaders(null)), String.class);
                    return requireOk(res.getBody(), action);
                } catch (BusinessException be2) {
                    throw be2;
                } catch (Exception e2) {
                    throw new BusinessException(502, action + "失败: " + e2.getMessage());
                }
            }
            throw be;
        } catch (Exception e) {
            throw new BusinessException(502, action + "失败: " + e.getMessage());
        }
    }

    private boolean isSessionExpired(BusinessException be) {
        String m = be.getMessage() == null ? "" : be.getMessage();
        return m.contains("session") || m.contains("登录") || m.contains("401") || m.contains("未登录");
    }

    private JsonNode requirePost(String url, MultiValueMap<String, String> form, String action) {
        try {
            HttpEntity<MultiValueMap<String, String>> req =
                    new HttpEntity<>(form, authHeaders(MediaType.APPLICATION_FORM_URLENCODED));
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.POST, req, String.class);
            return requireOk(res.getBody(), action);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(502, action + "失败: " + e.getMessage());
        }
    }

    private void requireDelete(String url, String action) {
        try {
            ResponseEntity<String> res = rest.exchange(url, HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(null)), String.class);
            requireOk(res.getBody(), action);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(502, action + "失败: " + e.getMessage());
        }
    }

    private JsonNode requireOk(String body, String action) {
        try {
            JsonNode root = mapper.readTree(body);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                throw new BusinessException(502, action + "失败: " + root.path("msg").asText(trim(body)));
            }
            return root;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(502, action + "响应解析失败: " + trim(body));
        }
    }

    private long extractCode(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) {
            return 0L;
        }
        if (data.isNumber()) {
            return data.asLong();
        }
        long code = data.path("code").asLong(0L);
        if (code > 0) {
            return code;
        }
        return data.path("projectCode").asLong(0L);
    }

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String trim(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
