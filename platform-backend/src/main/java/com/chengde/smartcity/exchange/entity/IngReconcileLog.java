package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ing_reconcile_log")
public class IngReconcileLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private BigDecimal matchedPct;
    private Integer diffRows;
    private String alertLevel;
    private String status;
    private String detailJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public BigDecimal getMatchedPct() { return matchedPct; }
    public void setMatchedPct(BigDecimal matchedPct) { this.matchedPct = matchedPct; }
    public Integer getDiffRows() { return diffRows; }
    public void setDiffRows(Integer diffRows) { this.diffRows = diffRows; }
    public String getAlertLevel() { return alertLevel; }
    public void setAlertLevel(String alertLevel) { this.alertLevel = alertLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
