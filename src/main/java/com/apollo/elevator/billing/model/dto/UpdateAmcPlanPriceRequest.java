package com.apollo.elevator.billing.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record UpdateAmcPlanPriceRequest(
        @NotNull(message = "Amount is required")
        @PositiveOrZero(message = "Amount cannot be negative")
        @DecimalMax(value = "9999999999.99", message = "Amount is too large")
        BigDecimal amount
) {}
