package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("biz_catalog_objection")
public class BizCatalogObjection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private Long catalogId;
    private Long demandId;
    private String objectionType;
    private String content;
    private String providerOrg;
    private String verifyOrg;
    private String status;
    private String handlerNote;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getCatalogId() { return catalogId; }
    public void setCatalogId(Long catalogId) { this.catalogId = catalogId; }
    public Long getDemandId() { return demandId; }
    public void setDemandId(Long demandId) { this.demandId = demandId; }
    public String getObjectionType() { return objectionType; }
    public void setObjectionType(String objectionType) { this.objectionType = objectionType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getProviderOrg() { return providerOrg; }
    public void setProviderOrg(String providerOrg) { this.providerOrg = providerOrg; }
    public String getVerifyOrg() { return verifyOrg; }
    public void setVerifyOrg(String verifyOrg) { this.verifyOrg = verifyOrg; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHandlerNote() { return handlerNote; }
    public void setHandlerNote(String handlerNote) { this.handlerNote = handlerNote; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
