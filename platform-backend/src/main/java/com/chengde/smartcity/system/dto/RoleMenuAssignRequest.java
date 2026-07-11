package com.chengde.smartcity.system.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RoleMenuAssignRequest(@NotEmpty List<Long> menuIds) {
}
