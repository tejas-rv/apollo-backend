package com.apollo.elevators.authorization.model.dto;

public record EngineerUserResponse(
        Long id,
        String username,
        String role
) {}
