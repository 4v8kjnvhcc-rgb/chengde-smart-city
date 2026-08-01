package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ana_indicator_query")
public class AnaIndicatorQuery {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String domainCode;
    private String queryNo;
    private String datasourceKey;
    private String datasourceName;
    private Integer timeoutSec;
    private String sqlText;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDomainCode() { return domainCode; }
    public void setDomainCode(String domainCode) { this.domainCode = domainCode; }
    public String getQueryNo() { return queryNo; }
    public void setQueryNo(String queryNo) { this.queryNo = queryNo; }
    public String getDatasourceKey() { return datasourceKey; }
    public void setDatasourceKey(String datasourceKey) { this.datasourceKey = datasourceKey; }
    public String getDatasourceName() { return datasourceName; }
    public void setDatasourceName(String datasourceName) { this.datasourceName = datasourceName; }
    public Integer getTimeoutSec() { return timeoutSec; }
    public void setTimeoutSec(Integer timeoutSec) { this.timeoutSec = timeoutSec; }
    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
