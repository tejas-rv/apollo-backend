package com.apollo.elevator.enquiry.model.dto;

import com.apollo.elevator.enquiry.model.enums.InquiryType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Note: consent-acceptance ("must be true") and phone-digit-count validation are intentionally
// done in the service layer (after trimming/normalizing values) rather than via @AssertTrue /
// a custom constraint here — this keeps the record simple and avoids accessor-naming pitfalls
// (e.g. @AssertTrue on a Boolean field named consentAccepted would require an "isConsentAccepted"
// style accessor that records don't generate).
public record ContactInquiryRequest(

        @NotNull(message = "Inquiry type is required")
        InquiryType inquiryType,

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Phone number is required")
        String phoneNumber,

        @Email(message = "Must be a valid email address")
        String email,

        String city,

        String requirementType,

        String message,

        Boolean consentAccepted,

        String sourcePage
) {}
