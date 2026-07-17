package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_fusion_physical")
public class GovFusionPhysical {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long entityId;
    private String physicalCode;
    private String tableName;
    private Long datasourceId;
    private String ddlSql;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getPhysicalCode() { return physicalCode; }
    public void setPhysicalCode(String physicalCode) { this.physicalCode = physicalCode; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public Long getDatasourceId() { return datasourceId; }
    public void setDatasourceId(Long datasourceId) { this.datasourceId = datasourceId; }
    public String getDdlSql() { return ddlSql; }
    public void setDdlSql(String ddlSql) { this.ddlSql = ddlSql; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
