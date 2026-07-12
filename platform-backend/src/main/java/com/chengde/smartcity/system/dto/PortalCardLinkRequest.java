package com.chengde.smartcity.system.dto;

import jakarta.validation.constraints.NotBlank;

public record PortalCardLinkRequest(
        @NotBlank String platformPath,
        @NotBlank String title,
        @NotBlank String url,
        String description,
        String openMode,
        String ssoMode,
        String ssoParam,
        Integer sortOrder,
        Integer status
) {}
