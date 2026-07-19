package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ing_lineage_edge")
public class IngLineageEdge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fromNode;
    private String toNode;
    private String fromLabel;
    private String toLabel;
    private String edgeType;
    private String fieldMapping;
    private Integer sortOrder;
    private Long projectId;
    private Long fromSourceId;
    private Long toSourceId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFromNode() { return fromNode; }
    public void setFromNode(String fromNode) { this.fromNode = fromNode; }
    public String getToNode() { return toNode; }
    public void setToNode(String toNode) { this.toNode = toNode; }
    public String getFromLabel() { return fromLabel; }
    public void setFromLabel(String fromLabel) { this.fromLabel = fromLabel; }
    public String getToLabel() { return toLabel; }
    public void setToLabel(String toLabel) { this.toLabel = toLabel; }
    public String getEdgeType() { return edgeType; }
    public void setEdgeType(String edgeType) { this.edgeType = edgeType; }
    public String getFieldMapping() { return fieldMapping; }
    public void setFieldMapping(String fieldMapping) { this.fieldMapping = fieldMapping; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getFromSourceId() { return fromSourceId; }
    public void setFromSourceId(Long fromSourceId) { this.fromSourceId = fromSourceId; }
    public Long getToSourceId() { return toSourceId; }
    public void setToSourceId(Long toSourceId) { this.toSourceId = toSourceId; }
}
