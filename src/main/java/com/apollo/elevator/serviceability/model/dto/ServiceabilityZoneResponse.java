package com.apollo.elevator.serviceability.model.dto;

public record ServiceabilityZoneResponse(
        String zoneId,
        String zoneName,
        String description,
        boolean active,
        long primaryEngineerCount,
        long backupEngineerCount,
        long assignedServices,
        long pendingServices
) {
}
