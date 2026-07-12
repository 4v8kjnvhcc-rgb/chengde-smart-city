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
    private String demandType;
    private String templateCode;
    private String stage;
    private Long matchedCatalogId;
    private BigDecimal matchScore;
    private String analysisNote;
    private String assigneeOrg;
    private String supplyMode;
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
    public String getDemandType() { return demandType; }
    public void setDemandType(String demandType) { this.demandType = demandType; }
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
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
