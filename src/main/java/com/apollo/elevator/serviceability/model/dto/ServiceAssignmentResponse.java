package com.apollo.elevator.serviceability.model.dto;

import com.apollo.elevator.serviceability.model.enums.AssignmentMethod;
import com.apollo.elevator.serviceability.model.enums.AssignmentStatus;
import com.apollo.elevator.serviceability.model.enums.ServicePriority;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ServiceAssignmentResponse(
        Long id,
        Long serviceVisitId,
        Long engineerId,
        String engineerName,
        String zoneId,
        String zoneName,
        LocalDate assignmentDate,
        LocalDate serviceDueDate,
        LocalDate scheduledDate,
        ServicePriority priority,
        AssignmentStatus status,
        AssignmentMethod assignmentMethod,
        String assignedBy,
        String assignmentRemarks,
        int visitSequence,
        LocalDateTime createdAt
) {
}
