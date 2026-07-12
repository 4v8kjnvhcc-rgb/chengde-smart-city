package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ing_probe_report")
public class IngProbeReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reportCode;
    private String sourceName;
    private BigDecimal nullRate;
    private String domainCheck;
    private String entityType;
    private String metricsJson;
    private String status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReportCode() { return reportCode; }
    public void setReportCode(String reportCode) { this.reportCode = reportCode; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public BigDecimal getNullRate() { return nullRate; }
    public void setNullRate(BigDecimal nullRate) { this.nullRate = nullRate; }
    public String getDomainCheck() { return domainCheck; }
    public void setDomainCheck(String domainCheck) { this.domainCheck = domainCheck; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
