package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("gov_quality_task")
public class GovQualityTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskName;
    private String description;
    private Long ruleId;
    private String status;
    private LocalDateTime lastRunAt;
    private BigDecimal lastScore;
    private String lastMessage;
    private String scheduleType;
    private String cronExpr;
    private Long datasourceId;
    private String metadataEntryCode;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public BigDecimal getLastScore() { return lastScore; }
    public void setLastScore(BigDecimal lastScore) { this.lastScore = lastScore; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public String getScheduleType() { return scheduleType; }
    public void setScheduleType(String scheduleType) { this.scheduleType = scheduleType; }
    public String getCronExpr() { return cronExpr; }
    public void setCronExpr(String cronExpr) { this.cronExpr = cronExpr; }
    public Long getDatasourceId() { return datasourceId; }
    public void setDatasourceId(Long datasourceId) { this.datasourceId = datasourceId; }
    public String getMetadataEntryCode() { return metadataEntryCode; }
    public void setMetadataEntryCode(String metadataEntryCode) { this.metadataEntryCode = metadataEntryCode; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
