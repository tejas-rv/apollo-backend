package com.apollo.elevator.quotation.model.dto;

import com.apollo.elevator.quotation.model.enums.QuotationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QuotationResponse(
        Long id,
        String quotationNumber,
        Long enquiryId,
        String customerName,
        String phoneNumber,
        String requirementType,
        BigDecimal amount,
        String notes,
        QuotationStatus status,
        String enquiryStatus,
        String quotationStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
