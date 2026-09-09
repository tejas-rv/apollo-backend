package com.apollo.elevator.enquiry.controller;

import com.apollo.elevator.common.api.ApiErrorResponse;
import com.apollo.elevator.enquiry.model.dto.ContactInquiryRequest;
import com.apollo.elevator.enquiry.model.dto.ContactInquiryResponse;
import com.apollo.elevator.enquiry.model.dto.SubmitInquiryResponse;
import com.apollo.elevator.enquiry.model.dto.UpdateStatusRequest;
import com.apollo.elevator.enquiry.model.enums.InquiryStatus;
import com.apollo.elevator.enquiry.service.ContactInquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Contact Inquiries", description = "Public contact/quote submissions and admin management")
public class ContactInquiryController {

    private final ContactInquiryService contactInquiryService;

    @PostMapping("/api/public/enquiries")
    @Operation(summary = "Submit a contact or quote inquiry", description = "Public endpoint used by the marketing site's contact and quote forms")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Inquiry submitted successfully",
            content = @Content(schema = @Schema(implementation = SubmitInquiryResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SubmitInquiryResponse> submit(@Valid @RequestBody ContactInquiryRequest request) {
        log.info("Public inquiry submission received. inquiryType={}, sourcePage={}", request.inquiryType(), request.sourcePage());
        SubmitInquiryResponse response = contactInquiryService.submit(request);
        log.info("Public inquiry submission completed. success={}", response.success());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/admin/enquiries")
    @Operation(summary = "List contact inquiries", description = "Paginated, filterable list of inquiries for admin users")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Inquiries retrieved successfully")
    })
    public ResponseEntity<Page<ContactInquiryResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Admin inquiry list requested. name={}, phone={}, status={}, from={}, to={}, page={}, size={}",
                name, phone, status, from, to, page, size);
        LocalDateTime fromDateTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toDateTime = to == null ? null : to.atTime(23, 59, 59);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ContactInquiryResponse> response = contactInquiryService.search(name, phone, status, fromDateTime, toDateTime, pageable);
        log.info("Admin inquiry list completed. totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/admin/enquiries/{id}")
    @Operation(summary = "Get inquiry details", description = "Fetch a single inquiry by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Inquiry retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Inquiry not found",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ContactInquiryResponse> get(@PathVariable Long id) {
        log.info("Admin inquiry detail requested. inquiryId={}", id);
        return ResponseEntity.ok(contactInquiryService.findById(id));
    }

    @PutMapping("/api/admin/enquiries/{id}/status")
    @Operation(summary = "Update inquiry status", description = "Transition an inquiry to a new status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status updated successfully"),
        @ApiResponse(responseCode = "404", description = "Inquiry not found",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ContactInquiryResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request,
            Authentication authentication
    ) {
        String modifiedBy = authentication != null ? authentication.getName() : "unknown";
        log.info("Admin inquiry status update requested. inquiryId={}, status={}, modifiedBy={}", id, request.status(), modifiedBy);
        ContactInquiryResponse response = contactInquiryService.updateStatus(id, request.status(), modifiedBy);
        log.info("Admin inquiry status update completed. inquiryId={}, status={}", id, response.status());
        return ResponseEntity.ok(response);
    }
}
