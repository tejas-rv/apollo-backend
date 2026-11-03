package com.apollo.elevator.servicevisit.model.dto;

import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import java.time.LocalDate;

public record ServiceVisitFilter(
        ServiceVisitStatus status,
        LocalDate from,
        LocalDate to,
        String query,
        boolean overdueOnly,
        boolean rescheduledOnly
) {
}
