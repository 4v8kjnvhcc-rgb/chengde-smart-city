package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_mask_crypto_ref")
public class IngMaskCryptoRef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String refCode;
    private String refName;
    private String algo;
    private String keyAlias;
    private Integer rotateDays;
    private String ownerOrg;
    private String status;
    private String remark;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRefCode() { return refCode; }
    public void setRefCode(String refCode) { this.refCode = refCode; }
    public String getRefName() { return refName; }
    public void setRefName(String refName) { this.refName = refName; }
    public String getAlgo() { return algo; }
    public void setAlgo(String algo) { this.algo = algo; }
    public String getKeyAlias() { return keyAlias; }
    public void setKeyAlias(String keyAlias) { this.keyAlias = keyAlias; }
    public Integer getRotateDays() { return rotateDays; }
    public void setRotateDays(Integer rotateDays) { this.rotateDays = rotateDays; }
    public String getOwnerOrg() { return ownerOrg; }
    public void setOwnerOrg(String ownerOrg) { this.ownerOrg = ownerOrg; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
