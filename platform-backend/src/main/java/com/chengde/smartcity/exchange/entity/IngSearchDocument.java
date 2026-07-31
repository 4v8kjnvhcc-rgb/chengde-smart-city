package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("ing_search_document")
public class IngSearchDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String docKey;
    private String docType;
    private Long assetId;
    private String assetCode;
    private String title;
    private String summary;
    private String contentText;
    private String tagsJson;
    private String categoryJson;
    private String physicalDb;
    private String physicalSchema;
    private String physicalTable;
    private Long sourceId;
    private java.math.BigDecimal weightScore;
    private Integer hotScore;
    private String identityKeys;
    private String highAttrsJson;
    private String midAttrsJson;
    private String status;
    private java.time.LocalDateTime indexedAt;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDocKey() { return docKey; }
    public void setDocKey(String docKey) { this.docKey = docKey; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public String getCategoryJson() { return categoryJson; }
    public void setCategoryJson(String categoryJson) { this.categoryJson = categoryJson; }
    public String getPhysicalDb() { return physicalDb; }
    public void setPhysicalDb(String physicalDb) { this.physicalDb = physicalDb; }
    public String getPhysicalSchema() { return physicalSchema; }
    public void setPhysicalSchema(String physicalSchema) { this.physicalSchema = physicalSchema; }
    public String getPhysicalTable() { return physicalTable; }
    public void setPhysicalTable(String physicalTable) { this.physicalTable = physicalTable; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public java.math.BigDecimal getWeightScore() { return weightScore; }
    public void setWeightScore(java.math.BigDecimal weightScore) { this.weightScore = weightScore; }
    public Integer getHotScore() { return hotScore; }
    public void setHotScore(Integer hotScore) { this.hotScore = hotScore; }
    public String getIdentityKeys() { return identityKeys; }
    public void setIdentityKeys(String identityKeys) { this.identityKeys = identityKeys; }
    public String getHighAttrsJson() { return highAttrsJson; }
    public void setHighAttrsJson(String highAttrsJson) { this.highAttrsJson = highAttrsJson; }
    public String getMidAttrsJson() { return midAttrsJson; }
    public void setMidAttrsJson(String midAttrsJson) { this.midAttrsJson = midAttrsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public java.time.LocalDateTime getIndexedAt() { return indexedAt; }
    public void setIndexedAt(java.time.LocalDateTime indexedAt) { this.indexedAt = indexedAt; }
    public java.time.LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }
    public java.time.LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(java.time.LocalDateTime updateTime) { this.updateTime = updateTime; }
}

