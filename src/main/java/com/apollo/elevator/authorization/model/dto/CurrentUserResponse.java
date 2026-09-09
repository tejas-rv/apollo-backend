package com.apollo.elevator.authorization.model.dto;

public record CurrentUserResponse(
        Long id,
        String username,
        String role,
        String email,
        String whatsapp,
        boolean enabled
) {}
