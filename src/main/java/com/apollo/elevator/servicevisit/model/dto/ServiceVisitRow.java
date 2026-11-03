package com.apollo.elevator.servicevisit.model.dto;

import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.servicevisit.model.enums.IssueRaisedBy;
import java.time.LocalDate;

/** One AMC service visit with its customer/contract context and reschedule markers. */
public record ServiceVisitRow(
        Long id,
        LocalDate serviceDate,
        ServiceVisitStatus status,
        String engineerName,
        Long assignedEngineerId,
        String workDone,
        Long amcContractId,
        String contractNumber,
        String contractStatus,
        Long liftId,
        String liftBrand,
        String liftSerialNumber,
        Long customerId,
        String customerCode,
        String customerName,
        String city,
        long rescheduleCount,
        IssueRaisedBy lastRaisedBy,
        String lastReason,
        Long rescheduledFromVisitId,
        Long followUpVisitId
) {
}
