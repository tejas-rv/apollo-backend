package com.apollo.elevator.serviceability.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ReassignServiceRequest(
        @NotNull @Positive Long engineerId,
        LocalDate scheduledDate,
        @Size(max = 30) String zoneId,
        @Size(max = 500) String remarks
) {
}
