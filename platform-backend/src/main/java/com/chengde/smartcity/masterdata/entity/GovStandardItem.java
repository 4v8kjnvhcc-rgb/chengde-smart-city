package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_standard_item")
public class GovStandardItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String itemCode;
    private String itemName;
    private String itemType;
    private String standardRef;
    private String dataType;
    private String dataFormat;
    private String valueDomain;
    private String businessDefinition;
    private String businessRule;
    private String referenceStandard;
    private String category;
    private String sensitivity;
    private String publishStatus;
    private Integer versionNo;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public String getStandardRef() { return standardRef; }
    public void setStandardRef(String standardRef) { this.standardRef = standardRef; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getDataFormat() { return dataFormat; }
    public void setDataFormat(String dataFormat) { this.dataFormat = dataFormat; }
    public String getValueDomain() { return valueDomain; }
    public void setValueDomain(String valueDomain) { this.valueDomain = valueDomain; }
    public String getBusinessDefinition() { return businessDefinition; }
    public void setBusinessDefinition(String businessDefinition) { this.businessDefinition = businessDefinition; }
    public String getBusinessRule() { return businessRule; }
    public void setBusinessRule(String businessRule) { this.businessRule = businessRule; }
    public String getReferenceStandard() { return referenceStandard; }
    public void setReferenceStandard(String referenceStandard) { this.referenceStandard = referenceStandard; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSensitivity() { return sensitivity; }
    public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
