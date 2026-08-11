package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("gov_quality_model_rule")
public class GovQualityModelRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelId;
    private Long modelTableId;
    private Long ruleCatalogId;
    private String ruleTypeCode;
    private String ruleTypeName;
    private String ruleName;
    private String tableName;
    private String fieldNames;
    private String remark;
    private String checkType;
    private String configJson;
    private BigDecimal threshold;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public Long getModelTableId() { return modelTableId; }
    public void setModelTableId(Long modelTableId) { this.modelTableId = modelTableId; }
    public Long getRuleCatalogId() { return ruleCatalogId; }
    public void setRuleCatalogId(Long ruleCatalogId) { this.ruleCatalogId = ruleCatalogId; }
    public String getRuleTypeCode() { return ruleTypeCode; }
    public void setRuleTypeCode(String ruleTypeCode) { this.ruleTypeCode = ruleTypeCode; }
    public String getRuleTypeName() { return ruleTypeName; }
    public void setRuleTypeName(String ruleTypeName) { this.ruleTypeName = ruleTypeName; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getFieldNames() { return fieldNames; }
    public void setFieldNames(String fieldNames) { this.fieldNames = fieldNames; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
