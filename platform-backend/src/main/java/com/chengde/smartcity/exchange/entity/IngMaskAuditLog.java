package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_mask_audit_log")
public class IngMaskAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String actionType;
    private Long policyId;
    private Integer policyVersion;
    private Long ruleId;
    private String targetType;
    private Long targetId;
    private String sceneCode;
    private Integer restored;
    private String sampleBefore;
    private String sampleAfter;
    private String result;
    private Long operatorId;
    private String operatorName;
    private String detailJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long policyId) { this.policyId = policyId; }
    public Integer getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(Integer policyVersion) { this.policyVersion = policyVersion; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getSceneCode() { return sceneCode; }
    public void setSceneCode(String sceneCode) { this.sceneCode = sceneCode; }
    public Integer getRestored() { return restored; }
    public void setRestored(Integer restored) { this.restored = restored; }
    public String getSampleBefore() { return sampleBefore; }
    public void setSampleBefore(String sampleBefore) { this.sampleBefore = sampleBefore; }
    public String getSampleAfter() { return sampleAfter; }
    public void setSampleAfter(String sampleAfter) { this.sampleAfter = sampleAfter; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
