package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_naming_standard")
public class GovNamingStandard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String namingType;
    private String namingName;
    private String standardContent;
    private String description;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNamingType() { return namingType; }
    public void setNamingType(String namingType) { this.namingType = namingType; }
    public String getNamingName() { return namingName; }
    public void setNamingName(String namingName) { this.namingName = namingName; }
    public String getStandardContent() { return standardContent; }
    public void setStandardContent(String standardContent) { this.standardContent = standardContent; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
