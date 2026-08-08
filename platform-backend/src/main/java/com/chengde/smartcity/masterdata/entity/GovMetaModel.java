package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_meta_model")
public class GovMetaModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelCode;
    private String modelNameZh;
    private String modelNameEn;
    private String modelType;
    private Integer dataLength;
    private Integer requiredFlag;
    private String componentType;
    private Long metaDataSourceId;
    private String sourceTableName;
    private String sourceColumnName;
    private String status;
    private String contentJson;
    private LocalDateTime publishedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getModelNameZh() { return modelNameZh; }
    public void setModelNameZh(String modelNameZh) { this.modelNameZh = modelNameZh; }
    public String getModelNameEn() { return modelNameEn; }
    public void setModelNameEn(String modelNameEn) { this.modelNameEn = modelNameEn; }
    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }
    public Integer getDataLength() { return dataLength; }
    public void setDataLength(Integer dataLength) { this.dataLength = dataLength; }
    public Integer getRequiredFlag() { return requiredFlag; }
    public void setRequiredFlag(Integer requiredFlag) { this.requiredFlag = requiredFlag; }
    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }
    public Long getMetaDataSourceId() { return metaDataSourceId; }
    public void setMetaDataSourceId(Long metaDataSourceId) { this.metaDataSourceId = metaDataSourceId; }
    public String getSourceTableName() { return sourceTableName; }
    public void setSourceTableName(String sourceTableName) { this.sourceTableName = sourceTableName; }
    public String getSourceColumnName() { return sourceColumnName; }
    public void setSourceColumnName(String sourceColumnName) { this.sourceColumnName = sourceColumnName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
