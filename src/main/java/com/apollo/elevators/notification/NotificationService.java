package com.apollo.elevators.notification;

import com.apollo.elevators.common.exception.NotificationDeliveryException;
import com.apollo.elevators.dto.notification.ContractEmailRequest;
import com.apollo.elevators.dto.notification.ContractPdfRequest;
import com.apollo.elevators.dto.notification.ContractPdfResponse;
import com.apollo.elevators.dto.notification.ContractWhatsAppRequest;
import com.apollo.elevators.dto.notification.EmailMessageRequest;
import com.apollo.elevators.dto.notification.HtmlToPdfNotificationRequest;
import com.apollo.elevators.dto.notification.HtmlToPdfNotificationResponse;
import com.apollo.elevators.dto.notification.NotificationResponse;
import com.apollo.elevators.dto.notification.PlainEmailRequest;
import com.apollo.elevators.dto.notification.PlainWhatsAppRequest;
import com.apollo.elevators.dto.notification.WhatsAppMessageRequest;
import com.apollo.elevators.enums.NotificationChannel;
import com.apollo.elevators.enums.NotificationStatus;
import com.apollo.elevators.repository.NotificationLogRepository;
import com.apollo.elevators.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final WhatsAppClient whatsAppClient;
    private final EmailClient emailClient;
    private final HtmlToPdfService htmlToPdfService;
    private final PdfTemplateService pdfTemplateService;
    private final DocumentService documentService;

    @Transactional
    public NotificationResponse sendWhatsAppMessage(WhatsAppMessageRequest request) {
        log.info(
                "Preparing WhatsApp notification. phoneNumber={}, messageLength={}, referenceKey={}",
                request.phoneNumber(),
                request.message() == null ? 0 : request.message().length(),
                request.referenceKey()
        );
        NotificationLog notificationLog = NotificationLog.builder()
                .channel(NotificationChannel.WHATSAPP)
                .recipient(normalizePhoneNumber(request.phoneNumber()))
                .message(request.message().trim())
                .referenceKey(request.referenceKey())
                .status(NotificationStatus.PENDING)
                .build();

        notificationLogRepository.save(notificationLog);
        log.info(
                "Notification log created. notificationId={}, recipient={}, status={}",
                notificationLog.getId(),
                notificationLog.getRecipient(),
                notificationLog.getStatus()
        );

        try {
            log.info(
                    "Sending WhatsApp message via provider. notificationId={}, recipient={}",
                    notificationLog.getId(),
                    notificationLog.getRecipient()
            );
            WhatsAppClient.WhatsAppDeliveryResult deliveryResult =
                    whatsAppClient.sendText(notificationLog.getRecipient(), notificationLog.getMessage());

            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setProviderMessageId(deliveryResult.providerMessageId());
            notificationLog.setSentAt(Instant.now());

            notificationLogRepository.save(notificationLog);
            log.info(
                    "WhatsApp delivery succeeded. notificationId={}, providerMessageId={}, sentAt={}",
                    notificationLog.getId(),
                    notificationLog.getProviderMessageId(),
                    notificationLog.getSentAt()
            );

            return toResponse(notificationLog);
        } catch (NotificationDeliveryException exception) {
            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setFailureReason(exception.getMessage());
            notificationLogRepository.save(notificationLog);
            log.error(
                    "WhatsApp delivery failed. notificationId={}, recipient={}, reason={}",
                    notificationLog.getId(),
                    notificationLog.getRecipient(),
                    exception.getMessage(),
                    exception
            );
            throw exception;
        }
    }

    @Transactional
    public NotificationResponse sendEmailMessage(EmailMessageRequest request) {
        log.info(
                "Preparing email notification. email={}, subjectLength={}, messageLength={}, referenceKey={}, attachmentCount={}",
                request.email(),
                request.subject() == null ? 0 : request.subject().length(),
                request.message() == null ? 0 : request.message().length(),
                request.referenceKey(),
                request.attachments() == null ? 0 : request.attachments().size()
        );
        NotificationLog notificationLog = NotificationLog.builder()
                .channel(NotificationChannel.EMAIL)
                .recipient(request.email().trim().toLowerCase())
                .message(buildEmailMessage(request))
                .referenceKey(request.referenceKey())
                .status(NotificationStatus.PENDING)
                .build();

        notificationLogRepository.save(notificationLog);
        log.info(
                "Email notification log created. notificationId={}, recipient={}, status={}",
                notificationLog.getId(),
                notificationLog.getRecipient(),
                notificationLog.getStatus()
        );

        try {
            log.info(
                    "Sending email via provider. notificationId={}, recipient={}",
                    notificationLog.getId(),
                    notificationLog.getRecipient()
            );
            EmailClient.EmailDeliveryResult deliveryResult = emailClient.sendEmail(
                    notificationLog.getRecipient(),
                    request.subject().trim(),
                    request.message().trim(),
                    mapAttachments(request.attachments())
            );

            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setProviderMessageId(deliveryResult.providerMessageId());
            notificationLog.setSentAt(Instant.now());
            notificationLogRepository.save(notificationLog);
            log.info(
                    "Email delivery succeeded. notificationId={}, providerMessageId={}, sentAt={}",
                    notificationLog.getId(),
                    notificationLog.getProviderMessageId(),
                    notificationLog.getSentAt()
            );
            return toResponse(notificationLog);
        } catch (NotificationDeliveryException exception) {
            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setFailureReason(exception.getMessage());
            notificationLogRepository.save(notificationLog);
            log.error(
                    "Email delivery failed. notificationId={}, recipient={}, reason={}",
                    notificationLog.getId(),
                    notificationLog.getRecipient(),
                    exception.getMessage(),
                    exception
            );
            throw exception;
        }
    }

    @Transactional
    public HtmlToPdfNotificationResponse sendHtmlAsPdfNotification(
            HtmlToPdfNotificationRequest request
    ) {
        boolean hasEmail = request.email() != null && !request.email().isBlank();
        boolean hasWhatsApp = request.whatsappPhoneNumber() != null && !request.whatsappPhoneNumber().isBlank();
        if (!hasEmail && !hasWhatsApp) {
            throw new NotificationDeliveryException("At least one target is required: email or whatsappPhoneNumber");
        }

        String sanitizedHtml = htmlToPdfService.sanitizeHtml(request.htmlContent());
        String pdfFileName = htmlToPdfService.ensurePdfFileName(request.pdfFileName());
        byte[] pdfContent = htmlToPdfService.generatePdf(sanitizedHtml);
        log.info(
                "Generated PDF from HTML. fileName={}, sizeBytes={}, hasEmail={}, hasWhatsApp={}",
                pdfFileName,
                pdfContent.length,
                hasEmail,
                hasWhatsApp
        );

        NotificationResponse emailResponse = null;
        NotificationResponse whatsAppResponse = null;

        if (hasEmail) {
            String subject = request.emailSubject() == null || request.emailSubject().isBlank()
                    ? "Generated PDF Document"
                    : request.emailSubject().trim();
            String message = request.emailMessage() == null || request.emailMessage().isBlank()
                    ? htmlToPdfService.defaultEmailMessage()
                    : request.emailMessage().trim();
            EmailMessageRequest emailRequest = new EmailMessageRequest(
                    request.email().trim().toLowerCase(),
                    subject,
                    message,
                    request.referenceKey(),
                    List.of(new EmailMessageRequest.EmailAttachmentRequest(
                            pdfFileName,
                            "application/pdf",
                            Base64.getEncoder().encodeToString(pdfContent)
                    ))
            );
            emailResponse = sendEmailMessage(emailRequest);
        }

        if (hasWhatsApp) {
            whatsAppResponse = sendWhatsAppPdfDocument(
                    request.whatsappPhoneNumber(),
                    request.whatsappCaption(),
                    request.referenceKey(),
                    pdfFileName,
                    pdfContent
            );
        }

        return new HtmlToPdfNotificationResponse(
                pdfFileName,
                pdfContent.length,
                emailResponse,
                whatsAppResponse
        );
    }

    @Transactional
    public ContractPdfResponse sendContractPdf(ContractPdfRequest request) {
        log.info(
                "Generating AMC contract PDF. contractNumber={}, customer={}, email={}",
                request.contract().contractNumber(),
                request.customer().name(),
                request.email()
        );

        Map<String, Object> variables = buildContractTemplateVariables(request);
        byte[] pdfBytes = pdfTemplateService.renderToPdf("amc-contract", variables);
        String pdfFileName = "AMC-Contract-" + request.contract().contractNumber() + ".pdf";
        log.info("Contract PDF generated. fileName={}, sizeBytes={}", pdfFileName, pdfBytes.length);

        if (request.email() == null || request.email().isBlank()) {
            log.info("No email provided — skipping email send. contractNumber={}", request.contract().contractNumber());
            return new ContractPdfResponse(
                    request.contract().contractNumber(),
                    pdfFileName,
                    pdfBytes.length,
                    null,
                    null,
                    "PDF generated successfully. No email address provided."
            );
        }

        String subject = request.emailSubject() != null && !request.emailSubject().isBlank()
                ? request.emailSubject().trim()
                : "AMC Contract - " + request.contract().contractNumber();

        String body = "Dear " + request.customer().name() + ",\n\n"
                + "Please find attached your Annual Maintenance Contract document.\n\n"
                + "Contract No : " + request.contract().contractNumber() + "\n"
                + "AMC Period  : " + nullSafe(request.contract().startDate()) + " to " + nullSafe(request.contract().endDate()) + "\n\n"
                + "For any queries, please contact our support team.\n\n"
                + "Regards,\nApollo Elevators Team";

        EmailMessageRequest emailRequest = new EmailMessageRequest(
                request.email().trim().toLowerCase(),
                subject,
                body,
                request.referenceKey(),
                List.of(new EmailMessageRequest.EmailAttachmentRequest(
                        pdfFileName,
                        "application/pdf",
                        Base64.getEncoder().encodeToString(pdfBytes)
                ))
        );

        NotificationResponse emailResponse = sendEmailMessage(emailRequest);
        log.info(
                "Contract PDF email sent. contractNumber={}, notificationId={}, status={}",
                request.contract().contractNumber(),
                emailResponse.id(),
                emailResponse.status()
        );

        return new ContractPdfResponse(
                request.contract().contractNumber(),
                pdfFileName,
                pdfBytes.length,
                emailResponse.status(),
                emailResponse.id() != null ? emailResponse.id().toString() : null,
                "AMC Contract PDF generated and sent to " + request.email()
        );
    }

    private Map<String, Object> buildContractTemplateVariables(ContractPdfRequest request) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("customer", request.customer());
        vars.put("contract", request.contract());
        vars.put("serviceHistory",
                request.serviceHistory() != null ? request.serviceHistory() : Collections.emptyList());
        vars.put("generatedDate",
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        return vars;
    }

    private String nullSafe(String value) {
        return value != null ? value : "N/A";
    }

    // =========================================================================
    // Clean notification API — 4 public methods exposed via controller
    // =========================================================================

    @Transactional
    public NotificationResponse sendPlainEmail(PlainEmailRequest request) {
        log.info("Plain email request. email={}, subject={}", request.email(), request.subject());
        NotificationLog notificationLog = NotificationLog.builder()
                .channel(NotificationChannel.EMAIL)
                .recipient(request.email().trim().toLowerCase())
                .message(request.body().trim())
                .referenceKey(request.referenceKey())
                .status(NotificationStatus.PENDING)
                .build();
        notificationLogRepository.save(notificationLog);
        try {
            EmailClient.EmailDeliveryResult result = emailClient.sendEmail(
                    notificationLog.getRecipient(),
                    request.subject().trim(),
                    request.body().trim(),
                    Collections.emptyList()
            );
            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setProviderMessageId(result.providerMessageId());
            notificationLog.setSentAt(Instant.now());
            notificationLogRepository.save(notificationLog);
            log.info("Plain email sent. notificationId={}, status={}", notificationLog.getId(), notificationLog.getStatus());
            return toResponse(notificationLog);
        } catch (NotificationDeliveryException exception) {
            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setFailureReason(exception.getMessage());
            notificationLogRepository.save(notificationLog);
            log.error("Plain email failed. notificationId={}, reason={}", notificationLog.getId(), exception.getMessage(), exception);
            throw exception;
        }
    }

    @Transactional
    public NotificationResponse sendContractEmail(ContractEmailRequest request) {
        log.info("Contract email request. customerId={}, email={}", request.customerId(), request.email());
        DocumentService.AmcContractPdfResult pdf = documentService.generateAmcContractPdfResult(request.customerId());

        String subject = (request.subject() != null && !request.subject().isBlank())
                ? request.subject().trim()
                : "AMC Contract — Apollo Elevators";
        String body = (request.body() != null && !request.body().isBlank())
                ? request.body().trim()
                : "Please find attached your AMC Contract document.";

        NotificationLog notificationLog = NotificationLog.builder()
                .channel(NotificationChannel.EMAIL)
                .recipient(request.email().trim().toLowerCase())
                .message(body)
                .referenceKey(request.referenceKey())
                .status(NotificationStatus.PENDING)
                .build();
        notificationLogRepository.save(notificationLog);
        try {
            EmailClient.EmailDeliveryResult result = emailClient.sendEmail(
                    notificationLog.getRecipient(),
                    subject,
                    body,
                    List.of(new EmailClient.EmailAttachment(pdf.fileName(), "application/pdf", pdf.pdfBytes()))
            );
            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setProviderMessageId(result.providerMessageId());
            notificationLog.setSentAt(Instant.now());
            notificationLogRepository.save(notificationLog);
            log.info("Contract email sent. notificationId={}, fileName={}, sizeBytes={}",
                    notificationLog.getId(), pdf.fileName(), pdf.pdfBytes().length);
            return toResponse(notificationLog);
        } catch (NotificationDeliveryException exception) {
            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setFailureReason(exception.getMessage());
            notificationLogRepository.save(notificationLog);
            log.error("Contract email failed. notificationId={}, reason={}", notificationLog.getId(), exception.getMessage(), exception);
            throw exception;
        }
    }

    @Transactional
    public NotificationResponse sendPlainWhatsApp(PlainWhatsAppRequest request) {
        String recipient = normalizePhoneNumber(request.phoneNumber());
        log.info("Plain WhatsApp request. recipient={}", recipient);
        NotificationLog notificationLog = NotificationLog.builder()
                .channel(NotificationChannel.WHATSAPP)
                .recipient(recipient)
                .message(request.message().trim())
                .referenceKey(request.referenceKey())
                .status(NotificationStatus.PENDING)
                .build();
        notificationLogRepository.save(notificationLog);
        try {
            WhatsAppClient.WhatsAppDeliveryResult result =
                    whatsAppClient.sendText(recipient, request.message().trim());
            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setProviderMessageId(result.providerMessageId());
            notificationLog.setSentAt(Instant.now());
            notificationLogRepository.save(notificationLog);
            log.info("Plain WhatsApp sent. notificationId={}, status={}", notificationLog.getId(), notificationLog.getStatus());
            return toResponse(notificationLog);
        } catch (NotificationDeliveryException exception) {
            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setFailureReason(exception.getMessage());
            notificationLogRepository.save(notificationLog);
            log.error("Plain WhatsApp failed. notificationId={}, reason={}", notificationLog.getId(), exception.getMessage(), exception);
            throw exception;
        }
    }

    @Transactional
    public NotificationResponse sendContractWhatsApp(ContractWhatsAppRequest request) {
        String recipient = normalizePhoneNumber(request.phoneNumber());
        log.info("Contract WhatsApp request. customerId={}, recipient={}", request.customerId(), recipient);
        DocumentService.AmcContractPdfResult pdf = documentService.generateAmcContractPdfResult(request.customerId());

        String caption = (request.caption() != null && !request.caption().isBlank())
                ? request.caption().trim()
                : "AMC Contract — Apollo Elevators";

        NotificationLog notificationLog = NotificationLog.builder()
                .channel(NotificationChannel.WHATSAPP)
                .recipient(recipient)
                .message("Document: " + pdf.fileName() + " | " + caption)
                .referenceKey(request.referenceKey())
                .status(NotificationStatus.PENDING)
                .build();
        notificationLogRepository.save(notificationLog);
        try {
            WhatsAppClient.WhatsAppDeliveryResult result =
                    whatsAppClient.sendDocument(recipient, pdf.fileName(), pdf.pdfBytes(), caption);
            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setProviderMessageId(result.providerMessageId());
            notificationLog.setSentAt(Instant.now());
            notificationLogRepository.save(notificationLog);
            log.info("Contract WhatsApp sent. notificationId={}, fileName={}", notificationLog.getId(), pdf.fileName());
            return toResponse(notificationLog);
        } catch (NotificationDeliveryException exception) {
            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setFailureReason(exception.getMessage());
            notificationLogRepository.save(notificationLog);
            log.error("Contract WhatsApp failed. notificationId={}, reason={}", notificationLog.getId(), exception.getMessage(), exception);
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
        log.debug("Normalizing phone number. input={}, intermediate={}", phoneNumber, normalized);

        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1);
        }
        log.info("Phone number normalized. normalized={}", normalized);

        return normalized;
    }

    private String buildEmailMessage(EmailMessageRequest request) {
        return "Subject: " + request.subject().trim() + "\n\n" + request.message().trim();
    }

    private NotificationResponse sendWhatsAppPdfDocument(
            String phoneNumber,
            String caption,
            String referenceKey,
            String fileName,
            byte[] content
    ) {
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        String finalCaption = caption == null || caption.isBlank()
                ? htmlToPdfService.defaultWhatsAppCaption()
                : caption.trim();
        NotificationLog notificationLog = NotificationLog.builder()
                .channel(NotificationChannel.WHATSAPP)
                .recipient(normalizedPhoneNumber)
                .message("PDF: " + fileName + " | " + finalCaption)
                .referenceKey(referenceKey)
                .status(NotificationStatus.PENDING)
                .build();
        notificationLogRepository.save(notificationLog);

        try {
            WhatsAppClient.WhatsAppDeliveryResult deliveryResult = whatsAppClient.sendDocument(
                    normalizedPhoneNumber,
                    fileName,
                    content,
                    finalCaption
            );
            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setProviderMessageId(deliveryResult.providerMessageId());
            notificationLog.setSentAt(Instant.now());
            notificationLogRepository.save(notificationLog);
            return toResponse(notificationLog);
        } catch (NotificationDeliveryException exception) {
            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setFailureReason(exception.getMessage());
            notificationLogRepository.save(notificationLog);
            throw exception;
        }
    }

    private List<EmailClient.EmailAttachment> mapAttachments(
            List<EmailMessageRequest.EmailAttachmentRequest> attachmentRequests
    ) {
        if (attachmentRequests == null || attachmentRequests.isEmpty()) {
            return Collections.emptyList();
        }

        return attachmentRequests.stream()
                .map(this::toAttachment)
                .toList();
    }

    private EmailClient.EmailAttachment toAttachment(
            EmailMessageRequest.EmailAttachmentRequest request
    ) {
        try {
            byte[] content = Base64.getDecoder().decode(request.base64Content().trim());
            String contentType = request.contentType();
            return new EmailClient.EmailAttachment(
                    request.fileName().trim(),
                    contentType == null ? null : contentType.trim(),
                    content
            );
        } catch (IllegalArgumentException exception) {
            throw new NotificationDeliveryException(
                    "Invalid base64 content for attachment: " + request.fileName(),
                    exception
            );
        }
    }
}
