package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_search_sync_policy")
public class IngSearchSyncPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String policyCode;
    private String policyName;
    private String scopeType;
    private String scopeRef;
    private String updateStrategy;
    private String cronExpr;
    private Integer includeTables;
    private Integer includeColumns;
    private Integer includeRegistry;
    private Integer includeDataSample;
    private Integer retryMax;
    private String status;
    private java.time.LocalDateTime lastRunAt;
    private java.time.LocalDateTime lastSuccessAt;
    private String lastStatus;
    private String lastMessage;
    private Integer docCount;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPolicyCode() { return policyCode; }
    public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getScopeRef() { return scopeRef; }
    public void setScopeRef(String scopeRef) { this.scopeRef = scopeRef; }
    public String getUpdateStrategy() { return updateStrategy; }
    public void setUpdateStrategy(String updateStrategy) { this.updateStrategy = updateStrategy; }
    public String getCronExpr() { return cronExpr; }
    public void setCronExpr(String cronExpr) { this.cronExpr = cronExpr; }
    public Integer getIncludeTables() { return includeTables; }
    public void setIncludeTables(Integer includeTables) { this.includeTables = includeTables; }
    public Integer getIncludeColumns() { return includeColumns; }
    public void setIncludeColumns(Integer includeColumns) { this.includeColumns = includeColumns; }
    public Integer getIncludeRegistry() { return includeRegistry; }
    public void setIncludeRegistry(Integer includeRegistry) { this.includeRegistry = includeRegistry; }
    public Integer getIncludeDataSample() { return includeDataSample; }
    public void setIncludeDataSample(Integer includeDataSample) { this.includeDataSample = includeDataSample; }
    public Integer getRetryMax() { return retryMax; }
    public void setRetryMax(Integer retryMax) { this.retryMax = retryMax; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public java.time.LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(java.time.LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public java.time.LocalDateTime getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(java.time.LocalDateTime lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public Integer getDocCount() { return docCount; }
    public void setDocCount(Integer docCount) { this.docCount = docCount; }
    public java.time.LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }
    public java.time.LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(java.time.LocalDateTime updateTime) { this.updateTime = updateTime; }
}

