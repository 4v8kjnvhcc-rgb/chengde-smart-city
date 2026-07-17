package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rc_managed_table")
public class RcManagedTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long themeId;
    private String physicalTable;
    private String metaEntryCode;
    private Long fusionPhysicalId;
    private String catalogResourceCode;
    private Long recordCount;
    private Long dataBytes;
    private Long indexBytes;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getThemeId() { return themeId; }
    public void setThemeId(Long themeId) { this.themeId = themeId; }
    public String getPhysicalTable() { return physicalTable; }
    public void setPhysicalTable(String physicalTable) { this.physicalTable = physicalTable; }
    public String getMetaEntryCode() { return metaEntryCode; }
    public void setMetaEntryCode(String metaEntryCode) { this.metaEntryCode = metaEntryCode; }
    public Long getFusionPhysicalId() { return fusionPhysicalId; }
    public void setFusionPhysicalId(Long fusionPhysicalId) { this.fusionPhysicalId = fusionPhysicalId; }
    public String getCatalogResourceCode() { return catalogResourceCode; }
    public void setCatalogResourceCode(String catalogResourceCode) { this.catalogResourceCode = catalogResourceCode; }
    public Long getRecordCount() { return recordCount; }
    public void setRecordCount(Long recordCount) { this.recordCount = recordCount; }
    public Long getDataBytes() { return dataBytes; }
    public void setDataBytes(Long dataBytes) { this.dataBytes = dataBytes; }
    public Long getIndexBytes() { return indexBytes; }
    public void setIndexBytes(Long indexBytes) { this.indexBytes = indexBytes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
