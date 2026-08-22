package com.apollo.elevators.dto.notification;

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
