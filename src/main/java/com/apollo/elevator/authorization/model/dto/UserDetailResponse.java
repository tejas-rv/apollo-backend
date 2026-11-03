package com.apollo.elevator.authorization.model.dto;

import com.apollo.elevator.authorization.model.enums.UserType;
import com.apollo.elevator.engineer.model.dto.EngineerScheduledVisit;
import com.apollo.elevator.engineer.model.enums.ReportStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** {@code stats} and the service lists are populated only for ENGINEER users. */
public record UserDetailResponse(
        Long id,
        String username,
        UserType userType,
        String role,
        boolean enabled,
        Profile profile,
        Stats stats,
        List<EngineerScheduledVisit> upcomingServices,
        List<EngineerScheduledVisit> overdueServices,
        List<CompletedService> recentServices
) {

    public record Profile(
            String employeeNumber,
            String fullName,
            String phone,
            String email,
            String address,
            String designation,
            LocalDate dateOfJoining,
            String specialization,
            String emergencyContactName,
            String emergencyContactPhone,
            String notes,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record Stats(
            long upcomingServices,
            long overdueServices,
            long completedServices,
            long completedThisMonth,
            LocalDate lastServiceDate
    ) {}

    public record CompletedService(
            Long reportId,
            LocalDate visitDate,
            Long customerId,
            String customerName,
            ReportStatus status,
            LocalDateTime submittedAt
    ) {}
}
