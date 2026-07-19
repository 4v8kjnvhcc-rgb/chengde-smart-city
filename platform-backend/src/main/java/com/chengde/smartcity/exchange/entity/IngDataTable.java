package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_data_table")
public class IngDataTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceId;
    private String tableCode;
    private String tableName;
    private String usageDesc;
    private String physicalTableName;
    private String sourceSchema;
    private String sourceTable;
    private Long sourceRowCount;
    private String primaryKeyCols;
    private String omFqn;
    private String modelingMode;
    private Integer columnCount;
    private Long storageBytes;
    private Long storageBytesToday;
    private LocalDateTime dataChangedAt;
    private String ddlSql;
    private String advancedSettingsJson;
    private Integer partitionFlag;
    private String status;
    private String collectStatus;
    private LocalDateTime lastCollectAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getTableCode() { return tableCode; }
    public void setTableCode(String tableCode) { this.tableCode = tableCode; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getUsageDesc() { return usageDesc; }
    public void setUsageDesc(String usageDesc) { this.usageDesc = usageDesc; }
    public String getPhysicalTableName() { return physicalTableName; }
    public void setPhysicalTableName(String physicalTableName) { this.physicalTableName = physicalTableName; }
    public String getSourceSchema() { return sourceSchema; }
    public void setSourceSchema(String sourceSchema) { this.sourceSchema = sourceSchema; }
    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
    public Long getSourceRowCount() { return sourceRowCount; }
    public void setSourceRowCount(Long sourceRowCount) { this.sourceRowCount = sourceRowCount; }
    public String getPrimaryKeyCols() { return primaryKeyCols; }
    public void setPrimaryKeyCols(String primaryKeyCols) { this.primaryKeyCols = primaryKeyCols; }
    public String getOmFqn() { return omFqn; }
    public void setOmFqn(String omFqn) { this.omFqn = omFqn; }
    public String getModelingMode() { return modelingMode; }
    public void setModelingMode(String modelingMode) { this.modelingMode = modelingMode; }
    public Integer getColumnCount() { return columnCount; }
    public void setColumnCount(Integer columnCount) { this.columnCount = columnCount; }
    public Long getStorageBytes() { return storageBytes; }
    public void setStorageBytes(Long storageBytes) { this.storageBytes = storageBytes; }
    public Long getStorageBytesToday() { return storageBytesToday; }
    public void setStorageBytesToday(Long storageBytesToday) { this.storageBytesToday = storageBytesToday; }
    public LocalDateTime getDataChangedAt() { return dataChangedAt; }
    public void setDataChangedAt(LocalDateTime dataChangedAt) { this.dataChangedAt = dataChangedAt; }
    public String getDdlSql() { return ddlSql; }
    public void setDdlSql(String ddlSql) { this.ddlSql = ddlSql; }
    public String getAdvancedSettingsJson() { return advancedSettingsJson; }
    public void setAdvancedSettingsJson(String advancedSettingsJson) { this.advancedSettingsJson = advancedSettingsJson; }
    public Integer getPartitionFlag() { return partitionFlag; }
    public void setPartitionFlag(Integer partitionFlag) { this.partitionFlag = partitionFlag; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCollectStatus() { return collectStatus; }
    public void setCollectStatus(String collectStatus) { this.collectStatus = collectStatus; }
    public LocalDateTime getLastCollectAt() { return lastCollectAt; }
    public void setLastCollectAt(LocalDateTime lastCollectAt) { this.lastCollectAt = lastCollectAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
