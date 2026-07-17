package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_governance_task_run")
public class GovGovernanceTaskRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String transName; // Kettle转换名称
    private String status;
    private String kettleTransName;
    private Long producedRows;
    private Long dsInstanceId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer totalNodes;
    private Integer successNodes;
    private Integer failedNodes;
    private Integer rowCount;
    private Integer lineCount; // Kettle处理行数
    private String message;
    private String triggeredBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTransName() { return transName; }
    public void setTransName(String transName) { this.transName = transName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getKettleTransName() { return kettleTransName; }
    public void setKettleTransName(String kettleTransName) { this.kettleTransName = kettleTransName; }
    public Long getProducedRows() { return producedRows; }
    public void setProducedRows(Long producedRows) { this.producedRows = producedRows; }
    public Long getDsInstanceId() { return dsInstanceId; }
    public void setDsInstanceId(Long dsInstanceId) { this.dsInstanceId = dsInstanceId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public Integer getTotalNodes() { return totalNodes; }
    public void setTotalNodes(Integer totalNodes) { this.totalNodes = totalNodes; }
    public Integer getSuccessNodes() { return successNodes; }
    public void setSuccessNodes(Integer successNodes) { this.successNodes = successNodes; }
    public Integer getFailedNodes() { return failedNodes; }
    public void setFailedNodes(Integer failedNodes) { this.failedNodes = failedNodes; }
    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
    public Integer getLineCount() { return lineCount; }
    public void setLineCount(Integer lineCount) { this.lineCount = lineCount; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
