package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_meta_relation")
public class GovMetaRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fromCode;
    private String toCode;
    private String relationType;
    private String label;
    private String status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFromCode() { return fromCode; }
    public void setFromCode(String fromCode) { this.fromCode = fromCode; }
    public String getToCode() { return toCode; }
    public void setToCode(String toCode) { this.toCode = toCode; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
