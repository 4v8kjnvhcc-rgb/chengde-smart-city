package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ana_bi_widget")
public class AnaBiWidget {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String widgetCode;
    private String widgetName;
    private String widgetType;
    private String mCode;
    private String deDashboardId;
    private String description;
    private Integer sortOrder;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWidgetCode() { return widgetCode; }
    public void setWidgetCode(String widgetCode) { this.widgetCode = widgetCode; }
    public String getWidgetName() { return widgetName; }
    public void setWidgetName(String widgetName) { this.widgetName = widgetName; }
    public String getWidgetType() { return widgetType; }
    public void setWidgetType(String widgetType) { this.widgetType = widgetType; }
    public String getMCode() { return mCode; }
    public void setMCode(String mCode) { this.mCode = mCode; }
    public String getDeDashboardId() { return deDashboardId; }
    public void setDeDashboardId(String deDashboardId) { this.deDashboardId = deDashboardId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
