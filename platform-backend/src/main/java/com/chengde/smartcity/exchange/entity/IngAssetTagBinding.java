package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_asset_tag_binding")
public class IngAssetTagBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tagId;
    private String assetType;
    private Long assetId;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String tagName;
    @TableField(exist = false)
    private String stdCode;
    @TableField(exist = false)
    private String assetLabel;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
    public String getStdCode() { return stdCode; }
    public void setStdCode(String stdCode) { this.stdCode = stdCode; }
    public String getAssetLabel() { return assetLabel; }
    public void setAssetLabel(String assetLabel) { this.assetLabel = assetLabel; }
}
