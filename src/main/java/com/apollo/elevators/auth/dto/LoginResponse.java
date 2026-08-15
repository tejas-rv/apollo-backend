package com.apollo.elevators.auth.dto;

public record LoginResponse(
        String token,
        String username,
        String role,
        long expiresInMs
) {}
