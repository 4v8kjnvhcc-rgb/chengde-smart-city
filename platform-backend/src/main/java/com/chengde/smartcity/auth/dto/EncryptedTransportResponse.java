package com.chengde.smartcity.auth.dto;

/**
 * 登录回包密文信封：用请求侧同一把 AES 密钥加密（新 IV），无需再传 keyCipher。
 */
public record EncryptedTransportResponse(
        String kid,
        String iv,
        String cipherText
) {
}
