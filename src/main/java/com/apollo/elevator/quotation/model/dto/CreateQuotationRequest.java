package com.apollo.elevator.quotation.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateQuotationRequest(
        @NotNull(message = "Enquiry id is required")
        Long enquiryId,

        @NotNull(message = "Quotation amount is required")
        @Positive(message = "Quotation amount must be greater than zero.")
        BigDecimal amount,

        String notes
) {}
