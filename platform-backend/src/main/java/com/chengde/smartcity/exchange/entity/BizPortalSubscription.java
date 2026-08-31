package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("biz_portal_subscription")
public class BizPortalSubscription {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long catalogId;
    private String applicantOrg;
    private String resourceType;
    private String purpose;
    private String applyPayload;
    private String status;
    /** PLATFORM=平台管理员；PROVIDER=目录提供单位 */
    private String approvalStep;
    private String platformReviewedBy;
    private String platformReviewerContact;
    private String platformApproverNote;
    private LocalDateTime platformReviewedAt;
    private String approverNote;
    private String reviewedBy;
    private String reviewerContact;
    private LocalDateTime reviewedAt;
    private Long govSubscriptionId;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String oauthClientId;
    private String oauthClientSecret;
    private String esbCustomerId;
    private String apiUrl;
    private String apiMethod;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCatalogId() { return catalogId; }
    public void setCatalogId(Long catalogId) { this.catalogId = catalogId; }
    public String getApplicantOrg() { return applicantOrg; }
    public void setApplicantOrg(String applicantOrg) { this.applicantOrg = applicantOrg; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getApplyPayload() { return applyPayload; }
    public void setApplyPayload(String applyPayload) { this.applyPayload = applyPayload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovalStep() { return approvalStep; }
    public void setApprovalStep(String approvalStep) { this.approvalStep = approvalStep; }
    public String getPlatformReviewedBy() { return platformReviewedBy; }
    public void setPlatformReviewedBy(String platformReviewedBy) { this.platformReviewedBy = platformReviewedBy; }
    public String getPlatformReviewerContact() { return platformReviewerContact; }
    public void setPlatformReviewerContact(String platformReviewerContact) { this.platformReviewerContact = platformReviewerContact; }
    public String getPlatformApproverNote() { return platformApproverNote; }
    public void setPlatformApproverNote(String platformApproverNote) { this.platformApproverNote = platformApproverNote; }
    public LocalDateTime getPlatformReviewedAt() { return platformReviewedAt; }
    public void setPlatformReviewedAt(LocalDateTime platformReviewedAt) { this.platformReviewedAt = platformReviewedAt; }
    public String getApproverNote() { return approverNote; }
    public void setApproverNote(String approverNote) { this.approverNote = approverNote; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewerContact() { return reviewerContact; }
    public void setReviewerContact(String reviewerContact) { this.reviewerContact = reviewerContact; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public Long getGovSubscriptionId() { return govSubscriptionId; }
    public void setGovSubscriptionId(Long govSubscriptionId) { this.govSubscriptionId = govSubscriptionId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getOauthClientId() { return oauthClientId; }
    public void setOauthClientId(String oauthClientId) { this.oauthClientId = oauthClientId; }
    public String getOauthClientSecret() { return oauthClientSecret; }
    public void setOauthClientSecret(String oauthClientSecret) { this.oauthClientSecret = oauthClientSecret; }
    public String getEsbCustomerId() { return esbCustomerId; }
    public void setEsbCustomerId(String esbCustomerId) { this.esbCustomerId = esbCustomerId; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getApiMethod() { return apiMethod; }
    public void setApiMethod(String apiMethod) { this.apiMethod = apiMethod; }
}
