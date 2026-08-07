package com.chengde.smartcity.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final SecurityUserDetailsService userDetailsService;
    private final SessionRedisService sessionRedisService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   SecurityUserDetailsService userDetailsService,
                                   SessionRedisService sessionRedisService) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.sessionRedisService = sessionRedisService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = tokenProvider.parse(token);
                if (!tokenProvider.isAccessToken(claims)) {
                    chain.doFilter(request, response);
                    return;
                }
                String jti = claims.getId();
                if (sessionRedisService.isBlacklisted(jti)) {
                    chain.doFilter(request, response);
                    return;
                }
                Long userId = claims.get("uid", Long.class);
                if (sessionRedisService.isSessionIdleExpired(userId)) {
                    // Redis 重启或会话键丢失时，凭有效 Access Token 恢复空闲计时，避免误报 401
                    sessionRedisService.touchSession(userId);
                }
                UserPrincipal principal = userDetailsService.loadByUserId(userId);
                var authorities = new java.util.ArrayList<>(principal.getPermissions().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList());
                // 超级/平台管理员：额外授予 ROLE_*，避免侧栏隐藏菜单导致权限码缺失时被拒
                if (principal.isSystemAdmin()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
                }
                if (principal.isPlatformAdmin()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
                }
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
                sessionRedisService.touchSession(userId);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
