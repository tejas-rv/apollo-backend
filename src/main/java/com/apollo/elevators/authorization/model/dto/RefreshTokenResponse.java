package com.apollo.elevators.authorization.model.dto;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresInMs
) {}
