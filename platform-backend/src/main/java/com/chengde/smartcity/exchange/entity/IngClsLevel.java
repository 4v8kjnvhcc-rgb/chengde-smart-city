package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_cls_level")
public class IngClsLevel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String levelCode;
    private String levelName;
    private Integer sortNo;
    private Integer sensitivityScore;
    private String controlStrength;
    private String description;
    private Integer shareAllowed;
    private Integer openAllowed;
    private Integer maskRequired;
    private String approvalLevel;
    private String status;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLevelCode() { return levelCode; }
    public void setLevelCode(String levelCode) { this.levelCode = levelCode; }
    public String getLevelName() { return levelName; }
    public void setLevelName(String levelName) { this.levelName = levelName; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Integer getSensitivityScore() { return sensitivityScore; }
    public void setSensitivityScore(Integer sensitivityScore) { this.sensitivityScore = sensitivityScore; }
    public String getControlStrength() { return controlStrength; }
    public void setControlStrength(String controlStrength) { this.controlStrength = controlStrength; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getShareAllowed() { return shareAllowed; }
    public void setShareAllowed(Integer shareAllowed) { this.shareAllowed = shareAllowed; }
    public Integer getOpenAllowed() { return openAllowed; }
    public void setOpenAllowed(Integer openAllowed) { this.openAllowed = openAllowed; }
    public Integer getMaskRequired() { return maskRequired; }
    public void setMaskRequired(Integer maskRequired) { this.maskRequired = maskRequired; }
    public String getApprovalLevel() { return approvalLevel; }
    public void setApprovalLevel(String approvalLevel) { this.approvalLevel = approvalLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
