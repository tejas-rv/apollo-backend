package com.apollo.elevators.notification;

import com.apollo.elevators.common.exception.NotificationDeliveryException;
import com.apollo.elevators.notification.dto.NotificationResponse;
import com.apollo.elevators.notification.dto.WhatsAppMessageRequest;
import com.apollo.elevators.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private WhatsAppClient whatsAppClient;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        doAnswer(invocation -> {
            NotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(1L);
            }
            return log;
        }).when(notificationLogRepository).save(any(NotificationLog.class));
    }

    @Test
    void sendWhatsAppMessageShouldPersistSuccessfulDelivery() {
        WhatsAppMessageRequest request = new WhatsAppMessageRequest("+919876543210", "Test message", "ENQUIRY-1");
        when(whatsAppClient.sendText("919876543210", "Test message"))
                .thenReturn(new WhatsAppClient.WhatsAppDeliveryResult("wamid-1"));

        NotificationResponse response = notificationService.sendWhatsAppMessage(request);

        assertThat(response.status()).isEqualTo("SENT");
        assertThat(response.providerMessageId()).isEqualTo("wamid-1");
        assertThat(response.recipient()).isEqualTo("919876543210");
    }

    @Test
    void sendWhatsAppMessageShouldMarkFailedDelivery() {
        WhatsAppMessageRequest request = new WhatsAppMessageRequest("+919876543210", "Test message", "ENQUIRY-2");
        when(whatsAppClient.sendText("919876543210", "Test message"))
                .thenThrow(new NotificationDeliveryException("Provider rejected request"));

        assertThatThrownBy(() -> notificationService.sendWhatsAppMessage(request))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessage("Provider rejected request");
    }
}
