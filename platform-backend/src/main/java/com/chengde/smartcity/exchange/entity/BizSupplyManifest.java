package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("biz_supply_manifest")
public class BizSupplyManifest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String manifestType;
    private Long refId;
    private String title;
    private String status;
    private String authLevel;
    private Integer cascadeFlag;
    private String exportPayload;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getManifestType() { return manifestType; }
    public void setManifestType(String manifestType) { this.manifestType = manifestType; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAuthLevel() { return authLevel; }
    public void setAuthLevel(String authLevel) { this.authLevel = authLevel; }
    public Integer getCascadeFlag() { return cascadeFlag; }
    public void setCascadeFlag(Integer cascadeFlag) { this.cascadeFlag = cascadeFlag; }
    public String getExportPayload() { return exportPayload; }
    public void setExportPayload(String exportPayload) { this.exportPayload = exportPayload; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
