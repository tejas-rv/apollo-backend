package com.apollo.elevator.billing.controller;

import com.apollo.elevator.billing.model.dto.AmcBillingStatusResponse;
import com.apollo.elevator.billing.model.dto.BillDraftRequest;
import com.apollo.elevator.billing.model.dto.BillDraftResponse;
import com.apollo.elevator.billing.model.dto.BillResponse;
import com.apollo.elevator.billing.model.dto.BillSummaryResponse;
import com.apollo.elevator.billing.model.dto.CreateBillRequest;
import com.apollo.elevator.billing.model.dto.MarkBillPaidRequest;
import com.apollo.elevator.billing.model.dto.NewAmcContractRequest;
import com.apollo.elevator.billing.model.enums.BillStatus;
import com.apollo.elevator.billing.model.enums.BillType;
import com.apollo.elevator.billing.service.BillingService;
import com.apollo.elevator.common.validation.IndianFormats;
import com.apollo.elevator.documents.model.dto.BillRequest;
import com.apollo.elevator.documents.service.DocumentService;
import com.apollo.elevator.notification.email.model.dto.EmailMessageRequest;
import com.apollo.elevator.notification.model.dto.NotificationResponse;
import com.apollo.elevator.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bills")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Billing", description = "Saved bills: AMC (plan instalments) and maintenance bills, payments and dashboard summary")
public class BillingController {

    private final BillingService billingService;
    private final NotificationService notificationService;

    @GetMapping("/customers/{customerId}/amc-contracts")
    @Operation(summary = "AMC contracts of a customer with their billing status for the current AMC year")
    public ResponseEntity<List<AmcBillingStatusResponse>> amcContracts(@PathVariable Long customerId) {
        return ResponseEntity.ok(billingService.amcBillingStatus(customerId));
    }

    @PostMapping("/customers/{customerId}/amc-contracts")
    @Operation(summary = "Create an AMC contract on a customer's lift (plan price and dates auto-filled) so it can be billed")
    public ResponseEntity<AmcBillingStatusResponse> createAmcContract(@PathVariable Long customerId,
                                                                      @Valid @RequestBody NewAmcContractRequest request,
                                                                      Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(billingService.createAmcContract(customerId, request, user(authentication)));
    }

    @PostMapping("/draft")
    @Operation(summary = "Build a pre-filled bill for review (AMC: next unbilled instalment; MAINTENANCE: blank line)")
    public ResponseEntity<BillDraftResponse> draft(@RequestBody BillDraftRequest request) {
        if (request.customerId() == null || request.billType() == null) {
            throw new IllegalArgumentException("customerId and billType are required");
        }
        return ResponseEntity.ok(billingService.draft(request));
    }

    @GetMapping("/next-invoice-number")
    @Operation(summary = "Suggest the next invoice number for the current financial year")
    public ResponseEntity<Map<String, String>> nextInvoiceNumber() {
        return ResponseEntity.ok(Map.of("invoiceNumber", billingService.nextInvoiceNumber()));
    }

    @PostMapping
    @Operation(summary = "Save the bill. AMC bills are rejected (409) if that AMC year instalment is already billed")
    public ResponseEntity<BillResponse> create(@Valid @RequestBody CreateBillRequest request, Authentication authentication) {
        if (request.customerId() == null || request.billType() == null || request.billRequest() == null) {
            throw new IllegalArgumentException("customerId, billType and billRequest are required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.create(request, user(authentication)));
    }

    @GetMapping
    @Operation(summary = "List saved bills (newest first)")
    public ResponseEntity<Page<BillResponse>> list(@RequestParam(required = false) BillStatus status,
                                                   @RequestParam(required = false) BillType billType,
                                                   @RequestParam(required = false) Long customerId,
                                                   @RequestParam(required = false) String q,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return ResponseEntity.ok(billingService.list(status, billType, customerId, q, pageable));
    }

    @GetMapping("/summary")
    @Operation(summary = "Dashboard figures: billed, paid (in bank), outstanding and projected. Defaults to current financial year")
    public ResponseEntity<BillSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(billingService.summary(from, to));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a saved bill")
    public ResponseEntity<BillResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(billingService.get(id));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the bill PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        DocumentService.PdfResult result = billingService.renderPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(result.pdfBytes().length)
                .body(result.pdfBytes());
    }

    @PostMapping("/{id}/send-email")
    @Operation(summary = "Email the saved bill PDF; marks it SENT on success")
    public ResponseEntity<NotificationResponse> sendEmail(@PathVariable Long id, @RequestParam String to,
                                                          Authentication authentication) {
        if (to == null || !to.trim().matches(IndianFormats.EMAIL)) {
            throw new IllegalArgumentException("Recipient email must be a valid email address");
        }
        BillRequest bill = billingService.billRequestOf(id);
        DocumentService.PdfResult pdf = billingService.renderPdf(id);
        String invoiceNumber = bill.bill().invoiceNumber();
        EmailMessageRequest email = new EmailMessageRequest(
                to.trim(),
                "Apollo Elevator – Bill " + invoiceNumber,
                "Dear " + bill.billTo().name() + ",\n\nPlease find attached your bill from Apollo Elevator.\n\nRegards,\nApollo Elevator",
                "bill-" + invoiceNumber,
                List.of(new EmailMessageRequest.EmailAttachmentRequest(pdf.fileName(), "application/pdf",
                        Base64.getEncoder().encodeToString(pdf.pdfBytes()))));
        NotificationResponse result = notificationService.sendEmailMessage(email);
        markSentIfDelivered(id, result, authentication);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/send-whatsapp")
    @Operation(summary = "WhatsApp the saved bill PDF; marks it SENT on success")
    public ResponseEntity<NotificationResponse> sendWhatsapp(@PathVariable Long id, @RequestParam String phone,
                                                             Authentication authentication) {
        String mobile = IndianFormats.normalizeMobile(phone);
        if (mobile == null) {
            throw new IllegalArgumentException("Recipient phone " + IndianFormats.MOBILE_MESSAGE);
        }
        BillRequest bill = billingService.billRequestOf(id);
        DocumentService.PdfResult pdf = billingService.renderPdf(id);
        NotificationResponse result = notificationService.sendBillWhatsAppAsResponse("91" + mobile, pdf.fileName(),
                pdf.pdfBytes(), "Bill " + bill.bill().invoiceNumber() + " from Apollo Elevator");
        markSentIfDelivered(id, result, authentication);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/paid")
    @Operation(summary = "Record payment received (counts as 'in bank' on the dashboard)")
    public ResponseEntity<BillResponse> markPaid(@PathVariable Long id, @Valid @RequestBody MarkBillPaidRequest request,
                                                 Authentication authentication) {
        return ResponseEntity.ok(billingService.markPaid(id, request, user(authentication)));
    }

    @PutMapping("/{id}/unpaid")
    @Operation(summary = "Undo a recorded payment")
    public ResponseEntity<BillResponse> markUnpaid(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(billingService.markUnpaid(id, user(authentication)));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an unpaid bill (frees the AMC instalment for re-billing)")
    public ResponseEntity<BillResponse> cancel(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(billingService.cancel(id, user(authentication)));
    }

    private void markSentIfDelivered(Long id, NotificationResponse result, Authentication authentication) {
        if (result != null && "SENT".equalsIgnoreCase(result.status())) {
            billingService.markSent(id, user(authentication));
        }
    }

    static String user(Authentication authentication) {
        return authentication != null && authentication.getName() != null ? authentication.getName() : "system";
    }
}
