package com.chengde.smartcity.system.dto;

import jakarta.validation.constraints.NotBlank;

public record SysDictRequest(
        @NotBlank String dictCode,
        @NotBlank String dictName,
        String remark,
        Integer sortOrder,
        Integer status
) {
}
