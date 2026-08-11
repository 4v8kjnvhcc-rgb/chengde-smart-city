package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_quality_scheme_rule")
public class GovQualitySchemeRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long schemeId;
    private Long modelRuleId;
    private String ruleTypeName;
    private String ruleName;
    private String tableName;
    private String fieldNames;
    private String checkType;
    private Integer sortNo;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSchemeId() { return schemeId; }
    public void setSchemeId(Long schemeId) { this.schemeId = schemeId; }
    public Long getModelRuleId() { return modelRuleId; }
    public void setModelRuleId(Long modelRuleId) { this.modelRuleId = modelRuleId; }
    public String getRuleTypeName() { return ruleTypeName; }
    public void setRuleTypeName(String ruleTypeName) { this.ruleTypeName = ruleTypeName; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getFieldNames() { return fieldNames; }
    public void setFieldNames(String fieldNames) { this.fieldNames = fieldNames; }
    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
