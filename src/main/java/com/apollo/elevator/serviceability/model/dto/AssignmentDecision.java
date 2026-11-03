package com.apollo.elevator.serviceability.model.dto;

import com.apollo.elevator.serviceability.model.enums.ServicePriority;
import java.time.LocalDate;

public record AssignmentDecision(
        Long serviceVisitId,
        String customerName,
        String address,
        String city,
        String pincode,
        String liftSerialNumber,
        LocalDate dueDate,
        ServicePriority priority,
        String zoneId,
        String zoneName,
        Long engineerId,
        String engineerName,
        boolean assignable,
        String reason,
        Long assignmentId
) {
}
