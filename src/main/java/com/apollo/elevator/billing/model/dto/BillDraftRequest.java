package com.apollo.elevator.billing.model.dto;

import com.apollo.elevator.billing.model.enums.BillType;
import com.apollo.elevator.billing.model.enums.PaymentFrequency;
import com.apollo.elevator.customer.model.enums.ContractType;
import jakarta.validation.constraints.NotNull;

/**
 * Input for building a bill draft. For AMC bills, {@code amcContractId} is required and
 * {@code contractType}/{@code paymentFrequency} default to the contract's values; they are
 * locked to the first bill's values once an AMC year has been billed.
 */
public record BillDraftRequest(
        @NotNull(message = "customerId is required")
        Long customerId,
        @NotNull(message = "billType is required")
        BillType billType,
        Long amcContractId,
        ContractType contractType,
        PaymentFrequency paymentFrequency
) {}
