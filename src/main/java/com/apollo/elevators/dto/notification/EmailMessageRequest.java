package com.apollo.elevators.dto.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EmailMessageRequest(
        @NotBlank(message = "Email recipient is required")
        @Email(message = "Email recipient must be valid")
        @Size(max = 255, message = "Email recipient must not exceed 255 characters")
        String email,

        @NotBlank(message = "Email subject is required")
        @Size(max = 200, message = "Email subject must not exceed 200 characters")
        String subject,

        @NotBlank(message = "Email message is required")
        @Size(max = 1000, message = "Email message must not exceed 1000 characters")
        String message,

        @Size(max = 100, message = "Reference key must not exceed 100 characters")
        String referenceKey,

        @Valid
        List<EmailAttachmentRequest> attachments
) {
        public record EmailAttachmentRequest(
                @NotBlank(message = "Attachment filename is required")
                @Size(max = 255, message = "Attachment filename must not exceed 255 characters")
                String fileName,

                @Size(max = 100, message = "Attachment content type must not exceed 100 characters")
                String contentType,

                @NotBlank(message = "Attachment base64 content is required")
                String base64Content
        ) {}
}
