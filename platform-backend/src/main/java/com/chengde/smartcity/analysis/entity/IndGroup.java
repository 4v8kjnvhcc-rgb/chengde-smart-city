package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

@TableName("ind_group")
public class IndGroup {
    @TableId(value = "uuid", type = IdType.INPUT)
    private String uuid;
    private String name;
    private String tableName;
    private Integer type;
    private String areaId;
    private String remark;
    private Integer publishStatus;
    private LocalDateTime publishTime;
    private String publishBy;
    private Integer modelType;
    private String dbMarkId;
    private String tableMarkId;

    @TableField(exist = false)
    private String ownerDomainCode;
    @TableField(exist = false)
    private String indicatorDomainName;

    @JsonProperty("id")
    public String getId() { return uuid; }
    @JsonProperty("id")
    public void setId(String id) { this.uuid = id; }
    @JsonIgnore
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    @JsonProperty("groupName")
    public String getGroupName() { return name; }
    @JsonProperty("groupName")
    public void setGroupName(String groupName) { this.name = groupName; }
    @JsonIgnore
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @JsonProperty("targetTable")
    public String getTargetTable() { return tableName; }
    @JsonProperty("targetTable")
    public void setTargetTable(String targetTable) { this.tableName = targetTable; }
    @JsonIgnore
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    @JsonProperty("groupCategory")
    public String getGroupCategory() {
        if (type == null) return "UNIT";
        if (type == 2) return "LIST";
        if (type == 3) return "COMPOSITE";
        return "UNIT";
    }
    @JsonProperty("groupCategory")
    public void setGroupCategory(String groupCategory) {
        this.type = categoryToType(groupCategory);
    }

    @JsonProperty("indicatorDomainId")
    public String getIndicatorDomainId() { return areaId; }
    @JsonProperty("indicatorDomainId")
    public void setIndicatorDomainId(String indicatorDomainId) { this.areaId = indicatorDomainId; }
    @JsonIgnore
    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }

    @JsonProperty("description")
    public String getDescription() { return remark; }
    @JsonProperty("description")
    public void setDescription(String description) { this.remark = description; }
    @JsonIgnore
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getPublishStatus() { return publishStatus; }
    public void setPublishStatus(Integer publishStatus) { this.publishStatus = publishStatus; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }
    public String getPublishBy() { return publishBy; }
    public void setPublishBy(String publishBy) { this.publishBy = publishBy; }
    public Integer getModelType() { return modelType; }
    public void setModelType(Integer modelType) { this.modelType = modelType; }

    @JsonProperty("modelMethod")
    public String getModelMethod() {
        return modelType != null && modelType == 2 ? "MANUAL" : "SQL";
    }
    @JsonProperty("modelMethod")
    public void setModelMethod(String modelMethod) {
        this.modelType = "MANUAL".equalsIgnoreCase(modelMethod) ? 2 : 1;
    }

    @JsonProperty("status")
    public String getStatus() {
        if (publishStatus != null && publishStatus == 1) return "PUBLISHED";
        if (publishStatus != null && publishStatus == 2) return "OFFLINE";
        return "DRAFT";
    }

    public String getDbMarkId() { return dbMarkId; }
    public void setDbMarkId(String dbMarkId) { this.dbMarkId = dbMarkId; }
    public String getTableMarkId() { return tableMarkId; }
    public void setTableMarkId(String tableMarkId) { this.tableMarkId = tableMarkId; }
    public String getOwnerDomainCode() { return ownerDomainCode; }
    public void setOwnerDomainCode(String ownerDomainCode) { this.ownerDomainCode = ownerDomainCode; }
    public String getIndicatorDomainName() { return indicatorDomainName; }
    public void setIndicatorDomainName(String indicatorDomainName) { this.indicatorDomainName = indicatorDomainName; }

    public static int categoryToType(String category) {
        if (category == null) return 1;
        String c = category.trim().toUpperCase();
        if ("2".equals(c) || "LIST".equals(c)) return 2;
        if ("3".equals(c) || "COMPOSITE".equals(c)) return 3;
        return 1;
    }
}
