package com.apollo.elevators.notification;

import com.apollo.elevators.common.exception.NotificationDeliveryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
@Profile("!dev")
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailClient implements EmailClient {

    private final JavaMailSender javaMailSender;
    private final EmailProperties emailProperties;

    @Override
    public EmailDeliveryResult sendEmail(
            String email,
            String subject,
            String message,
            List<EmailAttachment> attachments
    ) {
        validateConfiguration();
        int attachmentCount = attachments == null ? 0 : attachments.size();
        log.info(
                "SMTP email send invoked. email={}, subjectLength={}, messageLength={}, attachmentCount={}",
                email,
                subject == null ? 0 : subject.length(),
                message == null ? 0 : message.length(),
                attachmentCount
        );

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(emailProperties.getFrom().trim());
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(message, false);

            if (attachments != null) {
                for (EmailAttachment attachment : attachments) {
                    String contentType = attachment.contentType() == null
                            || attachment.contentType().isBlank()
                            ? "application/octet-stream"
                            : attachment.contentType();
                    helper.addAttachment(
                            attachment.fileName(),
                            new ByteArrayResource(attachment.content()),
                            contentType
                    );
                }
            }

            javaMailSender.send(mimeMessage);
            String providerMessageId = "smtp-" + UUID.randomUUID();
            log.info("SMTP email sent successfully. email={}, providerMessageId={}", email, providerMessageId);
            return new EmailDeliveryResult(providerMessageId);
        } catch (MessagingException exception) {
            log.error("SMTP email composition failed. email={}", email, exception);
            throw new NotificationDeliveryException("Failed to compose email message", exception);
        } catch (MailException exception) {
            log.error("SMTP email send failed. email={}", email, exception);
            throw new NotificationDeliveryException("Failed to send email via SMTP", exception);
        }
    }

    private void validateConfiguration() {
        if (!emailProperties.isEnabled()) {
            throw new NotificationDeliveryException("Email notifications are disabled");
        }
        if (isBlank(emailProperties.getFrom())) {
            throw new NotificationDeliveryException("Email provider configuration is incomplete");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
