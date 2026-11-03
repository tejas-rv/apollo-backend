package com.apollo.elevator.serviceability.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record AssignmentBatchRequest(
        @NotEmpty List<@NotNull Long> serviceVisitIds,
        LocalDate plannedDate,
        boolean confirm
) {
}
