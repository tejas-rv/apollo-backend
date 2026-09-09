package com.apollo.elevator.engineer.controller;

import com.apollo.elevator.common.api.ApiErrorResponse;
import com.apollo.elevator.engineer.model.dto.*;
import com.apollo.elevator.engineer.service.EngineerService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/engineer")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Engineer", description = "Engineer portal endpoints — restricted customer view + service reporting")
public class EngineerController {

    private final EngineerService engineerService;

    // =========================================================================
    // Dashboard
    // =========================================================================

    @GetMapping("/dashboard")
    @Operation(summary = "Engineer dashboard stats",
               description = "Returns: services today, this month, total submitted, upcoming services list, recent reports")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Dashboard data")})
    public ResponseEntity<Map<String, Object>> dashboard(Authentication auth) {
        Long engineerUserId = resolveUserId(auth);
        log.info("Engineer dashboard. engineerUserId={}", engineerUserId);
        return ResponseEntity.ok(engineerService.getEngineerDashboard(engineerUserId));
    }

    // =========================================================================
    // Customers (read-only, no AMC amounts)
    // =========================================================================

    @GetMapping("/customers")
    @Operation(summary = "List all customers (restricted view)",
               description = "Returns customer list without AMC financial data. Supports search via `query` param.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Customer page")})
    public ResponseEntity<Page<EngineerCustomerDto>> customers(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("customerName"));
        return ResponseEntity.ok(query != null && !query.isBlank()
                ? engineerService.searchCustomers(query, pr)
                : engineerService.getAllCustomers(pr));
    }

    @GetMapping("/customers/{customerId}")
    @Operation(summary = "Get single customer (restricted view)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer detail"),
            @ApiResponse(responseCode = "404", description = "Not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<EngineerCustomerDto> customer(
            @Parameter(description = "Customer database ID", required = true)
            @PathVariable Long customerId
    ) {
        log.info("Engineer customer detail. customerId={}", customerId);
        return ResponseEntity.ok(engineerService.getCustomerById(customerId));
    }

    // =========================================================================
    // Default checklist template
    // =========================================================================

    @GetMapping("/service-reports/checklist-template")
    @Operation(summary = "Get default service checklist questions",
               description = "Returns the standard list of yes/no and descriptive questions to prefill the report form.")
    public ResponseEntity<List<ServiceCheckItemDto>> checklistTemplate() {
        return ResponseEntity.ok(engineerService.getDefaultChecklist());
    }

    // =========================================================================
    // Service reports
    // =========================================================================

    @GetMapping("/service-reports")
    @Operation(summary = "My service reports", description = "Returns paginated list of reports submitted by the authenticated engineer.")
    public ResponseEntity<Page<ServiceReportResponse>> myReports(
            Authentication auth,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long engineerUserId = resolveUserId(auth);
        PageRequest pr = PageRequest.of(page, size);
        return ResponseEntity.ok(engineerService.getMyReports(engineerUserId, pr));
    }

    @GetMapping("/service-reports/{reportId}")
    @Operation(summary = "Get a single service report")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service report"),
            @ApiResponse(responseCode = "404", description = "Not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ServiceReportResponse> getReport(
            @PathVariable Long reportId
    ) {
        return ResponseEntity.ok(engineerService.getReportById(reportId));
    }

    @PostMapping("/service-reports")
    @Operation(
            summary = "Submit a service report",
            description = """
                    Submits the completed service checklist for a visit. The backend will:
                    1. Persist the report
                    2. Generate a PDF
                    3. Email it automatically to all admin users

                    The `checkItems` list must contain answers for all questions (use the
                    `/checklist-template` endpoint to get the default questions).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report submitted and PDF emailed to admin"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ServiceReportResponse> submitReport(
            Authentication auth,
            @Valid @RequestBody ServiceReportRequest request
    ) {
        Long engineerUserId = resolveUserId(auth);
        log.info("Submit service report. engineerUserId={}, customerId={}, amcContractId={}",
                engineerUserId, request.customerId(), request.amcContractId());
        ServiceReportResponse response = engineerService.submitReport(engineerUserId, request);
        log.info("Service report submitted. reportId={}, status={}", response.id(), response.status());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/service-reports/{reportId}/pdf")
    @Operation(summary = "Download service report PDF",
               description = "Generates and downloads the PDF for a previously submitted report.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF bytes",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "Report not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<byte[]> downloadReportPdf(@PathVariable Long reportId) {
        log.info("Download service report PDF. reportId={}", reportId);
        byte[] pdf = engineerService.generateReportPdf(reportId);
        String fileName = "ServiceReport-" + reportId + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    // -------------------------------------------------------------------------

    /** Resolves the authenticated user's database ID from the JWT principal name (username). */
    private Long resolveUserId(Authentication auth) {
        // The JwtAuthFilter sets the principal name = username; we look up the ID from the user store.
        // We inject the UserRepository via a helper that reads from the security context.
        // For now we store the user id in the JWT subject as the username; the service does the DB lookup.
        return engineerService.resolveEngineerUserId(auth.getName());
    }
}
