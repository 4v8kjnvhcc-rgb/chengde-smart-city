package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("biz_data_duty")
public class BizDataDuty {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long demandId;
    private String dutyOrg;
    private String dutyType;
    private Long catalogId;
    private String fulfillPath;
    private String status;
    private String remark;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDemandId() { return demandId; }
    public void setDemandId(Long demandId) { this.demandId = demandId; }
    public String getDutyOrg() { return dutyOrg; }
    public void setDutyOrg(String dutyOrg) { this.dutyOrg = dutyOrg; }
    public String getDutyType() { return dutyType; }
    public void setDutyType(String dutyType) { this.dutyType = dutyType; }
    public Long getCatalogId() { return catalogId; }
    public void setCatalogId(Long catalogId) { this.catalogId = catalogId; }
    public String getFulfillPath() { return fulfillPath; }
    public void setFulfillPath(String fulfillPath) { this.fulfillPath = fulfillPath; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
