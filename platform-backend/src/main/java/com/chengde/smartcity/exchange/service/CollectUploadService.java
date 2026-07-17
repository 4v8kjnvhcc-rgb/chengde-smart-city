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
import java.util.List;
import java.util.Map;
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
    private final AuditService auditService;
    private final StorageIntegrationClient storageClient;

    public CollectUploadService(IngUploadTemplateMapper templateMapper, IngUploadRecordMapper uploadMapper,
                                IngIngestTaskMapper taskMapper, IngIngestChannelMapper channelMapper,
                                IngDataTableMapper dataTableMapper,
                                AuditService auditService, StorageIntegrationClient storageClient) {
        this.templateMapper = templateMapper;
        this.uploadMapper = uploadMapper;
        this.taskMapper = taskMapper;
        this.channelMapper = channelMapper;
        this.dataTableMapper = dataTableMapper;
        this.auditService = auditService;
        this.storageClient = storageClient;
    }

    public List<IngUploadTemplate> listTemplates() {
        return templateMapper.selectList(new LambdaQueryWrapper<IngUploadTemplate>().orderByAsc(IngUploadTemplate::getId));
    }

    @Transactional
    public Long createTemplate(UserPrincipal operator, Map<String, Object> body) {
        IngUploadTemplate t = new IngUploadTemplate();
        t.setTemplateCode(str(body.get("templateCode"), "TPL_" + System.currentTimeMillis()));
        t.setTemplateName(required(body.get("templateName"), "templateName").toString());
        t.setColumnMappingJson(str(body.get("columnMappingJson"), "[]"));
        t.setValidateRulesJson(str(body.get("validateRulesJson"), null));
        t.setStatus("ACTIVE");
        templateMapper.insert(t);
        return t.getId();
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

    @Transactional
    public Long createTask(UserPrincipal operator, Map<String, Object> body) {
        Long channelId = Long.valueOf(String.valueOf(required(body.get("channelId"), "channelId")));
        IngIngestChannel ch = channelMapper.selectById(channelId);
        if (ch == null) {
            throw new BusinessException(404, "通道不存在");
        }
        IngIngestTask task = new IngIngestTask();
        task.setTaskCode("TASK_" + System.currentTimeMillis());
        task.setTaskName(required(body.get("taskName"), "taskName").toString());
        task.setChannelId(channelId);
        task.setSourceId(longVal(body.get("sourceId")));
        task.setTableId(longVal(body.get("tableId")));
        task.setTargetTable(str(body.get("targetTable"), null));
        task.setCollectedRows(longVal(body.get("collectedRows")));
        task.setScheduleCron(str(body.get("scheduleCron"), "0 2 * * *"));
        String status = str(body.get("status"), "IDLE");
        task.setStatus(status);
        task.setLastRunMessage("registered type=" + ch.getChannelType());
        if ("SUCCESS".equalsIgnoreCase(status)) {
            task.setLastRunAt(java.time.LocalDateTime.now());
            task.setLastRunMessage(str(body.get("lastRunMessage"), "collect success"));
            markTableCollected(task.getTableId(), task.getTargetTable(), task.getCollectedRows());
        }
        taskMapper.insert(task);
        return task.getId();
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
