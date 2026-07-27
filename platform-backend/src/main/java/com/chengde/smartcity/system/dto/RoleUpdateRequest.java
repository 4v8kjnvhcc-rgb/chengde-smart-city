package com.chengde.smartcity.system.dto;

import jakarta.validation.constraints.Size;

public record RoleUpdateRequest(
        @Size(max = 128)
        String roleName,
        @Size(max = 255)
        String description,
        Integer status
) {}
