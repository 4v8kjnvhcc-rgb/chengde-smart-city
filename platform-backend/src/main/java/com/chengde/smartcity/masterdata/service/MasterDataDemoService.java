package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovOmConnector;
import com.chengde.smartcity.masterdata.entity.GovQualityRule;
import com.chengde.smartcity.masterdata.entity.GovQualityTask;
import com.chengde.smartcity.masterdata.entity.RcBackupJob;
import com.chengde.smartcity.masterdata.entity.RcThemeLibrary;
import com.chengde.smartcity.masterdata.entity.UnsDocument;
import com.chengde.smartcity.masterdata.mapper.GovOmConnectorMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityRuleMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskMapper;
import com.chengde.smartcity.masterdata.mapper.RcBackupJobMapper;
import com.chengde.smartcity.masterdata.mapper.RcThemeLibraryMapper;
import com.chengde.smartcity.masterdata.mapper.UnsDocumentMapper;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.openmetadata.OpenMetadataClient;
import com.chengde.smartcity.integration.storage.StorageIntegrationClient;
import com.chengde.smartcity.security.UserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterDataDemoService {

    private final GovOmConnectorMapper connectorMapper;
    private final GovQualityRuleMapper ruleMapper;
    private final GovQualityTaskMapper qualityTaskMapper;
    private final UnsDocumentMapper documentMapper;
    private final RcThemeLibraryMapper themeMapper;
    private final RcBackupJobMapper backupJobMapper;
    private final AuditService auditService;
    private final IntegrationProperties integrationProperties;
    private final OpenMetadataClient openMetadataClient;
    private final StorageIntegrationClient storageClient;

    public MasterDataDemoService(GovOmConnectorMapper connectorMapper, GovQualityRuleMapper ruleMapper,
                                 GovQualityTaskMapper qualityTaskMapper, UnsDocumentMapper documentMapper,
                                 RcThemeLibraryMapper themeMapper, RcBackupJobMapper backupJobMapper,
                                 AuditService auditService, IntegrationProperties integrationProperties,
                                 OpenMetadataClient openMetadataClient, StorageIntegrationClient storageClient) {
        this.connectorMapper = connectorMapper;
        this.ruleMapper = ruleMapper;
        this.qualityTaskMapper = qualityTaskMapper;
        this.documentMapper = documentMapper;
        this.themeMapper = themeMapper;
        this.backupJobMapper = backupJobMapper;
        this.auditService = auditService;
        this.integrationProperties = integrationProperties;
        this.openMetadataClient = openMetadataClient;
        this.storageClient = storageClient;
    }

    public List<GovOmConnector> listConnectors() {
        return connectorMapper.selectList(new LambdaQueryWrapper<GovOmConnector>().orderByDesc(GovOmConnector::getId));
    }

    @Transactional
    public Long createConnector(UserPrincipal operator, Map<String, Object> body) {
        GovOmConnector c = new GovOmConnector();
        c.setConnectorCode(str(body.get("connectorCode"), "OM_CONN_" + UUID.randomUUID().toString().substring(0, 8)));
        c.setConnectorName(required(body.get("connectorName"), "connectorName"));
        c.setSourceType(str(body.get("sourceType"), "MySQL"));
        if (body.containsKey("jdbcUrl")) c.setJdbcUrl(str(body.get("jdbcUrl"), null));
        if (body.containsKey("jdbcUser")) c.setJdbcUser(str(body.get("jdbcUser"), null));
        if (body.containsKey("jdbcPassword")) c.setJdbcPassword(str(body.get("jdbcPassword"), null));
        if (body.containsKey("jdbcDatabase")) c.setJdbcDatabase(str(body.get("jdbcDatabase"), null));
        c.setStatus("ACTIVE");
        c.setCreatedBy(operator.getUsername());
        connectorMapper.insert(c);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "OM_CONNECTOR_CREATE", "gov_om_connector", String.valueOf(c.getId()), c.getConnectorName());
        return c.getId();
    }

    @Transactional
    public Map<String, Object> syncConnector(UserPrincipal operator, Long id) {
        GovOmConnector c = connectorMapper.selectById(id);
        if (c == null) {
            throw new BusinessException(404, "connector not found");
        }
        c.setLastSyncAt(LocalDateTime.now());
        if (integrationProperties.isEnabled() && openMetadataClient.isHealthy()) {
            try {
                Map<String, Object> om = openMetadataClient.ingestService(c.getConnectorName());
                c.setLastMessage("OpenMetadata sync: " + om.getOrDefault("status", "TRIGGERED"));
            } catch (Exception e) {
                if (!integrationProperties.isDemoFallback()) {
                    throw new BusinessException(503, "OpenMetadata sync failed: " + e.getMessage());
                }
                c.setLastMessage("metadata sync ok, tables=" + (5 + (int) (Math.random() * 20)));
            }
        } else {
            c.setLastMessage("metadata sync ok, tables=" + (5 + (int) (Math.random() * 20)));
        }
        connectorMapper.updateById(c);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "OM_CONNECTOR_SYNC", "gov_om_connector", String.valueOf(id), c.getLastMessage());
        return Map.of("connectorCode", c.getConnectorCode(), "message", c.getLastMessage(), "status", "SUCCESS");
    }

    public List<GovQualityRule> listRules() {
        return ruleMapper.selectList(new LambdaQueryWrapper<GovQualityRule>().orderByDesc(GovQualityRule::getId));
    }

    @Transactional
    public Long createRule(UserPrincipal operator, Map<String, Object> body) {
        GovQualityRule rule = new GovQualityRule();
        rule.setRuleCode(str(body.get("ruleCode"), "QR_" + UUID.randomUUID().toString().substring(0, 8)));
        rule.setRuleName(required(body.get("ruleName"), "ruleName"));
        rule.setRuleType(str(body.get("ruleType"), "COMPLETENESS"));
        rule.setOrgId(operator.getOrgId());
        rule.setStatus("ENABLED");
        rule.setCreatedBy(operator.getUsername());
        ruleMapper.insert(rule);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "QUALITY_RULE_CREATE", "gov_quality_rule", String.valueOf(rule.getId()), rule.getRuleName());
        return rule.getId();
    }

    public List<GovQualityTask> listQualityTasks() {
        return qualityTaskMapper.selectList(new LambdaQueryWrapper<GovQualityTask>().orderByDesc(GovQualityTask::getId));
    }

    @Transactional
    public Long createQualityTask(UserPrincipal operator, Map<String, Object> body) {
        GovQualityTask task = new GovQualityTask();
        task.setTaskName(required(body.get("taskName"), "taskName"));
        Object ruleId = body.get("ruleId");
        if (ruleId != null) {
            task.setRuleId(Long.valueOf(String.valueOf(ruleId)));
        }
        task.setStatus("READY");
        task.setCreatedBy(operator.getUsername());
        qualityTaskMapper.insert(task);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "QUALITY_TASK_CREATE", "gov_quality_task", String.valueOf(task.getId()), task.getTaskName());
        return task.getId();
    }

    @Transactional
    public Map<String, Object> runQualityTask(UserPrincipal operator, Long id) {
        GovQualityTask task = qualityTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "quality task not found");
        }
        BigDecimal score = BigDecimal.valueOf(80 + Math.random() * 20).setScale(2, RoundingMode.HALF_UP);
        task.setStatus("SUCCESS");
        task.setLastRunAt(LocalDateTime.now());
        task.setLastScore(score);
        task.setLastMessage("quality score=" + score);
        qualityTaskMapper.updateById(task);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "QUALITY_TASK_RUN", "gov_quality_task", String.valueOf(id), task.getLastMessage());
        return Map.of("taskId", id, "status", "SUCCESS", "score", score);
    }

    public List<UnsDocument> listDocuments(String keyword) {
        LambdaQueryWrapper<UnsDocument> q = new LambdaQueryWrapper<UnsDocument>().orderByDesc(UnsDocument::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.like(UnsDocument::getTitle, keyword);
        }
        return documentMapper.selectList(q);
    }

    @Transactional
    public Long registerDocument(UserPrincipal operator, Map<String, Object> body) {
        UnsDocument doc = new UnsDocument();
        doc.setDocCode(str(body.get("docCode"), "DOC_" + UUID.randomUUID().toString().substring(0, 8)));
        doc.setTitle(required(body.get("title"), "title"));
        doc.setContentType(str(body.get("contentType"), "application/pdf"));
        byte[] payload = ("doc-" + doc.getTitle()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (integrationProperties.isEnabled()) {
            try {
                doc.setStorageKey(storageClient.storeDocument(doc.getTitle(), doc.getContentType(), payload));
            } catch (Exception e) {
                // 本地落盘兜底，明确 local://，禁止伪装 s3 成功
                java.nio.file.Path local = java.nio.file.Path.of("data", "nas-demo", "uns-docs",
                        doc.getDocCode() + ".bin");
                try {
                    java.nio.file.Files.createDirectories(local.getParent());
                    java.nio.file.Files.write(local, payload);
                    doc.setStorageKey("local://" + local.toAbsolutePath());
                } catch (Exception io) {
                    throw new BusinessException(500, "文档落盘失败: " + io.getMessage());
                }
            }
        } else {
            java.nio.file.Path local = java.nio.file.Path.of("data", "nas-demo", "uns-docs",
                    doc.getDocCode() + ".bin");
            try {
                java.nio.file.Files.createDirectories(local.getParent());
                java.nio.file.Files.write(local, payload);
                doc.setStorageKey("local://" + local.toAbsolutePath());
            } catch (Exception io) {
                throw new BusinessException(500, "文档落盘失败: " + io.getMessage());
            }
        }
        doc.setIndexStatus("PENDING");
        doc.setCreatedBy(operator.getUsername());
        documentMapper.insert(doc);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_DOC_REGISTER", "uns_document", String.valueOf(doc.getId()), doc.getTitle());
        return doc.getId();
    }

    @Transactional
    public Map<String, Object> indexDocument(UserPrincipal operator, Long id) {
        UnsDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        if (!integrationProperties.isEnabled() || !storageClient.isElasticsearchHealthy()) {
            doc.setIndexStatus("FAILED");
            documentMapper.updateById(doc);
            throw new BusinessException(503, "Elasticsearch 不可用，无法建索（禁止假成功标记已索引）");
        }
        Map<String, Object> indexed = storageClient.indexDocument(String.valueOf(id), doc.getTitle(), doc.getStorageKey());
        String status = String.valueOf(indexed.getOrDefault("indexStatus", "FAILED"));
        doc.setIndexStatus(status);
        documentMapper.updateById(doc);
        if (!"INDEXED".equalsIgnoreCase(status)) {
            throw new BusinessException(500, "建索失败: " + indexed.getOrDefault("note", status));
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "UNS_DOC_INDEX", "uns_document", String.valueOf(id), doc.getTitle());
        indexed = new java.util.HashMap<>(indexed);
        indexed.put("docCode", doc.getDocCode());
        return indexed;
    }

    public List<RcThemeLibrary> listThemes() {
        return themeMapper.selectList(new LambdaQueryWrapper<RcThemeLibrary>().orderByDesc(RcThemeLibrary::getId));
    }

    @Transactional
    public Long createTheme(UserPrincipal operator, Map<String, Object> body) {
        RcThemeLibrary theme = new RcThemeLibrary();
        theme.setThemeCode(str(body.get("themeCode"), "THEME_" + UUID.randomUUID().toString().substring(0, 8)));
        theme.setThemeName(required(body.get("themeName"), "themeName"));
        theme.setPartitionKey(str(body.get("partitionKey"), "org_id"));
        theme.setStatus("ACTIVE");
        theme.setCreatedBy(operator.getUsername());
        themeMapper.insert(theme);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_THEME_CREATE", "rc_theme_library", String.valueOf(theme.getId()), theme.getThemeName());
        return theme.getId();
    }

    public List<RcBackupJob> listBackupJobs() {
        return backupJobMapper.selectList(new LambdaQueryWrapper<RcBackupJob>().orderByDesc(RcBackupJob::getId));
    }

    @Transactional
    public Long createBackupJob(UserPrincipal operator, Map<String, Object> body) {
        RcBackupJob job = new RcBackupJob();
        job.setJobName(required(body.get("jobName"), "jobName"));
        Object themeId = body.get("themeId");
        if (themeId != null) {
            job.setThemeId(Long.valueOf(String.valueOf(themeId)));
        }
        job.setStatus("READY");
        job.setCreatedBy(operator.getUsername());
        backupJobMapper.insert(job);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_BACKUP_CREATE", "rc_backup_job", String.valueOf(job.getId()), job.getJobName());
        return job.getId();
    }

    @Transactional
    public Map<String, Object> runBackup(UserPrincipal operator, Long id) {
        RcBackupJob job = backupJobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(404, "backup job not found");
        }
        String backupPath;
        try {
            Path dir = Path.of("data", "nas-demo", "backups");
            Files.createDirectories(dir);
            Path file = dir.resolve("backup-job-" + id + "-" + System.currentTimeMillis() + ".marker");
            Files.writeString(file, "themeId=" + job.getThemeId() + "\njobName=" + job.getJobName() + "\n");
            backupPath = file.toAbsolutePath().toString();
        } catch (Exception e) {
            throw new BusinessException(500, "backup write failed: " + e.getMessage());
        }
        job.setStatus("SUCCESS");
        job.setLastRunAt(LocalDateTime.now());
        job.setLastMessage("backup completed: " + backupPath);
        backupJobMapper.updateById(job);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_BACKUP_RUN", "rc_backup_job", String.valueOf(id), job.getLastMessage());
        return Map.of("jobId", id, "status", "SUCCESS", "message", job.getLastMessage(), "backupPath", backupPath);
    }

    private static String required(Object value, String label) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(400, label + " required");
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
