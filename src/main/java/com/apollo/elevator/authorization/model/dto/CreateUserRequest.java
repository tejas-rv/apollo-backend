package com.apollo.elevator.authorization.model.dto;

import com.apollo.elevator.authorization.model.enums.UserType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "username is required")
        @Size(min = 3, max = 100, message = "username must be between 3 and 100 characters")
        @Pattern(regexp = "^[A-Za-z0-9._@-]+$", message = "username may contain only letters, digits, '.', '_', '@' or '-'")
        String username,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password,

        @NotNull(message = "userType is required (ADMIN, OFFICE or ENGINEER)")
        UserType userType,

        @Valid
        @NotNull(message = "profile is required")
        UserProfileRequest profile
) {}
