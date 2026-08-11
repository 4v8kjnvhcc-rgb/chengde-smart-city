package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ana_pop_service_contract")
public class AnaPopServiceContract {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String domainCode;
    private String serviceCode;
    private String serviceName;
    private String mCode;
    private String mode;
    private String pathOrChannel;
    private String requestSample;
    private String responseSample;
    private String description;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDomainCode() { return domainCode; }
    public void setDomainCode(String domainCode) { this.domainCode = domainCode; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getMCode() { return mCode; }
    public void setMCode(String mCode) { this.mCode = mCode; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getPathOrChannel() { return pathOrChannel; }
    public void setPathOrChannel(String pathOrChannel) { this.pathOrChannel = pathOrChannel; }
    public String getRequestSample() { return requestSample; }
    public void setRequestSample(String requestSample) { this.requestSample = requestSample; }
    public String getResponseSample() { return responseSample; }
    public void setResponseSample(String responseSample) { this.responseSample = responseSample; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
