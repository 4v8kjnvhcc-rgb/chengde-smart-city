package com.chengde.smartcity.system.service;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysMailConfig;
import com.chengde.smartcity.system.mapper.SysMailConfigMapper;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailConfigService {

    private static final Logger log = LoggerFactory.getLogger(MailConfigService.class);
    private static final long ROW_ID = 1L;

    private final SysMailConfigMapper mapper;

    public MailConfigService(SysMailConfigMapper mapper) {
        this.mapper = mapper;
    }

    public SysMailConfig requireRow() {
        SysMailConfig row = mapper.selectById(ROW_ID);
        if (row == null) {
            row = new SysMailConfig();
            row.setId(ROW_ID);
            row.setEnabled(0);
            row.setSmtpPort(465);
            row.setSmtpSsl(1);
            row.setCreatedAt(LocalDateTime.now());
            mapper.insert(row);
        }
        return row;
    }

    public Map<String, Object> toView() {
        SysMailConfig c = requireRow();
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", c.getEnabled() != null && c.getEnabled() == 1);
        m.put("smtpHost", c.getSmtpHost());
        m.put("smtpPort", c.getSmtpPort());
        m.put("smtpSsl", c.getSmtpSsl() != null && c.getSmtpSsl() == 1);
        m.put("username", c.getUsername());
        m.put("passwordSet", c.getPasswordEnc() != null && !c.getPasswordEnc().isBlank());
        m.put("fromName", c.getFromName());
        m.put("fromAddress", c.getFromAddress());
        return m;
    }

    public void save(UserPrincipal operator, Map<String, Object> body) {
        SysMailConfig c = requireRow();
        if (body.containsKey("enabled")) {
            c.setEnabled(truthy(body.get("enabled")) ? 1 : 0);
        }
        if (body.containsKey("smtpHost")) {
            c.setSmtpHost(asStr(body.get("smtpHost")));
        }
        if (body.containsKey("smtpPort")) {
            try {
                c.setSmtpPort(Integer.parseInt(String.valueOf(body.get("smtpPort"))));
            } catch (NumberFormatException ignored) {
                // keep
            }
        }
        if (body.containsKey("smtpSsl")) {
            c.setSmtpSsl(truthy(body.get("smtpSsl")) ? 1 : 0);
        }
        if (body.containsKey("username")) {
            c.setUsername(asStr(body.get("username")));
        }
        if (body.containsKey("password") && body.get("password") != null
                && !String.valueOf(body.get("password")).isBlank()) {
            // 简化：明文存库（生产可换密文）；前端回显不返回
            c.setPasswordEnc(String.valueOf(body.get("password")));
        }
        if (body.containsKey("fromName")) {
            c.setFromName(asStr(body.get("fromName")));
        }
        if (body.containsKey("fromAddress")) {
            c.setFromAddress(asStr(body.get("fromAddress")));
        }
        c.setUpdatedBy(operator.getUserId());
        c.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(c);
    }

    public void sendTest(UserPrincipal operator, String to) {
        if (to == null || to.isBlank() || !to.contains("@")) {
            throw new BusinessException(400, "请填写有效收件人邮箱");
        }
        SysMailConfig c = requireRow();
        if (c.getSmtpHost() == null || c.getSmtpHost().isBlank()) {
            throw new BusinessException(400, "请先配置 SMTP 主机");
        }
        if (c.getPasswordEnc() == null || c.getPasswordEnc().isBlank()) {
            throw new BusinessException(400, "请先配置邮箱密码");
        }
        try {
            JavaMailSenderImpl sender = buildSender(c);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            String from = c.getFromAddress() != null && !c.getFromAddress().isBlank()
                    ? c.getFromAddress() : c.getUsername();
            if (c.getFromName() != null && !c.getFromName().isBlank()) {
                helper.setFrom(from, c.getFromName());
            } else {
                helper.setFrom(from);
            }
            helper.setTo(to.trim());
            helper.setSubject("【承德智慧城市】系统邮箱测试");
            helper.setText("这是一封系统邮箱配置测试邮件。操作人：" + operator.getUsername()
                    + "，时间：" + LocalDateTime.now(), false);
            sender.send(message);
            log.info("测试邮件已发送 to={} by={}", to, operator.getUsername());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("发送测试邮件失败: {}", e.getMessage());
            throw new BusinessException(500, "发送失败：" + e.getMessage());
        }
    }

    public void send(String to, String subject, String text) throws Exception {
        SysMailConfig c = requireRow();
        if (c.getEnabled() == null || c.getEnabled() != 1) {
            throw new BusinessException(400, "系统邮箱未启用");
        }
        JavaMailSenderImpl sender = buildSender(c);
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        String from = c.getFromAddress() != null && !c.getFromAddress().isBlank()
                ? c.getFromAddress() : c.getUsername();
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text, false);
        sender.send(message);
    }

    private JavaMailSenderImpl buildSender(SysMailConfig c) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(c.getSmtpHost());
        sender.setPort(c.getSmtpPort() == null ? 465 : c.getSmtpPort());
        sender.setUsername(c.getUsername());
        sender.setPassword(c.getPasswordEnc());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        boolean ssl = c.getSmtpSsl() == null || c.getSmtpSsl() == 1;
        if (ssl) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        return sender;
    }

    private static boolean truthy(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(o)) || "1".equals(String.valueOf(o));
    }

    private static String asStr(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
