package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("rc_asset_catalog_entry")
public class RcAssetCatalogEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String entryCode;
    private String entryName;
    private Long libId;
    private String driveTask;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEntryCode() { return entryCode; }
    public void setEntryCode(String entryCode) { this.entryCode = entryCode; }
    public String getEntryName() { return entryName; }
    public void setEntryName(String entryName) { this.entryName = entryName; }
    public Long getLibId() { return libId; }
    public void setLibId(Long libId) { this.libId = libId; }
    public String getDriveTask() { return driveTask; }
    public void setDriveTask(String driveTask) { this.driveTask = driveTask; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
