package com.apollo.elevator.googledrive.controller;

import com.apollo.elevator.googledrive.model.DriveDocumentCategory;
import com.apollo.elevator.googledrive.model.dto.CustomerDriveGenerationResponse;
import com.apollo.elevator.googledrive.model.dto.DriveDownload;
import com.apollo.elevator.googledrive.model.dto.DriveFileMetadata;
import com.apollo.elevator.googledrive.service.CustomerDriveDocumentService;
import com.apollo.elevator.googledrive.service.GoogleDriveService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/google-drive")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "google.drive", name = "enabled", havingValue = "true")
public class GoogleDriveController {

    private final GoogleDriveService googleDriveService;
    private final CustomerDriveDocumentService customerDriveDocumentService;

    /**
     * Generates the AMC contract, GST bill, and service-history PDFs for the given customer
     * from existing document-generation services, and uploads each to its category folder
     * in Google Drive. No files need to be supplied by the caller.
     */
    @PostMapping("/customer-documents/{customerId}")
    public ResponseEntity<CustomerDriveGenerationResponse> generateAndUploadCustomerDocuments(
            @PathVariable Long customerId
    ) {
        log.info("Google Drive customer document generation requested. customerId={}", customerId);
        CustomerDriveGenerationResponse response =
                customerDriveDocumentService.generateAndUploadCustomerDocuments(customerId);
        log.info(
                "Google Drive customer document generation completed. customerId={}, contractUploaded={}, "
                        + "billUploaded={}, serviceHistoryCount={}",
                customerId,
                response.contract() != null,
                response.bill() != null,
                response.serviceHistory().size()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/customer-documents/{customerId}/lifts/{liftId}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DriveFileMetadata> uploadLiftImage(
            @PathVariable Long customerId,
            @PathVariable Long liftId,
            @RequestParam("file") MultipartFile file
    ) {
        log.info(
                "Google Drive lift image upload requested. customerId={}, liftId={}, originalFilename={}, sizeBytes={}",
                customerId, liftId, file.getOriginalFilename(), file.getSize()
        );
        DriveFileMetadata uploaded = googleDriveService.uploadLiftImage(customerId, liftId, file);
        return ResponseEntity.ok(uploaded);
    }

    @GetMapping("/customer-documents/{customerId}/lifts/{liftId}/images")
    public ResponseEntity<List<DriveFileMetadata>> listLiftImages(
            @PathVariable Long customerId,
            @PathVariable Long liftId
    ) {
        return ResponseEntity.ok(googleDriveService.listLiftImages(customerId, liftId));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DriveFileMetadata> upload(
            @RequestParam Long customerId,
            @RequestParam DriveDocumentCategory category,
            @RequestParam(required = false) LocalDate documentDate,
            @RequestParam("file") MultipartFile file
    ) {
        log.info(
                "Google Drive upload requested. customerId={}, category={}, originalFilename={}, sizeBytes={}",
                customerId, category, file.getOriginalFilename(), file.getSize()
        );
        DriveFileMetadata uploaded = googleDriveService.uploadFile(
                customerId, category, documentDate, file);
        return ResponseEntity.ok(uploaded);
    }

    @GetMapping("/files")
    public ResponseEntity<List<DriveFileMetadata>> listFiles(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) DriveDocumentCategory category
    ) {
        if (customerId == null && category == null) {
            return ResponseEntity.ok(googleDriveService.listFiles());
        }
        if (customerId == null || category == null) {
            throw new IllegalArgumentException(
                    "customerId and category must be supplied together");
        }
        return ResponseEntity.ok(googleDriveService.listCustomerFiles(customerId, category));
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<DriveFileMetadata> getFileMetadata(@PathVariable String fileId) {
        return ResponseEntity.ok(googleDriveService.getFileMetadata(fileId));
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String fileId) {
        DriveDownload download = googleDriveService.downloadFile(fileId);
        DriveFileMetadata metadata = download.metadata();
        MediaType mediaType = parseMediaType(metadata.mimeType());

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(metadata.name(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .contentType(mediaType);
        if (metadata.size() != null) {
            response.contentLength(metadata.size());
        }
        return response.body(new InputStreamResource(download.content()));
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> delete(@PathVariable String fileId) {
        googleDriveService.deleteFile(fileId);
        return ResponseEntity.noContent().build();
    }

    private MediaType parseMediaType(String mimeType) {
        if (!org.springframework.util.StringUtils.hasText(mimeType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
