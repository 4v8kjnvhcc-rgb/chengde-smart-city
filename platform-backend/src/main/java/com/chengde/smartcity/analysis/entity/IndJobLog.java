package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

@TableName("ind_job_log")
public class IndJobLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobName;
    private String jobId;
    private String groupId;
    private Integer runStatus;
    private LocalDateTime runTime;
    private Integer successCnt;
    private Integer failCnt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public Integer getRunStatus() { return runStatus; }
    public void setRunStatus(Integer runStatus) { this.runStatus = runStatus; }
    public LocalDateTime getRunTime() { return runTime; }
    public void setRunTime(LocalDateTime runTime) { this.runTime = runTime; }
    public Integer getSuccessCnt() { return successCnt; }
    public void setSuccessCnt(Integer successCnt) { this.successCnt = successCnt; }
    public Integer getFailCnt() { return failCnt; }
    public void setFailCnt(Integer failCnt) { this.failCnt = failCnt; }

    @JsonProperty("triggerType")
    public String getTriggerType() { return "MANUAL"; }
    @JsonProperty("execStatus")
    public String getExecStatus() {
        if (runStatus != null && runStatus == 3) return "SUCCESS";
        if (runStatus != null && runStatus == 4) return "FAILED";
        if (runStatus != null && runStatus == 2) return "RUNNING";
        return "NONE";
    }
    @JsonProperty("calcResult")
    public String getCalcResult() {
        int s = successCnt == null ? 0 : successCnt;
        int f = failCnt == null ? 0 : failCnt;
        if (s > 0 && f == 0) return "ALL_SUCCESS";
        if (s > 0) return "PARTIAL";
        if (f > 0) return "FAILED";
        return "NONE";
    }
    @JsonProperty("message")
    public String getMessage() {
        return "成功 " + (successCnt == null ? 0 : successCnt) + "，失败 " + (failCnt == null ? 0 : failCnt);
    }
    @JsonProperty("startedAt")
    public LocalDateTime getStartedAt() { return runTime; }
}
