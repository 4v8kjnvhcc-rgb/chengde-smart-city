package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rc_theme_library")
public class RcThemeLibrary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String themeCode;
    private String themeName;
    private String libraryKind;
    private String zoneCode;
    private String ownerOrg;
    private String description;
    private String partitionKey;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getThemeCode() { return themeCode; }
    public void setThemeCode(String themeCode) { this.themeCode = themeCode; }
    public String getThemeName() { return themeName; }
    public void setThemeName(String themeName) { this.themeName = themeName; }
    public String getLibraryKind() { return libraryKind; }
    public void setLibraryKind(String libraryKind) { this.libraryKind = libraryKind; }
    public String getZoneCode() { return zoneCode; }
    public void setZoneCode(String zoneCode) { this.zoneCode = zoneCode; }
    public String getOwnerOrg() { return ownerOrg; }
    public void setOwnerOrg(String ownerOrg) { this.ownerOrg = ownerOrg; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPartitionKey() { return partitionKey; }
    public void setPartitionKey(String partitionKey) { this.partitionKey = partitionKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
