package com.apollo.elevator.serviceability.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ManualServiceAssignmentRequest(
        @NotNull @Positive Long serviceVisitId,
        @NotNull @Positive Long engineerId,
        @Size(max = 30) String zoneId,
        LocalDate scheduledDate,
        @Size(max = 500) String remarks
) {
}
