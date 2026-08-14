package com.chengde.smartcity.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 混合加密传输体：RSA-OAEP(AES-key) + AES-GCM(敏感 JSON)。
 * 登录时 captcha/totp 可放在外层；账号密码必须在 cipherText 内。
 */
public record EncryptedTransportRequest(
        @NotBlank String kid,
        @NotBlank String keyCipher,
        @NotBlank String iv,
        @NotBlank String cipherText,
        String totpCode,
        String captchaId,
        String captchaCode
) {
}
