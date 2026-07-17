package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_catalog_authorization")
public class GovCatalogAuthorization {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String authorizationCode;
    private Long subscriptionId;
    private Long resourceId;
    private String granteeOrg;
    private String granteeUser;
    private String shareMode;
    private String status;
    private String credentialRef;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAuthorizationCode() { return authorizationCode; }
    public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }
    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public String getGranteeOrg() { return granteeOrg; }
    public void setGranteeOrg(String granteeOrg) { this.granteeOrg = granteeOrg; }
    public String getGranteeUser() { return granteeUser; }
    public void setGranteeUser(String granteeUser) { this.granteeUser = granteeUser; }
    public String getShareMode() { return shareMode; }
    public void setShareMode(String shareMode) { this.shareMode = shareMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCredentialRef() { return credentialRef; }
    public void setCredentialRef(String credentialRef) { this.credentialRef = credentialRef; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
