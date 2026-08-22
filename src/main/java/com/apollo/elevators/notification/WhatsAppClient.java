package com.apollo.elevators.notification;

public interface WhatsAppClient {

    WhatsAppDeliveryResult sendText(String phoneNumber, String message);

    record WhatsAppDeliveryResult(String providerMessageId) {}
}
