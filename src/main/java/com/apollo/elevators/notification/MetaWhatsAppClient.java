package com.apollo.elevators.notification;

import com.apollo.elevators.common.exception.NotificationDeliveryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MetaWhatsAppClient implements WhatsAppClient {

    private final RestClient.Builder restClientBuilder;
    private final WhatsAppProperties whatsAppProperties;

    @Override
    public WhatsAppDeliveryResult sendText(String phoneNumber, String message) {
        log.info(
                "Meta WhatsApp send invoked. phoneNumber={}, messageLength={}",
                phoneNumber,
                message == null ? 0 : message.length()
        );
        validateConfiguration();
        log.info(
                "Meta WhatsApp configuration validated. baseUrl={}, apiVersion={}, phoneNumberId={}",
                whatsAppProperties.getBaseUrl(),
                whatsAppProperties.getApiVersion(),
                whatsAppProperties.getPhoneNumberId()
        );

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
                log.error("Meta WhatsApp provider response missing message id");
                throw new NotificationDeliveryException("WhatsApp provider did not return a message id");
            }

            String providerMessageId = response.messages().getFirst().id();
            log.info("Meta WhatsApp delivery accepted by provider. providerMessageId={}", providerMessageId);
            return new WhatsAppDeliveryResult(providerMessageId);
        } catch (RestClientResponseException exception) {
            log.error(
                    "Meta WhatsApp provider rejected request. statusCode={}, responseBody={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );
            throw new NotificationDeliveryException(
                    "WhatsApp provider rejected the request: " + exception.getResponseBodyAsString(),
                    exception
            );
        } catch (RestClientException exception) {
            log.error("Meta WhatsApp provider call failed", exception);
            throw new NotificationDeliveryException("Failed to call the WhatsApp provider", exception);
        }
    }

    @Override
    public WhatsAppDeliveryResult sendDocument(
            String phoneNumber,
            String fileName,
            byte[] content,
            String caption
    ) {
        validateConfiguration();
        if (content == null || content.length == 0) {
            throw new NotificationDeliveryException("WhatsApp document content is empty");
        }
        log.info(
                "Meta WhatsApp document send invoked. phoneNumber={}, fileName={}, contentSize={}",
                phoneNumber,
                fileName,
                content.length
        );

        RestClient restClient = restClientBuilder
                .baseUrl(whatsAppProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + whatsAppProperties.getAccessToken())
                .build();

        try {
            String mediaId = uploadMedia(restClient, fileName, content);
            MetaWhatsAppResponse response = restClient.post()
                    .uri("/{version}/{phoneNumberId}/messages",
                            whatsAppProperties.getApiVersion(),
                            whatsAppProperties.getPhoneNumberId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MetaWhatsAppDocumentMessageRequest(
                            "whatsapp",
                            phoneNumber,
                            "document",
                            new MetaWhatsAppDocument(mediaId, fileName, caption)
                    ))
                    .retrieve()
                    .body(MetaWhatsAppResponse.class);

            if (response == null || response.messages() == null || response.messages().isEmpty()) {
                throw new NotificationDeliveryException("WhatsApp provider did not return a message id for document");
            }

            String providerMessageId = response.messages().getFirst().id();
            log.info("Meta WhatsApp document accepted by provider. providerMessageId={}", providerMessageId);
            return new WhatsAppDeliveryResult(providerMessageId);
        } catch (RestClientResponseException exception) {
            log.error(
                    "Meta WhatsApp document request rejected. statusCode={}, responseBody={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );
            throw new NotificationDeliveryException(
                    "WhatsApp provider rejected the document request: " + exception.getResponseBodyAsString(),
                    exception
            );
        } catch (RestClientException exception) {
            log.error("Meta WhatsApp document call failed", exception);
            throw new NotificationDeliveryException("Failed to call the WhatsApp provider for document", exception);
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

    private String uploadMedia(
            RestClient restClient,
            String fileName,
            byte[] content
    ) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("messaging_product", "whatsapp");
        body.add("type", "application/pdf");
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });

        MetaWhatsAppMediaUploadResponse uploadResponse = restClient.post()
                .uri("/{version}/{phoneNumberId}/media",
                        whatsAppProperties.getApiVersion(),
                        whatsAppProperties.getPhoneNumberId())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(MetaWhatsAppMediaUploadResponse.class);

        if (uploadResponse == null || uploadResponse.id() == null || uploadResponse.id().isBlank()) {
            throw new NotificationDeliveryException("WhatsApp provider did not return media id for uploaded document");
        }
        return uploadResponse.id();
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

    private record MetaWhatsAppDocumentMessageRequest(
            String messaging_product,
            String to,
            String type,
            MetaWhatsAppDocument document
    ) {}

    private record MetaWhatsAppDocument(
            String id,
            String filename,
            String caption
    ) {}

    private record MetaWhatsAppResponse(
            List<MetaWhatsAppMessage> messages
    ) {}

    private record MetaWhatsAppMessage(
            String id
    ) {}

    private record MetaWhatsAppMediaUploadResponse(
            String id
    ) {}
}
