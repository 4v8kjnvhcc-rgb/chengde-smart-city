package com.chengde.smartcity.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UserCreateRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String displayName,
        @NotNull Long orgId,
        List<Long> roleIds
) {
}
