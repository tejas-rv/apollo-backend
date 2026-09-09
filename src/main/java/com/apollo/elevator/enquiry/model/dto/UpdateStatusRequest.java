package com.apollo.elevator.enquiry.model.dto;

import com.apollo.elevator.enquiry.model.enums.InquiryStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "Status is required")
        InquiryStatus status
) {}
