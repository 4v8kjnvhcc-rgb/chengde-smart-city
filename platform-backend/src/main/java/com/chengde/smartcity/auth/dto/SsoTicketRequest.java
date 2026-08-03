package com.chengde.smartcity.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SsoTicketRequest(
        @NotBlank String targetApp,
        String redirectUrl
) {}
