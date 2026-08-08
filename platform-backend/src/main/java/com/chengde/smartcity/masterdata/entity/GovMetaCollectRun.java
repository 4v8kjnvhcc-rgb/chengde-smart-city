package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_meta_collect_run")
public class GovMetaCollectRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String triggerType;
    private Long dsInstanceId;
    private String dsState;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer tableCount;
    private String summary;
    private String logText;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public Long getDsInstanceId() { return dsInstanceId; }
    public void setDsInstanceId(Long dsInstanceId) { this.dsInstanceId = dsInstanceId; }
    public String getDsState() { return dsState; }
    public void setDsState(String dsState) { this.dsState = dsState; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public Integer getTableCount() { return tableCount; }
    public void setTableCount(Integer tableCount) { this.tableCount = tableCount; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getLogText() { return logText; }
    public void setLogText(String logText) { this.logText = logText; }
}
