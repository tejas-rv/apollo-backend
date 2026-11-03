package com.apollo.elevator.billing.model.dto;

import com.apollo.elevator.billing.model.enums.PaymentFrequency;
import com.apollo.elevator.customer.model.enums.AmcStatus;
import com.apollo.elevator.customer.model.enums.ContractType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** An AMC contract of a customer with its billing progress for the current AMC year. */
public record AmcBillingStatusResponse(
        Long amcContractId,
        String contractNumber,
        String liftSerialNumber,
        AmcStatus status,
        LocalDate startDate,
        LocalDate endDate,
        ContractType contractType,
        BigDecimal amcAmount,
        PaymentFrequency paymentFrequency,
        int billedInstalments,
        int totalInstalments,
        Integer nextInstalmentNo,
        boolean fullyBilled
) {}
