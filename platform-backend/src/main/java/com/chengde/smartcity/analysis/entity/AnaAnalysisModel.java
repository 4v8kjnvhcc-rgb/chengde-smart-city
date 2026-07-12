package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ana_analysis_model")
public class AnaAnalysisModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelCode;
    private String modelName;
    private String domainCode;
    private String mCode;
    private String deDashboardId;
    private Integer sampleRowCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getDomainCode() { return domainCode; }
    public void setDomainCode(String domainCode) { this.domainCode = domainCode; }
    public String getMCode() { return mCode; }
    public void setMCode(String mCode) { this.mCode = mCode; }
    public String getDeDashboardId() { return deDashboardId; }
    public void setDeDashboardId(String deDashboardId) { this.deDashboardId = deDashboardId; }
    public Integer getSampleRowCount() { return sampleRowCount; }
    public void setSampleRowCount(Integer sampleRowCount) { this.sampleRowCount = sampleRowCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
