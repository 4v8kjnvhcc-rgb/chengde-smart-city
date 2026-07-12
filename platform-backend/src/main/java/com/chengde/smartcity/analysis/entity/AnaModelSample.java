package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

@TableName("ana_model_sample")
public class AnaModelSample {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelId;
    private Integer rowNo;
    private String dim1;
    private String dim2;
    private BigDecimal metric1;
    private BigDecimal metric2;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public Integer getRowNo() { return rowNo; }
    public void setRowNo(Integer rowNo) { this.rowNo = rowNo; }
    public String getDim1() { return dim1; }
    public void setDim1(String dim1) { this.dim1 = dim1; }
    public String getDim2() { return dim2; }
    public void setDim2(String dim2) { this.dim2 = dim2; }
    public BigDecimal getMetric1() { return metric1; }
    public void setMetric1(BigDecimal metric1) { this.metric1 = metric1; }
    public BigDecimal getMetric2() { return metric2; }
    public void setMetric2(BigDecimal metric2) { this.metric2 = metric2; }
}
