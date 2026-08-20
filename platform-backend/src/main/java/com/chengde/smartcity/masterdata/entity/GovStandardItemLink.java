package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_standard_item_link")
public class GovStandardItemLink {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceItemId;
    private Long targetItemId;
    private String linkType;
    private String remark;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSourceItemId() { return sourceItemId; }
    public void setSourceItemId(Long sourceItemId) { this.sourceItemId = sourceItemId; }
    public Long getTargetItemId() { return targetItemId; }
    public void setTargetItemId(Long targetItemId) { this.targetItemId = targetItemId; }
    public String getLinkType() { return linkType; }
    public void setLinkType(String linkType) { this.linkType = linkType; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
