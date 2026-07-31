package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ing_asset_tag_binding")
public class IngAssetTagBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tagId;
    private String assetType;
    private Long assetId;
    private String source;
    private BigDecimal confidence;
    private String confirmStatus;
    private String taggedBy;
    private String note;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String tagName;
    @TableField(exist = false)
    private String tagCode;
    @TableField(exist = false)
    private String stdCode;
    @TableField(exist = false)
    private String dimType;
    @TableField(exist = false)
    private String assetLabel;
    @TableField(exist = false)
    private String assetCode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getConfirmStatus() { return confirmStatus; }
    public void setConfirmStatus(String confirmStatus) { this.confirmStatus = confirmStatus; }
    public String getTaggedBy() { return taggedBy; }
    public void setTaggedBy(String taggedBy) { this.taggedBy = taggedBy; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
    public String getTagCode() { return tagCode; }
    public void setTagCode(String tagCode) { this.tagCode = tagCode; }
    public String getStdCode() { return stdCode; }
    public void setStdCode(String stdCode) { this.stdCode = stdCode; }
    public String getDimType() { return dimType; }
    public void setDimType(String dimType) { this.dimType = dimType; }
    public String getAssetLabel() { return assetLabel; }
    public void setAssetLabel(String assetLabel) { this.assetLabel = assetLabel; }
    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }
}
