package com.chengde.smartcity.system.dto;

public record OrgUpdateRequest(
        String orgName,
        Long parentId,
        Integer status,
        Integer sortOrder
) {}
