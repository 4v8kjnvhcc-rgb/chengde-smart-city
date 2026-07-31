package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_cls_scope_rule")
public class IngClsScopeRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleCode;
    private String ruleName;
    private String levelCode;
    private Long categoryId;
    private String actionType;
    private Integer allowFlag;
    private Integer maskRequired;
    private Integer approvalRequired;
    private String subjectScope;
    private Integer severityWeight;
    private String remark;
    private String status;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getLevelCode() { return levelCode; }
    public void setLevelCode(String levelCode) { this.levelCode = levelCode; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public Integer getAllowFlag() { return allowFlag; }
    public void setAllowFlag(Integer allowFlag) { this.allowFlag = allowFlag; }
    public Integer getMaskRequired() { return maskRequired; }
    public void setMaskRequired(Integer maskRequired) { this.maskRequired = maskRequired; }
    public Integer getApprovalRequired() { return approvalRequired; }
    public void setApprovalRequired(Integer approvalRequired) { this.approvalRequired = approvalRequired; }
    public String getSubjectScope() { return subjectScope; }
    public void setSubjectScope(String subjectScope) { this.subjectScope = subjectScope; }
    public Integer getSeverityWeight() { return severityWeight; }
    public void setSeverityWeight(Integer severityWeight) { this.severityWeight = severityWeight; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
