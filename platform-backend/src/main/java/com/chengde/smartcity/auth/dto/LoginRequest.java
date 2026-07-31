package com.chengde.smartcity.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        String totpCode,
        String captchaId,
        String captchaCode
) {
}
