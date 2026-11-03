package com.apollo.elevator.serviceability.model.dto;

import java.time.LocalDate;
import java.util.List;

public record ServiceabilityDashboardResponse(
        LocalDate date,
        long totalScheduled,
        long overdue,
        long dueToday,
        long dueWithinThreeDays,
        long unassigned,
        long activeEngineers,
        List<ZoneWorkload> zones,
        List<EngineerWorkload> engineers
) {
    public record ZoneWorkload(
            String zoneId,
            String zoneName,
            long total,
            long overdue,
            long unassigned
    ) {
    }

    public record EngineerWorkload(
            Long engineerId,
            String engineerName,
            String zoneId,
            int dailyCapacity,
            long scheduled,
            long completed,
            long overdue,
            boolean available
    ) {
    }
}
