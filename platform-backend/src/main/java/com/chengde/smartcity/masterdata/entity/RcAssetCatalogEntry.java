package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rc_asset_catalog_entry")
public class RcAssetCatalogEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String entryCode;
    private String entryName;
    private Long libId;
    private Long managedTableId;
    private String subsystemCode;
    private String visibility;
    private Integer encryptEnabled;
    private String encryptAlgo;
    private String publishStatus;
    private String rejectReason;
    private String description;
    private String driveTask;
    private String exchangeTaskRef;
    private LocalDateTime lastExchangeAt;
    private String lastExchangeMessage;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEntryCode() { return entryCode; }
    public void setEntryCode(String entryCode) { this.entryCode = entryCode; }
    public String getEntryName() { return entryName; }
    public void setEntryName(String entryName) { this.entryName = entryName; }
    public Long getLibId() { return libId; }
    public void setLibId(Long libId) { this.libId = libId; }
    public Long getManagedTableId() { return managedTableId; }
    public void setManagedTableId(Long managedTableId) { this.managedTableId = managedTableId; }
    public String getSubsystemCode() { return subsystemCode; }
    public void setSubsystemCode(String subsystemCode) { this.subsystemCode = subsystemCode; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public Integer getEncryptEnabled() { return encryptEnabled; }
    public void setEncryptEnabled(Integer encryptEnabled) { this.encryptEnabled = encryptEnabled; }
    public String getEncryptAlgo() { return encryptAlgo; }
    public void setEncryptAlgo(String encryptAlgo) { this.encryptAlgo = encryptAlgo; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDriveTask() { return driveTask; }
    public void setDriveTask(String driveTask) { this.driveTask = driveTask; }
    public String getExchangeTaskRef() { return exchangeTaskRef; }
    public void setExchangeTaskRef(String exchangeTaskRef) { this.exchangeTaskRef = exchangeTaskRef; }
    public LocalDateTime getLastExchangeAt() { return lastExchangeAt; }
    public void setLastExchangeAt(LocalDateTime lastExchangeAt) { this.lastExchangeAt = lastExchangeAt; }
    public String getLastExchangeMessage() { return lastExchangeMessage; }
    public void setLastExchangeMessage(String lastExchangeMessage) { this.lastExchangeMessage = lastExchangeMessage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
