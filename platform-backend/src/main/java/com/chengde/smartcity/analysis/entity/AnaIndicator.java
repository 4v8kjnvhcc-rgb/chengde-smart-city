package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ana_indicator")
public class AnaIndicator {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String domainCode;
    private Long queryId;
    private String resultField;
    private String fieldType;
    private Integer fieldLength;
    private Integer fieldPrecision;
    private String fieldName;
    private String indicatorCode;
    private String indicatorName;
    private String sourceTable;
    private String sourceColumn;
    private String aggFunc;
    private String exprText;
    private String unitLabel;
    private String description;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 列表展示：关联查询编号（非表字段） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String queryNo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDomainCode() { return domainCode; }
    public void setDomainCode(String domainCode) { this.domainCode = domainCode; }
    public Long getQueryId() { return queryId; }
    public void setQueryId(Long queryId) { this.queryId = queryId; }
    public String getResultField() { return resultField; }
    public void setResultField(String resultField) { this.resultField = resultField; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    public Integer getFieldLength() { return fieldLength; }
    public void setFieldLength(Integer fieldLength) { this.fieldLength = fieldLength; }
    public Integer getFieldPrecision() { return fieldPrecision; }
    public void setFieldPrecision(Integer fieldPrecision) { this.fieldPrecision = fieldPrecision; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public String getIndicatorName() { return indicatorName; }
    public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }
    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
    public String getSourceColumn() { return sourceColumn; }
    public void setSourceColumn(String sourceColumn) { this.sourceColumn = sourceColumn; }
    public String getAggFunc() { return aggFunc; }
    public void setAggFunc(String aggFunc) { this.aggFunc = aggFunc; }
    public String getExprText() { return exprText; }
    public void setExprText(String exprText) { this.exprText = exprText; }
    public String getUnitLabel() { return unitLabel; }
    public void setUnitLabel(String unitLabel) { this.unitLabel = unitLabel; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getQueryNo() { return queryNo; }
    public void setQueryNo(String queryNo) { this.queryNo = queryNo; }
}
