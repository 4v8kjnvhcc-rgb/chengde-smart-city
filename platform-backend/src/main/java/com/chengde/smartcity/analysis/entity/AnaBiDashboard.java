package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ana_bi_dashboard")
public class AnaBiDashboard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dashCode;
    private String dashName;
    private String deDashboardId;
    private String status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDashCode() { return dashCode; }
    public void setDashCode(String dashCode) { this.dashCode = dashCode; }
    public String getDashName() { return dashName; }
    public void setDashName(String dashName) { this.dashName = dashName; }
    public String getDeDashboardId() { return deDashboardId; }
    public void setDeDashboardId(String deDashboardId) { this.deDashboardId = deDashboardId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
