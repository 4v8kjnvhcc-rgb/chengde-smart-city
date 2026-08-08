package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_meta_collect_task")
public class GovMetaCollectTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskCode;
    private String taskName;
    private Long connectorId;
    private Long ingDataSourceId;
    private Long ingTableId;
    private Long modelId;
    private String cronExpr;
    private String scopeType;
    private String tableList;
    private String scheduleType;
    private String publishStatus;
    private Long categoryId;
    private Long metaDataSourceId;
    private Long dsProjectCode;
    private Long dsDefinitionCode;
    private Integer dsScheduleId;
    private String status;
    private LocalDateTime lastRunAt;
    private String lastMessage;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskCode() { return taskCode; }
    public void setTaskCode(String taskCode) { this.taskCode = taskCode; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public Long getConnectorId() { return connectorId; }
    public void setConnectorId(Long connectorId) { this.connectorId = connectorId; }
    public Long getIngDataSourceId() { return ingDataSourceId; }
    public void setIngDataSourceId(Long ingDataSourceId) { this.ingDataSourceId = ingDataSourceId; }
    public Long getIngTableId() { return ingTableId; }
    public void setIngTableId(Long ingTableId) { this.ingTableId = ingTableId; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public String getCronExpr() { return cronExpr; }
    public void setCronExpr(String cronExpr) { this.cronExpr = cronExpr; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getTableList() { return tableList; }
    public void setTableList(String tableList) { this.tableList = tableList; }
    public String getScheduleType() { return scheduleType; }
    public void setScheduleType(String scheduleType) { this.scheduleType = scheduleType; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getMetaDataSourceId() { return metaDataSourceId; }
    public void setMetaDataSourceId(Long metaDataSourceId) { this.metaDataSourceId = metaDataSourceId; }
    public Long getDsProjectCode() { return dsProjectCode; }
    public void setDsProjectCode(Long dsProjectCode) { this.dsProjectCode = dsProjectCode; }
    public Long getDsDefinitionCode() { return dsDefinitionCode; }
    public void setDsDefinitionCode(Long dsDefinitionCode) { this.dsDefinitionCode = dsDefinitionCode; }
    public Integer getDsScheduleId() { return dsScheduleId; }
    public void setDsScheduleId(Integer dsScheduleId) { this.dsScheduleId = dsScheduleId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
