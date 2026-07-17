package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("biz_data_demand")
public class BizDataDemand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String demandTitle;
    private String requesterOrg;
    private Long targetCatalogId;
    private String status;
    private String confirmNote;
    private String confirmFeedback;
    private String demandType;
    private String demandContent;
    private String modelFields;
    private String templateCode;
    private String stage;
    private Long matchedCatalogId;
    private BigDecimal matchScore;
    private String analysisNote;
    private String assigneeOrg;
    private String supplyMode;
    private String fulfillPath;
    private String evalStatus;
    private String shareAttr;
    private String superviseNote;
    private java.time.LocalDateTime superviseAt;
    private String superviseBy;
    private String analysisPayload;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDemandTitle() { return demandTitle; }
    public void setDemandTitle(String demandTitle) { this.demandTitle = demandTitle; }
    public String getRequesterOrg() { return requesterOrg; }
    public void setRequesterOrg(String requesterOrg) { this.requesterOrg = requesterOrg; }
    public Long getTargetCatalogId() { return targetCatalogId; }
    public void setTargetCatalogId(Long targetCatalogId) { this.targetCatalogId = targetCatalogId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConfirmNote() { return confirmNote; }
    public void setConfirmNote(String confirmNote) { this.confirmNote = confirmNote; }
    public String getConfirmFeedback() { return confirmFeedback; }
    public void setConfirmFeedback(String confirmFeedback) { this.confirmFeedback = confirmFeedback; }
    public String getDemandType() { return demandType; }
    public void setDemandType(String demandType) { this.demandType = demandType; }
    public String getDemandContent() { return demandContent; }
    public void setDemandContent(String demandContent) { this.demandContent = demandContent; }
    public String getModelFields() { return modelFields; }
    public void setModelFields(String modelFields) { this.modelFields = modelFields; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public Long getMatchedCatalogId() { return matchedCatalogId; }
    public void setMatchedCatalogId(Long matchedCatalogId) { this.matchedCatalogId = matchedCatalogId; }
    public BigDecimal getMatchScore() { return matchScore; }
    public void setMatchScore(BigDecimal matchScore) { this.matchScore = matchScore; }
    public String getAnalysisNote() { return analysisNote; }
    public void setAnalysisNote(String analysisNote) { this.analysisNote = analysisNote; }
    public String getAssigneeOrg() { return assigneeOrg; }
    public void setAssigneeOrg(String assigneeOrg) { this.assigneeOrg = assigneeOrg; }
    public String getSupplyMode() { return supplyMode; }
    public void setSupplyMode(String supplyMode) { this.supplyMode = supplyMode; }
    public String getFulfillPath() { return fulfillPath; }
    public void setFulfillPath(String fulfillPath) { this.fulfillPath = fulfillPath; }
    public String getEvalStatus() { return evalStatus; }
    public void setEvalStatus(String evalStatus) { this.evalStatus = evalStatus; }
    public String getShareAttr() { return shareAttr; }
    public void setShareAttr(String shareAttr) { this.shareAttr = shareAttr; }
    public String getSuperviseNote() { return superviseNote; }
    public void setSuperviseNote(String superviseNote) { this.superviseNote = superviseNote; }
    public java.time.LocalDateTime getSuperviseAt() { return superviseAt; }
    public void setSuperviseAt(java.time.LocalDateTime superviseAt) { this.superviseAt = superviseAt; }
    public String getSuperviseBy() { return superviseBy; }
    public void setSuperviseBy(String superviseBy) { this.superviseBy = superviseBy; }
    public String getAnalysisPayload() { return analysisPayload; }
    public void setAnalysisPayload(String analysisPayload) { this.analysisPayload = analysisPayload; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
