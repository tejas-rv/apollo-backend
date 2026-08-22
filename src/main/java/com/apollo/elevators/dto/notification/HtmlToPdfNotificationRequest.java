package com.apollo.elevators.dto.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record HtmlToPdfNotificationRequest(
        @NotBlank(message = "HTML content is required")
        String htmlContent,

        @Size(max = 255, message = "PDF file name must not exceed 255 characters")
        String pdfFileName,

        @Email(message = "Email recipient must be valid")
        @Size(max = 255, message = "Email recipient must not exceed 255 characters")
        String email,

        @Size(max = 200, message = "Email subject must not exceed 200 characters")
        String emailSubject,

        @Size(max = 1000, message = "Email message must not exceed 1000 characters")
        String emailMessage,

        @Pattern(
                regexp = "^\\+?[1-9]\\d{9,14}$",
                message = "WhatsApp phone number must be in international format"
        )
        String whatsappPhoneNumber,

        @Size(max = 1000, message = "WhatsApp caption must not exceed 1000 characters")
        String whatsappCaption,

        @Size(max = 100, message = "Reference key must not exceed 100 characters")
        String referenceKey
) {}
