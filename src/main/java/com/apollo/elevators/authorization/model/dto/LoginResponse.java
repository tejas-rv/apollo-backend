package com.apollo.elevators.authorization.model.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String username,
        String role,
        long expiresInMs
) {}
