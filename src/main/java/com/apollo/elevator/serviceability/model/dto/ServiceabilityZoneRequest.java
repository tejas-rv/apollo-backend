package com.apollo.elevator.serviceability.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ServiceabilityZoneRequest(
        @NotBlank
        @Pattern(regexp = "[A-Z0-9-]{2,30}", message = "Zone ID must contain uppercase letters, digits or hyphens")
        String zoneId,
        @NotBlank @Size(max = 100) String zoneName,
        @Size(max = 500) String description,
        boolean active
) {
}
