package com.apollo.elevator.billing.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MarkBillPaidRequest(
        @NotNull(message = "paidDate is required")
        LocalDate paidDate,
        @Size(max = 100, message = "Payment reference must not exceed 100 characters")
        String paymentReference
) {}
