package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_metadata_registry")
public class GovMetadataRegistry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String entryCode;
    private String entryName;
    private String entryType;
    private String parentCode;
    private String omRef;
    private Long modelId;
    private Long runId;
    private Long dataSourceId;
    private Long sourceTableId;
    private String physicalTableName;
    private String description;
    private String tags;
    private String keywords;
    private String securityLevel;
    private String changeFlag;
    private String status;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEntryCode() { return entryCode; }
    public void setEntryCode(String entryCode) { this.entryCode = entryCode; }
    public String getEntryName() { return entryName; }
    public void setEntryName(String entryName) { this.entryName = entryName; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public String getParentCode() { return parentCode; }
    public void setParentCode(String parentCode) { this.parentCode = parentCode; }
    public String getOmRef() { return omRef; }
    public void setOmRef(String omRef) { this.omRef = omRef; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long dataSourceId) { this.dataSourceId = dataSourceId; }
    public Long getSourceTableId() { return sourceTableId; }
    public void setSourceTableId(Long sourceTableId) { this.sourceTableId = sourceTableId; }
    public String getPhysicalTableName() { return physicalTableName; }
    public void setPhysicalTableName(String physicalTableName) { this.physicalTableName = physicalTableName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public String getSecurityLevel() { return securityLevel; }
    public void setSecurityLevel(String securityLevel) { this.securityLevel = securityLevel; }
    public String getChangeFlag() { return changeFlag; }
    public void setChangeFlag(String changeFlag) { this.changeFlag = changeFlag; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
