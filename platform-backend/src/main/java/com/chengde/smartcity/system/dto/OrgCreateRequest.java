package com.chengde.smartcity.system.dto;

import jakarta.validation.constraints.NotBlank;

public record OrgCreateRequest(
        @NotBlank String orgCode,
        @NotBlank String orgName,
        Long parentId,
        Integer orgType
) {}
