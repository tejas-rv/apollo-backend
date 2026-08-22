package com.apollo.elevators.dto.auth;

public record CurrentUserResponse(
        Long id,
        String username,
        String role,
        String email,
        String whatsapp,
        boolean enabled
) {}
