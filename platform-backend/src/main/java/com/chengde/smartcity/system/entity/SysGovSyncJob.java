package com.chengde.smartcity.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_gov_sync_job")
public class SysGovSyncJob {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long targetId;
    private String targetName;
    private Integer syncOrg;
    private Integer syncUser;
    private Integer syncRole;
    private Integer syncPassword;
    private Integer syncSms;
    private String orgIdsJson;
    private String userIdsJson;
    private String roleIdsJson;
    private String payloadSummary;
    private Integer syncedOrgCount;
    private Integer syncedUserCount;
    private Integer syncedRoleCount;
    private String status;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public Integer getSyncOrg() { return syncOrg; }
    public void setSyncOrg(Integer syncOrg) { this.syncOrg = syncOrg; }
    public Integer getSyncUser() { return syncUser; }
    public void setSyncUser(Integer syncUser) { this.syncUser = syncUser; }
    public Integer getSyncRole() { return syncRole; }
    public void setSyncRole(Integer syncRole) { this.syncRole = syncRole; }
    public Integer getSyncPassword() { return syncPassword; }
    public void setSyncPassword(Integer syncPassword) { this.syncPassword = syncPassword; }
    public Integer getSyncSms() { return syncSms; }
    public void setSyncSms(Integer syncSms) { this.syncSms = syncSms; }
    public String getOrgIdsJson() { return orgIdsJson; }
    public void setOrgIdsJson(String orgIdsJson) { this.orgIdsJson = orgIdsJson; }
    public String getUserIdsJson() { return userIdsJson; }
    public void setUserIdsJson(String userIdsJson) { this.userIdsJson = userIdsJson; }
    public String getRoleIdsJson() { return roleIdsJson; }
    public void setRoleIdsJson(String roleIdsJson) { this.roleIdsJson = roleIdsJson; }
    public String getPayloadSummary() { return payloadSummary; }
    public void setPayloadSummary(String payloadSummary) { this.payloadSummary = payloadSummary; }
    public Integer getSyncedOrgCount() { return syncedOrgCount; }
    public void setSyncedOrgCount(Integer syncedOrgCount) { this.syncedOrgCount = syncedOrgCount; }
    public Integer getSyncedUserCount() { return syncedUserCount; }
    public void setSyncedUserCount(Integer syncedUserCount) { this.syncedUserCount = syncedUserCount; }
    public Integer getSyncedRoleCount() { return syncedRoleCount; }
    public void setSyncedRoleCount(Integer syncedRoleCount) { this.syncedRoleCount = syncedRoleCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
