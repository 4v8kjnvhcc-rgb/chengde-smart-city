package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("rc_base_library")
public class RcBaseLibrary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String libCode;
    private String libName;
    private String libType;
    private Integer recordCount;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLibCode() { return libCode; }
    public void setLibCode(String libCode) { this.libCode = libCode; }
    public String getLibName() { return libName; }
    public void setLibName(String libName) { this.libName = libName; }
    public String getLibType() { return libType; }
    public void setLibType(String libType) { this.libType = libType; }
    public Integer getRecordCount() { return recordCount; }
    public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
