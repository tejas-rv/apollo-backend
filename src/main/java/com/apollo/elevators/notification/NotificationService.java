package com.apollo.elevators.notification;

import com.apollo.elevators.common.exception.NotificationDeliveryException;
import com.apollo.elevators.notification.dto.NotificationResponse;
import com.apollo.elevators.notification.dto.WhatsAppMessageRequest;
import com.apollo.elevators.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final WhatsAppClient whatsAppClient;

    @Transactional
    public NotificationResponse sendWhatsAppMessage(WhatsAppMessageRequest request) {
        NotificationLog log = NotificationLog.builder()
                .channel(NotificationChannel.WHATSAPP)
                .recipient(normalizePhoneNumber(request.phoneNumber()))
                .message(request.message().trim())
                .referenceKey(request.referenceKey())
                .status(NotificationStatus.PENDING)
                .build();

        notificationLogRepository.save(log);

        try {
            WhatsAppClient.WhatsAppDeliveryResult deliveryResult =
                    whatsAppClient.sendText(log.getRecipient(), log.getMessage());

            log.setStatus(NotificationStatus.SENT);
            log.setProviderMessageId(deliveryResult.providerMessageId());
            log.setSentAt(Instant.now());

            notificationLogRepository.save(log);

            return toResponse(log);
        } catch (NotificationDeliveryException exception) {
            log.setStatus(NotificationStatus.FAILED);
            log.setFailureReason(exception.getMessage());
            notificationLogRepository.save(log);
            throw exception;
        }
    }

    private NotificationResponse toResponse(NotificationLog log) {
        return new NotificationResponse(
                log.getId(),
                log.getChannel().name(),
                log.getRecipient(),
                log.getStatus().name(),
                log.getProviderMessageId(),
                log.getFailureReason(),
                log.getCreatedAt(),
                log.getSentAt()
        );
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String normalized = phoneNumber.replaceAll("[^\\d+]", "");

        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1);
        }

        return normalized;
    }
}
