package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rc_catalog_exchange_job")
public class RcCatalogExchangeJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long catalogEntryId;
    private String jobCode;
    private String jobName;
    private Long managedTableId;
    private String physicalTable;
    private Long rowCount;
    private String runStatus;
    private String message;
    private String createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCatalogEntryId() { return catalogEntryId; }
    public void setCatalogEntryId(Long catalogEntryId) { this.catalogEntryId = catalogEntryId; }
    public String getJobCode() { return jobCode; }
    public void setJobCode(String jobCode) { this.jobCode = jobCode; }
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public Long getManagedTableId() { return managedTableId; }
    public void setManagedTableId(Long managedTableId) { this.managedTableId = managedTableId; }
    public String getPhysicalTable() { return physicalTable; }
    public void setPhysicalTable(String physicalTable) { this.physicalTable = physicalTable; }
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String runStatus) { this.runStatus = runStatus; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
