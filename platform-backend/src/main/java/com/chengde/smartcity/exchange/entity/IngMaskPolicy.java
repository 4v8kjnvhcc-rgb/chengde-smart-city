package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_mask_policy")
public class IngMaskPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String policyCode;
    private String policyName;
    private String sceneCode;
    private String matchLevelCode;
    private Long matchCategoryId;
    private String matchRole;
    private String matchPurpose;
    private String matchEnv;
    private Integer priority;
    private Integer strictMode;
    private String ruleIdsJson;
    private String fieldOverridesJson;
    private Integer versionNo;
    private String status;
    private LocalDateTime publishedAt;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPolicyCode() { return policyCode; }
    public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }
    public String getSceneCode() { return sceneCode; }
    public void setSceneCode(String sceneCode) { this.sceneCode = sceneCode; }
    public String getMatchLevelCode() { return matchLevelCode; }
    public void setMatchLevelCode(String matchLevelCode) { this.matchLevelCode = matchLevelCode; }
    public Long getMatchCategoryId() { return matchCategoryId; }
    public void setMatchCategoryId(Long matchCategoryId) { this.matchCategoryId = matchCategoryId; }
    public String getMatchRole() { return matchRole; }
    public void setMatchRole(String matchRole) { this.matchRole = matchRole; }
    public String getMatchPurpose() { return matchPurpose; }
    public void setMatchPurpose(String matchPurpose) { this.matchPurpose = matchPurpose; }
    public String getMatchEnv() { return matchEnv; }
    public void setMatchEnv(String matchEnv) { this.matchEnv = matchEnv; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Integer getStrictMode() { return strictMode; }
    public void setStrictMode(Integer strictMode) { this.strictMode = strictMode; }
    public String getRuleIdsJson() { return ruleIdsJson; }
    public void setRuleIdsJson(String ruleIdsJson) { this.ruleIdsJson = ruleIdsJson; }
    public String getFieldOverridesJson() { return fieldOverridesJson; }
    public void setFieldOverridesJson(String fieldOverridesJson) { this.fieldOverridesJson = fieldOverridesJson; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
