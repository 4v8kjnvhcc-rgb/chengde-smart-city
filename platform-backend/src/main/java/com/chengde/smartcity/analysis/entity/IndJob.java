package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

@TableName("ind_job")
public class IndJob {
    @TableId(value = "uuid", type = IdType.INPUT)
    private String uuid;
    private String name;
    private String groupId;
    private Integer cronCodeId;
    private String xxlJobId;
    private Integer runStatus;
    private LocalDateTime runTime;
    private String executorAddress;
    private String remark;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
    private Integer publishStatus;
    private LocalDateTime publishTime;
    private String publishBy;
    private Integer retryNum;
    private Integer status;
    private Integer calcResult;
    private Long dsProjectCode;
    private Long dsDefinitionCode;
    private Integer dsScheduleId;
    private Long dsInstanceId;
    private String scheduleCron;
    private String lastLog;

    @TableField(exist = false)
    private String ownerDomainCode;

    @JsonProperty("id")
    public String getId() { return uuid; }
    @JsonProperty("id")
    public void setId(String id) { this.uuid = id; }
    @JsonIgnore
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    @JsonProperty("taskName")
    public String getTaskName() { return name; }
    @JsonProperty("taskName")
    public void setTaskName(String taskName) { this.name = taskName; }
    @JsonIgnore
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public Integer getCronCodeId() { return cronCodeId; }
    public void setCronCodeId(Integer cronCodeId) { this.cronCodeId = cronCodeId; }
    public String getXxlJobId() { return xxlJobId; }
    public void setXxlJobId(String xxlJobId) { this.xxlJobId = xxlJobId; }
    public Integer getRunStatus() { return runStatus; }
    public void setRunStatus(Integer runStatus) { this.runStatus = runStatus; }
    public LocalDateTime getRunTime() { return runTime; }
    public void setRunTime(LocalDateTime runTime) { this.runTime = runTime; }
    public String getExecutorAddress() { return executorAddress; }
    public void setExecutorAddress(String executorAddress) { this.executorAddress = executorAddress; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    @JsonIgnore
    public Integer getPublishStatusRaw() { return publishStatus; }
    public void setPublishStatus(Integer publishStatus) { this.publishStatus = publishStatus; }
    @JsonIgnore
    public Integer getPublishStatusInt() { return publishStatus; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }
    public String getPublishBy() { return publishBy; }
    public void setPublishBy(String publishBy) { this.publishBy = publishBy; }
    public Integer getRetryNum() { return retryNum; }
    public void setRetryNum(Integer retryNum) { this.retryNum = retryNum; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getCalcResultRaw() { return calcResult; }
    public void setCalcResult(Integer calcResult) { this.calcResult = calcResult; }
    public Long getDsProjectCode() { return dsProjectCode; }
    public void setDsProjectCode(Long dsProjectCode) { this.dsProjectCode = dsProjectCode; }
    public Long getDsDefinitionCode() { return dsDefinitionCode; }
    public void setDsDefinitionCode(Long dsDefinitionCode) { this.dsDefinitionCode = dsDefinitionCode; }
    public Integer getDsScheduleId() { return dsScheduleId; }
    public void setDsScheduleId(Integer dsScheduleId) { this.dsScheduleId = dsScheduleId; }
    public Long getDsInstanceId() { return dsInstanceId; }
    public void setDsInstanceId(Long dsInstanceId) { this.dsInstanceId = dsInstanceId; }
    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }
    public String getLastLog() { return lastLog; }
    public void setLastLog(String lastLog) { this.lastLog = lastLog; }
    public String getOwnerDomainCode() { return ownerDomainCode; }
    public void setOwnerDomainCode(String ownerDomainCode) { this.ownerDomainCode = ownerDomainCode; }

    @JsonProperty("execCycle")
    public String getExecCycle() {
        return cronCodeId != null && cronCodeId == 1 ? "DAILY" : "MONTHLY";
    }

    @JsonProperty("scheduleStatus")
    public String getScheduleStatus() {
        if (status != null && status == 3) return "STARTED";
        return "STOPPED";
    }

    @JsonProperty("execStatus")
    public String getExecStatus() {
        if (runStatus == null || runStatus == 1) return "NONE";
        if (runStatus == 2) return "RUNNING";
        if (runStatus == 3) return "SUCCESS";
        if (runStatus == 4) return "FAILED";
        return "NONE";
    }

    @JsonProperty("calcResult")
    public String getCalcResult() {
        if (calcResult == null || calcResult == 0) return "NONE";
        if (calcResult == 1) return "ALL_SUCCESS";
        if (calcResult == 2) return "PARTIAL";
        return "FAILED";
    }

    @JsonProperty("publishStatus")
    public String getPublishStatus() {
        if (publishStatus != null && publishStatus == 1) return "PUBLISHED";
        if (publishStatus != null && publishStatus == 2) return "OFFLINE";
        return "DRAFT";
    }

    @JsonProperty("publishedAt")
    public LocalDateTime getPublishedAt() { return publishTime; }
    @JsonProperty("lastRunAt")
    public LocalDateTime getLastRunAt() { return runTime; }
    @JsonProperty("lastRunMessage")
    public String getLastRunMessage() { return remark; }
    @JsonProperty("createdAt")
    public LocalDateTime getCreatedAt() { return createTime; }
    @JsonProperty("updatedAt")
    public LocalDateTime getUpdatedAt() { return updateTime; }
}
