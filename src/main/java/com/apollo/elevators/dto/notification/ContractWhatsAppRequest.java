package com.apollo.elevators.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ContractWhatsAppRequest(

        @NotNull(message = "Customer ID is required")
        Long customerId,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[1-9]\\d{9,14}$",
                message = "Phone number must be in international format, e.g. +919876543210")
        String phoneNumber,

        @Size(max = 1000, message = "Caption must not exceed 1000 characters")
        String caption,

        @Size(max = 100)
        String referenceKey
) {}
