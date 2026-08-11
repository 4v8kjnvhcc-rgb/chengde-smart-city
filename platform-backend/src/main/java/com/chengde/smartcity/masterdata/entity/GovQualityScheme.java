package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_quality_scheme")
public class GovQualityScheme {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String schemeName;
    private Long modelId;
    private String modelName;
    private String executorAddress;
    private String cronExpr;
    private String cycleName;
    private String description;
    private String scheduleStatus;
    private String generateStatus;
    private String execStatus;
    private LocalDateTime lastExecAt;
    private String lastMessage;
    private Long qualityTaskId;
    private Long dsProjectCode;
    private Long dsDefinitionCode;
    private Integer dsScheduleId;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getExecutorAddress() { return executorAddress; }
    public void setExecutorAddress(String executorAddress) { this.executorAddress = executorAddress; }
    public String getCronExpr() { return cronExpr; }
    public void setCronExpr(String cronExpr) { this.cronExpr = cronExpr; }
    public String getCycleName() { return cycleName; }
    public void setCycleName(String cycleName) { this.cycleName = cycleName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getScheduleStatus() { return scheduleStatus; }
    public void setScheduleStatus(String scheduleStatus) { this.scheduleStatus = scheduleStatus; }
    public String getGenerateStatus() { return generateStatus; }
    public void setGenerateStatus(String generateStatus) { this.generateStatus = generateStatus; }
    public String getExecStatus() { return execStatus; }
    public void setExecStatus(String execStatus) { this.execStatus = execStatus; }
    public LocalDateTime getLastExecAt() { return lastExecAt; }
    public void setLastExecAt(LocalDateTime lastExecAt) { this.lastExecAt = lastExecAt; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public Long getQualityTaskId() { return qualityTaskId; }
    public void setQualityTaskId(Long qualityTaskId) { this.qualityTaskId = qualityTaskId; }
    public Long getDsProjectCode() { return dsProjectCode; }
    public void setDsProjectCode(Long dsProjectCode) { this.dsProjectCode = dsProjectCode; }
    public Long getDsDefinitionCode() { return dsDefinitionCode; }
    public void setDsDefinitionCode(Long dsDefinitionCode) { this.dsDefinitionCode = dsDefinitionCode; }
    public Integer getDsScheduleId() { return dsScheduleId; }
    public void setDsScheduleId(Integer dsScheduleId) { this.dsScheduleId = dsScheduleId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
