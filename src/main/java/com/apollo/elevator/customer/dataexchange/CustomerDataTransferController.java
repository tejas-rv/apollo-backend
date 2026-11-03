package com.apollo.elevator.customer.dataexchange;

import com.apollo.elevator.common.util.LogMaskingUtil;
import com.apollo.elevator.common.api.ApiErrorResponse;
import com.apollo.elevator.customer.dataexchange.dto.CustomerExportResponse;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportMode;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/customers/data")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Customer Data Import/Export", description = "Admin-only bulk customer import and export (Excel)")
public class CustomerDataTransferController {

    private final CustomerDataTransferService transferService;

    @PostMapping(value = "/import", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.ALL_VALUE})
    @Operation(
            summary = "Import customer data",
            description = "Imports customers, representatives, lifts, AMC contracts and service history from "
                    + "Apollo_Elevator_Customer_Data.xlsx. When fromGoogleDrive=true the file is read from the "
                    + "configured Google Drive folder; otherwise the uploaded 'file' part is used. Customers whose "
                    + "customerCode already exists are updated; rows with a blank customerCode are created with a "
                    + "generated APL-ELV-<initials>-<id> code (lifts matched by liftId/serialNumber, AMC contracts "
                    + "by amcId/contractNumber, service visits by visitId; blank cells keep existing values). "
                    + "mode=MERGE (default) adds new rows and deletes nothing. mode=OVERRIDE first saves a backup "
                    + "of the current data to Google Drive as <fileName>_<yyyyMMdd_HHmmss>_<username>.xlsx, then "
                    + "replaces the representatives and service visits of the customers/AMC contracts in the file "
                    + "(customers, lifts and AMC contracts are never deleted; COMPLETED visits are kept when the "
                    + "file was exported without them). Invalid customers are reported and not saved."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Import processed",
                    content = @Content(schema = @Schema(implementation = CustomerImportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or unreadable file",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "File not found in Google Drive",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Google Drive integration is disabled (also required for OVERRIDE)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CustomerImportResponse> importCustomers(
            @Parameter(description = "true = read from Google Drive, false = use the uploaded file")
            @RequestParam(defaultValue = "false") boolean fromGoogleDrive,
            @Parameter(description = "Customer data .xlsx file (required when fromGoogleDrive=false)")
            @RequestParam(value = "file", required = false) MultipartFile file,
            @Parameter(description = "MERGE = add/update only; OVERRIDE = back up to Drive, then replace representatives and visits")
            @RequestParam(defaultValue = "MERGE") CustomerImportMode mode,
            Authentication authentication
    ) {
        String username = authentication == null ? null : authentication.getName();
        log.info("Customer data import requested. fromGoogleDrive={}, mode={}, user={}, fileName={}",
                fromGoogleDrive,
                mode,
                LogMaskingUtil.mask("username", username),
                LogMaskingUtil.mask("fileName", file == null ? null : file.getOriginalFilename()));
        CustomerImportResponse response = fromGoogleDrive
                ? transferService.importFromGoogleDrive(mode, username)
                : transferService.importFromUpload(file, mode, username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/export")
    @Operation(
            summary = "Export customer data",
            description = "Exports all customer data to Apollo_Elevator_Customer_Data.xlsx. When toGoogleDrive=true "
                    + "the file is saved (overwritten) in the configured Google Drive folder and its metadata is "
                    + "returned; otherwise the file is downloaded."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Excel file download, or Drive upload result",
                    content = {
                            @Content(mediaType = CustomerWorkbookSchema.CONTENT_TYPE),
                            @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = CustomerExportResponse.class))
                    }),
            @ApiResponse(responseCode = "409", description = "Google Drive integration is disabled",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<?> exportCustomers(
            @Parameter(description = "true = save to Google Drive, false = download the file")
            @RequestParam(defaultValue = "false") boolean toGoogleDrive,
            @Parameter(description = "false = leave COMPLETED service visits out of the Service History sheet")
            @RequestParam(defaultValue = "true") boolean includeCompleted
    ) {
        log.info("Customer data export requested. toGoogleDrive={}, includeCompleted={}", toGoogleDrive, includeCompleted);
        if (toGoogleDrive) {
            return ResponseEntity.ok(transferService.exportToGoogleDrive(includeCompleted));
        }

        CustomerDataExportService.ExportedWorkbook workbook = transferService.exportForDownload(includeCompleted);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(CustomerWorkbookSchema.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(CustomerWorkbookSchema.FILE_NAME)
                        .build()
                        .toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentLength(workbook.content().length)
                .body(workbook.content());
    }
}
