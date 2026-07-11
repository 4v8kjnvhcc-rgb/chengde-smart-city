package com.chengde.smartcity.system.dto;

import java.util.List;

public record MenuTreeNode(
        Long id,
        Long parentId,
        String menuName,
        Integer menuType,
        String path,
        String component,
        String permission,
        String icon,
        String mCode,
        String integrationType,
        List<MenuTreeNode> children
) {
}
