package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_upload_template")
public class IngUploadTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateCode;
    private String templateName;
    private String columnMappingJson;
    private String validateRulesJson;
    private String status;
    private Long orgId;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String orgName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getColumnMappingJson() { return columnMappingJson; }
    public void setColumnMappingJson(String columnMappingJson) { this.columnMappingJson = columnMappingJson; }
    public String getValidateRulesJson() { return validateRulesJson; }
    public void setValidateRulesJson(String validateRulesJson) { this.validateRulesJson = validateRulesJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
}
