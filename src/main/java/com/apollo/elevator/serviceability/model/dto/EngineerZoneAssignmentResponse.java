package com.apollo.elevator.serviceability.model.dto;

import com.apollo.elevator.serviceability.model.enums.EngineerZoneType;
import java.time.LocalDate;

public record EngineerZoneAssignmentResponse(
        Long id,
        Long engineerId,
        String engineerName,
        String employeeNumber,
        String zoneId,
        String zoneName,
        EngineerZoneType assignmentType,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        int dailyCapacity,
        boolean available,
        long scheduledCount,
        long completedCount,
        long overdueCount
) {
}
