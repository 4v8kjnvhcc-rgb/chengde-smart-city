package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_quality_fix_sla")
public class GovQualityFixSla {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleCode;
    private String ruleName;
    private String severity;
    private Integer fixDays;
    private String overdueAction;
    private String notifyRoles;
    private String remark;
    private Integer sortNo;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Integer getFixDays() { return fixDays; }
    public void setFixDays(Integer fixDays) { this.fixDays = fixDays; }
    public String getOverdueAction() { return overdueAction; }
    public void setOverdueAction(String overdueAction) { this.overdueAction = overdueAction; }
    public String getNotifyRoles() { return notifyRoles; }
    public void setNotifyRoles(String notifyRoles) { this.notifyRoles = notifyRoles; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
