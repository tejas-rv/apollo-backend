package com.apollo.elevator.servicevisit.model.dto;

public record ServiceVisitSummary(
        long scheduled,
        long overdue,
        long completed,
        long cancelled,
        long rescheduled
) {
}
