package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_catalog_resource")
public class GovCatalogResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String resourceCode;
    private String resourceName;
    private String resourceType;
    private String metadataEntryCode;
    private Long dataSourceId;
    private String physicalTableName;
    private String sourcePathType;
    private java.math.BigDecimal qualityScore;
    private String categoryPath;
    private Long categoryId;
    private String providerOrg;
    private String resourceFormat;
    private String shareType;
    private String updateCycle;
    private String description;
    private Integer secretFlag;
    private String publishStatus;
    private String approvalStatus;
    private String subscriptionStatus;
    private Integer versionNo;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getResourceCode() { return resourceCode; }
    public void setResourceCode(String resourceCode) { this.resourceCode = resourceCode; }
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getMetadataEntryCode() { return metadataEntryCode; }
    public void setMetadataEntryCode(String metadataEntryCode) { this.metadataEntryCode = metadataEntryCode; }
    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long dataSourceId) { this.dataSourceId = dataSourceId; }
    public String getPhysicalTableName() { return physicalTableName; }
    public void setPhysicalTableName(String physicalTableName) { this.physicalTableName = physicalTableName; }
    public String getSourcePathType() { return sourcePathType; }
    public void setSourcePathType(String sourcePathType) { this.sourcePathType = sourcePathType; }
    public java.math.BigDecimal getQualityScore() { return qualityScore; }
    public void setQualityScore(java.math.BigDecimal qualityScore) { this.qualityScore = qualityScore; }
    public String getCategoryPath() { return categoryPath; }
    public void setCategoryPath(String categoryPath) { this.categoryPath = categoryPath; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getProviderOrg() { return providerOrg; }
    public void setProviderOrg(String providerOrg) { this.providerOrg = providerOrg; }
    public String getResourceFormat() { return resourceFormat; }
    public void setResourceFormat(String resourceFormat) { this.resourceFormat = resourceFormat; }
    public String getShareType() { return shareType; }
    public void setShareType(String shareType) { this.shareType = shareType; }
    public String getUpdateCycle() { return updateCycle; }
    public void setUpdateCycle(String updateCycle) { this.updateCycle = updateCycle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSecretFlag() { return secretFlag; }
    public void setSecretFlag(Integer secretFlag) { this.secretFlag = secretFlag; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(String subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
