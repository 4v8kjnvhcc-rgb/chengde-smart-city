package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rc_partition_def")
public class RcPartitionDef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String partitionCode;
    private String partitionName;
    private String partitionType;
    private Long themeId;
    private String tableName;
    private String partitionColumn;
    private String expressionText;
    private String previewDdl;
    private String pretestStatus;
    private String pretestMessage;
    private LocalDateTime pretestAt;
    private String remark;
    private LocalDateTime updatedAt;
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
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getPartitionColumn() { return partitionColumn; }
    public void setPartitionColumn(String partitionColumn) { this.partitionColumn = partitionColumn; }
    public String getExpressionText() { return expressionText; }
    public void setExpressionText(String expressionText) { this.expressionText = expressionText; }
    public String getPreviewDdl() { return previewDdl; }
    public void setPreviewDdl(String previewDdl) { this.previewDdl = previewDdl; }
    public String getPretestStatus() { return pretestStatus; }
    public void setPretestStatus(String pretestStatus) { this.pretestStatus = pretestStatus; }
    public String getPretestMessage() { return pretestMessage; }
    public void setPretestMessage(String pretestMessage) { this.pretestMessage = pretestMessage; }
    public LocalDateTime getPretestAt() { return pretestAt; }
    public void setPretestAt(LocalDateTime pretestAt) { this.pretestAt = pretestAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
