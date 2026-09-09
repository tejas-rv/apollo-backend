package com.apollo.elevator.authorization.model.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateEngineerRequest(
        @NotBlank(message = "username is required") String username,
        @NotBlank(message = "password is required") String password
) {}
