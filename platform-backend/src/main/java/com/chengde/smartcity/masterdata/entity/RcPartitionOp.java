package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rc_partition_op")
public class RcPartitionOp {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long partitionDefId;
    private Long managedTableId;
    private String physicalTable;
    private String opType;
    private String opStatus;
    private String previewSql;
    private String message;
    private String createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPartitionDefId() { return partitionDefId; }
    public void setPartitionDefId(Long partitionDefId) { this.partitionDefId = partitionDefId; }
    public Long getManagedTableId() { return managedTableId; }
    public void setManagedTableId(Long managedTableId) { this.managedTableId = managedTableId; }
    public String getPhysicalTable() { return physicalTable; }
    public void setPhysicalTable(String physicalTable) { this.physicalTable = physicalTable; }
    public String getOpType() { return opType; }
    public void setOpType(String opType) { this.opType = opType; }
    public String getOpStatus() { return opStatus; }
    public void setOpStatus(String opStatus) { this.opStatus = opStatus; }
    public String getPreviewSql() { return previewSql; }
    public void setPreviewSql(String previewSql) { this.previewSql = previewSql; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
