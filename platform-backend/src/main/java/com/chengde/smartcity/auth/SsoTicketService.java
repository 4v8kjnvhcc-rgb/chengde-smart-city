package com.chengde.smartcity.auth;

import com.chengde.smartcity.auth.dto.SsoTicketRequest;
import com.chengde.smartcity.auth.dto.SsoTicketResponse;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.config.PortalSsoProperties;
import com.chengde.smartcity.security.UserPrincipal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SsoTicketService {

    private static final Logger log = LoggerFactory.getLogger(SsoTicketService.class);
    private static final String TARGET_ASSESSMENT = "assessment";

    private final PortalSsoProperties properties;
    private final SecretKey secretKey;

    public SsoTicketService(PortalSsoProperties properties) {
        this.properties = properties;
        byte[] keyBytes = padSecret(properties.secret());
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public SsoTicketResponse issue(UserPrincipal principal, SsoTicketRequest request) {
        if (principal == null) {
            throw new BusinessException(401, "未登录");
        }
        if (!properties.enabled()) {
            throw new BusinessException(503, "门户票据 SSO 未启用");
        }
        String targetApp = request.targetApp() == null ? "" : request.targetApp().trim().toLowerCase(Locale.ROOT);
        if (!TARGET_ASSESSMENT.equals(targetApp)) {
            throw new BusinessException(400, "暂不支持的目标应用: " + request.targetApp());
        }

        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.ticketTtlSeconds());
        String roles = principal.getRoleCodes() == null ? "" : String.join(",", principal.getRoleCodes());
        String displayName = StringUtils.hasText(principal.getDisplayName())
                ? principal.getDisplayName()
                : principal.getUsername();

        String ticket = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(principal.getUsername())
                .claim("aud", TARGET_ASSESSMENT)
                .claim("uid", principal.getUserId())
                .claim("displayName", displayName)
                .claim("roles", roles)
                .claim("type", "portal_sso")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();

        log.info("Issued portal SSO ticket user={} target={} ttl={}s",
                principal.getUsername(), targetApp, properties.ticketTtlSeconds());
        return new SsoTicketResponse(ticket, properties.ticketTtlSeconds(), targetApp);
    }

    private static byte[] padSecret(String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length >= 32) {
            return keyBytes;
        }
        byte[] padded = new byte[32];
        System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
        return padded;
    }
}
