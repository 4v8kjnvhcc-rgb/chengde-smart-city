package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@TableName("ind_area")
public class IndArea {
    @TableId(value = "uuid", type = IdType.INPUT)
    private String uuid;
    private String name;
    private String dbSchema;
    private String remark;
    private String dbMarkId;

    @TableField(exist = false)
    private String ownerDomainCode;

    /** 该域下是否已有指标（组）；有则禁止编辑/删除 */
    @TableField(exist = false)
    private Boolean hasIndicators;

    @JsonProperty("id")
    public String getId() { return uuid; }
    @JsonProperty("id")
    public void setId(String id) { this.uuid = id; }

    @JsonIgnore
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    @JsonProperty("domainName")
    public String getDomainName() { return name; }
    @JsonProperty("domainName")
    public void setDomainName(String domainName) { this.name = domainName; }

    @JsonIgnore
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @JsonProperty("domainDbName")
    public String getDomainDbName() { return dbSchema; }
    @JsonProperty("domainDbName")
    public void setDomainDbName(String domainDbName) { this.dbSchema = domainDbName; }

    @JsonIgnore
    public String getDbSchema() { return dbSchema; }
    public void setDbSchema(String dbSchema) { this.dbSchema = dbSchema; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getDbMarkId() { return dbMarkId; }
    public void setDbMarkId(String dbMarkId) { this.dbMarkId = dbMarkId; }
    public String getOwnerDomainCode() { return ownerDomainCode; }
    public void setOwnerDomainCode(String ownerDomainCode) { this.ownerDomainCode = ownerDomainCode; }

    public Boolean getHasIndicators() { return hasIndicators; }
    public void setHasIndicators(Boolean hasIndicators) { this.hasIndicators = hasIndicators; }
}
