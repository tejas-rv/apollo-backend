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
}
