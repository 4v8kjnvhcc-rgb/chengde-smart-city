package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_data_source")
public class IngDataSource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long systemId;
    private String sourceCode;
    private String sourceName;
    private String systemName;
    private String sourceType;
    private String connStatus;
    private String registerStatus;
    private String rejectReason;
    private Integer tableCount;
    private String connConfigJson;
    private String sourceSchema;
    private LocalDateTime probeAt;
    private String probeMessage;
    private String omServiceName;
    private String syncStatus;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getConnStatus() { return connStatus; }
    public void setConnStatus(String connStatus) { this.connStatus = connStatus; }
    public String getRegisterStatus() { return registerStatus; }
    public void setRegisterStatus(String registerStatus) { this.registerStatus = registerStatus; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Integer getTableCount() { return tableCount; }
    public void setTableCount(Integer tableCount) { this.tableCount = tableCount; }
    public String getConnConfigJson() { return connConfigJson; }
    public void setConnConfigJson(String connConfigJson) { this.connConfigJson = connConfigJson; }
    public String getSourceSchema() { return sourceSchema; }
    public void setSourceSchema(String sourceSchema) { this.sourceSchema = sourceSchema; }
    public LocalDateTime getProbeAt() { return probeAt; }
    public void setProbeAt(LocalDateTime probeAt) { this.probeAt = probeAt; }
    public String getProbeMessage() { return probeMessage; }
    public void setProbeMessage(String probeMessage) { this.probeMessage = probeMessage; }
    public String getOmServiceName() { return omServiceName; }
    public void setOmServiceName(String omServiceName) { this.omServiceName = omServiceName; }
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
