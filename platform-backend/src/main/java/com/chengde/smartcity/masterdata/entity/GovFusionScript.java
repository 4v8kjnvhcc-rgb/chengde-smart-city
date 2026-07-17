package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_fusion_script")
public class GovFusionScript {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String scriptCode;
    private String scriptName;
    private String scriptType;
    private String scriptContent;
    private Long datasourceId;
    private String publishStatus;
    private Integer versionNo;
    private String status;
    private LocalDateTime lastRunAt;
    private String lastMessage;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getScriptCode() { return scriptCode; }
    public void setScriptCode(String scriptCode) { this.scriptCode = scriptCode; }
    public String getScriptName() { return scriptName; }
    public void setScriptName(String scriptName) { this.scriptName = scriptName; }
    public String getScriptType() { return scriptType; }
    public void setScriptType(String scriptType) { this.scriptType = scriptType; }
    public String getScriptContent() { return scriptContent; }
    public void setScriptContent(String scriptContent) { this.scriptContent = scriptContent; }
    public Long getDatasourceId() { return datasourceId; }
    public void setDatasourceId(Long datasourceId) { this.datasourceId = datasourceId; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
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
