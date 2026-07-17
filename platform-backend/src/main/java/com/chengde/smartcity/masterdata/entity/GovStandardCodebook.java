package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("gov_standard_codebook")
public class GovStandardCodebook {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long standardItemId;
    private String codeValue;
    private String codeName;
    private String codeDesc;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStandardItemId() { return standardItemId; }
    public void setStandardItemId(Long standardItemId) { this.standardItemId = standardItemId; }
    public String getCodeValue() { return codeValue; }
    public void setCodeValue(String codeValue) { this.codeValue = codeValue; }
    public String getCodeName() { return codeName; }
    public void setCodeName(String codeName) { this.codeName = codeName; }
    public String getCodeDesc() { return codeDesc; }
    public void setCodeDesc(String codeDesc) { this.codeDesc = codeDesc; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
