package com.chengde.smartcity.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_app_grant")
public class SysAppGrant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appId;
    private String granteeType;
    private Long granteeId;
    private String perm;
    private Long grantedBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    public String getGranteeType() { return granteeType; }
    public void setGranteeType(String granteeType) { this.granteeType = granteeType; }
    public Long getGranteeId() { return granteeId; }
    public void setGranteeId(Long granteeId) { this.granteeId = granteeId; }
    public String getPerm() { return perm; }
    public void setPerm(String perm) { this.perm = perm; }
    public Long getGrantedBy() { return grantedBy; }
    public void setGrantedBy(Long grantedBy) { this.grantedBy = grantedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
