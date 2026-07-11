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
                    chain.doFilter(request, response);
                    return;
                }
                UserPrincipal principal = userDetailsService.loadByUsername(claims.getSubject());
                var authorities = principal.getPermissions().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
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
