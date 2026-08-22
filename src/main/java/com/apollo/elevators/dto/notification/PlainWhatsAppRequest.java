package com.apollo.elevators.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlainWhatsAppRequest(

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[1-9]\\d{9,14}$",
                message = "Phone number must be in international format, e.g. +919876543210")
        String phoneNumber,

        @NotBlank(message = "Message is required")
        @Size(max = 4096, message = "Message must not exceed 4096 characters")
        String message,

        @Size(max = 100)
        String referenceKey
) {}
