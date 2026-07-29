package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_asset_catalog_reg")
public class IngAssetCatalogReg {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String assetName;
    private String assetDesc;
    private String ownerName;
    private String contactInfo;
    private String dataTags;
    private Long orgId;
    private String orgName;
    private Long projectId;
    private String projectName;
    private Long sourceId;
    private String systemName;
    private Long tableId;
    private String tableName;
    private String accessMode;
    private String formatType;
    private String transferMode;
    private Integer formatLocked;
    private String bizPurpose;
    private String bizScenario;
    private String accessScope;
    private String controlReq;
    private String qualityFilePath;
    private String qualityFileName;
    private String riskFilePath;
    private String riskFileName;
    private String otherInfo;
    private String status;
    private String rejectReason;
    private LocalDateTime reportedAt;
    private LocalDateTime archivedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public String getAssetDesc() { return assetDesc; }
    public void setAssetDesc(String assetDesc) { this.assetDesc = assetDesc; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }
    public String getDataTags() { return dataTags; }
    public void setDataTags(String dataTags) { this.dataTags = dataTags; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getAccessMode() { return accessMode; }
    public void setAccessMode(String accessMode) { this.accessMode = accessMode; }
    public String getFormatType() { return formatType; }
    public void setFormatType(String formatType) { this.formatType = formatType; }
    public String getTransferMode() { return transferMode; }
    public void setTransferMode(String transferMode) { this.transferMode = transferMode; }
    public Integer getFormatLocked() { return formatLocked; }
    public void setFormatLocked(Integer formatLocked) { this.formatLocked = formatLocked; }
    public String getBizPurpose() { return bizPurpose; }
    public void setBizPurpose(String bizPurpose) { this.bizPurpose = bizPurpose; }
    public String getBizScenario() { return bizScenario; }
    public void setBizScenario(String bizScenario) { this.bizScenario = bizScenario; }
    public String getAccessScope() { return accessScope; }
    public void setAccessScope(String accessScope) { this.accessScope = accessScope; }
    public String getControlReq() { return controlReq; }
    public void setControlReq(String controlReq) { this.controlReq = controlReq; }
    public String getQualityFilePath() { return qualityFilePath; }
    public void setQualityFilePath(String qualityFilePath) { this.qualityFilePath = qualityFilePath; }
    public String getQualityFileName() { return qualityFileName; }
    public void setQualityFileName(String qualityFileName) { this.qualityFileName = qualityFileName; }
    public String getRiskFilePath() { return riskFilePath; }
    public void setRiskFilePath(String riskFilePath) { this.riskFilePath = riskFilePath; }
    public String getRiskFileName() { return riskFileName; }
    public void setRiskFileName(String riskFileName) { this.riskFileName = riskFileName; }
    public String getOtherInfo() { return otherInfo; }
    public void setOtherInfo(String otherInfo) { this.otherInfo = otherInfo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public LocalDateTime getReportedAt() { return reportedAt; }
    public void setReportedAt(LocalDateTime reportedAt) { this.reportedAt = reportedAt; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
