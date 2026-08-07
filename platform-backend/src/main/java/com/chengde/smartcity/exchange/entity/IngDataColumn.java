package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ing_data_column")
public class IngDataColumn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tableId;
    private String columnCode;
    private String columnName;
    private String dataType;
    private Integer nullableFlag;
    private Integer pkFlag;
    private Integer sortOrder;
    private String semanticDesc;
    private Integer lengthVal;
    private String componentType;
    private String requiredTip;
    private Integer builtInFlag;
    private Integer partitionColFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public String getColumnCode() { return columnCode; }
    public void setColumnCode(String columnCode) { this.columnCode = columnCode; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Integer getNullableFlag() { return nullableFlag; }
    public void setNullableFlag(Integer nullableFlag) { this.nullableFlag = nullableFlag; }
    public Integer getPkFlag() { return pkFlag; }
    public void setPkFlag(Integer pkFlag) { this.pkFlag = pkFlag; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getSemanticDesc() { return semanticDesc; }
    public void setSemanticDesc(String semanticDesc) { this.semanticDesc = semanticDesc; }
    public Integer getLengthVal() { return lengthVal; }
    public void setLengthVal(Integer lengthVal) { this.lengthVal = lengthVal; }
    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }
    public String getRequiredTip() { return requiredTip; }
    public void setRequiredTip(String requiredTip) { this.requiredTip = requiredTip; }
    public Integer getBuiltInFlag() { return builtInFlag; }
    public void setBuiltInFlag(Integer builtInFlag) { this.builtInFlag = builtInFlag; }
    public Integer getPartitionColFlag() { return partitionColFlag; }
    public void setPartitionColFlag(Integer partitionColFlag) { this.partitionColFlag = partitionColFlag; }
}
