package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.masterdata.entity.GovQualityAlertChannel;
import com.chengde.smartcity.masterdata.entity.GovQualityAlertLog;
import com.chengde.smartcity.masterdata.entity.GovQualityIssue;
import com.chengde.smartcity.masterdata.entity.GovQualityTask;
import com.chengde.smartcity.masterdata.mapper.GovQualityAlertChannelMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityAlertLogMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityIssueMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.service.MailConfigService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 质量告警：通道配置 + 稽核异常邮件/短信推送台账（邮件走系统 SMTP，短信 LEDGER 诚实落库）。
 */
@Service
public class QualityAlertService {

    private static final Logger log = LoggerFactory.getLogger(QualityAlertService.class);
    private static final long CHANNEL_ID = 1L;

    private final GovQualityAlertChannelMapper channelMapper;
    private final GovQualityAlertLogMapper alertLogMapper;
    private final GovQualityIssueMapper issueMapper;
    private final GovQualityTaskMapper taskMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final MailConfigService mailConfigService;

    public QualityAlertService(GovQualityAlertChannelMapper channelMapper,
                               GovQualityAlertLogMapper alertLogMapper,
                               GovQualityIssueMapper issueMapper,
                               GovQualityTaskMapper taskMapper,
                               IngDataSourceMapper dataSourceMapper,
                               MailConfigService mailConfigService) {
        this.channelMapper = channelMapper;
        this.alertLogMapper = alertLogMapper;
        this.issueMapper = issueMapper;
        this.taskMapper = taskMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.mailConfigService = mailConfigService;
    }

    public Map<String, Object> getChannel() {
        return toChannelView(requireChannel());
    }

