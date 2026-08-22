package com.apollo.elevators.dto.auth;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String username,
        String role,
        long expiresInMs
) {}
