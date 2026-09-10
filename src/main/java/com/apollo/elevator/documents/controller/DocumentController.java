package com.apollo.elevator.documents.controller;

import com.apollo.elevator.common.api.ApiErrorResponse;
import com.apollo.elevator.documents.model.dto.BillPreviewResponse;
import com.apollo.elevator.documents.model.dto.BillRequest;
import com.apollo.elevator.documents.model.enums.DocumentType;
import com.apollo.elevator.documents.service.DocumentService;
import com.apollo.elevator.notification.email.model.dto.EmailMessageRequest;
import com.apollo.elevator.notification.model.dto.NotificationResponse;
import com.apollo.elevator.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents", description = "Document generation endpoints")
public class DocumentController {

    private final DocumentService documentService;
    private final NotificationService notificationService;

    // =========================================================================
    // AMC CONTRACT  (database-driven)
    // =========================================================================

    @GetMapping("/customers/{customerId}")
    @Operation(
            summary = "Generate Customer Document PDF",
            description = """
                    Generates a PDF for the given customer.

                    | `documentType`  | Output                      |
                    |-----------------|-----------------------------|
                    | `AMC_CONTRACT`  | AMC Contract PDF (default)  |

                    For bills, use the two-step preview → generate flow below.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generated",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Unsupported documentType for this endpoint",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Customer / lift / AMC not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<byte[]> generateCustomerDocument(
            @Parameter(description = "Database ID of the customer", required = true)
            @PathVariable Long customerId,
            @Parameter(description = "Document type (default: AMC_CONTRACT)")
            @RequestParam(defaultValue = "AMC_CONTRACT") DocumentType documentType
    ) {
        log.info("Document PDF request. customerId={}, documentType={}", customerId, documentType);
        DocumentService.PdfResult result = documentService.generateCustomerDocument(customerId, documentType);
        log.info("Document PDF ready. fileName={}, sizeBytes={}", result.fileName(), result.pdfBytes().length);
        return pdfResponse(result);
    }

    /** Backward-compatible alias kept so existing callers don't break. */
    @GetMapping("/customers/{customerId}/amc-contract")
    @Operation(summary = "Generate AMC Contract PDF (legacy — use GET /customers/{customerId} instead)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generated",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "Not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<byte[]> generateAmcContractPdfLegacy(
            @PathVariable Long customerId
    ) {
        return generateCustomerDocument(customerId, DocumentType.AMC_CONTRACT);
    }

    // =========================================================================
    // BILL — STEP 1: PREVIEW  (GET → JSON for UI review)
    // =========================================================================

    @GetMapping("/customers/{customerId}/bill-preview")
    @Operation(
            summary = "Preview bill data fetched from the customer's AMC",
            description = """
                    **Step 1 of the 2-step bill flow.**

                    Fetches the customer and their active AMC contract from the database and returns
                    a pre-filled `BillRequest` JSON object for the UI to display and optionally edit.

                    The response object looks like:
                    ```json
                    {
                      "documentType": "GST_BILL",
                      "billRequest": { ... }
                    }
                    ```

                    Once the user has reviewed / edited the data, POST the `billRequest` body to
                    `POST /bills/generate?documentType=GST_BILL` to produce the final PDF.

                    **Entity default for GST_BILL:**
                    - `GST_BILL` → `APOLLO_ELEVATOR` (GSTIN: 29ABPFA4107Q1ZU)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pre-filled bill data ready for review",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BillPreviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid documentType",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Customer / lift / AMC not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<BillPreviewResponse> previewBill(
            @Parameter(description = "Database ID of the customer", required = true)
            @PathVariable Long customerId,
            @Parameter(description = "Bill type: GST_BILL", required = true,
                    schema = @Schema(implementation = DocumentType.class))
            @RequestParam DocumentType documentType
    ) {
        log.info("Bill preview request. customerId={}, documentType={}", customerId, documentType);
        BillPreviewResponse preview = documentService.buildBillPreview(customerId, documentType);
        log.info("Bill preview ready. customerId={}, invoiceNumber={}",
                customerId, preview.billRequest().bill().invoiceNumber());
        return ResponseEntity.ok(preview);
    }

    // =========================================================================
    // BILL — STEP 2: GENERATE PDF  (POST reviewed JSON → PDF)
    // =========================================================================

    @PostMapping("/bills/generate")
    @Operation(
            summary = "Generate Bill PDF from reviewed bill data",
            description = """
                    **Step 2 of the 2-step bill flow.**

                    Accepts the `BillRequest` JSON (as returned by — or edited after —
                    `GET /customers/{customerId}/bill-preview`) and renders it into a PDF.

                    | `documentType` | Template                                     |
                    |----------------|----------------------------------------------|
                    | `GST_BILL`     | Tax Invoice — Apollo Elevator (with SGST/CGST) |

                    **Typical UI flow:**
                    1. `GET /customers/{id}/bill-preview?documentType=GST_BILL` → show JSON to user
                    2. User reviews / edits fields in the UI
                    3. `POST /bills/generate?documentType=GST_BILL` with the (edited) body → download PDF
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bill PDF generated",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Validation error or wrong documentType",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<byte[]> generateBillPdf(
            @Parameter(description = "Bill type: GST_BILL", required = true,
                    schema = @Schema(implementation = DocumentType.class))
            @RequestParam DocumentType documentType,
            @Valid @RequestBody BillRequest billRequest
    ) {
        log.info("Bill PDF generate request. documentType={}, invoiceNumber={}, entity={}",
                documentType, billRequest.bill().invoiceNumber(), billRequest.entity());
        DocumentService.PdfResult result = documentService.generateBillPdf(documentType, billRequest);
        log.info("Bill PDF ready. fileName={}, sizeBytes={}", result.fileName(), result.pdfBytes().length);
        return pdfResponse(result);
    }

    // =========================================================================
    // BILL — SEND VIA EMAIL
    // =========================================================================

    @PostMapping("/bills/send-email")
    @Operation(
            summary = "Generate bill PDF and send via email",
            description = "Generates the bill PDF from the reviewed BillRequest and emails it as an attachment."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email sent"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<NotificationResponse> sendBillEmail(
            @Parameter(description = "Bill type: GST_BILL", required = true)
            @RequestParam DocumentType documentType,
            @Parameter(description = "Recipient email address", required = true)
            @RequestParam String to,
            @Valid @RequestBody BillRequest billRequest
    ) {
        log.info("Send bill via email. documentType={}, to={}", documentType, to);
        DocumentService.PdfResult pdf = documentService.generateBillPdf(documentType, billRequest);
        String base64 = java.util.Base64.getEncoder().encodeToString(pdf.pdfBytes());
        String subject = "Apollo Elevator – Bill " + billRequest.bill().invoiceNumber();
        String body = "Dear " + billRequest.billTo().name() + ",\n\nPlease find attached your bill from Apollo Elevator.\n\nRegards,\nApollo Elevator";
        EmailMessageRequest emailReq = new EmailMessageRequest(
                to, subject, body, "bill-" + billRequest.bill().invoiceNumber(),
                java.util.List.of(new EmailMessageRequest.EmailAttachmentRequest(pdf.fileName(), "application/pdf", base64))
        );
        NotificationResponse result = notificationService.sendEmailMessage(emailReq);
        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // BILL — SEND VIA WHATSAPP
    // =========================================================================

    @PostMapping("/bills/send-whatsapp")
    @Operation(
            summary = "Generate bill PDF and send via WhatsApp",
            description = "Generates the bill PDF and sends it as a WhatsApp document message."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "WhatsApp message sent"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<NotificationResponse> sendBillWhatsapp(
            @Parameter(description = "Bill type: GST_BILL", required = true)
            @RequestParam DocumentType documentType,
            @Parameter(description = "Recipient phone number in international format", required = true)
            @RequestParam String phone,
            @Valid @RequestBody BillRequest billRequest
    ) {
        log.info("Send bill via WhatsApp. documentType={}, phone={}", documentType, phone);
        DocumentService.PdfResult pdf = documentService.generateBillPdf(documentType, billRequest);
        NotificationResponse result = notificationService.sendBillWhatsAppAsResponse(phone, pdf.fileName(), pdf.pdfBytes(),
                "Bill " + billRequest.bill().invoiceNumber() + " from Apollo Elevator");
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------

    private static ResponseEntity<byte[]> pdfResponse(DocumentService.PdfResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(result.pdfBytes().length)
                .body(result.pdfBytes());
    }
}
