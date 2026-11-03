package com.apollo.elevator.enquiry.model.dto;

import com.apollo.elevator.common.validation.IndianFormats;
import com.apollo.elevator.enquiry.model.enums.InquiryType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Note: consent-acceptance ("must be true") and phone-digit-count validation are intentionally
// done in the service layer (after trimming/normalizing values) rather than via @AssertTrue /
// a custom constraint here — this keeps the record simple and avoids accessor-naming pitfalls
// (e.g. @AssertTrue on a Boolean field named consentAccepted would require an "isConsentAccepted"
// style accessor that records don't generate).
public record ContactInquiryRequest(

        @NotNull(message = "Inquiry type is required")
        InquiryType inquiryType,

        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        @Pattern(regexp = IndianFormats.NAME, message = "Full name " + IndianFormats.NAME_MESSAGE)
        String fullName,

        @NotBlank(message = "Phone number is required")
        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        String phoneNumber,

        @Email(regexp = IndianFormats.EMAIL, message = "Must be a valid email address")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        String email,

        @Size(max = 100, message = "City must not exceed 100 characters")
        @Pattern(regexp = IndianFormats.NAME, message = "City " + IndianFormats.NAME_MESSAGE)
        String city,

        String requirementType,

        @Size(max = 2000, message = "Message must not exceed 2000 characters")
        String message,

        Boolean consentAccepted,

        String sourcePage
) {}
