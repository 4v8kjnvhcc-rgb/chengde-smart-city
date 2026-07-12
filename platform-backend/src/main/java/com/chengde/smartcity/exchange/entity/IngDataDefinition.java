package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_data_definition")
public class IngDataDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String defCode;
    private String defName;
    private String businessDesc;
    private String techDesc;
    private String metadataJson;
    private String status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDefCode() { return defCode; }
    public void setDefCode(String defCode) { this.defCode = defCode; }
    public String getDefName() { return defName; }
    public void setDefName(String defName) { this.defName = defName; }
    public String getBusinessDesc() { return businessDesc; }
    public void setBusinessDesc(String businessDesc) { this.businessDesc = businessDesc; }
    public String getTechDesc() { return techDesc; }
    public void setTechDesc(String techDesc) { this.techDesc = techDesc; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
