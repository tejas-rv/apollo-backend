package com.apollo.elevator.serviceability.model.dto;

import com.apollo.elevator.serviceability.model.enums.EngineerZoneType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record EngineerZoneAssignmentRequest(
        @NotNull @Positive Long engineerId,
        @NotNull @Size(max = 30) String zoneId,
        @NotNull EngineerZoneType assignmentType,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        Integer dailyCapacity,
        Boolean available
) {
}
