package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("biz_catalog_item")
public class BizCatalogItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String catalogCode;
    private String title;
    private String description;
    private String catalogKind;
    private String themeCode;
    private String themeName;
    private String providerOrg;
    private String shareModes;
    private Integer resourceCount;
    private Integer hotScore;
    private LocalDateTime publishedAt;
    private String publishStatus;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCatalogCode() { return catalogCode; }
    public void setCatalogCode(String catalogCode) { this.catalogCode = catalogCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCatalogKind() { return catalogKind; }
    public void setCatalogKind(String catalogKind) { this.catalogKind = catalogKind; }
    public String getThemeCode() { return themeCode; }
    public void setThemeCode(String themeCode) { this.themeCode = themeCode; }
    public String getThemeName() { return themeName; }
    public void setThemeName(String themeName) { this.themeName = themeName; }
    public String getProviderOrg() { return providerOrg; }
    public void setProviderOrg(String providerOrg) { this.providerOrg = providerOrg; }
    public String getShareModes() { return shareModes; }
    public void setShareModes(String shareModes) { this.shareModes = shareModes; }
    public Integer getResourceCount() { return resourceCount; }
    public void setResourceCount(Integer resourceCount) { this.resourceCount = resourceCount; }
    public Integer getHotScore() { return hotScore; }
    public void setHotScore(Integer hotScore) { this.hotScore = hotScore; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
