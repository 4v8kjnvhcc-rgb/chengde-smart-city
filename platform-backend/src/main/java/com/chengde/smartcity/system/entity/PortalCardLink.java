package com.chengde.smartcity.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("portal_card_link")
public class PortalCardLink {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String platformPath;
    private String title;
    private String url;
    private String description;
    private String openMode;
    private String ssoMode;
    private String ssoParam;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlatformPath() { return platformPath; }
    public void setPlatformPath(String platformPath) { this.platformPath = platformPath; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOpenMode() { return openMode; }
    public void setOpenMode(String openMode) { this.openMode = openMode; }
    public String getSsoMode() { return ssoMode; }
    public void setSsoMode(String ssoMode) { this.ssoMode = ssoMode; }
    public String getSsoParam() { return ssoParam; }
    public void setSsoParam(String ssoParam) { this.ssoParam = ssoParam; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
