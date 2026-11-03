package com.apollo.elevator.serviceability.model.dto;

import java.util.List;

public record EngineerServiceabilityResponse(
        Long engineerId,
        String engineerName,
        String employeeNumber,
        boolean accountEnabled,
        boolean available,
        int dailyCapacity,
        List<String> primaryZones,
        List<String> backupZones,
        long assignedToday,
        long completedToday,
        long overdue
) {
}
