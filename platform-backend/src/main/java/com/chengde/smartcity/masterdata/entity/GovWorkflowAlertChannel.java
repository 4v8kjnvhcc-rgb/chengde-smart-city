package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_workflow_alert_channel")
public class GovWorkflowAlertChannel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer mailEnabled;
    private String mailReceivers;
    private Integer smsEnabled;
    private String smsPhones;
    private String smsGatewayUrl;
    private String smsSignName;
    private String smsTemplateCode;
    private String ownerName;
    private String updatedBy;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getMailEnabled() { return mailEnabled; }
    public void setMailEnabled(Integer mailEnabled) { this.mailEnabled = mailEnabled; }
    public String getMailReceivers() { return mailReceivers; }
    public void setMailReceivers(String mailReceivers) { this.mailReceivers = mailReceivers; }
    public Integer getSmsEnabled() { return smsEnabled; }
    public void setSmsEnabled(Integer smsEnabled) { this.smsEnabled = smsEnabled; }
    public String getSmsPhones() { return smsPhones; }
    public void setSmsPhones(String smsPhones) { this.smsPhones = smsPhones; }
    public String getSmsGatewayUrl() { return smsGatewayUrl; }
    public void setSmsGatewayUrl(String smsGatewayUrl) { this.smsGatewayUrl = smsGatewayUrl; }
    public String getSmsSignName() { return smsSignName; }
    public void setSmsSignName(String smsSignName) { this.smsSignName = smsSignName; }
    public String getSmsTemplateCode() { return smsTemplateCode; }
    public void setSmsTemplateCode(String smsTemplateCode) { this.smsTemplateCode = smsTemplateCode; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
