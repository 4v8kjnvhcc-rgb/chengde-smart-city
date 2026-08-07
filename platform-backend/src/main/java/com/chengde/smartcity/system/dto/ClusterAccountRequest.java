package com.chengde.smartcity.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ClusterAccountRequest {

    @NotBlank
    private String clusterCode;
    @NotBlank
    private String clusterName;
    private String accountName;
    private String accountPassword;
    private String endpoint;
    private String remark;
    @NotNull
    private Integer status;

    public String getClusterCode() { return clusterCode; }
    public void setClusterCode(String clusterCode) { this.clusterCode = clusterCode; }
    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getAccountPassword() { return accountPassword; }
    public void setAccountPassword(String accountPassword) { this.accountPassword = accountPassword; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
