package com.apollo.elevators.notification;

import java.util.List;

public interface EmailClient {

    EmailDeliveryResult sendEmail(
            String email,
            String subject,
            String message,
            List<EmailAttachment> attachments
    );

    record EmailAttachment(
            String fileName,
            String contentType,
            byte[] content
    ) {}

    record EmailDeliveryResult(String providerMessageId) {}
}
