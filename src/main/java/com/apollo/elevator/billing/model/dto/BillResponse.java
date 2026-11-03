package com.apollo.elevator.billing.model.dto;

import com.apollo.elevator.billing.model.enums.BillStatus;
import com.apollo.elevator.billing.model.enums.BillType;
import com.apollo.elevator.billing.model.enums.PaymentFrequency;
import com.apollo.elevator.customer.model.enums.ContractType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BillResponse(
        Long id,
        String invoiceNumber,
        Long customerId,
        String customerName,
        Long amcContractId,
        BillType billType,
        ContractType contractType,
        PaymentFrequency paymentFrequency,
        Integer instalmentNo,
        Integer totalInstalments,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate invoiceDate,
        BigDecimal taxableAmount,
        BigDecimal gstAmount,
        BigDecimal totalAmount,
        BillStatus status,
        LocalDate paidDate,
        String paymentReference,
        String createdBy,
        LocalDateTime createdAt
) {}
