package com.apollo.elevators.documents.service;

import com.apollo.elevators.common.exception.NotificationDeliveryException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URL;

@Service
public class HtmlToPdfService {

    // Classpath location containing amc-contract.html, apollo-amc-contract.css,
    // and the NotoSans .ttf files. Must match wherever your templates actually
    // live (src/main/resources/templates/pdf/).
    private static final String TEMPLATE_BASE_PATH = "/templates/pdf/";

    public byte[] generatePdf(String htmlContent) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // Strip UTF-8 BOM if present — openhtmltopdf XML parser rejects content before prolog
            String cleanHtml = htmlContent.startsWith("\uFEFF")
                    ? htmlContent.substring(1)
                    : htmlContent;

            // Base URI so relative <link href="..."> and CSS url(...) resolve.
            // Without this (passing null, as before), openhtmltopdf can't
            // locate apollo-amc-contract.css or the embedded fonts, and both
            // fail SILENTLY — no CSS, no ₹ glyph, no exception thrown.
            URL baseUrl = getClass().getResource(TEMPLATE_BASE_PATH);
            if (baseUrl == null) {
                throw new IllegalStateException(
                        "Could not resolve classpath resource " + TEMPLATE_BASE_PATH
                                + " — check it exists under src/main/resources"
                );
            }
            String baseUri = baseUrl.toExternalForm();

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(cleanHtml, baseUri);
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
