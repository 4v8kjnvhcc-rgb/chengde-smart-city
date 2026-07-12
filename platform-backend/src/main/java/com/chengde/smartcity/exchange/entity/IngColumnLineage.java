package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ing_column_lineage")
public class IngColumnLineage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tableNode;
    private String columnCode;
    private String columnName;
    private String upstreamTable;
    private String upstreamColumn;
    private String downstreamTable;
    private String downstreamColumn;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTableNode() { return tableNode; }
    public void setTableNode(String tableNode) { this.tableNode = tableNode; }
    public String getColumnCode() { return columnCode; }
    public void setColumnCode(String columnCode) { this.columnCode = columnCode; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getUpstreamTable() { return upstreamTable; }
    public void setUpstreamTable(String upstreamTable) { this.upstreamTable = upstreamTable; }
    public String getUpstreamColumn() { return upstreamColumn; }
    public void setUpstreamColumn(String upstreamColumn) { this.upstreamColumn = upstreamColumn; }
    public String getDownstreamTable() { return downstreamTable; }
    public void setDownstreamTable(String downstreamTable) { this.downstreamTable = downstreamTable; }
    public String getDownstreamColumn() { return downstreamColumn; }
    public void setDownstreamColumn(String downstreamColumn) { this.downstreamColumn = downstreamColumn; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
