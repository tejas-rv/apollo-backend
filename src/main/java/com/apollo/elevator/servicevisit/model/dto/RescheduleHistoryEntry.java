package com.apollo.elevator.servicevisit.model.dto;

import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.servicevisit.model.entity.ServiceVisitReschedule;
import com.apollo.elevator.servicevisit.model.enums.IssueRaisedBy;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record RescheduleHistoryEntry(
        Long id,
        Long serviceVisitId,
        Long newServiceVisitId,
        boolean followUpCreated,
        IssueRaisedBy raisedBy,
        String reason,
        ServiceVisitStatus previousStatus,
        LocalDate previousDate,
        LocalDate newDate,
        String previousEngineerName,
        String newEngineerName,
        String rescheduledBy,
        LocalDateTime createdAt
) {

    public static RescheduleHistoryEntry from(ServiceVisitReschedule r) {
        return new RescheduleHistoryEntry(
                r.getId(),
                r.getServiceVisitId(),
                r.getNewServiceVisitId(),
                r.getNewServiceVisitId() != null && !r.getNewServiceVisitId().equals(r.getServiceVisitId()),
                r.getRaisedBy(),
                r.getReason(),
                r.getPreviousStatus(),
                r.getPreviousDate(),
                r.getNewDate(),
                r.getPreviousEngineerName(),
                r.getNewEngineerName(),
                r.getRescheduledBy(),
                r.getCreatedAt());
    }
}
