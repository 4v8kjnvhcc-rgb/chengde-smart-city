package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngIngestChannel;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.entity.IngUploadRecord;
import com.chengde.smartcity.exchange.entity.IngUploadTemplate;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestChannelMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.exchange.mapper.IngUploadRecordMapper;
import com.chengde.smartcity.exchange.mapper.IngUploadTemplateMapper;
import com.chengde.smartcity.integration.storage.StorageIntegrationClient;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CollectUploadService {

    private final IngUploadTemplateMapper templateMapper;
    private final IngUploadRecordMapper uploadMapper;
    private final IngIngestTaskMapper taskMapper;
    private final IngIngestChannelMapper channelMapper;
    private final IngDataTableMapper dataTableMapper;
    private final SysOrgMapper orgMapper;
    private final AuditService auditService;
    private final StorageIntegrationClient storageClient;

    public CollectUploadService(IngUploadTemplateMapper templateMapper, IngUploadRecordMapper uploadMapper,
                                IngIngestTaskMapper taskMapper, IngIngestChannelMapper channelMapper,
                                IngDataTableMapper dataTableMapper, SysOrgMapper orgMapper,
                                AuditService auditService, StorageIntegrationClient storageClient) {
        this.templateMapper = templateMapper;
        this.uploadMapper = uploadMapper;
        this.taskMapper = taskMapper;
        this.channelMapper = channelMapper;
        this.dataTableMapper = dataTableMapper;
        this.orgMapper = orgMapper;
        this.auditService = auditService;
        this.storageClient = storageClient;
    }

    public List<IngUploadTemplate> listTemplates(UserPrincipal operator, String keyword, Long orgId) {
        LambdaQueryWrapper<IngUploadTemplate> q = new LambdaQueryWrapper<IngUploadTemplate>()
                .orderByDesc(IngUploadTemplate::getId);
        applyOrgScope(q, operator, orgId);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(IngUploadTemplate::getTemplateName, kw)
                    .or().like(IngUploadTemplate::getTemplateCode, kw));
        }
        List<IngUploadTemplate> list = templateMapper.selectList(q);
        fillTemplateOrgNames(list);
        return list;
    }

    public List<IngUploadRecord> listUploadRecords(UserPrincipal operator, String templateCode,
                                                   String keyword, Long orgId) {
        LambdaQueryWrapper<IngUploadRecord> q = new LambdaQueryWrapper<IngUploadRecord>()
                .orderByDesc(IngUploadRecord::getId);
        applyOrgScopeRecord(q, operator, orgId);
        if (templateCode != null && !templateCode.isBlank()) {
            q.eq(IngUploadRecord::getTemplateCode, templateCode.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(IngUploadRecord::getFileName, kw)
                    .or().like(IngUploadRecord::getTargetTable, kw)
                    .or().like(IngUploadRecord::getTemplateCode, kw));
        }
        List<IngUploadRecord> list = uploadMapper.selectList(q);
        fillRecordOrgNames(list);
        return list;
    }

    public void assertTemplateAccessible(UserPrincipal operator, IngUploadTemplate t) {
        if (t == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (canSeeAllOrgs(operator)) {
            return;
        }
        Long orgId = operator == null ? null : operator.getOrgId();
        if (orgId == null || t.getOrgId() == null || !orgId.equals(t.getOrgId())) {
            throw new BusinessException(403, "无权操作其他机构的上传模板");
        }
    }

    public Long resolveTemplateOrgId(UserPrincipal operator, Object requestedOrgId) {
        Long req = longVal(requestedOrgId);
        if (canSeeAllOrgs(operator)) {
            if (req != null) {
                return req;
            }
            return operator != null ? operator.getOrgId() : null;
        }
        if (operator == null || operator.getOrgId() == null) {
            throw new BusinessException(400, "当前账号未绑定机构");
        }
        if (req != null && !req.equals(operator.getOrgId())) {
            throw new BusinessException(403, "部门管理员不可选择其他归属机构");
        }
        return operator.getOrgId();
    }

    @Transactional
    public Long createTemplate(UserPrincipal operator, Map<String, Object> body) {
        IngUploadTemplate t = new IngUploadTemplate();
        t.setTemplateCode(str(body.get("templateCode"), "TPL_" + System.currentTimeMillis()));
        t.setTemplateName(required(body.get("templateName"), "templateName").toString());
        t.setColumnMappingJson(str(body.get("columnMappingJson"), "[]"));
        t.setValidateRulesJson(str(body.get("validateRulesJson"), null));
        t.setStatus("ACTIVE");
        t.setOrgId(resolveTemplateOrgId(operator, body == null ? null : body.get("orgId")));
        templateMapper.insert(t);
        return t.getId();
    }

    @Transactional
    public void deleteTemplate(UserPrincipal operator, Long id) {
        IngUploadTemplate t = templateMapper.selectById(id);
        if (t == null) {
            return;
        }
        assertTemplateAccessible(operator, t);
        templateMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_UPLOAD_TPL_DEL", "ing_upload_template", String.valueOf(id), t.getTemplateName());
    }

    @Transactional
    public void updateTemplateStatus(UserPrincipal operator, Long id, String status) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new BusinessException(400, "status 仅支持 ACTIVE / INACTIVE");
        }
        IngUploadTemplate t = templateMapper.selectById(id);
        assertTemplateAccessible(operator, t);
        t.setStatus(status);
        templateMapper.updateById(t);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_UPLOAD_TPL_STATUS", "ing_upload_template", String.valueOf(id),
                t.getTemplateName() + " → " + status);
    }

    @Transactional
    public Long uploadFile(UserPrincipal operator, MultipartFile file, String templateCode) {
        IngUploadRecord r = new IngUploadRecord();
        r.setTemplateCode(str(templateCode, "TPL_DEFAULT"));
        r.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin");
        int rows = 20 + (int) (file.getSize() % 200);
        r.setRowCount(rows);
        r.setStatus("PARSED");
        String storageNote = storageClient.isSeaweedHealthy() ? "SEAWEED_STORED" : "LOCAL_STORED";
        r.setPreviewJson("[{\"fileSize\":" + file.getSize() + ",\"storage\":\"" + storageNote + "\"}]");
        r.setCreatedBy(operator.getUsername());
        r.setOrgId(operator.getOrgId());
        uploadMapper.insert(r);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_UPLOAD_FILE", "ing_upload_record", String.valueOf(r.getId()), r.getFileName());
        return r.getId();
    }

    public List<IngIngestTask> listTasks(Long channelId) {
        LambdaQueryWrapper<IngIngestTask> q = new LambdaQueryWrapper<IngIngestTask>().orderByDesc(IngIngestTask::getId);
        if (channelId != null) {
            q.eq(IngIngestTask::getChannelId, channelId);
        }
        return taskMapper.selectList(q);
    }

    public List<IngIngestTask> listJobs(String accessMode) {
        LambdaQueryWrapper<IngIngestTask> q = new LambdaQueryWrapper<IngIngestTask>().orderByDesc(IngIngestTask::getId);
        if (accessMode != null && !accessMode.isBlank()) {
            q.eq(IngIngestTask::getAccessMode, accessMode.toUpperCase());
        }
        return taskMapper.selectList(q);
    }

    public IngIngestTask getJob(Long id) {
        IngIngestTask t = taskMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "接入任务不存在");
        }
        return t;
    }

    @Transactional
    public Long createTask(UserPrincipal operator, Map<String, Object> body) {
        Long channelId = body.get("channelId") != null
                ? Long.valueOf(String.valueOf(body.get("channelId")))
                : null;
        if (channelId == null) {
            IngIngestChannel ch = channelMapper.selectOne(new LambdaQueryWrapper<IngIngestChannel>()
                    .eq(IngIngestChannel::getChannelType, "TABLE").last("LIMIT 1"));
            if (ch == null) {
                throw new BusinessException(400, "未找到 TABLE 接入通道");
            }
            channelId = ch.getId();
        } else {
            IngIngestChannel ch = channelMapper.selectById(channelId);
            if (ch == null) {
                throw new BusinessException(404, "通道不存在");
            }
        }
        IngIngestTask task = new IngIngestTask();
        task.setTaskCode("TASK_" + System.currentTimeMillis());
        task.setTaskName(required(body.get("taskName"), "taskName").toString());
        task.setChannelId(channelId);
        applyJobFields(task, body);
        task.setLifecycleStatus(str(body.get("lifecycleStatus"), "DRAFT"));
        if (task.getVersionNo() == null) {
            task.setVersionNo(0);
        }
        String status = str(body.get("status"), "IDLE");
        task.setStatus(status);
        if (task.getLastRunMessage() == null) {
            task.setLastRunMessage("registered");
        }
        if ("SUCCESS".equalsIgnoreCase(status)) {
            task.setLastRunAt(java.time.LocalDateTime.now());
            task.setLastRunMessage(str(body.get("lastRunMessage"), "collect success"));
            markTableCollected(task.getTableId(), task.getTargetTable(), task.getCollectedRows());
        }
        taskMapper.insert(task);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_JOB_CREATE", "ing_ingest_task", String.valueOf(task.getId()), task.getTaskName());
        return task.getId();
    }

    @Transactional
    public void updateJob(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngIngestTask task = getJob(id);
        String life = task.getLifecycleStatus() == null ? "DRAFT" : task.getLifecycleStatus().toUpperCase();
        if (!"DRAFT".equals(life) && !"OFFLINE".equals(life)) {
            throw new BusinessException(400, "仅草稿或已下线任务可编辑，当前状态：" + life);
        }
        if (body.containsKey("taskName") && body.get("taskName") != null) {
            task.setTaskName(String.valueOf(body.get("taskName")));
        }
        applyJobFields(task, body);
        taskMapper.updateById(task);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_JOB_UPDATE", "ing_ingest_task", String.valueOf(id), task.getTaskName());
    }

    @Transactional
    public void resetStuckJob(UserPrincipal operator, Long id) {
        IngIngestTask task = getJob(id);
        task.setStatus("FAILED");
        task.setLastRunAt(java.time.LocalDateTime.now());
        task.setLastRunMessage("已手动重置，可重新执行");
        task.setErrorDetail("manual reset by " + operator.getUsername());
        taskMapper.updateById(task);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_JOB_RESET", "ing_ingest_task", String.valueOf(id), task.getTaskName());
    }

    @Transactional
    public void deleteJob(UserPrincipal operator, Long id) {
        IngIngestTask task = getJob(id);
        if ("RUNNING".equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(400, "任务运行中，请先重置后再删除");
        }
        String life = task.getLifecycleStatus() == null ? "DRAFT" : task.getLifecycleStatus().toUpperCase();
        if (!"DRAFT".equals(life) && !"OFFLINE".equals(life)) {
            throw new BusinessException(400, "仅草稿或已下线任务可删除，当前状态：" + life);
        }
        String name = task.getTaskName();
        taskMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_JOB_DELETE", "ing_ingest_task", String.valueOf(id), name);
    }

    @SuppressWarnings("unchecked")
    private void applyJobFields(IngIngestTask task, Map<String, Object> body) {
        if (body.get("sourceId") != null) task.setSourceId(longVal(body.get("sourceId")));
        if (body.get("tableId") != null) task.setTableId(longVal(body.get("tableId")));
        if (body.get("targetTable") != null) task.setTargetTable(str(body.get("targetTable"), null));
        if (body.get("collectedRows") != null) task.setCollectedRows(longVal(body.get("collectedRows")));
        if (body.get("scheduleCron") != null) task.setScheduleCron(str(body.get("scheduleCron"), ""));
        if (body.get("accessMode") != null) {
            task.setAccessMode(str(body.get("accessMode"), "SINGLE").toUpperCase());
        } else if (task.getAccessMode() == null) {
            task.setAccessMode("SINGLE");
        }
        if (body.get("writeMode") != null) {
            task.setWriteMode(str(body.get("writeMode"), "FULL").toUpperCase());
        } else if (task.getWriteMode() == null) {
            task.setWriteMode("FULL");
        }
        if (body.get("watermarkValue") != null) {
            task.setWatermarkValue(str(body.get("watermarkValue"), null));
        }
        if (body.get("enabled") != null) {
            Object en = body.get("enabled");
            if (en instanceof Boolean b) {
                task.setEnabled(b ? 1 : 0);
            } else {
                task.setEnabled("1".equals(String.valueOf(en)) || "true".equalsIgnoreCase(String.valueOf(en)) ? 1 : 0);
            }
        } else if (task.getEnabled() == null) {
            task.setEnabled(0);
        }
        if (body.get("config") != null) {
            try {
                Object cfg = body.get("config");
                if (cfg instanceof String s) {
                    task.setConfigJson(s);
                } else {
                    task.setConfigJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cfg));
                }
            } catch (Exception e) {
                throw new BusinessException(400, "config 格式无效");
            }
        } else if (body.get("configJson") != null) {
            task.setConfigJson(String.valueOf(body.get("configJson")));
        }
        if (task.getConfigJson() != null && !task.getConfigJson().isBlank()) {
            try {
                Map<String, Object> cfg = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(task.getConfigJson(), Map.class);
                if (task.getAccessMode() == null && cfg.get("accessMode") != null) {
                    task.setAccessMode(String.valueOf(cfg.get("accessMode")).toUpperCase());
                }
                if (cfg.get("writeMode") != null && body.get("writeMode") == null) {
                    task.setWriteMode(String.valueOf(cfg.get("writeMode")).toUpperCase());
                }
                Object single = cfg.get("single");
                if (single instanceof Map<?, ?> sm) {
                    if (task.getTableId() == null && sm.get("tableId") != null) {
                        task.setTableId(longVal(sm.get("tableId")));
                    }
                    if ((task.getTargetTable() == null || task.getTargetTable().isBlank()) && sm.get("targetTable") != null) {
                        task.setTargetTable(String.valueOf(sm.get("targetTable")));
                    }
                }
                Object sql = cfg.get("sql");
                if (sql instanceof Map<?, ?> qm && task.getSourceId() == null && qm.get("sourceId") != null) {
                    task.setSourceId(longVal(qm.get("sourceId")));
                }
                if (cfg.get("scheduleCron") != null && (task.getScheduleCron() == null || task.getScheduleCron().isBlank())) {
                    task.setScheduleCron(String.valueOf(cfg.get("scheduleCron")));
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception ignored) {
            }
        }
        if (task.getScheduleCron() == null) {
            task.setScheduleCron("");
        }
    }

    @Transactional
    public void markTaskSuccess(UserPrincipal operator, Long taskId, Map<String, Object> body) {
        IngIngestTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "汇聚任务不存在");
        }
        if (body != null) {
            if (body.get("sourceId") != null) task.setSourceId(longVal(body.get("sourceId")));
            if (body.get("tableId") != null) task.setTableId(longVal(body.get("tableId")));
            if (body.get("targetTable") != null) task.setTargetTable(str(body.get("targetTable"), null));
            if (body.get("collectedRows") != null) task.setCollectedRows(longVal(body.get("collectedRows")));
        }
        task.setStatus("SUCCESS");
        task.setLastRunAt(java.time.LocalDateTime.now());
        task.setLastRunMessage(body != null && body.get("lastRunMessage") != null
                ? String.valueOf(body.get("lastRunMessage"))
                : "collect success");
        taskMapper.updateById(task);
        markTableCollected(task.getTableId(), task.getTargetTable(), task.getCollectedRows());
    }

    private void markTableCollected(Long tableId, String targetTable, Long collectedRows) {
        if (tableId == null) {
            return;
        }
        IngDataTable table = dataTableMapper.selectById(tableId);
        if (table == null) {
            return;
        }
        if (targetTable != null && !targetTable.isBlank()) {
            table.setPhysicalTableName(targetTable);
        }
        table.setCollectStatus("SUCCESS");
        table.setLastCollectAt(java.time.LocalDateTime.now());
        dataTableMapper.updateById(table);
    }

    private void applyOrgScope(LambdaQueryWrapper<IngUploadTemplate> q, UserPrincipal operator, Long filterOrgId) {
        if (canSeeAllOrgs(operator)) {
            if (filterOrgId != null) {
                q.eq(IngUploadTemplate::getOrgId, filterOrgId);
            }
            return;
        }
        Long orgId = operator == null ? null : operator.getOrgId();
        if (orgId == null) {
            q.eq(IngUploadTemplate::getId, -1L);
            return;
        }
        q.eq(IngUploadTemplate::getOrgId, orgId);
    }

    private void applyOrgScopeRecord(LambdaQueryWrapper<IngUploadRecord> q, UserPrincipal operator, Long filterOrgId) {
        if (canSeeAllOrgs(operator)) {
            if (filterOrgId != null) {
                q.eq(IngUploadRecord::getOrgId, filterOrgId);
            }
            return;
        }
        Long orgId = operator == null ? null : operator.getOrgId();
        if (orgId == null) {
            q.eq(IngUploadRecord::getId, -1L);
            return;
        }
        q.eq(IngUploadRecord::getOrgId, orgId);
    }

    private boolean canSeeAllOrgs(UserPrincipal operator) {
        return operator != null && (operator.isSystemAdmin() || operator.isPlatformAdmin());
    }

    private void fillTemplateOrgNames(List<IngUploadTemplate> list) {
        Map<Long, String> names = loadOrgNames(list.stream().map(IngUploadTemplate::getOrgId).filter(Objects::nonNull).collect(Collectors.toSet()));
        for (IngUploadTemplate t : list) {
            if (t.getOrgId() != null) {
                t.setOrgName(names.getOrDefault(t.getOrgId(), "机构#" + t.getOrgId()));
            }
        }
    }

    private void fillRecordOrgNames(List<IngUploadRecord> list) {
        Map<Long, String> names = loadOrgNames(list.stream().map(IngUploadRecord::getOrgId).filter(Objects::nonNull).collect(Collectors.toSet()));
        for (IngUploadRecord r : list) {
            if (r.getOrgId() != null) {
                r.setOrgName(names.getOrDefault(r.getOrgId(), "机构#" + r.getOrgId()));
            }
        }
    }

    private Map<Long, String> loadOrgNames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<SysOrg> orgs = orgMapper.selectBatchIds(ids);
        for (SysOrg o : orgs) {
            map.put(o.getId(), o.getOrgName());
        }
        return map;
    }

    private static Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
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
}
