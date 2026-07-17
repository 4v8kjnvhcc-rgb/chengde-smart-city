package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_governance_node_log")
public class GovGovernanceNodeLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long taskId;
    private String nodeId;
    private String nodeType;
    private String nodeName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer inputRows;
    private Integer outputRows;
    private String message;
    private String detailJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public Integer getInputRows() { return inputRows; }
    public void setInputRows(Integer inputRows) { this.inputRows = inputRows; }
    public Integer getOutputRows() { return outputRows; }
    public void setOutputRows(Integer outputRows) { this.outputRows = outputRows; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
