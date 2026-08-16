package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@TableName("ind_field")
public class IndField {
    @TableId(value = "uuid", type = IdType.INPUT)
    private String uuid;
    private String name;
    private String tag;
    private String fieldName;
    private String fieldType;
    private Integer fieldLength;
    private Integer fieldPrecision;
    private Integer fieldPosition;
    private String groupId;
    private String sqlId;
    private String sqlSerial;
    private String rsColumn;
    private Integer useDefault;
    private String defaultValue;

    @TableField(exist = false)
    private String queryNo;

    @JsonProperty("id")
    public String getId() { return uuid; }
    @JsonProperty("id")
    public void setId(String id) { this.uuid = id; }
    @JsonIgnore
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    @JsonProperty("indicatorName")
    public String getIndicatorName() { return name; }
    @JsonProperty("indicatorName")
    public void setIndicatorName(String indicatorName) { this.name = indicatorName; }
    @JsonIgnore
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @JsonProperty("indicatorFlag")
    public String getIndicatorFlag() { return tag; }
    @JsonProperty("indicatorFlag")
    public void setIndicatorFlag(String indicatorFlag) { this.tag = indicatorFlag; }
    @JsonIgnore
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    public Integer getFieldLength() { return fieldLength; }
    public void setFieldLength(Integer fieldLength) { this.fieldLength = fieldLength; }
    public Integer getFieldPrecision() { return fieldPrecision; }
    public void setFieldPrecision(Integer fieldPrecision) { this.fieldPrecision = fieldPrecision; }
    public Integer getFieldPosition() { return fieldPosition; }
    public void setFieldPosition(Integer fieldPosition) { this.fieldPosition = fieldPosition; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getSqlId() { return sqlId; }
    public void setSqlId(String sqlId) { this.sqlId = sqlId; }
    public String getSqlSerial() { return sqlSerial; }
    public void setSqlSerial(String sqlSerial) { this.sqlSerial = sqlSerial; }

    @JsonProperty("resultField")
    public String getResultField() { return rsColumn; }
    @JsonProperty("resultField")
    public void setResultField(String resultField) { this.rsColumn = resultField; }
    @JsonIgnore
    public String getRsColumn() { return rsColumn; }
    public void setRsColumn(String rsColumn) { this.rsColumn = rsColumn; }

    public Integer getUseDefault() { return useDefault; }
    public void setUseDefault(Integer useDefault) { this.useDefault = useDefault; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public String getQueryNo() { return queryNo != null ? queryNo : sqlSerial; }
    public void setQueryNo(String queryNo) { this.queryNo = queryNo; }
}
