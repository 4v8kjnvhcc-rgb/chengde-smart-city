package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("gov_standard_mapping")
public class GovStandardMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long standardItemId;
    private String sourceSystem;
    private String sourceTable;
    private String sourceColumn;
    private String mappingStatus;
    private BigDecimal matchScore;
    private String remark;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStandardItemId() { return standardItemId; }
    public void setStandardItemId(Long standardItemId) { this.standardItemId = standardItemId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
    public String getSourceColumn() { return sourceColumn; }
    public void setSourceColumn(String sourceColumn) { this.sourceColumn = sourceColumn; }
    public String getMappingStatus() { return mappingStatus; }
    public void setMappingStatus(String mappingStatus) { this.mappingStatus = mappingStatus; }
    public BigDecimal getMatchScore() { return matchScore; }
    public void setMatchScore(BigDecimal matchScore) { this.matchScore = matchScore; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
