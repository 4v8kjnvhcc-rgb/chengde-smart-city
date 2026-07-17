package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_fusion_relation")
public class GovFusionRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long domainId;
    private String relationCode;
    private String relationName;
    private Long fromEntityId;
    private Long toEntityId;
    private String relationType;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDomainId() { return domainId; }
    public void setDomainId(Long domainId) { this.domainId = domainId; }
    public String getRelationCode() { return relationCode; }
    public void setRelationCode(String relationCode) { this.relationCode = relationCode; }
    public String getRelationName() { return relationName; }
    public void setRelationName(String relationName) { this.relationName = relationName; }
    public Long getFromEntityId() { return fromEntityId; }
    public void setFromEntityId(Long fromEntityId) { this.fromEntityId = fromEntityId; }
    public Long getToEntityId() { return toEntityId; }
    public void setToEntityId(Long toEntityId) { this.toEntityId = toEntityId; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
