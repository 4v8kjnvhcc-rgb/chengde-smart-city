package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_quality_issue")
public class GovQualityIssue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long taskId;
    private Long ruleId;
    private Long detailId;
    private String checkType;
    private String targetTable;
    private String targetColumn;
    private String issueType;
    private String issueValue;
    private Integer issueCount;
    private String sampleData;
    private String severity;
    private String status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getDetailId() { return detailId; }
    public void setDetailId(Long detailId) { this.detailId = detailId; }
    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }
    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public String getTargetColumn() { return targetColumn; }
    public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }
    public String getIssueValue() { return issueValue; }
    public void setIssueValue(String issueValue) { this.issueValue = issueValue; }
    public Integer getIssueCount() { return issueCount; }
    public void setIssueCount(Integer issueCount) { this.issueCount = issueCount; }
    public String getSampleData() { return sampleData; }
    public void setSampleData(String sampleData) { this.sampleData = sampleData; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
