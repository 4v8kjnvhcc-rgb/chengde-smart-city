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
    private String omEntityId;
    private String omSyncStatus;
    private LocalDateTime omSyncedAt;
    private Long modelId;
    private Long runId;
    private Long dataSourceId;
    private Long sourceTableId;
    private String physicalTableName;
    private String databaseName;
    private String schemaName;
    private String dataLayer;
    private String businessDomain;
    private String ownerName;
    private String conformStatus;
    private String conformReport;
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
    public String getOmEntityId() { return omEntityId; }
    public void setOmEntityId(String omEntityId) { this.omEntityId = omEntityId; }
    public String getOmSyncStatus() { return omSyncStatus; }
    public void setOmSyncStatus(String omSyncStatus) { this.omSyncStatus = omSyncStatus; }
    public LocalDateTime getOmSyncedAt() { return omSyncedAt; }
    public void setOmSyncedAt(LocalDateTime omSyncedAt) { this.omSyncedAt = omSyncedAt; }
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
    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public String getDataLayer() { return dataLayer; }
    public void setDataLayer(String dataLayer) { this.dataLayer = dataLayer; }
    public String getBusinessDomain() { return businessDomain; }
    public void setBusinessDomain(String businessDomain) { this.businessDomain = businessDomain; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getConformStatus() { return conformStatus; }
    public void setConformStatus(String conformStatus) { this.conformStatus = conformStatus; }
    public String getConformReport() { return conformReport; }
    public void setConformReport(String conformReport) { this.conformReport = conformReport; }
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
