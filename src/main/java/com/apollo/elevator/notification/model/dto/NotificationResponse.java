package com.apollo.elevator.notification.model.dto;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String channel,
        String recipient,
        String status,
        String providerMessageId,
        String failureReason,
        Instant createdAt,
        Instant sentAt
) {}
