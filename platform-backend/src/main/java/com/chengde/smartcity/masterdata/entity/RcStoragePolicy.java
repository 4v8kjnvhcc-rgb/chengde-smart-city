package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rc_storage_policy")
public class RcStoragePolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String policyCode;
    private String policyName;
    private String actionType;
    private Integer retentionDays;
    private Long themeId;
    private Long managedTableId;
    private String storageStrategy;
    private Long backupLibraryId;
    private String tableRule;
    private Integer compressEnabled;
    private String compressType;
    private String destroyRule;
    private Integer scheduleEnabled;
    private String scheduleCron;
    private LocalDateTime nextRunAt;
    private LocalDateTime lastRunAt;
    private String lastRunStatus;
    private String lastRunMessage;
    private Long dsProjectCode;
    private Long dsDefinitionCode;
    private Integer dsScheduleId;
    private String dsPublishStatus;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPolicyCode() { return policyCode; }
    public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
    public Long getThemeId() { return themeId; }
    public void setThemeId(Long themeId) { this.themeId = themeId; }
    public Long getManagedTableId() { return managedTableId; }
    public void setManagedTableId(Long managedTableId) { this.managedTableId = managedTableId; }
    public String getStorageStrategy() { return storageStrategy; }
    public void setStorageStrategy(String storageStrategy) { this.storageStrategy = storageStrategy; }
    public Long getBackupLibraryId() { return backupLibraryId; }
    public void setBackupLibraryId(Long backupLibraryId) { this.backupLibraryId = backupLibraryId; }
    public String getTableRule() { return tableRule; }
    public void setTableRule(String tableRule) { this.tableRule = tableRule; }
    public Integer getCompressEnabled() { return compressEnabled; }
    public void setCompressEnabled(Integer compressEnabled) { this.compressEnabled = compressEnabled; }
    public String getCompressType() { return compressType; }
    public void setCompressType(String compressType) { this.compressType = compressType; }
    public String getDestroyRule() { return destroyRule; }
    public void setDestroyRule(String destroyRule) { this.destroyRule = destroyRule; }
    public Integer getScheduleEnabled() { return scheduleEnabled; }
    public void setScheduleEnabled(Integer scheduleEnabled) { this.scheduleEnabled = scheduleEnabled; }
    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getLastRunStatus() { return lastRunStatus; }
    public void setLastRunStatus(String lastRunStatus) { this.lastRunStatus = lastRunStatus; }
    public String getLastRunMessage() { return lastRunMessage; }
    public void setLastRunMessage(String lastRunMessage) { this.lastRunMessage = lastRunMessage; }
    public Long getDsProjectCode() { return dsProjectCode; }
    public void setDsProjectCode(Long dsProjectCode) { this.dsProjectCode = dsProjectCode; }
    public Long getDsDefinitionCode() { return dsDefinitionCode; }
    public void setDsDefinitionCode(Long dsDefinitionCode) { this.dsDefinitionCode = dsDefinitionCode; }
    public Integer getDsScheduleId() { return dsScheduleId; }
    public void setDsScheduleId(Integer dsScheduleId) { this.dsScheduleId = dsScheduleId; }
    public String getDsPublishStatus() { return dsPublishStatus; }
    public void setDsPublishStatus(String dsPublishStatus) { this.dsPublishStatus = dsPublishStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
