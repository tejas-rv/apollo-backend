package com.apollo.elevator.billing.model.dto;

import com.apollo.elevator.customer.model.enums.ContractType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AmcPlanPriceResponse(
        ContractType contractType,
        BigDecimal amount,
        String updatedBy,
        LocalDateTime updatedAt
) {}
