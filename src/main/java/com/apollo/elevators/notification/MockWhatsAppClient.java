package com.apollo.elevators.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub WhatsApp client activated on the "dev" profile.
 * Logs the message instead of calling Meta — no credentials needed.
 */
@Component
@Profile("dev")
public class MockWhatsAppClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppClient.class);

    @Override
    public WhatsAppDeliveryResult sendText(String phoneNumber, String message) {
        String mockMessageId = "mock-" + UUID.randomUUID();
        log.info("[MOCK WHATSAPP] ➜ To: {} | Message: {} | MockId: {}", phoneNumber, message, mockMessageId);
        return new WhatsAppDeliveryResult(mockMessageId);
    }

    @Override
    public WhatsAppDeliveryResult sendDocument(
            String phoneNumber,
            String fileName,
            byte[] content,
            String caption
    ) {
        String mockMessageId = "mock-doc-" + UUID.randomUUID();
        log.info(
                "[MOCK WHATSAPP DOC] ➜ To: {} | File: {} | Size: {} bytes | Caption: {} | MockId: {}",
                phoneNumber,
                fileName,
                content == null ? 0 : content.length,
                caption,
                mockMessageId
        );
        return new WhatsAppDeliveryResult(mockMessageId);
    }
}
