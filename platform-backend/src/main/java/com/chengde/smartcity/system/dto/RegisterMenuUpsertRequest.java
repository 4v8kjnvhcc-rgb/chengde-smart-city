package com.chengde.smartcity.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RegisterMenuUpsertRequest {

    @NotNull
    private Long parentId;
    /** 可空：空则服务端按标题/权限码自动生成 */
    private String routeName;
    @NotBlank
    private String menuName;
    private String icon;
    /** 目录可空；菜单项/按钮必填（服务端再校验） */
    private String path;
    private String component;
    /** 1目录 2菜单项 3按钮 */
    @NotNull
    private Integer menuType;
    /** 1显示 0隐藏 */
    @NotNull
    private Integer visible;
    private Integer sortOrder;
    private String permission;
    private String mCode;

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }
    public String getMenuName() { return menuName; }
    public void setMenuName(String menuName) { this.menuName = menuName; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public Integer getMenuType() { return menuType; }
    public void setMenuType(Integer menuType) { this.menuType = menuType; }
    public Integer getVisible() { return visible; }
    public void setVisible(Integer visible) { this.visible = visible; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public String getMCode() { return mCode; }
    public void setMCode(String mCode) { this.mCode = mCode; }
}
