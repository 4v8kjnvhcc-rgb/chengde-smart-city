package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("biz_gov_matter")
public class BizGovMatter {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String matterCode;
    private String matterName;
    private String matterType;
    private String regionScope;
    private String status;
    private Integer sortOrder;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMatterCode() { return matterCode; }
    public void setMatterCode(String matterCode) { this.matterCode = matterCode; }
    public String getMatterName() { return matterName; }
    public void setMatterName(String matterName) { this.matterName = matterName; }
    public String getMatterType() { return matterType; }
    public void setMatterType(String matterType) { this.matterType = matterType; }
    public String getRegionScope() { return regionScope; }
    public void setRegionScope(String regionScope) { this.regionScope = regionScope; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
