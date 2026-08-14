package com.chengde.smartcity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 登录/改密等敏感字段传输加密（RSA-OAEP 包 AES 密钥 + AES-GCM）。
 * 多实例部署时请配置同一 private-key-pem，否则公钥与解密私钥可能不一致。
 */
@ConfigurationProperties(prefix = "app.transport-crypto")
public record TransportCryptoProperties(
        @DefaultValue("v1") String kid,
        /** PKCS#8 PEM；空则进程启动时生成临时密钥 */
        @DefaultValue("") String privateKeyPem,
        /** 允许的时钟偏移（秒），用于校验密文内 ts */
        @DefaultValue("120") long maxSkewSeconds
) {
}
