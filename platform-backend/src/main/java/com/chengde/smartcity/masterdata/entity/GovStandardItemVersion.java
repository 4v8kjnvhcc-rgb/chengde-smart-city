package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_standard_item_version")
public class GovStandardItemVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long standardItemId;
    private Integer versionNo;
    private String snapshotJson;
    private String changeSummary;
    private String publishedBy;
    private LocalDateTime publishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStandardItemId() { return standardItemId; }
    public void setStandardItemId(Long standardItemId) { this.standardItemId = standardItemId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getChangeSummary() { return changeSummary; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
