package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ana_model_indicator")
public class AnaModelIndicator {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelId;
    private Long indicatorId;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public Long getIndicatorId() { return indicatorId; }
    public void setIndicatorId(Long indicatorId) { this.indicatorId = indicatorId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
