package com.apollo.elevators.controller;

import com.apollo.elevators.common.api.ApiErrorResponse;
import com.apollo.elevators.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents", description = "Document generation endpoints")
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping("/customers/{customerId}/amc-contract")
    @Operation(
            summary = "Generate AMC Contract PDF",
            description = """
                    Fetches the customer and their AMC data from the database, renders the
                    server-side `amc-contract.html` template, and returns a ready-to-download PDF.

                    **File name format:** `{customerCode}_apollo_amc_{amcYear}.pdf`

                    - Picks the first lift of the customer.
                    - Prefers the **ACTIVE** AMC contract; falls back to the latest by end date.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "PDF generated — returned as a downloadable file",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))
            ),
            @ApiResponse(responseCode = "404", description = "Customer / lift / AMC not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden (ADMIN role required)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<byte[]> generateAmcContractPdf(
            @Parameter(description = "Database ID of the customer", required = true)
            @PathVariable Long customerId
    ) {
        log.info("AMC contract PDF request received. customerId={}", customerId);

        String fileName = documentService.buildPdfFileName(customerId);
        byte[] pdfBytes = documentService.generateAmcContractPdf(customerId);

        log.info("AMC contract PDF ready. customerId={}, fileName={}, sizeBytes={}", customerId, fileName, pdfBytes.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }
}

