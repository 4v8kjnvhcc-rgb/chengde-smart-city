package com.chengde.smartcity.system.dto;

import jakarta.validation.constraints.NotBlank;

public record SysDictItemRequest(
        @NotBlank String itemKey,
        String itemValue,
        String itemLabel,
        Integer sortOrder,
        Integer status,
        String remark
) {
}
