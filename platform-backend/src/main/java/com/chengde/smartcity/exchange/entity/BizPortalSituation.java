package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("biz_portal_situation")
public class BizPortalSituation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String situationCode;
    private String situationName;
    private String domainRoute;
    private String modelMCode;
    private String summaryMetric;
    private String boardUrl;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSituationCode() { return situationCode; }
    public void setSituationCode(String situationCode) { this.situationCode = situationCode; }
    public String getSituationName() { return situationName; }
    public void setSituationName(String situationName) { this.situationName = situationName; }
    public String getDomainRoute() { return domainRoute; }
    public void setDomainRoute(String domainRoute) { this.domainRoute = domainRoute; }
    public String getModelMCode() { return modelMCode; }
    public void setModelMCode(String modelMCode) { this.modelMCode = modelMCode; }
    public String getSummaryMetric() { return summaryMetric; }
    public void setSummaryMetric(String summaryMetric) { this.summaryMetric = summaryMetric; }
    public String getBoardUrl() { return boardUrl; }
    public void setBoardUrl(String boardUrl) { this.boardUrl = boardUrl; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
