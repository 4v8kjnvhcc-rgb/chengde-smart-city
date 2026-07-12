package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ing_asset_tag")
public class IngAssetTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tagCode;
    private String tagName;
    private String ruleExpr;
    private String tagDesc;
    private Integer hitCount;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
}
