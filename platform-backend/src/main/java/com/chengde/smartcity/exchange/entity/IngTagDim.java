package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_tag_dim")
public class IngTagDim {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dimCode;
    private String dimName;
    private String description;
    private Integer sortNo;
    private String status;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDimCode() { return dimCode; }
    public void setDimCode(String dimCode) { this.dimCode = dimCode; }
    public String getDimName() { return dimName; }
    public void setDimName(String dimName) { this.dimName = dimName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
