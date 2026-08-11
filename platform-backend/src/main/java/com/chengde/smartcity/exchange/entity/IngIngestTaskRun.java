package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_ingest_task_run")
public class IngIngestTaskRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String triggerType;
    private String runStatus;
    private String scheduleResult;
    private Long collectedRows;
    private Long insertRows;
    private Long updateRows;
    private Integer tableCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private LocalDateTime scheduleTime;
    private Long dsInstanceId;
    private String message;
    private String errorDetail;
    private String logText;
    private String detailJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String runStatus) { this.runStatus = runStatus; }
    public String getScheduleResult() { return scheduleResult; }
    public void setScheduleResult(String scheduleResult) { this.scheduleResult = scheduleResult; }
    public Long getCollectedRows() { return collectedRows; }
    public void setCollectedRows(Long collectedRows) { this.collectedRows = collectedRows; }
    public Long getInsertRows() { return insertRows; }
    public void setInsertRows(Long insertRows) { this.insertRows = insertRows; }
    public Long getUpdateRows() { return updateRows; }
    public void setUpdateRows(Long updateRows) { this.updateRows = updateRows; }
    public Integer getTableCount() { return tableCount; }
    public void setTableCount(Integer tableCount) { this.tableCount = tableCount; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public LocalDateTime getScheduleTime() { return scheduleTime; }
    public void setScheduleTime(LocalDateTime scheduleTime) { this.scheduleTime = scheduleTime; }
    public Long getDsInstanceId() { return dsInstanceId; }
    public void setDsInstanceId(Long dsInstanceId) { this.dsInstanceId = dsInstanceId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }
    public String getLogText() { return logText; }
    public void setLogText(String logText) { this.logText = logText; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
