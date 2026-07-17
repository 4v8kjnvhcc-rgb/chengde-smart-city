package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rc_backup_artifact")
public class RcBackupArtifact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    private Long managedTableId;
    private String physicalTable;
    private String filePath;
    private String fileName;
    private Long rowCount;
    private Long byteSize;
    private String sha256;
    private String status;
    private String message;
    private String createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public Long getManagedTableId() { return managedTableId; }
    public void setManagedTableId(Long managedTableId) { this.managedTableId = managedTableId; }
    public String getPhysicalTable() { return physicalTable; }
    public void setPhysicalTable(String physicalTable) { this.physicalTable = physicalTable; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long byteSize) { this.byteSize = byteSize; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
