package com.apollo.elevator.enquiry.model.dto;

import com.apollo.elevator.enquiry.model.enums.InquiryStatus;
import com.apollo.elevator.enquiry.model.enums.InquiryType;
import java.time.LocalDateTime;

public record ContactInquiryResponse(
        Long id,
        InquiryType inquiryType,
        String fullName,
        String phoneNumber,
        String email,
        String city,
        String requirementType,
        String message,
        Boolean consentAccepted,
        String sourcePage,
        InquiryStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
