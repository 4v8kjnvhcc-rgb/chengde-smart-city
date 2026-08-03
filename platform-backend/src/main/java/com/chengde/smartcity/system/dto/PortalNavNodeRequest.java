package com.chengde.smartcity.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PortalNavNodeRequest(
        @NotNull Long parentId,
        @NotBlank String name,
        @NotBlank String nodeType,
        Integer sortOrder,
        String url,
        String menuPath,
        String openMode,
        String ssoMode,
        String themeKey,
        String remark,
        Integer status
) {}
