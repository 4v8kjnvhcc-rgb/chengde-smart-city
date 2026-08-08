package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_meta_data_source")
public class GovMetaDataSource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceCode;
    private String sourceName;
    private Integer sortOrder;
    private String deptName;
    private Long orgId;
    private String orgName;
    private Long categoryId;
    private Long tagCategoryId;
    private String belongSystem;
    private String remarks;
    private String adapterType;
    private Integer realtimeFlag;
    private Integer readOnlyFlag;
    private String dbHost;
    private Integer dbPort;
    private String dbName;
    private String dbSchema;
    private String username;
    private String passwordCipher;
    private Long connectorId;
    private Long ingSourceId;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getTagCategoryId() { return tagCategoryId; }
    public void setTagCategoryId(Long tagCategoryId) { this.tagCategoryId = tagCategoryId; }
    public String getBelongSystem() { return belongSystem; }
    public void setBelongSystem(String belongSystem) { this.belongSystem = belongSystem; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public String getAdapterType() { return adapterType; }
    public void setAdapterType(String adapterType) { this.adapterType = adapterType; }
    public Integer getRealtimeFlag() { return realtimeFlag; }
    public void setRealtimeFlag(Integer realtimeFlag) { this.realtimeFlag = realtimeFlag; }
    public Integer getReadOnlyFlag() { return readOnlyFlag; }
    public void setReadOnlyFlag(Integer readOnlyFlag) { this.readOnlyFlag = readOnlyFlag; }
    public String getDbHost() { return dbHost; }
    public void setDbHost(String dbHost) { this.dbHost = dbHost; }
    public Integer getDbPort() { return dbPort; }
    public void setDbPort(Integer dbPort) { this.dbPort = dbPort; }
    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }
    public String getDbSchema() { return dbSchema; }
    public void setDbSchema(String dbSchema) { this.dbSchema = dbSchema; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordCipher() { return passwordCipher; }
    public void setPasswordCipher(String passwordCipher) { this.passwordCipher = passwordCipher; }
    public Long getConnectorId() { return connectorId; }
    public void setConnectorId(Long connectorId) { this.connectorId = connectorId; }
    public Long getIngSourceId() { return ingSourceId; }
    public void setIngSourceId(Long ingSourceId) { this.ingSourceId = ingSourceId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
