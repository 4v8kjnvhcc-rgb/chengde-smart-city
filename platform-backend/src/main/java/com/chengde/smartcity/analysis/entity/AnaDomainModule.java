package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ana_domain_module")
public class AnaDomainModule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String domainCode;
    private String mCode;
    private String moduleName;
    private String moduleType;
    private String capGroup;
    private String deDashboardId;
    private String status;
    private Integer sortOrder;
    private LocalDateTime lastRunAt;
    private String lastMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDomainCode() { return domainCode; }
    public void setDomainCode(String domainCode) { this.domainCode = domainCode; }
    public String getMCode() { return mCode; }
    public void setMCode(String mCode) { this.mCode = mCode; }
    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public String getModuleType() { return moduleType; }
    public void setModuleType(String moduleType) { this.moduleType = moduleType; }
    public String getCapGroup() { return capGroup; }
    public void setCapGroup(String capGroup) { this.capGroup = capGroup; }
    public String getDeDashboardId() { return deDashboardId; }
    public void setDeDashboardId(String deDashboardId) { this.deDashboardId = deDashboardId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
}
