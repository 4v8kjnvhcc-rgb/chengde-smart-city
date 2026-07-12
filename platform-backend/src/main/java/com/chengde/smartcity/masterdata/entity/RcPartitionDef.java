package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("rc_partition_def")
public class RcPartitionDef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String partitionCode;
    private String partitionName;
    private String partitionType;
    private Long themeId;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPartitionCode() { return partitionCode; }
    public void setPartitionCode(String partitionCode) { this.partitionCode = partitionCode; }
    public String getPartitionName() { return partitionName; }
    public void setPartitionName(String partitionName) { this.partitionName = partitionName; }
    public String getPartitionType() { return partitionType; }
    public void setPartitionType(String partitionType) { this.partitionType = partitionType; }
    public Long getThemeId() { return themeId; }
    public void setThemeId(Long themeId) { this.themeId = themeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
