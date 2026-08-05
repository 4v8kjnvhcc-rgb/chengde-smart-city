package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_catalog_subscription")
public class GovCatalogSubscription {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resourceId;
    private String applicantOrg;
    private String applicantUser;
    private String shareMode;
    private String purpose;
    private String applyPayload;
    private String status;
    private String reviewComment;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String distributeResult;
    private LocalDateTime distributeAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public String getApplicantOrg() { return applicantOrg; }
    public void setApplicantOrg(String applicantOrg) { this.applicantOrg = applicantOrg; }
    public String getApplicantUser() { return applicantUser; }
    public void setApplicantUser(String applicantUser) { this.applicantUser = applicantUser; }
    public String getShareMode() { return shareMode; }
    public void setShareMode(String shareMode) { this.shareMode = shareMode; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getApplyPayload() { return applyPayload; }
    public void setApplyPayload(String applyPayload) { this.applyPayload = applyPayload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getDistributeResult() { return distributeResult; }
    public void setDistributeResult(String distributeResult) { this.distributeResult = distributeResult; }
    public LocalDateTime getDistributeAt() { return distributeAt; }
    public void setDistributeAt(LocalDateTime distributeAt) { this.distributeAt = distributeAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
