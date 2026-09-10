package com.apollo.elevator.quotation.controller;

import com.apollo.elevator.common.api.ApiErrorResponse;
import com.apollo.elevator.quotation.model.dto.CreateQuotationRequest;
import com.apollo.elevator.quotation.model.dto.QuotationPendingResponse;
import com.apollo.elevator.quotation.model.dto.QuotationResponse;
import com.apollo.elevator.quotation.model.dto.UpdateQuotationStatusRequest;
import com.apollo.elevator.quotation.model.enums.QuotationStatus;
import com.apollo.elevator.quotation.service.QuotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Quotations", description = "Admin quotation lifecycle management")
public class QuotationController {

    private final QuotationService quotationService;

    @GetMapping("/quotations")
    @Operation(summary = "List quotations", description = "Fetch paginated quotations with optional status, enquiryStatus and query filtering")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quotations retrieved successfully")
    })
    public ResponseEntity<Page<QuotationResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String enquiryStatus,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        log.info("Admin quotation list requested. status={}, enquiryStatus={}, query={}, page={}, size={}", status, enquiryStatus, query, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<QuotationResponse> response = quotationService.list(status, enquiryStatus, query, pageable);
        log.info("Admin quotation list completed. totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/enquiries/quotation-pending")
    @Operation(summary = "List IN_PROGRESS enquiries without quotations", description = "Returns enquiries eligible for quotation creation but not yet quoted")
    public ResponseEntity<Page<QuotationPendingResponse>> listPendingQuotations(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(quotationService.listPendingQuotations(query, pageable));
    }

    @PostMapping("/quotations")
    @Operation(summary = "Create quotation", description = "Creates a quotation for an enquiry and updates enquiry status when required")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Quotation created successfully",
                content = @Content(schema = @Schema(implementation = QuotationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid quotation payload",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Enquiry not found",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Duplicate active quotation",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<QuotationResponse> create(
            @Valid @RequestBody CreateQuotationRequest request,
            Authentication authentication
    ) {
        String createdBy = authentication != null ? authentication.getName() : "system";
        log.info("Admin quotation create requested. enquiryId={}, amount={}, createdBy={}", request.enquiryId(), request.amount(), createdBy);
        QuotationResponse response = quotationService.create(request, createdBy);
        log.info("Admin quotation created. quotationId={}, quotationNumber={}, createdBy={}", response.id(), response.quotationNumber(), createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/quotations/{id}/status")
    @Operation(summary = "Update quotation status", description = "Transition quotation through supported status values")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quotation status updated successfully",
                content = @Content(schema = @Schema(implementation = QuotationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid quotation status transition",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Quotation not found",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<QuotationResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateQuotationStatusRequest request,
            Authentication authentication
    ) {
        String updatedBy = authentication != null ? authentication.getName() : "system";
        log.info("Admin quotation status update requested. quotationId={}, status={}, updatedBy={}", id, request.status(), updatedBy);
        QuotationResponse response = quotationService.updateStatus(id, request.status(), updatedBy);
        log.info("Admin quotation status update completed. quotationId={}, status={}", id, response.status());
        return ResponseEntity.ok(response);
    }
}
