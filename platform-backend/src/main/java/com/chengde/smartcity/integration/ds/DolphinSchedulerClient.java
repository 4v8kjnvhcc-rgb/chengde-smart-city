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
        try {
            rest.getForEntity(props.getDs().getUrl() + "/actuator/health", String.class);
            return true;
        } catch (Exception e) {
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
        String url = base() + "/projects/" + projectCode + "/process-definition/" + definitionCode + "/release";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", "");
        form.add("releaseState", state);
        requirePost(url, form, "上线 DS 流程定义");
    }

    // ---------- 实例 ----------

    /** 启动流程实例，返回真实 processInstanceId（轮询最近实例获取）。 */
    public long startInstance(long projectCode, long definitionCode) {
        String url = base() + "/projects/" + projectCode + "/executors/start-process-instance";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("processDefinitionCode", String.valueOf(definitionCode));
        // DS 3.2.2 必填；空串表示立即执行（非补数）
        form.add("scheduleTime", "");
        form.add("failureStrategy", "END");
        form.add("warningType", "NONE");
        form.add("warningGroupId", "0");
        form.add("execType", "START_PROCESS");
        form.add("startNodeList", "");
        form.add("taskDependType", "TASK_POST");
        form.add("runMode", "RUN_MODE_SERIAL");
        form.add("processInstancePriority", "MEDIUM");
        form.add("workerGroup", "default");
        form.add("tenantCode", resolveTenant());
        form.add("environmentCode", "-1");
        form.add("dryRun", "0");
        form.add("testFlag", "0");
        requirePost(url, form, "启动 DS 流程实例");
        return waitForLatestInstanceId(projectCode, definitionCode);
    }

    /** DS start 返回的 data 是 triggerCode，不是 processInstanceId；轮询直到实例出现。 */
    private long waitForLatestInstanceId(long projectCode, long definitionCode) {
        for (int i = 0; i < 30; i++) {
            try {
                return latestInstanceId(projectCode, definitionCode);
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

    private long latestInstanceId(long projectCode, long definitionCode) {
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

    /** 失败重跑（从失败任务恢复）。 */
    public Map<String, Object> retryInstance(long projectCode, long instanceId) {
        return execute(projectCode, instanceId, "START_FAILURE_TASK_PROCESS", "重跑 DS 实例");
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
        return props.getDs().getUrl();
    }

    private void ensureSession() {
        if (props.getDs().getToken() != null && !props.getDs().getToken().isBlank()) {
            return;
        }
        if (sessionId != null) {
            return;
        }
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
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(502, "DS 登录失败: " + e.getMessage());
        }
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
            throw be;
        } catch (Exception e) {
            throw new BusinessException(502, action + "失败: " + e.getMessage());
        }
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
