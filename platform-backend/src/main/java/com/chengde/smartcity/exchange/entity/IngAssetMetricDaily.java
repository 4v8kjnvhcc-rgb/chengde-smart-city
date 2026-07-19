package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("ing_asset_metric_daily")
public class IngAssetMetricDaily {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate metricDate;
    private String metricType;
    private Long refId;
    private BigDecimal valueNum;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getMetricDate() { return metricDate; }
    public void setMetricDate(LocalDate metricDate) { this.metricDate = metricDate; }
    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public BigDecimal getValueNum() { return valueNum; }
    public void setValueNum(BigDecimal valueNum) { this.valueNum = valueNum; }
}
