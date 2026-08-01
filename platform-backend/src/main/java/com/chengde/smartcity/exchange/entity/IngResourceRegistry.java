package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_resource_registry")
public class IngResourceRegistry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String registryCode;
    private String resourceCode;
    private String title;
    private String providerOrg;
    private String resourceFormat;
    private String shareType;
    private String updateCycle;
    private String description;
    private String categoryPath;
    private Long categoryId;
    private String secretLevel;
    private String publishStatus;
    private String approvalStatus;
    private Long refSourceId;
    private Long refTableId;
    private String assetSummary;
    private Long portalCatalogId;
    private String createdBy;
    private Long orgId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRegistryCode() { return registryCode; }
    public void setRegistryCode(String registryCode) { this.registryCode = registryCode; }
    public String getResourceCode() { return resourceCode; }
    public void setResourceCode(String resourceCode) { this.resourceCode = resourceCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
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
    public String getCategoryPath() { return categoryPath; }
    public void setCategoryPath(String categoryPath) { this.categoryPath = categoryPath; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getSecretLevel() { return secretLevel; }
    public void setSecretLevel(String secretLevel) { this.secretLevel = secretLevel; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public Long getRefSourceId() { return refSourceId; }
    public void setRefSourceId(Long refSourceId) { this.refSourceId = refSourceId; }
    public Long getRefTableId() { return refTableId; }
    public void setRefTableId(Long refTableId) { this.refTableId = refTableId; }
    public String getAssetSummary() { return assetSummary; }
    public void setAssetSummary(String assetSummary) { this.assetSummary = assetSummary; }
    public Long getPortalCatalogId() { return portalCatalogId; }
    public void setPortalCatalogId(Long portalCatalogId) { this.portalCatalogId = portalCatalogId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
