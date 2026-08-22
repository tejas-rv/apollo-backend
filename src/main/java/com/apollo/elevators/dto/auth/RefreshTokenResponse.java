package com.apollo.elevators.dto.auth;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresInMs
) {}
