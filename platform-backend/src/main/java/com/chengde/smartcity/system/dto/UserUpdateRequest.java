package com.chengde.smartcity.system.dto;

import java.util.List;

public record UserUpdateRequest(
        String displayName,
        Integer status,
        Long orgId,
        List<Long> roleIds
) {
}
