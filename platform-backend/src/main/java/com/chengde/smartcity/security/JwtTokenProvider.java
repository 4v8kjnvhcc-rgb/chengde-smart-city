package com.chengde.smartcity.security;

import com.chengde.smartcity.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String username, Long orgId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.accessTokenMinutes() * 60);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim("uid", userId)
                .claim("orgId", orgId)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.refreshTokenHours() * 3600);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim("uid", userId)
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get("type", String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return "refresh".equals(claims.get("type", String.class));
    }
}
