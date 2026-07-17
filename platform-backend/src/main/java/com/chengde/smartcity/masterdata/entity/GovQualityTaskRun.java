package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("gov_quality_task_run")
public class GovQualityTaskRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private BigDecimal score;
    private Integer totalChecks;
    private Integer issueCount;
    private String message;
    private String triggeredBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public Integer getTotalChecks() { return totalChecks; }
    public void setTotalChecks(Integer totalChecks) { this.totalChecks = totalChecks; }
    public Integer getIssueCount() { return issueCount; }
    public void setIssueCount(Integer issueCount) { this.issueCount = issueCount; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
