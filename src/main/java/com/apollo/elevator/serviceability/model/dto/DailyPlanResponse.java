package com.apollo.elevator.serviceability.model.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyPlanResponse(
        LocalDate date,
        List<EngineerPlan> engineers,
        List<AssignmentDecision> unassigned,
        int totalAssigned,
        int overloadedEngineers
) {
    public record EngineerPlan(
            Long engineerId,
            String engineerName,
            String zoneId,
            String zoneName,
            int dailyCapacity,
            int scheduledCount,
            int completedCount,
            boolean overloaded,
            List<AssignmentDecision> visits
    ) {
    }
}
