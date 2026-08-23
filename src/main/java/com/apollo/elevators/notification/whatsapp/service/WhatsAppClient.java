package com.apollo.elevators.notification.whatsapp.service;

public interface WhatsAppClient {

    WhatsAppDeliveryResult sendText(String phoneNumber, String message);

    WhatsAppDeliveryResult sendDocument(
            String phoneNumber,
            String fileName,
            byte[] content,
            String caption
    );

    record WhatsAppDeliveryResult(String providerMessageId) {}
}
