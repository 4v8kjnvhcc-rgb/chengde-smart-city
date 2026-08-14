package com.chengde.smartcity.system.dto;

import com.chengde.smartcity.auth.dto.EncryptedTransportRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UserCreateRequest(
        @NotBlank String username,
        @NotNull @Valid EncryptedTransportRequest passwordTransport,
        @NotBlank String displayName,
        @NotBlank String phone,
        @NotNull Long orgId,
        List<Long> roleIds
) {
}
