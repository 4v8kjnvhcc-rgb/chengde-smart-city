package com.chengde.smartcity.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds,
        UserInfo user,
        Boolean passwordWarn,
        Integer passwordAgeDays,
        String passwordWarnMessage
) {
    public TokenResponse(String accessToken, String refreshToken, long accessTokenExpiresInSeconds, UserInfo user) {
        this(accessToken, refreshToken, accessTokenExpiresInSeconds, user, null, null, null);
    }

    public record UserInfo(Long id, String username, String displayName, Long orgId, String orgName) {
    }
}
