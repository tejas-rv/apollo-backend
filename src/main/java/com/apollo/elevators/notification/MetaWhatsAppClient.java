package com.apollo.elevators.notification;

import com.apollo.elevators.common.exception.NotificationDeliveryException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MetaWhatsAppClient implements WhatsAppClient {

    private final RestClient.Builder restClientBuilder;
    private final WhatsAppProperties whatsAppProperties;

    @Override
    public WhatsAppDeliveryResult sendText(String phoneNumber, String message) {
        validateConfiguration();

        RestClient restClient = restClientBuilder
                .baseUrl(whatsAppProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + whatsAppProperties.getAccessToken())
                .build();

        try {
            MetaWhatsAppResponse response = restClient.post()
                    .uri("/{version}/{phoneNumberId}/messages",
                            whatsAppProperties.getApiVersion(),
                            whatsAppProperties.getPhoneNumberId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MetaWhatsAppRequest(
                            "whatsapp",
                            phoneNumber,
                            "text",
                            new MetaWhatsAppText(false, message)
                    ))
                    .retrieve()
                    .body(MetaWhatsAppResponse.class);

            if (response == null || response.messages() == null || response.messages().isEmpty()) {
                throw new NotificationDeliveryException("WhatsApp provider did not return a message id");
            }

            return new WhatsAppDeliveryResult(response.messages().getFirst().id());
        } catch (RestClientResponseException exception) {
            throw new NotificationDeliveryException(
                    "WhatsApp provider rejected the request: " + exception.getResponseBodyAsString(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new NotificationDeliveryException("Failed to call the WhatsApp provider", exception);
        }
    }

    private void validateConfiguration() {
        if (!whatsAppProperties.isEnabled()) {
            throw new NotificationDeliveryException("WhatsApp notifications are disabled");
        }
        if (isBlank(whatsAppProperties.getPhoneNumberId()) || isBlank(whatsAppProperties.getAccessToken())) {
            throw new NotificationDeliveryException("WhatsApp provider configuration is incomplete");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record MetaWhatsAppRequest(
            String messaging_product,
            String to,
            String type,
            MetaWhatsAppText text
    ) {}

    private record MetaWhatsAppText(
            boolean preview_url,
            String body
    ) {}

    private record MetaWhatsAppResponse(
            List<MetaWhatsAppMessage> messages
    ) {}

    private record MetaWhatsAppMessage(
            String id
    ) {}
}
