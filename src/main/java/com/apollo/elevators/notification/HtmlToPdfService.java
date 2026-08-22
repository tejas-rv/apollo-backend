package com.apollo.elevators.notification;

import com.apollo.elevators.common.exception.NotificationDeliveryException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Service
public class HtmlToPdfService {

    public byte[] generatePdf(String htmlContent) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // Strip UTF-8 BOM if present — openhtmltopdf XML parser rejects content before prolog
            String cleanHtml = htmlContent.startsWith("\uFEFF")
                    ? htmlContent.substring(1)
                    : htmlContent;
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(cleanHtml, null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new NotificationDeliveryException("Failed to generate PDF from HTML", exception);
        }
    }

    public String ensurePdfFileName(String requestedFileName) {
        String fallback = "generated-document.pdf";
        if (requestedFileName == null || requestedFileName.isBlank()) {
            return fallback;
        }
        String trimmed = requestedFileName.trim();
        if (trimmed.toLowerCase().endsWith(".pdf")) {
            return trimmed;
        }
        return trimmed + ".pdf";
    }

    public String sanitizeHtml(String htmlContent) {
        if (htmlContent == null) {
            return "";
        }
        return htmlContent.strip();
    }

    public String defaultEmailMessage() {
        return "Please find the attached PDF document generated from HTML content.";
    }

    public String defaultWhatsAppCaption() {
        return "Generated PDF document";
    }
}
