package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.ArrayList;
import java.util.List;

@TableName("ing_asset_tag")
public class IngAssetTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private Integer level;
    private String tagSource;
    private String stdCode;
    private String tagCode;
    private String tagName;
    private String ruleExpr;
    private String tagDesc;
    private Integer hitCount;
    private String status;

    @TableField(exist = false)
    private List<IngAssetTag> children = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public String getTagSource() { return tagSource; }
    public void setTagSource(String tagSource) { this.tagSource = tagSource; }
    public String getStdCode() { return stdCode; }
    public void setStdCode(String stdCode) { this.stdCode = stdCode; }
    public String getTagCode() { return tagCode; }
    public void setTagCode(String tagCode) { this.tagCode = tagCode; }
    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
    public String getRuleExpr() { return ruleExpr; }
    public void setRuleExpr(String ruleExpr) { this.ruleExpr = ruleExpr; }
    public String getTagDesc() { return tagDesc; }
    public void setTagDesc(String tagDesc) { this.tagDesc = tagDesc; }
    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<IngAssetTag> getChildren() { return children; }
    public void setChildren(List<IngAssetTag> children) { this.children = children; }
}
