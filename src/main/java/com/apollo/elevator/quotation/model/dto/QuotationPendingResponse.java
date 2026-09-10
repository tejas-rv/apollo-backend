package com.apollo.elevator.quotation.model.dto;

import com.apollo.elevator.enquiry.model.enums.InquiryStatus;

public record QuotationPendingResponse(
        Long id,
        String fullName,
        String phoneNumber,
        String requirementType,
        InquiryStatus status,
        String quotationStatus
) {}
