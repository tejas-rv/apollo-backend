package com.apollo.elevator.customer.dataexchange;

import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.customer.dataexchange.dto.CustomerDataSource;
import com.apollo.elevator.customer.dataexchange.dto.CustomerExportResponse;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportMode;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportResponse;
import com.apollo.elevator.googledrive.model.dto.DriveDownload;
import com.apollo.elevator.googledrive.model.dto.DriveFileMetadata;
import com.apollo.elevator.googledrive.service.GoogleDriveService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Resolves where customer data is imported from / exported to: Google Drive (the
 * {@value CustomerWorkbookSchema#FILE_NAME} file in the configured root folder) or a direct
 * file upload/download.
 */
@Service
@Slf4j
public class CustomerDataTransferService {

    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final ZoneId BACKUP_ZONE = ZoneId.of("Asia/Kolkata");

    private final CustomerDataImportService importService;
    private final CustomerDataExportService exportService;
    private final ObjectProvider<GoogleDriveService> googleDriveService;
    private final Clock clock;

    @Autowired
    public CustomerDataTransferService(CustomerDataImportService importService,
                                       CustomerDataExportService exportService,
                                       ObjectProvider<GoogleDriveService> googleDriveService) {
        this(importService, exportService, googleDriveService, Clock.system(BACKUP_ZONE));
    }

    CustomerDataTransferService(CustomerDataImportService importService,
                                CustomerDataExportService exportService,
                                ObjectProvider<GoogleDriveService> googleDriveService,
                                Clock clock) {
        this.importService = importService;
        this.exportService = exportService;
        this.googleDriveService = googleDriveService;
        this.clock = clock;
    }

    public CustomerImportResponse importFromUpload(MultipartFile file) {
        return importFromUpload(file, CustomerImportMode.MERGE, null);
    }

    public CustomerImportResponse importFromUpload(MultipartFile file, CustomerImportMode mode, String username) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please upload the customer data file (" + CustomerWorkbookSchema.FILE_NAME + ")");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Only Excel .xlsx files are supported");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read the uploaded file", exception);
        }
        return runImport(content, fileName, CustomerDataSource.UPLOAD, mode, username);
    }

    public CustomerImportResponse importFromGoogleDrive() {
        return importFromGoogleDrive(CustomerImportMode.MERGE, null);
    }

    public CustomerImportResponse importFromGoogleDrive(CustomerImportMode mode, String username) {
        GoogleDriveService drive = requireGoogleDrive();
        DriveFileMetadata file = drive.findFileInRootFolder(CustomerWorkbookSchema.FILE_NAME)
                .or(() -> drive.findFileInRootFolder(CustomerWorkbookSchema.FILE_BASE_NAME))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "'" + CustomerWorkbookSchema.FILE_NAME
                                + "' was not found in the configured Google Drive folder"));

        log.info("Importing customer data from Google Drive. fileId={}, fileName={}",
                file.id(), com.apollo.elevator.common.util.LogMaskingUtil.mask("fileName", file.name()));
        DriveDownload download = drive.downloadFile(file.id());
        byte[] bytes;
        try (InputStream content = download.content()) {
            bytes = content.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read the file from Google Drive", exception);
        }
        return runImport(bytes, file.name(), CustomerDataSource.GOOGLE_DRIVE, mode, username);
    }

    /**
     * MERGE imports directly. OVERRIDE first saves a full export of the current data to Google Drive
     * as {@code <fileName>_<yyyyMMdd_HHmmss>_<username>.xlsx}; the import is aborted if the backup fails.
     */
    private CustomerImportResponse runImport(byte[] content, String fileName, CustomerDataSource source,
                                             CustomerImportMode mode, String username) {
        CustomerImportMode effectiveMode = mode == null ? CustomerImportMode.MERGE : mode;
        if (effectiveMode == CustomerImportMode.MERGE) {
            return importService.importWorkbook(content, fileName, source, CustomerImportMode.MERGE);
        }

        GoogleDriveService drive = requireGoogleDrive(
                "Override import needs Google Drive to store the backup, but the Google Drive integration is disabled.");
        String backupName = backupFileName(fileName, username, LocalDateTime.now(clock));
        CustomerDataExportService.ExportedWorkbook snapshot = exportService.exportWorkbook(true);
        DriveFileMetadata backup = drive.uploadFileToRootFolder(
                backupName, CustomerWorkbookSchema.CONTENT_TYPE, snapshot.content());
        log.info("Backup saved before override import. fileId={}, fileName={}, customerCount={}",
                backup.id(),
                com.apollo.elevator.common.util.LogMaskingUtil.mask("fileName", backup.name()),
                snapshot.customerCount());
        return importService.importWorkbook(content, fileName, source, CustomerImportMode.OVERRIDE)
                .withBackup(backup);
    }

    static String backupFileName(String importedFileName, String username, LocalDateTime timestamp) {
        String base = importedFileName == null ? "" : importedFileName.trim();
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        base = base.substring(slash + 1);
        if (base.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            base = base.substring(0, base.length() - ".xlsx".length());
        }
        base = sanitize(base);
        if (base.isEmpty()) {
            base = CustomerWorkbookSchema.FILE_BASE_NAME;
        }
        String user = sanitize(username == null ? "" : username);
        if (user.isEmpty()) {
            user = "unknown";
        }
        return base + "_" + BACKUP_TIMESTAMP.format(timestamp) + "_" + user + ".xlsx";
    }

    private static String sanitize(String value) {
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public CustomerDataExportService.ExportedWorkbook exportForDownload(boolean includeCompleted) {
        return exportService.exportWorkbook(includeCompleted);
    }

    public CustomerExportResponse exportToGoogleDrive(boolean includeCompleted) {
        GoogleDriveService drive = requireGoogleDrive();
        CustomerDataExportService.ExportedWorkbook workbook = exportService.exportWorkbook(includeCompleted);
        DriveFileMetadata uploaded = drive.uploadFileToRootFolder(
                CustomerWorkbookSchema.FILE_NAME,
                CustomerWorkbookSchema.CONTENT_TYPE,
                workbook.content()
        );
        log.info("Customer data exported to Google Drive. fileId={}, customerCount={}",
                uploaded.id(), workbook.customerCount());
        return new CustomerExportResponse(CustomerWorkbookSchema.FILE_NAME, workbook.customerCount(), uploaded);
    }

    private GoogleDriveService requireGoogleDrive() {
        return requireGoogleDrive(
                "Google Drive integration is disabled. Use the manual upload/download option instead.");
    }

    private GoogleDriveService requireGoogleDrive(String disabledMessage) {
        GoogleDriveService drive = googleDriveService.getIfAvailable();
        if (drive == null) {
            throw new IllegalStateException(disabledMessage);
        }
        return drive;
    }
}
