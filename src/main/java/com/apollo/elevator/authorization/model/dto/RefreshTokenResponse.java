package com.apollo.elevator.authorization.model.dto;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresInMs
) {}
