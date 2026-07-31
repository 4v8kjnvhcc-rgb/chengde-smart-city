package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_mask_rule")
public class IngMaskRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleCode;
    private String ruleName;
    private String algoType;
    private String paramJson;
    private Integer reversible;
    private String matchFieldPattern;
    private String matchDataType;
    private String matchSensitiveTag;
    private String failPolicy;
    private Integer versionNo;
    private String status;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getAlgoType() { return algoType; }
    public void setAlgoType(String algoType) { this.algoType = algoType; }
    public String getParamJson() { return paramJson; }
    public void setParamJson(String paramJson) { this.paramJson = paramJson; }
    public Integer getReversible() { return reversible; }
    public void setReversible(Integer reversible) { this.reversible = reversible; }
    public String getMatchFieldPattern() { return matchFieldPattern; }
    public void setMatchFieldPattern(String matchFieldPattern) { this.matchFieldPattern = matchFieldPattern; }
    public String getMatchDataType() { return matchDataType; }
    public void setMatchDataType(String matchDataType) { this.matchDataType = matchDataType; }
    public String getMatchSensitiveTag() { return matchSensitiveTag; }
    public void setMatchSensitiveTag(String matchSensitiveTag) { this.matchSensitiveTag = matchSensitiveTag; }
    public String getFailPolicy() { return failPolicy; }
    public void setFailPolicy(String failPolicy) { this.failPolicy = failPolicy; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
