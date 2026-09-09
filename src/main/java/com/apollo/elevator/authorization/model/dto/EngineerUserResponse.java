package com.apollo.elevator.authorization.model.dto;

public record EngineerUserResponse(
        Long id,
        String username,
        String role
) {}
