package com.apollo.elevators.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

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
        log.info("Rendering PDF template. template={}, variableKeys={}", templateName, variables.keySet());
        String html = render(templateName, variables);
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
}
