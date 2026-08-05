package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("uns_document")
public class UnsDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String docCode;
    private String title;
    private String originalFileName;
    private String contentType;
    private String storageKey;
    private Long fileSize;
    private String description;
    private String author;
    private String mediaFormat;
    private Integer mediaWidth;
    private Integer mediaHeight;
    private Integer mediaDurationSec;
    private String featureJson;
    private String contentJson;
    private String fingerprint;
    private String metaStatus;
    private String sourceType;
    private String sourceSystem;
    private String sourceUrl;
    private String indexStatus;
    private String categoryCode;
    private String publishStatus;
    private String tagJson;
    private String processStatus;
    private Long linkedDocId;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDocCode() { return docCode; }
    public void setDocCode(String docCode) { this.docCode = docCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getMediaFormat() { return mediaFormat; }
    public void setMediaFormat(String mediaFormat) { this.mediaFormat = mediaFormat; }
    public Integer getMediaWidth() { return mediaWidth; }
    public void setMediaWidth(Integer mediaWidth) { this.mediaWidth = mediaWidth; }
    public Integer getMediaHeight() { return mediaHeight; }
    public void setMediaHeight(Integer mediaHeight) { this.mediaHeight = mediaHeight; }
    public Integer getMediaDurationSec() { return mediaDurationSec; }
    public void setMediaDurationSec(Integer mediaDurationSec) { this.mediaDurationSec = mediaDurationSec; }
    public String getFeatureJson() { return featureJson; }
    public void setFeatureJson(String featureJson) { this.featureJson = featureJson; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getMetaStatus() { return metaStatus; }
    public void setMetaStatus(String metaStatus) { this.metaStatus = metaStatus; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getIndexStatus() { return indexStatus; }
    public void setIndexStatus(String indexStatus) { this.indexStatus = indexStatus; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public String getTagJson() { return tagJson; }
    public void setTagJson(String tagJson) { this.tagJson = tagJson; }
    public String getProcessStatus() { return processStatus; }
    public void setProcessStatus(String processStatus) { this.processStatus = processStatus; }
    public Long getLinkedDocId() { return linkedDocId; }
    public void setLinkedDocId(Long linkedDocId) { this.linkedDocId = linkedDocId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
