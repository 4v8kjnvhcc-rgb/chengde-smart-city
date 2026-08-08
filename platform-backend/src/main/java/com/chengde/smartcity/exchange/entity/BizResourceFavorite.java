package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("biz_resource_favorite")
public class BizResourceFavorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private Long catalogId;
    private Long govResourceId;
    private String title;
    private String catalogCode;
    private String providerOrg;
    private String resourceType;
    private String resourceTypeLabel;
    private LocalDateTime followedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Long getCatalogId() { return catalogId; }
    public void setCatalogId(Long catalogId) { this.catalogId = catalogId; }
    public Long getGovResourceId() { return govResourceId; }
    public void setGovResourceId(Long govResourceId) { this.govResourceId = govResourceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCatalogCode() { return catalogCode; }
    public void setCatalogCode(String catalogCode) { this.catalogCode = catalogCode; }
    public String getProviderOrg() { return providerOrg; }
    public void setProviderOrg(String providerOrg) { this.providerOrg = providerOrg; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceTypeLabel() { return resourceTypeLabel; }
    public void setResourceTypeLabel(String resourceTypeLabel) { this.resourceTypeLabel = resourceTypeLabel; }
    public LocalDateTime getFollowedAt() { return followedAt; }
    public void setFollowedAt(LocalDateTime followedAt) { this.followedAt = followedAt; }
}
