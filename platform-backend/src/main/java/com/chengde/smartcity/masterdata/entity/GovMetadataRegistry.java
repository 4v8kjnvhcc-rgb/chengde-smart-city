package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("gov_metadata_registry")
public class GovMetadataRegistry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String entryCode;
    private String entryName;
    private String entryType;
    private String parentCode;
    private String omRef;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEntryCode() { return entryCode; }
    public void setEntryCode(String entryCode) { this.entryCode = entryCode; }
    public String getEntryName() { return entryName; }
    public void setEntryName(String entryName) { this.entryName = entryName; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public String getParentCode() { return parentCode; }
    public void setParentCode(String parentCode) { this.parentCode = parentCode; }
    public String getOmRef() { return omRef; }
    public void setOmRef(String omRef) { this.omRef = omRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
