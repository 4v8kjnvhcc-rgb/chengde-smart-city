package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ana_indicator_group")
public class AnaIndicatorGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ownerDomainCode;
    private Long indicatorDomainId;
    private String groupName;
    private String targetTable;
    private String groupCategory;
    private String modelMethod;
    private String description;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String indicatorDomainName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOwnerDomainCode() { return ownerDomainCode; }
    public void setOwnerDomainCode(String ownerDomainCode) { this.ownerDomainCode = ownerDomainCode; }
    public Long getIndicatorDomainId() { return indicatorDomainId; }
    public void setIndicatorDomainId(Long indicatorDomainId) { this.indicatorDomainId = indicatorDomainId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public String getGroupCategory() { return groupCategory; }
    public void setGroupCategory(String groupCategory) { this.groupCategory = groupCategory; }
    public String getModelMethod() { return modelMethod; }
    public void setModelMethod(String modelMethod) { this.modelMethod = modelMethod; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getIndicatorDomainName() { return indicatorDomainName; }
    public void setIndicatorDomainName(String indicatorDomainName) { this.indicatorDomainName = indicatorDomainName; }
}
