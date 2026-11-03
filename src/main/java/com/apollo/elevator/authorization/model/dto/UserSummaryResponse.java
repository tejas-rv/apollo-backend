package com.apollo.elevator.authorization.model.dto;

import com.apollo.elevator.authorization.model.enums.UserType;
import java.time.LocalDate;

/** Workload counts are populated only for ENGINEER users (zero/null otherwise). */
public record UserSummaryResponse(
        Long id,
        String username,
        UserType userType,
        String role,
        boolean enabled,
        String employeeNumber,
        String fullName,
        String phone,
        String email,
        String designation,
        long upcomingServices,
        long overdueServices,
        long completedServices,
        LocalDate lastServiceDate
) {}
