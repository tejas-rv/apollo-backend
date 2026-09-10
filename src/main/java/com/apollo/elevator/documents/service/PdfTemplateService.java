package com.apollo.elevator.documents.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class PdfTemplateService {

    private final TemplateEngine templateEngine;
    private final HtmlToPdfService htmlToPdfService;

    /**
     * Injects the Spring Boot auto-configured SpringTemplateEngine (SpEL-based, no OGNL needed).
     * Spring Boot's resolver already looks in classpath:/templates/ with .html suffix,
     * so templates/pdf/amc-contract.html is resolved as "pdf/amc-contract".
     */
    public PdfTemplateService(TemplateEngine templateEngine, HtmlToPdfService htmlToPdfService) {
        this.templateEngine = templateEngine;
        this.htmlToPdfService = htmlToPdfService;
    }

    /**
     * Renders the named template with the given data model and converts it to PDF bytes.
     *
     * @param templateName path relative to templates/ directory (without .html), e.g. "pdf/amc-contract"
     * @param variables    data variables to inject into the template
     * @return PDF bytes ready to attach or send
     */
    public byte[] renderToPdf(String templateName, Map<String, Object> variables) {
        Map<String, Object> templateVariables = new HashMap<>(variables == null ? Map.of() : variables);
        templateVariables.putIfAbsent("logoDataUri", resourceToDataUri("templates/pdf/apollo_elevator_logo.png"));

        log.info("Rendering PDF template. template={}, variableKeys={}", templateName, templateVariables.keySet());
        String html = render(templateName, templateVariables);
        log.debug("Template rendered to HTML. htmlLength={}", html.length());
        byte[] pdfBytes = htmlToPdfService.generatePdf(html);
        log.info("PDF generated from template. template={}, pdfSizeBytes={}", templateName, pdfBytes.length);
        return pdfBytes;
    }

    public String render(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(templateName, context);
    }

    private String resourceToDataUri(String resourcePath) {
        try (InputStream inputStream = getClass().getResourceAsStream("/" + resourcePath)) {
            if (inputStream == null) {
                log.warn("Could not resolve PDF asset resource: {}", resourcePath);
                return "";
            }
            byte[] bytes = inputStream.readAllBytes();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            log.warn("Failed to read PDF asset resource: {}", resourcePath, e);
            return "";
        }
    }
}