    @Transactional
    public void saveChannel(UserPrincipal operator, Map<String, Object> body) {
        GovQualityAlertChannel c = requireChannel();
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

    /**
     * 稽核完成后推送：有异常才告警；邮件尽量真实发送，短信写 LEDGER 台账。
     */
    public Map<String, Object> notifyAfterRun(Long schemeId, Long taskId, Long runId, String taskName) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<GovQualityIssue> issues = issueMapper.selectList(new LambdaQueryWrapper<GovQualityIssue>()
                .eq(GovQualityIssue::getRunId, runId)
                .orderByDesc(GovQualityIssue::getId)
                .last("LIMIT 50"));
        out.put("issueCount", issues.size());
        if (issues.isEmpty()) {
            out.put("skipped", true);
            out.put("message", "无异常问题，跳过告警推送");
            return out;
        }

        GovQualityAlertChannel ch = requireChannel();
        String dbLabel = resolveDbLabel(taskId);
        StringBuilder body = new StringBuilder();
        body.append("【数据质量异常告警】\n");
        body.append("任务：").append(taskName == null ? ("#" + taskId) : taskName).append('\n');
        body.append("运行ID：").append(runId).append('\n');
        body.append("责任人：").append(ch.getOwnerName() == null ? "未指定" : ch.getOwnerName()).append('\n');
        body.append("问题数：").append(issues.size()).append('\n');
        body.append("定位明细（库/表/字段/值）：\n");
        int n = 0;
        for (GovQualityIssue iss : issues) {
            if (n++ >= 20) {
                body.append("…其余 ").append(issues.size() - 20).append(" 条见平台监控\n");
                break;
            }
            body.append("- 库=").append(dbLabel)
                    .append(" 表=").append(nullToDash(iss.getTargetTable()))
                    .append(" 字段=").append(nullToDash(iss.getTargetColumn()))
                    .append(" 值=").append(nullToDash(firstNonBlank(iss.getIssueValue(), iss.getSampleData())))
                    .append(" 类型=").append(nullToDash(iss.getCheckType()))
                    .append('\n');
        }
        body.append("请登录「数据质量监控」查看校验日志并及时整改。\n");
        String content = body.toString();
        String subject = "数据质量异常告警 · " + (taskName == null ? ("运行#" + runId) : taskName);

        List<Map<String, Object>> pushes = new ArrayList<>();
        if (ch.getMailEnabled() != null && ch.getMailEnabled() == 1
                && ch.getMailReceivers() != null && !ch.getMailReceivers().isBlank()) {
            for (String to : splitReceivers(ch.getMailReceivers())) {
                pushes.add(sendEmail(schemeId, taskId, runId, to, subject, content));
            }
        }
        if (ch.getSmsEnabled() != null && ch.getSmsEnabled() == 1
                && ch.getSmsPhones() != null && !ch.getSmsPhones().isBlank()) {
            for (String phone : splitReceivers(ch.getSmsPhones())) {
                pushes.add(sendSmsLedger(schemeId, taskId, runId, phone, subject, content));
            }
        }
        if (pushes.isEmpty()) {
            // 未配置通道时仍落一条台账，便于监控页追溯「应告警」
            GovQualityAlertLog row = new GovQualityAlertLog();
            row.setSchemeId(schemeId);
            row.setTaskId(taskId);
            row.setRunId(runId);
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
        out.put("pushes", pushes);
        out.put("owner", ch.getOwnerName());
        return out;
    }

    public List<Map<String, Object>> listAlertLogs(Long runId, Long schemeId) {
        LambdaQueryWrapper<GovQualityAlertLog> q = new LambdaQueryWrapper<GovQualityAlertLog>()
                .orderByDesc(GovQualityAlertLog::getId)
                .last("LIMIT 100");
        if (runId != null) {
            q.eq(GovQualityAlertLog::getRunId, runId);
        }
        if (schemeId != null) {
            q.eq(GovQualityAlertLog::getSchemeId, schemeId);
        }
        List<GovQualityAlertLog> rows = alertLogMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityAlertLog r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("schemeId", r.getSchemeId());
            m.put("taskId", r.getTaskId());
            m.put("runId", r.getRunId());
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

    /** 监控页问题工单：取最近 OPEN 问题并附库名/责任人。 */
    public List<Map<String, Object>> listOpenTickets(int limit) {
        int n = Math.max(1, Math.min(limit, 100));
        List<GovQualityIssue> issues = issueMapper.selectList(new LambdaQueryWrapper<GovQualityIssue>()
                .eq(GovQualityIssue::getStatus, "OPEN")
                .orderByDesc(GovQualityIssue::getId)
                .last("LIMIT " + n));
        GovQualityAlertChannel ch = requireChannel();
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityIssue iss : issues) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", iss.getId());
            m.put("runId", iss.getRunId());
            m.put("taskId", iss.getTaskId());
            m.put("title", buildTicketTitle(iss));
            m.put("databaseName", resolveDbLabel(iss.getTaskId()));
            m.put("targetTable", iss.getTargetTable());
            m.put("targetColumn", iss.getTargetColumn());
            m.put("issueValue", firstNonBlank(iss.getIssueValue(), iss.getSampleData()));
            m.put("checkType", iss.getCheckType());
            m.put("severity", iss.getSeverity());
            m.put("owner", ch.getOwnerName());
            m.put("channel", channelHint(ch));
            m.put("status", iss.getStatus());
            m.put("createdAt", iss.getCreatedAt());
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> sendEmail(Long schemeId, Long taskId, Long runId,
                                          String to, String subject, String content) {
        GovQualityAlertLog row = new GovQualityAlertLog();
        row.setSchemeId(schemeId);
        row.setTaskId(taskId);
        row.setRunId(runId);
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
            log.warn("quality alert mail failed to={}: {}", to, e.getMessage());
            row.setStatus("FAILED");
            row.setMessage(truncate(e.getMessage(), 400));
        }
        alertLogMapper.insert(row);
        return Map.of("channel", "EMAIL", "receivers", to, "status", row.getStatus(), "message", row.getMessage());
    }

    private Map<String, Object> sendSmsLedger(Long schemeId, Long taskId, Long runId,
                                              String phone, String subject, String content) {
        GovQualityAlertLog row = new GovQualityAlertLog();
        row.setSchemeId(schemeId);
        row.setTaskId(taskId);
        row.setRunId(runId);
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

    private String resolveDbLabel(Long taskId) {
        if (taskId == null) return "—";
        GovQualityTask task = taskMapper.selectById(taskId);
        if (task == null || task.getDatasourceId() == null) {
            return "—";
        }
        Long dsId = task.getDatasourceId();
        if (dsId == -1L) return "源层ODS";
        if (dsId == -2L) return "过程层DWD";
        if (dsId == -3L) return "主题层DWS";
        if (dsId == -4L) return "专题层ADS";
        IngDataSource ds = dataSourceMapper.selectById(dsId);
        if (ds != null && ds.getSourceName() != null) {
            return ds.getSourceName();
        }
        return "数据源#" + dsId;
    }

    private GovQualityAlertChannel requireChannel() {
        GovQualityAlertChannel c = channelMapper.selectById(CHANNEL_ID);
        if (c == null) {
            c = new GovQualityAlertChannel();
            c.setId(CHANNEL_ID);
            c.setMailEnabled(0);
            c.setSmsEnabled(0);
            c.setOwnerName("数据治理组");
            c.setUpdatedAt(LocalDateTime.now());
            channelMapper.insert(c);
        }
        return c;
    }

    private Map<String, Object> toChannelView(GovQualityAlertChannel c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mailEnabled", c.getMailEnabled() != null && c.getMailEnabled() == 1);
        m.put("mailReceivers", c.getMailReceivers());
        m.put("smsEnabled", c.getSmsEnabled() != null && c.getSmsEnabled() == 1);
        m.put("smsPhones", c.getSmsPhones());
        m.put("smsGatewayUrl", c.getSmsGatewayUrl());
        m.put("smsSignName", c.getSmsSignName());
        m.put("smsTemplateCode", c.getSmsTemplateCode());
        m.put("ownerName", c.getOwnerName());
        return m;
    }

    private static String buildTicketTitle(GovQualityIssue iss) {
        return nullToDash(iss.getCheckType()) + " · "
                + nullToDash(iss.getTargetTable()) + "."
                + nullToDash(iss.getTargetColumn());
    }

    private static String channelHint(GovQualityAlertChannel ch) {
        List<String> parts = new ArrayList<>();
        if (ch.getMailEnabled() != null && ch.getMailEnabled() == 1) parts.add("邮件");
        if (ch.getSmsEnabled() != null && ch.getSmsEnabled() == 1) parts.add("短信");
        return parts.isEmpty() ? "台账" : String.join("+", parts);
    }

    private static List<String> splitReceivers(String raw) {
        String[] parts = raw.split("[;；,，\\s]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) out.add(p.trim());
        }
        return out;
    }

    private static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String nullToDash(String v) {
        return v == null || v.isBlank() ? "—" : v;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
