package com.apollo.elevators.dto.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlainEmailRequest(

        @NotBlank(message = "Recipient email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Subject is required")
        @Size(max = 200, message = "Subject must not exceed 200 characters")
        String subject,

        @NotBlank(message = "Body is required")
        @Size(max = 4000, message = "Body must not exceed 4000 characters")
        String body,

        @Size(max = 100)
        String referenceKey
) {}
