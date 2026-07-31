package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("ing_search_global_binding")
public class IngSearchGlobalBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long globalFieldId;
    private Long tableId;
    private Long columnId;
    private String columnCode;
    private java.math.BigDecimal matchScore;
    private String confirmStatus;
    private String matchSource;
    private java.time.LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGlobalFieldId() { return globalFieldId; }
    public void setGlobalFieldId(Long globalFieldId) { this.globalFieldId = globalFieldId; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public Long getColumnId() { return columnId; }
    public void setColumnId(Long columnId) { this.columnId = columnId; }
    public String getColumnCode() { return columnCode; }
    public void setColumnCode(String columnCode) { this.columnCode = columnCode; }
    public java.math.BigDecimal getMatchScore() { return matchScore; }
    public void setMatchScore(java.math.BigDecimal matchScore) { this.matchScore = matchScore; }
    public String getConfirmStatus() { return confirmStatus; }
    public void setConfirmStatus(String confirmStatus) { this.confirmStatus = confirmStatus; }
    public String getMatchSource() { return matchSource; }
    public void setMatchSource(String matchSource) { this.matchSource = matchSource; }
    public java.time.LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }
}

