package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_search_global_field")
public class IngSearchGlobalField {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fieldCode;
    private String fieldName;
    private String semantic;
    private String dataType;
    private String controlType;
    private String dictCode;
    private Integer requiredFlag;
    private String matchNameRegex;
    private String matchCommentKeywords;
    private String matchTags;
    private Integer versionNo;
    private String status;
    private String description;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFieldCode() { return fieldCode; }
    public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getSemantic() { return semantic; }
    public void setSemantic(String semantic) { this.semantic = semantic; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getControlType() { return controlType; }
    public void setControlType(String controlType) { this.controlType = controlType; }
    public String getDictCode() { return dictCode; }
    public void setDictCode(String dictCode) { this.dictCode = dictCode; }
    public Integer getRequiredFlag() { return requiredFlag; }
    public void setRequiredFlag(Integer requiredFlag) { this.requiredFlag = requiredFlag; }
    public String getMatchNameRegex() { return matchNameRegex; }
    public void setMatchNameRegex(String matchNameRegex) { this.matchNameRegex = matchNameRegex; }
    public String getMatchCommentKeywords() { return matchCommentKeywords; }
    public void setMatchCommentKeywords(String matchCommentKeywords) { this.matchCommentKeywords = matchCommentKeywords; }
    public String getMatchTags() { return matchTags; }
    public void setMatchTags(String matchTags) { this.matchTags = matchTags; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public java.time.LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }
    public java.time.LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(java.time.LocalDateTime updateTime) { this.updateTime = updateTime; }
}

