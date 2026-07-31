package com.chengde.smartcity.security;

import com.chengde.smartcity.config.JwtProperties;
import com.chengde.smartcity.config.SecurityProperties;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SessionRedisService {

    private static final String ACTIVE_TOKEN_PREFIX = "session:active:";
    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final String RATE_USER_PREFIX = "rate:user:";
    private static final String RATE_IP_PREFIX = "rate:ip:";

    private final StringRedisTemplate redis;
    private final JwtProperties jwtProperties;
    private final SecurityProperties securityProperties;

    public SessionRedisService(StringRedisTemplate redis, JwtProperties jwtProperties, SecurityProperties securityProperties) {
        this.redis = redis;
        this.jwtProperties = jwtProperties;
        this.securityProperties = securityProperties;
    }

    public void bindActiveAccessToken(Long userId, String jti, String accessToken) {
        String key = ACTIVE_TOKEN_PREFIX + userId;
        redis.opsForValue().set(key, jti);
        redis.opsForValue().set(ACTIVE_TOKEN_PREFIX + "token:" + userId, accessToken,
                Duration.ofMinutes(jwtProperties.accessTokenMinutes()));
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + jti));
    }

    public void blacklist(String jti, long seconds) {
        redis.opsForValue().set(BLACKLIST_PREFIX + jti, "1", Duration.ofSeconds(seconds));
    }

    public void logout(Long userId, String jti) {
        blacklist(jti, jwtProperties.accessTokenMinutes() * 60);
        redis.delete(ACTIVE_TOKEN_PREFIX + userId);
        redis.delete(ACTIVE_TOKEN_PREFIX + "token:" + userId);
    }

    public void touchSession(Long userId) {
        redis.opsForValue().set("session:idle:" + userId, "1",
                Duration.ofMinutes(securityProperties.sessionIdleMinutes()));
    }

    public boolean isSessionIdleExpired(Long userId) {
        return !Boolean.TRUE.equals(redis.hasKey("session:idle:" + userId));
    }

    public boolean checkRateLimit(String userKey, String ip, int maxPerMinute) {
        String uk = RATE_USER_PREFIX + userKey;
        String ik = RATE_IP_PREFIX + ip;
        long u = incrementWithTtl(uk);
        long i = incrementWithTtl(ik);
        return u <= maxPerMinute && i <= maxPerMinute;
    }

    private long incrementWithTtl(String key) {
        Long v = redis.opsForValue().increment(key);
        if (v != null && v == 1L) {
            redis.expire(key, Duration.ofMinutes(1));
        }
        return v == null ? 0 : v;
    }

    /** 临时键（验证码等），TTL 秒 */
    public void putTemp(String key, String value, long seconds) {
        redis.opsForValue().set("temp:" + key, value, Duration.ofSeconds(seconds));
    }

    public String getTemp(String key) {
        return redis.opsForValue().get("temp:" + key);
    }

    public void deleteTemp(String key) {
        redis.delete("temp:" + key);
    }
}
