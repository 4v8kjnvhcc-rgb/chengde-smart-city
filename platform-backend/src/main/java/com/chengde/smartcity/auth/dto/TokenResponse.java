package com.chengde.smartcity.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds,
        UserInfo user
) {
    public record UserInfo(Long id, String username, String displayName, Long orgId) {
    }
}
