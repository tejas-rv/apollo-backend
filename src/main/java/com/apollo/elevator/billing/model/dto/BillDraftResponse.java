package com.apollo.elevator.billing.model.dto;

import com.apollo.elevator.billing.model.enums.BillType;
import com.apollo.elevator.billing.model.enums.PaymentFrequency;
import com.apollo.elevator.customer.model.enums.ContractType;
import com.apollo.elevator.documents.model.dto.BillRequest;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Pre-filled bill for the UI. For AMC bills, line item 1 is the AMC instalment and its rate is fixed. */
public record BillDraftResponse(
        BillType billType,
        Long customerId,
        Long amcContractId,
        String contractNumber,
        ContractType contractType,
        PaymentFrequency paymentFrequency,
        Integer instalmentNo,
        Integer totalInstalments,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate amcPeriodStart,
        LocalDate amcPeriodEnd,
        BigDecimal planAmount,
        BigDecimal instalmentAmount,
        boolean planLocked,
        BillRequest billRequest
) {}
