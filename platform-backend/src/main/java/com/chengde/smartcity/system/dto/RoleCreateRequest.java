package com.chengde.smartcity.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleCreateRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$", message = "角色编码须以字母开头，仅含字母数字下划线")
        String roleCode,
        @NotBlank
        @Size(max = 128)
        String roleName,
        @Size(max = 255)
        String description,
        Integer roleType
) {}
