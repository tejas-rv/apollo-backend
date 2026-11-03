package com.apollo.elevator.servicevisit.model.dto;

import com.apollo.elevator.servicevisit.model.enums.IssueRaisedBy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Reschedule a visit after an issue was raised. A SCHEDULED visit is moved to {@code newDate};
 * a COMPLETED/CANCELLED visit gets a new follow-up visit on {@code newDate}.
 */
public record RescheduleVisitRequest(

        @NotNull(message = "New service date is required")
        LocalDate newDate,

        @NotNull(message = "Raised by is required (CUSTOMER or ADMIN)")
        IssueRaisedBy raisedBy,

        @NotBlank(message = "Reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason,

        /** Engineer to assign; when omitted the visit keeps its current engineer. */
        Long assignedEngineerId,

        /** Work to be done on the follow-up visit; defaults to "Re-service: {reason}". */
        @Size(max = 500, message = "Work to be done must not exceed 500 characters")
        String workDone
) {
}
