package com.apollo.elevators.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile("dev")
public class MockEmailClient implements EmailClient {

    private static final Logger log = LoggerFactory.getLogger(MockEmailClient.class);

    @Override
    public EmailDeliveryResult sendEmail(
            String email,
            String subject,
            String message,
            List<EmailAttachment> attachments
    ) {
        String mockMessageId = "mock-email-" + UUID.randomUUID();
        int attachmentCount = attachments == null ? 0 : attachments.size();
        log.info(
                "[MOCK EMAIL] ➜ To: {} | Subject: {} | Message: {} | AttachmentCount: {} | MockId: {}",
                email,
                subject,
                message,
                attachmentCount,
                mockMessageId
        );
        return new EmailDeliveryResult(mockMessageId);
    }
}
