package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gov_fusion_field")
public class GovFusionField {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long entityId;
    private String fieldCode;
    private String fieldName;
    private String dataType;
    private Integer nullableFlag;
    private Integer pkFlag;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getFieldCode() { return fieldCode; }
    public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Integer getNullableFlag() { return nullableFlag; }
    public void setNullableFlag(Integer nullableFlag) { this.nullableFlag = nullableFlag; }
    public Integer getPkFlag() { return pkFlag; }
    public void setPkFlag(Integer pkFlag) { this.pkFlag = pkFlag; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
