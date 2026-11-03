package com.apollo.elevator.authorization.model.dto;

import com.apollo.elevator.authorization.model.enums.UserType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
        @NotNull(message = "userType is required (ADMIN, OFFICE or ENGINEER)")
        UserType userType,

        @Valid
        @NotNull(message = "profile is required")
        UserProfileRequest profile
) {}
