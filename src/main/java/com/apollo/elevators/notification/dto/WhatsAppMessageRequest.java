package com.apollo.elevators.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WhatsAppMessageRequest(
        @NotBlank(message = "Phone number is required")
        @Pattern(
                regexp = "^\\+?[1-9]\\d{9,14}$",
                message = "Phone number must be in international format"
        )
        String phoneNumber,

        @NotBlank(message = "Message is required")
        @Size(max = 1000, message = "Message must not exceed 1000 characters")
        String message,

        @Size(max = 100, message = "Reference key must not exceed 100 characters")
        String referenceKey
) {}
