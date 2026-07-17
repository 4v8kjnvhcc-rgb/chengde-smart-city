package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_fusion_script_version")
public class GovFusionScriptVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private Integer versionNo;
    private String scriptContent;
    private String changeSummary;
    private String publishedBy;
    private LocalDateTime publishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getScriptContent() { return scriptContent; }
    public void setScriptContent(String scriptContent) { this.scriptContent = scriptContent; }
    public String getChangeSummary() { return changeSummary; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
