package com.chengde.smartcity.auth;

import com.chengde.smartcity.auth.dto.EncryptedTransportRequest;
import com.chengde.smartcity.auth.dto.EncryptedTransportResponse;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.config.TransportCryptoProperties;
import com.chengde.smartcity.security.SessionRedisService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TransportCryptoService {

    private static final Logger log = LoggerFactory.getLogger(TransportCryptoService.class);
    private static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private final TransportCryptoProperties properties;
    private final SessionRedisService sessionRedisService;
    private final ObjectMapper objectMapper;

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private String publicKeySpkiBase64;

    public TransportCryptoService(TransportCryptoProperties properties,
                                  SessionRedisService sessionRedisService,
                                  ObjectMapper objectMapper) {
        this.properties = properties;
        this.sessionRedisService = sessionRedisService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            String pem = properties.privateKeyPem() == null ? "" : properties.privateKeyPem().trim();
            if (!pem.isEmpty()) {
                this.privateKey = loadPrivateKey(pem);
                this.publicKey = publicKeyFromPrivate(privateKey);
                log.info("Transport crypto loaded from configured PEM, kid={}", properties.kid());
            } else {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048);
                KeyPair pair = gen.generateKeyPair();
                this.privateKey = pair.getPrivate();
                this.publicKey = pair.getPublic();
                log.warn("Transport crypto using ephemeral RSA key (set app.transport-crypto.private-key-pem for multi-instance), kid={}",
                        properties.kid());
            }
            this.publicKeySpkiBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("初始化传输加密密钥失败", e);
        }
    }

    public Map<String, Object> publicKeyInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kid", properties.kid());
        m.put("algorithm", "RSA-OAEP");
        m.put("hash", "SHA-256");
        m.put("publicKey", publicKeySpkiBase64);
        return m;
    }

    /** 解密并校验 ts/nonce，同时保留 AES 密钥供加密回包。 */
    public record OpenedEnvelope(JsonNode payload, byte[] aesKey) {
    }

    /** 解密并校验 ts/nonce，返回明文 JSON。 */
    public JsonNode decryptAndVerify(EncryptedTransportRequest request) {
        return openEnvelope(request).payload();
    }

    public OpenedEnvelope openEnvelope(EncryptedTransportRequest request) {
        if (request.kid() == null || !request.kid().equals(properties.kid())) {
            throw new BusinessException(400, "加密密钥版本不匹配，请刷新页面后重试");
        }
        try {
            byte[] aesKey = rsaOaepDecrypt(Base64.getDecoder().decode(request.keyCipher()));
            if (aesKey.length != 16 && aesKey.length != 32) {
                throw new BusinessException(400, "凭证解密失败");
            }
            byte[] iv = Base64.getDecoder().decode(request.iv());
            byte[] cipherBytes = Base64.getDecoder().decode(request.cipherText());
            byte[] plainBytes = aesGcmDecrypt(aesKey, iv, cipherBytes);
            JsonNode node = objectMapper.readTree(new String(plainBytes, StandardCharsets.UTF_8));
            verifyTsAndNonce(node);
            return new OpenedEnvelope(node, aesKey);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Transport decrypt failed: {}", e.toString());
            throw new BusinessException(400, "凭证解密失败");
        }
    }

    /** 用请求侧同一把 AES 密钥加密回包（新 IV）。 */
    public EncryptedTransportResponse encryptForClient(byte[] aesKey, Object body) {
        if (aesKey == null || (aesKey.length != 16 && aesKey.length != 32)) {
            throw new BusinessException(500, "登录回包加密失败");
        }
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            byte[] plain = objectMapper.writeValueAsBytes(body);
            byte[] cipher = aesGcmEncrypt(aesKey, iv, plain);
            return new EncryptedTransportResponse(
                    properties.kid(),
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(cipher));
        } catch (Exception e) {
            log.warn("Transport encrypt response failed: {}", e.toString());
            throw new BusinessException(500, "登录回包加密失败");
        }
    }

    public String requireText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) {
            throw new BusinessException(400, "缺少字段: " + field);
        }
        return v.asText();
    }

    private void verifyTsAndNonce(JsonNode node) {
        JsonNode tsNode = node.get("ts");
        JsonNode nonceNode = node.get("nonce");
        if (tsNode == null || !tsNode.canConvertToLong() || nonceNode == null || nonceNode.asText().isBlank()) {
            throw new BusinessException(400, "凭证缺少时效参数");
        }
        long ts = tsNode.asLong();
        long skewMs = properties.maxSkewSeconds() * 1000L;
        long now = System.currentTimeMillis();
        if (Math.abs(now - ts) > skewMs) {
            throw new BusinessException(400, "传输凭证已过期，请重试");
        }
        String nonce = nonceNode.asText().trim();
        if (nonce.length() < 8 || nonce.length() > 128) {
            throw new BusinessException(400, "凭证参数无效");
        }
        long ttlSeconds = Math.max(properties.maxSkewSeconds() * 2, 60);
        if (!sessionRedisService.tryConsumeAuthNonce(nonce, ttlSeconds)) {
            throw new BusinessException(400, "传输凭证不可重复使用");
        }
    }

    /** 将 Map 解析为加密信封。 */
    public EncryptedTransportRequest parseEnvelope(Object raw) {
        if (raw == null) {
            throw new BusinessException(400, "缺少加密口令");
        }
        if (raw instanceof EncryptedTransportRequest env) {
            return env;
        }
        if (raw instanceof Map<?, ?> map) {
            String kid = asTrimmed(map.get("kid"));
            String keyCipher = asTrimmed(map.get("keyCipher"));
            String iv = asTrimmed(map.get("iv"));
            String cipherText = asTrimmed(map.get("cipherText"));
            if (kid.isEmpty() || keyCipher.isEmpty() || iv.isEmpty() || cipherText.isEmpty()) {
                throw new BusinessException(400, "加密口令字段不完整");
            }
            return new EncryptedTransportRequest(kid, keyCipher, iv, cipherText, null, null, null);
        }
        throw new BusinessException(400, "加密口令格式无效");
    }

    public String decryptPassword(EncryptedTransportRequest envelope) {
        return requireText(decryptAndVerify(envelope), "password");
    }

    /** 拒绝明文 password；若存在 passwordTransport 则解密返回，否则 null。 */
    public String resolveOptionalTransportPassword(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        rejectPlaintextPassword(body);
        Object raw = body.get("passwordTransport");
        if (raw == null) {
            return null;
        }
        return decryptPassword(parseEnvelope(raw));
    }

    public String requireTransportPassword(Map<String, Object> body) {
        String pwd = resolveOptionalTransportPassword(body);
        if (pwd == null || pwd.isBlank()) {
            throw new BusinessException(400, "请提供加密口令（passwordTransport）");
        }
        return pwd;
    }

    public void rejectPlaintextPassword(Map<String, Object> body) {
        if (body == null) {
            return;
        }
        Object pwd = body.get("password");
        if (pwd != null && !String.valueOf(pwd).isBlank()) {
            throw new BusinessException(400, "请使用加密传输口令（passwordTransport），禁止明文 password");
        }
    }

    private static String asTrimmed(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private byte[] rsaOaepDecrypt(byte[] cipher) throws Exception {
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        c.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256);
        return c.doFinal(cipher);
    }

    private byte[] aesGcmEncrypt(byte[] key, byte[] iv, byte[] plain) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return c.doFinal(plain);
    }

    private byte[] aesGcmDecrypt(byte[] key, byte[] iv, byte[] cipherAndTag) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return c.doFinal(cipherAndTag);
    }

    private static PrivateKey loadPrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static PublicKey publicKeyFromPrivate(PrivateKey privateKey) throws Exception {
        if (privateKey instanceof java.security.interfaces.RSAPrivateCrtKey crt) {
            java.security.spec.RSAPublicKeySpec spec =
                    new java.security.spec.RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent());
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        }
        throw new IllegalStateException("私钥不是 RSA CRT 格式，无法导出公钥；请使用 PKCS#8 RSA 私钥 PEM");
    }
}
