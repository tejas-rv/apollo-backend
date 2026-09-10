package com.apollo.elevator.quotation.model.dto;

import com.apollo.elevator.quotation.model.enums.QuotationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateQuotationStatusRequest(
        @NotNull(message = "Status is required")
        QuotationStatus status
) {}
