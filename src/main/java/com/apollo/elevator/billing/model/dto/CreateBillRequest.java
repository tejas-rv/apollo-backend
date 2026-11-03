package com.apollo.elevator.billing.model.dto;

import com.apollo.elevator.billing.model.enums.BillType;
import com.apollo.elevator.billing.model.enums.PaymentFrequency;
import com.apollo.elevator.customer.model.enums.ContractType;
import com.apollo.elevator.documents.model.dto.BillRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateBillRequest(
        @NotNull(message = "customerId is required")
        Long customerId,
        @NotNull(message = "billType is required")
        BillType billType,
        Long amcContractId,
        ContractType contractType,
        PaymentFrequency paymentFrequency,
        @NotNull(message = "billRequest is required")
        @Valid
        BillRequest billRequest
) {}
