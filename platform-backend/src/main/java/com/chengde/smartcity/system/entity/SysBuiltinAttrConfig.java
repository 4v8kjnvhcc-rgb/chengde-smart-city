package com.chengde.smartcity.system.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_builtin_attr_config")
public class SysBuiltinAttrConfig {

    @TableId
    private Long id;
    private Integer columnCode;
    private Integer columnName;
    private Integer dataType;
    private Integer lengthVal;
    private Integer componentType;
    private Integer nullableFlag;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getColumnCode() { return columnCode; }
    public void setColumnCode(Integer columnCode) { this.columnCode = columnCode; }
    public Integer getColumnName() { return columnName; }
    public void setColumnName(Integer columnName) { this.columnName = columnName; }
    public Integer getDataType() { return dataType; }
    public void setDataType(Integer dataType) { this.dataType = dataType; }
    public Integer getLengthVal() { return lengthVal; }
    public void setLengthVal(Integer lengthVal) { this.lengthVal = lengthVal; }
    public Integer getComponentType() { return componentType; }
    public void setComponentType(Integer componentType) { this.componentType = componentType; }
    public Integer getNullableFlag() { return nullableFlag; }
    public void setNullableFlag(Integer nullableFlag) { this.nullableFlag = nullableFlag; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
