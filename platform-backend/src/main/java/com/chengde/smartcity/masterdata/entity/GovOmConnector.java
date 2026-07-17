package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_om_connector")
public class GovOmConnector {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String connectorCode;
    private String connectorName;
    private String sourceType;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private String jdbcDatabase;
    private String status;
    private LocalDateTime lastSyncAt;
    private String lastMessage;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getConnectorCode() { return connectorCode; }
    public void setConnectorCode(String connectorCode) { this.connectorCode = connectorCode; }
    public String getConnectorName() { return connectorName; }
    public void setConnectorName(String connectorName) { this.connectorName = connectorName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public String getJdbcUser() { return jdbcUser; }
    public void setJdbcUser(String jdbcUser) { this.jdbcUser = jdbcUser; }
    public String getJdbcPassword() { return jdbcPassword; }
    public void setJdbcPassword(String jdbcPassword) { this.jdbcPassword = jdbcPassword; }
    public String getJdbcDatabase() { return jdbcDatabase; }
    public void setJdbcDatabase(String jdbcDatabase) { this.jdbcDatabase = jdbcDatabase; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
