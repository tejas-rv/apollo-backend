package com.apollo.elevators.engineer.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ServiceReportRequest(
        @NotNull(message = "AMC contract ID is required") Long amcContractId,
        @NotNull(message = "Customer ID is required")    Long customerId,
        @NotNull(message = "Visit date is required")     LocalDate visitDate,

        @Size(max = 2000) String overallNotes,

        @Valid @NotNull List<ServiceCheckItemDto> checkItems
) {}
