package com.chengde.smartcity.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("cross_dept_access_request")
public class CrossDeptAccessRequest {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicantUserId;
    private Long applicantOrgId;
    private Long targetOrgId;
    private String resourceType;
    private String resourceId;
    private String reason;
    /** 0待审 1通过 2拒绝 */
    private Integer status;
    private Long approverUserId;
    private LocalDateTime approvedAt;
    private String approveComment;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApplicantUserId() { return applicantUserId; }
    public void setApplicantUserId(Long applicantUserId) { this.applicantUserId = applicantUserId; }
    public Long getApplicantOrgId() { return applicantOrgId; }
    public void setApplicantOrgId(Long applicantOrgId) { this.applicantOrgId = applicantOrgId; }
    public Long getTargetOrgId() { return targetOrgId; }
    public void setTargetOrgId(Long targetOrgId) { this.targetOrgId = targetOrgId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getApproverUserId() { return approverUserId; }
    public void setApproverUserId(Long approverUserId) { this.approverUserId = approverUserId; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getApproveComment() { return approveComment; }
    public void setApproveComment(String approveComment) { this.approveComment = approveComment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
