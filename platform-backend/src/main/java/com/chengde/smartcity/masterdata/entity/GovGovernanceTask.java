package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_governance_task")
public class GovGovernanceTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskCode;
    private String taskName;
    private String description;
    private String graphJson;
    private String fusionSpecJson;
    private String status;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime lastRunAt;
    private String lastMessage;
    private Integer scheduleEnabled;
    private String scheduleCron;
    private String scheduleMode;
    private LocalDateTime startTime;
    private String timeUnit;
    private Integer intervalValue;
    private LocalDateTime nextRunAt;
    private String variablesJson;
    private String engineType;
    /** GOVERNANCE=ODS→DWD；FUSION=DWD→DWS/ADS */
    private String taskDomain;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskCode() { return taskCode; }
    public void setTaskCode(String taskCode) { this.taskCode = taskCode; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getGraphJson() { return graphJson; }
    public void setGraphJson(String graphJson) { this.graphJson = graphJson; }
    public String getFusionSpecJson() { return fusionSpecJson; }
    public void setFusionSpecJson(String fusionSpecJson) { this.fusionSpecJson = fusionSpecJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }
    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public Integer getScheduleEnabled() { return scheduleEnabled; }
    public void setScheduleEnabled(Integer scheduleEnabled) { this.scheduleEnabled = scheduleEnabled; }
    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }
    public String getScheduleMode() { return scheduleMode; }
    public void setScheduleMode(String scheduleMode) { this.scheduleMode = scheduleMode; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public String getTimeUnit() { return timeUnit; }
    public void setTimeUnit(String timeUnit) { this.timeUnit = timeUnit; }
    public Integer getIntervalValue() { return intervalValue; }
    public void setIntervalValue(Integer intervalValue) { this.intervalValue = intervalValue; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
    public String getVariablesJson() { return variablesJson; }
    public void setVariablesJson(String variablesJson) { this.variablesJson = variablesJson; }
    public String getEngineType() { return engineType; }
    public void setEngineType(String engineType) { this.engineType = engineType; }
    public String getTaskDomain() { return taskDomain; }
    public void setTaskDomain(String taskDomain) { this.taskDomain = taskDomain; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
