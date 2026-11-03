package com.apollo.elevator.serviceability.model.dto;

import java.time.LocalDate;
import java.util.List;

public record AssignmentBatchResponse(
        LocalDate plannedDate,
        boolean confirmed,
        int assignedCount,
        int blockedCount,
        List<AssignmentDecision> decisions
) {
}
