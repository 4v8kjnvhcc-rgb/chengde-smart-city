package com.chengde.smartcity.system.dto;

import java.util.ArrayList;
import java.util.List;

public class PortalNavNodeTree {

    private Long id;
    private Long parentId;
    private String name;
    private String nodeType;
    private Integer sortOrder;
    private String url;
    private String menuPath;
    private String openMode;
    private String themeKey;
    private String remark;
    private Integer status;
    private List<PortalNavNodeTree> children = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getMenuPath() { return menuPath; }
    public void setMenuPath(String menuPath) { this.menuPath = menuPath; }
    public String getOpenMode() { return openMode; }
    public void setOpenMode(String openMode) { this.openMode = openMode; }
    public String getThemeKey() { return themeKey; }
    public void setThemeKey(String themeKey) { this.themeKey = themeKey; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public List<PortalNavNodeTree> getChildren() { return children; }
    public void setChildren(List<PortalNavNodeTree> children) { this.children = children; }
}
