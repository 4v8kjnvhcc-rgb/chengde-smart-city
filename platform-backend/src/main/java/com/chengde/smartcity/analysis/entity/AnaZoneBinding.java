package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ana_zone_binding")
public class AnaZoneBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String domainCode;
    private String zoneCode;
    private String assetType;
    private String assetRef;
    private String assetName;
    private String physicalTable;
    private String metaEntryCode;
    private String dataLayer;
    /** DATATYPE | LATENCY */
    private String dimGroup;
    /** STRUCT | UNSTRUCT | API | CDC */
    private String accessMode;
    private String remark;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDomainCode() { return domainCode; }
    public void setDomainCode(String domainCode) { this.domainCode = domainCode; }
    public String getZoneCode() { return zoneCode; }
    public void setZoneCode(String zoneCode) { this.zoneCode = zoneCode; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public String getAssetRef() { return assetRef; }
    public void setAssetRef(String assetRef) { this.assetRef = assetRef; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public String getPhysicalTable() { return physicalTable; }
    public void setPhysicalTable(String physicalTable) { this.physicalTable = physicalTable; }
    public String getMetaEntryCode() { return metaEntryCode; }
    public void setMetaEntryCode(String metaEntryCode) { this.metaEntryCode = metaEntryCode; }
    public String getDataLayer() { return dataLayer; }
    public void setDataLayer(String dataLayer) { this.dataLayer = dataLayer; }
    public String getDimGroup() { return dimGroup; }
    public void setDimGroup(String dimGroup) { this.dimGroup = dimGroup; }
    public String getAccessMode() { return accessMode; }
    public void setAccessMode(String accessMode) { this.accessMode = accessMode; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
