package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.ds.DolphinSchedulerClient;
import com.chengde.smartcity.masterdata.entity.GovCrossPipeline;
import com.chengde.smartcity.masterdata.entity.GovWorkflowAlertChannel;
import com.chengde.smartcity.masterdata.entity.GovWorkflowAlertLog;
import com.chengde.smartcity.masterdata.mapper.GovCrossPipelineMapper;
import com.chengde.smartcity.masterdata.mapper.GovWorkflowAlertChannelMapper;
import com.chengde.smartcity.masterdata.mapper.GovWorkflowAlertLogMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.service.MailConfigService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrossModulePipelineMonitorService {

    private static final Logger log = LoggerFactory.getLogger(CrossModulePipelineMonitorService.class);
    private static final String DS_PROJECT = "chengde_cross_pipeline";
    private static final long CHANNEL_ID = 1L;
    private static final DateTimeFormatter DS_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> DONE = Set.of("SUCCESS", "FORCED_SUCCESS");
    private static final Set<String> RUNNING = Set.of(
            "RUNNING_EXECUTION", "SUBMITTED_SUCCESS", "ACCEPT", "DELAY_EXECUTION", "SERIAL_WAIT", "READY_PAUSE");
    private static final Set<String> WAITING = Set.of(
            "WAITING_THREAD", "WAITING_DEPEND", "WAITING_DEPENDENCY", "PAUSE", "READY_STOP");
    private static final Set<String> FAILED = Set.of("FAILURE", "FAILED", "STOP", "KILL");

    private final GovCrossPipelineMapper pipelineMapper;
    private final GovWorkflowAlertChannelMapper channelMapper;
    private final GovWorkflowAlertLogMapper alertLogMapper;
    private final DolphinSchedulerClient dsClient;
    private final IntegrationProperties integrationProperties;
    private final MailConfigService mailConfigService;

    public CrossModulePipelineMonitorService(GovCrossPipelineMapper pipelineMapper,
                                             GovWorkflowAlertChannelMapper channelMapper,
                                             GovWorkflowAlertLogMapper alertLogMapper,
                                             DolphinSchedulerClient dsClient,
                                             IntegrationProperties integrationProperties,
                                             MailConfigService mailConfigService) {
        this.pipelineMapper = pipelineMapper;
        this.channelMapper = channelMapper;
        this.alertLogMapper = alertLogMapper;
        this.dsClient = dsClient;
        this.integrationProperties = integrationProperties;
        this.mailConfigService = mailConfigService;
    }

    public Map<String, Object> todayOverview() {
        List<GovCrossPipeline> published = listPublished();
        int publishedCount = published.size();
        int dsCompleted = 0;
        int dsRunning = 0;
        int dsWaiting = 0;
        int dsFailed = 0;
        int dsRows = 0;
        boolean dsOk = isDsAvailable();
        String message = null;
        Long projectCode = null;
        long durationSumMs = 0L;
        int durationCount = 0;

        if (dsOk) {
            try {
                projectCode = resolveProjectCode();
                LocalDate today = LocalDate.now();
                String start = today.atStartOfDay().format(DS_DT);
                String end = today.atTime(LocalTime.of(23, 59, 59)).format(DS_DT);
                Map<String, Object> page = dsClient.listProcessInstances(projectCode, 1, 100, start, end, null, null);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rows = (List<Map<String, Object>>) page.getOrDefault("totalList", List.of());
                dsRows = rows.size();
                for (Map<String, Object> row : rows) {
                    String bucket = bucket(str(row.get("state")));
                    switch (bucket) {
                        case "COMPLETED" -> dsCompleted++;
                        case "RUNNING" -> dsRunning++;
                        case "WAITING" -> dsWaiting++;
                        case "FAILED" -> dsFailed++;
                        default -> dsWaiting++;
                    }
                    Long ms = parseDurationMs(row);
                    if (ms != null && ms > 0 && "COMPLETED".equals(bucket)) {
                        durationSumMs += ms;
                        durationCount++;
                    }
                }
            } catch (BusinessException e) {
                dsOk = false;
                message = e.getMessage();
            } catch (Exception e) {
                log.warn("todayOverview failed: {}", e.getMessage());
                dsOk = false;
                message = "拉取 DS 实例失败: " + e.getMessage();
            }
        } else {
            message = "DolphinScheduler 不可用，已展示本地已发布流水线";
        }

        int planned = estimatePlannedToday();
        int waitingPublished = Math.max(0, publishedCount - dsRows);
        int waiting = dsWaiting + waitingPublished;
        int totalExpected = Math.max(publishedCount, Math.max(planned, dsRows));
        if (totalExpected == 0) {
            totalExpected = publishedCount;
        }

        long avgMs = durationCount > 0 ? durationSumMs / durationCount : 15 * 60_000L;
        int remain = waiting + dsRunning;
        LocalDateTime eta = remain > 0
                ? LocalDateTime.now().plus(Duration.ofMillis(avgMs * Math.max(remain, 0)))
                : null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", LocalDate.now().toString());
        out.put("totalExpected", totalExpected);
        out.put("completed", dsCompleted);
        out.put("running", dsRunning);
        out.put("waiting", waiting);
        out.put("failed", dsFailed);
        out.put("publishedCount", publishedCount);
        out.put("avgDurationMs", avgMs);
        out.put("estimatedFinishAt", eta == null ? null : eta.format(DS_DT));
        out.put("dsAvailable", dsOk);
        out.put("projectCode", projectCode);
        if (message != null) {
            out.put("message", message);
        } else if (dsRows == 0 && publishedCount > 0) {
            out.put("message", "已发布 " + publishedCount + " 条流水线，今日尚无运行实例（发布不会自动执行，可点「执行」或启动定时）");
        }
        return out;
    }

    public Map<String, Object> listInstances(String keyword, String stateType, String priority,
                                             int pageNo, int pageSize) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        Set<Long> seenDefs = new HashSet<>();
        boolean dsOk = isDsAvailable();
        String message = null;
        Long projectCode = null;

        if (dsOk) {
            try {
                projectCode = resolveProjectCode();
                LocalDate today = LocalDate.now();
                String start = today.minusDays(7).atStartOfDay().format(DS_DT);
                String end = today.atTime(LocalTime.of(23, 59, 59)).format(DS_DT);
                Map<String, Object> page = dsClient.listProcessInstances(
                        projectCode, 1, 100, start, end, blankToNull(keyword), blankToNull(stateType));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> raw = (List<Map<String, Object>>) page.getOrDefault("totalList", List.of());
                if (raw.isEmpty()) {
                    page = dsClient.listProcessInstances(
                            projectCode, 1, 100, null, null, blankToNull(keyword), blankToNull(stateType));
                    raw = (List<Map<String, Object>>) page.getOrDefault("totalList", List.of());
                }
                Map<Long, GovCrossPipeline> byDef = indexByDefinition();
                for (Map<String, Object> row : raw) {
                    long defCode = toLong(row.get("processDefinitionCode"));
                    if (defCode > 0) {
                        seenDefs.add(defCode);
                    }
                    GovCrossPipeline p = byDef.get(defCode);
                    String pipePriority = p == null || p.getPriority() == null ? "MEDIUM" : p.getPriority();
                    String dsPriority = str(row.get("processInstancePriority"));
                    if (dsPriority == null || dsPriority.isBlank()) {
                        dsPriority = pipePriority;
                    }
                    if (priority != null && !priority.isBlank()
                            && !priority.equalsIgnoreCase(dsPriority)
                            && !priority.equalsIgnoreCase(pipePriority)) {
                        continue;
                    }
                    Map<String, Object> m = new LinkedHashMap<>(row);
                    m.put("pipelineId", p == null ? null : p.getId());
                    m.put("pipelineName", p == null ? str(row.get("name")) : p.getPipelineName());
                    m.put("priority", DolphinSchedulerClient.normalizePriority(dsPriority));
                    m.put("projectCode", projectCode);
                    m.put("stateBucket", bucket(str(row.get("state"))));
                    m.put("virtual", false);
                    enriched.add(m);
                }
            } catch (BusinessException e) {
                dsOk = false;
                message = e.getMessage();
            } catch (Exception e) {
                log.warn("listInstances failed: {}", e.getMessage());
                dsOk = false;
                message = "查询 DS 实例失败: " + e.getMessage();
            }
        } else {
            message = "DolphinScheduler 不可用，已展示本地已发布流水线";
        }

        boolean wantWaiting = stateType == null || stateType.isBlank()
                || "WAITING".equalsIgnoreCase(stateType)
                || "WAITING_DEPEND".equalsIgnoreCase(stateType);
        if (wantWaiting) {
            for (GovCrossPipeline p : listPublished()) {
                if (p.getDsDefinitionCode() != null && seenDefs.contains(p.getDsDefinitionCode())) {
                    continue;
                }
                if (keyword != null && !keyword.isBlank()) {
                    String hay = ((p.getPipelineName() == null ? "" : p.getPipelineName())
                            + " " + (p.getLastMessage() == null ? "" : p.getLastMessage())).toLowerCase(Locale.ROOT);
                    if (!hay.contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                }
                String pipePriority = p.getPriority() == null ? "MEDIUM" : p.getPriority();
                if (priority != null && !priority.isBlank() && !priority.equalsIgnoreCase(pipePriority)) {
                    continue;
                }
                enriched.add(virtualWaitingRow(p, projectCode));
            }
        }

        enriched.sort((a, b) -> Integer.compare(
                priorityRank(str(b.get("priority"))),
                priorityRank(str(a.get("priority")))));
        int total = enriched.size();
        int from = Math.max(0, (Math.max(1, pageNo) - 1) * Math.max(1, pageSize));
        int to = Math.min(total, from + Math.max(1, pageSize));
        List<Map<String, Object>> slice = from >= total ? List.of() : enriched.subList(from, to);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("records", slice);
        out.put("dsAvailable", dsOk);
        out.put("projectCode", projectCode);
        if (message != null) {
            out.put("message", message);
        }
        return out;
    }

    @Transactional
    public void setPriority(UserPrincipal operator, Long pipelineId, String priority) {
        GovCrossPipeline p = requirePipeline(pipelineId);
        if ("RUNNING".equals(p.getScheduleStatus())) {
            throw new BusinessException(400, "请先停止定时再调整优先级");
        }
        p.setPriority(DolphinSchedulerClient.normalizePriority(priority));
        p.setUpdatedAt(LocalDateTime.now());
        p.setLastMessage("优先级已设为 " + p.getPriority() + "（重新发布后对定时生效）");
        pipelineMapper.updateById(p);
    }

    public Map<String, Object> instanceLogs(Long projectCode, Long instanceId, String logType) {
        if (instanceId == null || instanceId <= 0) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("logType", logType);
            out.put("content", "该流水线已发布但尚未产生运行实例。请到「跨模块流水线」点击「执行」，或配置周期后启动定时。");
            out.put("tasks", List.of());
            return out;
        }
        requireDs();
        long pc = projectCode == null ? resolveProjectCode() : projectCode;
        String type = logType == null ? "PROCESS" : logType.trim().toUpperCase(Locale.ROOT);
        if ("CLUSTER".equals(type)) {
            Map<String, Object> cluster = dsClient.clusterSnapshot();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("logType", "CLUSTER");
            out.put("content", cluster.get("text"));
            out.put("masters", cluster.get("masters"));
            out.put("workers", cluster.get("workers"));
            return out;
        }
        List<Map<String, Object>> tasks = dsClient.listTaskInstances(pc, instanceId);
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> t : tasks) {
            String state = str(t.get("state"));
            boolean failed = FAILED.contains(state == null ? "" : state.toUpperCase(Locale.ROOT));
            if ("ERROR".equals(type) && !failed) {
                continue;
            }
            long taskId = toLong(t.get("id"));
            sb.append("----- ").append(t.get("name")).append(" [").append(state).append("] -----\n");
            if (taskId <= 0) {
                sb.append("(无任务实例 ID)\n");
                continue;
            }
            try {
                sb.append(dsClient.queryTaskLog(taskId, 0, 2000)).append('\n');
            } catch (Exception e) {
                sb.append("读取日志失败: ").append(e.getMessage()).append('\n');
            }
        }
        if (sb.length() == 0) {
            sb.append("ERROR".equals(type) ? "无失败任务日志" : "暂无过程日志");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("logType", type);
        out.put("content", sb.toString());
        out.put("tasks", tasks);
        return out;
    }

    public Map<String, Object> control(UserPrincipal operator, Long projectCode, Long instanceId, String action) {
        if (instanceId == null || instanceId <= 0) {
            throw new BusinessException(400, "尚未产生运行实例，请先在「跨模块流水线」点击执行");
        }
        requireDs();
        long pc = projectCode == null ? resolveProjectCode() : projectCode;
        String act = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        return switch (act) {
            case "RETRY" -> dsClient.retryInstance(pc, instanceId);
            case "PAUSE" -> dsClient.pauseInstance(pc, instanceId);
            case "RESUME" -> dsClient.resumeInstance(pc, instanceId);
            case "STOP" -> dsClient.stopInstance(pc, instanceId);
            default -> throw new BusinessException(400, "不支持的操作: " + action);
        };
    }

    public Map<String, Object> getAlertChannel() {
        return toChannelView(requireChannel());
    }

    @Transactional
    public void saveAlertChannel(UserPrincipal operator, Map<String, Object> body) {
        GovWorkflowAlertChannel c = requireChannel();
        if (body.containsKey("mailEnabled")) {
            c.setMailEnabled(truthy(body.get("mailEnabled")) ? 1 : 0);
        }
        if (body.containsKey("mailReceivers")) {
            c.setMailReceivers(str(body.get("mailReceivers")));
        }
        if (body.containsKey("smsEnabled")) {
            c.setSmsEnabled(truthy(body.get("smsEnabled")) ? 1 : 0);
        }
        if (body.containsKey("smsPhones")) {
            c.setSmsPhones(str(body.get("smsPhones")));
        }
        if (body.containsKey("smsGatewayUrl")) {
            c.setSmsGatewayUrl(str(body.get("smsGatewayUrl")));
        }
        if (body.containsKey("smsSignName")) {
            c.setSmsSignName(str(body.get("smsSignName")));
        }
        if (body.containsKey("smsTemplateCode")) {
            c.setSmsTemplateCode(str(body.get("smsTemplateCode")));
        }
        if (body.containsKey("ownerName")) {
            c.setOwnerName(str(body.get("ownerName")));
        }
        if (operator != null) {
            c.setUpdatedBy(operator.getUsername());
        }
        c.setUpdatedAt(LocalDateTime.now());
        channelMapper.updateById(c);
    }

    public Map<String, Object> notifyInstance(UserPrincipal operator, Long pipelineId, Long instanceId, String state) {
        GovWorkflowAlertChannel ch = requireChannel();
        String name = "实例#" + instanceId;
        if (pipelineId != null) {
            GovCrossPipeline p = pipelineMapper.selectById(pipelineId);
            if (p != null) {
                name = p.getPipelineName() + " / 实例#" + instanceId;
            }
        }
        String subject = "工作流运行告警 · " + name;
        String content = "【工作流调度告警】\n流水线实例：" + name + "\n状态：" + (state == null ? "FAILURE" : state)
                + "\n责任人：" + (ch.getOwnerName() == null ? "未指定" : ch.getOwnerName())
                + "\n请登录「工作流调度 · 实时任务监控」查看错误/过程/集群日志。\n";
        List<Map<String, Object>> pushes = new ArrayList<>();
        if (ch.getMailEnabled() != null && ch.getMailEnabled() == 1
                && ch.getMailReceivers() != null && !ch.getMailReceivers().isBlank()) {
            for (String to : splitReceivers(ch.getMailReceivers())) {
                pushes.add(sendEmail(pipelineId, instanceId, to, subject, content));
            }
        }
        if (ch.getSmsEnabled() != null && ch.getSmsEnabled() == 1
                && ch.getSmsPhones() != null && !ch.getSmsPhones().isBlank()) {
            for (String phone : splitReceivers(ch.getSmsPhones())) {
                pushes.add(sendSmsLedger(pipelineId, instanceId, phone, subject, content));
            }
        }
        if (pushes.isEmpty()) {
            GovWorkflowAlertLog row = new GovWorkflowAlertLog();
            row.setPipelineId(pipelineId);
            row.setInstanceId(instanceId);
            row.setChannel("EMAIL");
            row.setReceivers(ch.getOwnerName());
            row.setSubject(subject);
            row.setContent(content);
            row.setStatus("LEDGER");
            row.setMessage("未启用邮件/短信通道，已记台账待配置后推送");
            row.setCreatedAt(LocalDateTime.now());
            alertLogMapper.insert(row);
            pushes.add(Map.of("channel", "LEDGER", "status", "LEDGER", "message", row.getMessage()));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pushes", pushes);
        out.put("owner", ch.getOwnerName());
        return out;
    }

    public List<Map<String, Object>> listAlertLogs(Long instanceId, Long pipelineId) {
        LambdaQueryWrapper<GovWorkflowAlertLog> q = new LambdaQueryWrapper<GovWorkflowAlertLog>()
                .orderByDesc(GovWorkflowAlertLog::getId)
                .last("LIMIT 100");
        if (instanceId != null) {
            q.eq(GovWorkflowAlertLog::getInstanceId, instanceId);
        }
        if (pipelineId != null) {
            q.eq(GovWorkflowAlertLog::getPipelineId, pipelineId);
        }
        List<GovWorkflowAlertLog> rows = alertLogMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovWorkflowAlertLog r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("pipelineId", r.getPipelineId());
            m.put("instanceId", r.getInstanceId());
            m.put("channel", r.getChannel());
            m.put("receivers", r.getReceivers());
            m.put("subject", r.getSubject());
            m.put("content", r.getContent());
            m.put("status", r.getStatus());
            m.put("message", r.getMessage());
            m.put("createdAt", r.getCreatedAt());
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> listPipelinesForPriority() {
        List<GovCrossPipeline> rows = pipelineMapper.selectList(new LambdaQueryWrapper<GovCrossPipeline>()
                .orderByDesc(GovCrossPipeline::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovCrossPipeline p : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("pipelineName", p.getPipelineName());
            m.put("priority", p.getPriority() == null ? "MEDIUM" : p.getPriority());
            m.put("publishStatus", p.getPublishStatus());
            m.put("scheduleStatus", p.getScheduleStatus());
            out.add(m);
        }
        return out;
    }

    private List<GovCrossPipeline> listPublished() {
        return pipelineMapper.selectList(new LambdaQueryWrapper<GovCrossPipeline>()
                .eq(GovCrossPipeline::getPublishStatus, "SUCCESS")
                .orderByDesc(GovCrossPipeline::getId));
    }

    private Map<String, Object> virtualWaitingRow(GovCrossPipeline p, Long projectCode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", -p.getId());
        m.put("name", p.getPipelineName());
        m.put("state", "WAITING");
        m.put("startTime", p.getLastRunAt() == null ? null : p.getLastRunAt().format(DS_DT));
        m.put("endTime", null);
        m.put("pipelineId", p.getId());
        m.put("pipelineName", p.getPipelineName());
        m.put("priority", p.getPriority() == null ? "MEDIUM" : p.getPriority());
        m.put("projectCode", projectCode != null ? projectCode : p.getDsProjectCode());
        m.put("stateBucket", "WAITING");
        m.put("virtual", true);
        m.put("lastMessage", p.getLastMessage());
        return m;
    }

    private int estimatePlannedToday() {
        List<GovCrossPipeline> running = pipelineMapper.selectList(new LambdaQueryWrapper<GovCrossPipeline>()
                .eq(GovCrossPipeline::getScheduleStatus, "RUNNING")
                .eq(GovCrossPipeline::getPublishStatus, "SUCCESS"));
        int total = 0;
        for (GovCrossPipeline p : running) {
            total += estimateCronFiresToday(p.getScheduleCron());
        }
        return total;
    }

    private int estimateCronFiresToday(String cron) {
        if (cron == null || cron.isBlank()) {
            return 0;
        }
        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 6) {
            return 1;
        }
        String hour = parts[2];
        String minute = parts[1];
        if ("*".equals(hour) && "0".equals(minute)) {
            return 24;
        }
        if ("*".equals(hour) && "*".equals(minute)) {
            return 24 * 60;
        }
        if (hour.contains("/")) {
            try {
                int step = Integer.parseInt(hour.substring(hour.indexOf('/') + 1));
                return step <= 0 ? 1 : Math.max(1, 24 / step);
            } catch (Exception e) {
                return 1;
            }
        }
        return 1;
    }

    private Map<Long, GovCrossPipeline> indexByDefinition() {
        Map<Long, GovCrossPipeline> map = new HashMap<>();
        for (GovCrossPipeline p : pipelineMapper.selectList(new LambdaQueryWrapper<>())) {
            if (p.getDsDefinitionCode() != null) {
                map.put(p.getDsDefinitionCode(), p);
            }
        }
        return map;
    }

    private long resolveProjectCode() {
        Long code = null;
        for (GovCrossPipeline p : pipelineMapper.selectList(new LambdaQueryWrapper<GovCrossPipeline>()
                .isNotNull(GovCrossPipeline::getDsProjectCode)
                .last("LIMIT 20"))) {
            if (p.getDsProjectCode() != null) {
                code = p.getDsProjectCode();
                break;
            }
        }
        if (code != null) {
            return code;
        }
        return dsClient.ensureProject(DS_PROJECT);
    }

    private void requireDs() {
        if (!isDsAvailable()) {
            throw new BusinessException(502, "DolphinScheduler 不可用");
        }
    }

    private boolean isDsAvailable() {
        return integrationProperties.isEnabled() && dsClient.isHealthy();
    }

    private Map<String, Object> emptyOverview(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", LocalDate.now().toString());
        out.put("totalExpected", 0);
        out.put("completed", 0);
        out.put("running", 0);
        out.put("waiting", 0);
        out.put("failed", 0);
        out.put("avgDurationMs", 0);
        out.put("estimatedFinishAt", null);
        out.put("dsAvailable", false);
        out.put("message", message);
        return out;
    }

    private Map<String, Object> emptyInstances(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", 0);
        out.put("records", List.of());
        out.put("dsAvailable", false);
        out.put("message", message);
        return out;
    }

    private GovCrossPipeline requirePipeline(Long id) {
        GovCrossPipeline p = pipelineMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(404, "流水线不存在");
        }
        return p;
    }

    private GovWorkflowAlertChannel requireChannel() {
        GovWorkflowAlertChannel c = channelMapper.selectById(CHANNEL_ID);
        if (c == null) {
            throw new BusinessException(500, "工作流告警通道未初始化");
        }
        return c;
    }

    private Map<String, Object> toChannelView(GovWorkflowAlertChannel c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mailEnabled", c.getMailEnabled() != null && c.getMailEnabled() == 1);
        m.put("mailReceivers", c.getMailReceivers());
        m.put("smsEnabled", c.getSmsEnabled() != null && c.getSmsEnabled() == 1);
        m.put("smsPhones", c.getSmsPhones());
        m.put("smsGatewayUrl", c.getSmsGatewayUrl());
        m.put("smsSignName", c.getSmsSignName());
        m.put("smsTemplateCode", c.getSmsTemplateCode());
        m.put("ownerName", c.getOwnerName());
        m.put("updatedBy", c.getUpdatedBy());
        m.put("updatedAt", c.getUpdatedAt());
        return m;
    }

    private Map<String, Object> sendEmail(Long pipelineId, Long instanceId, String to, String subject, String content) {
        GovWorkflowAlertLog row = new GovWorkflowAlertLog();
        row.setPipelineId(pipelineId);
        row.setInstanceId(instanceId);
        row.setChannel("EMAIL");
        row.setReceivers(to);
        row.setSubject(subject);
        row.setContent(content);
        row.setCreatedAt(LocalDateTime.now());
        try {
            mailConfigService.send(to, subject, content);
            row.setStatus("SUCCESS");
            row.setMessage("邮件已发送");
        } catch (Exception e) {
            log.warn("workflow alert mail failed to={}: {}", to, e.getMessage());
            row.setStatus("FAILED");
            row.setMessage(truncate(e.getMessage(), 400));
        }
        alertLogMapper.insert(row);
        return Map.of("channel", "EMAIL", "receivers", to, "status", row.getStatus(), "message", row.getMessage());
    }

    private Map<String, Object> sendSmsLedger(Long pipelineId, Long instanceId, String phone,
                                              String subject, String content) {
        GovWorkflowAlertLog row = new GovWorkflowAlertLog();
        row.setPipelineId(pipelineId);
        row.setInstanceId(instanceId);
        row.setChannel("SMS");
        row.setReceivers(phone);
        row.setSubject(subject);
        row.setContent(truncate(content, 500));
        row.setStatus("LEDGER");
        row.setMessage("短信通道按工程约束记台账（未直连真实短信网关）");
        row.setCreatedAt(LocalDateTime.now());
        alertLogMapper.insert(row);
        return Map.of("channel", "SMS", "receivers", phone, "status", "LEDGER", "message", row.getMessage());
    }

    private static String bucket(String state) {
        if (state == null || state.isBlank()) {
            return "WAITING";
        }
        String s = state.toUpperCase(Locale.ROOT);
        if (DONE.contains(s)) return "COMPLETED";
        if (FAILED.contains(s)) return "FAILED";
        if (RUNNING.contains(s)) return "RUNNING";
        if (WAITING.contains(s)) return "WAITING";
        if (s.contains("SUCCESS")) return "COMPLETED";
        if (s.contains("FAIL") || s.contains("KILL") || s.contains("STOP")) return "FAILED";
        if (s.contains("RUN")) return "RUNNING";
        return "WAITING";
    }

    private static int priorityRank(String p) {
        return switch (DolphinSchedulerClient.normalizePriority(p)) {
            case "HIGHEST" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private static Long parseDurationMs(Map<String, Object> row) {
        Object duration = row.get("duration");
        if (duration instanceof Number n) {
            return n.longValue();
        }
        String start = str(row.get("startTime"));
        String end = str(row.get("endTime"));
        if (start == null || end == null || start.isBlank() || end.isBlank() || "null".equalsIgnoreCase(end)) {
            return null;
        }
        try {
            LocalDateTime s = LocalDateTime.parse(start.replace('T', ' ').substring(0, 19), DS_DT);
            LocalDateTime e = LocalDateTime.parse(end.replace('T', ' ').substring(0, 19), DS_DT);
            return Duration.between(s, e).toMillis();
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> splitReceivers(String raw) {
        String[] parts = raw.split("[;；,，\\s]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                out.add(p.trim());
            }
        }
        return out;
    }

    private static boolean truthy(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return v != null && List.of("1", "true", "TRUE", "yes", "YES").contains(String.valueOf(v));
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
