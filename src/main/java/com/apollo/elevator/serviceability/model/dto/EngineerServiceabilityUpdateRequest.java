package com.apollo.elevator.serviceability.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EngineerServiceabilityUpdateRequest(
        @Positive int dailyCapacity,
        @NotNull Boolean available
) {
}
