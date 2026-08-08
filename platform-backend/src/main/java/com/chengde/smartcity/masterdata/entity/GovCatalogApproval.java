package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_catalog_approval")
public class GovCatalogApproval {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resourceId;
    private Long categoryId;
    private String catalogOrigin;
    private String actionType;
    private String status;
    private String submitComment;
    private String reviewComment;
    private String submittedBy;
    private LocalDateTime submittedAt;
    private String reviewedBy;
    private String reviewerContact;
    private LocalDateTime reviewedAt;
    private String payloadJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCatalogOrigin() { return catalogOrigin; }
    public void setCatalogOrigin(String catalogOrigin) { this.catalogOrigin = catalogOrigin; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubmitComment() { return submitComment; }
    public void setSubmitComment(String submitComment) { this.submitComment = submitComment; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewerContact() { return reviewerContact; }
    public void setReviewerContact(String reviewerContact) { this.reviewerContact = reviewerContact; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
}
