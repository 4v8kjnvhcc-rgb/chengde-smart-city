package com.chengde.smartcity.auth.dto;

public record SsoTicketResponse(
        String ticket,
        long expiresInSeconds,
        String targetApp
) {}
