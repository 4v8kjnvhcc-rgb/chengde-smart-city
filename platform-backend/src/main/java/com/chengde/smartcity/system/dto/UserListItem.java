package com.chengde.smartcity.system.dto;

import java.util.ArrayList;
import java.util.List;

/** 用户列表项（含角色，供组织机构侧按单位查看账号） */
public class UserListItem {

    private Long id;
    private String username;
    private String displayName;
    private Long orgId;
    private Integer status;
    private List<Long> roleIds = new ArrayList<>();
    private List<String> roleNames = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds != null ? roleIds : new ArrayList<>(); }
    public List<String> getRoleNames() { return roleNames; }
    public void setRoleNames(List<String> roleNames) { this.roleNames = roleNames != null ? roleNames : new ArrayList<>(); }
}
