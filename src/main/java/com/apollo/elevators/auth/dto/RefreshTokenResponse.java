package com.apollo.elevators.auth.dto;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresInMs
) {}
