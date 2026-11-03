package com.apollo.elevator.customer.dataexchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.customer.dataexchange.dto.CustomerDataSource;
import com.apollo.elevator.customer.dataexchange.dto.CustomerExportResponse;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportMode;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportResponse;
import com.apollo.elevator.googledrive.model.dto.DriveFileMetadata;
import com.apollo.elevator.googledrive.service.GoogleDriveService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

class CustomerDataTransferServiceTest {

    private CustomerDataImportService importService;
    private CustomerDataExportService exportService;
    private GoogleDriveService driveService;
    private ObjectProvider<GoogleDriveService> driveProvider;
    private CustomerDataTransferService transferService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        importService = mock(CustomerDataImportService.class);
        exportService = mock(CustomerDataExportService.class);
        driveService = mock(GoogleDriveService.class);
        driveProvider = mock(ObjectProvider.class);
        transferService = new CustomerDataTransferService(importService, exportService, driveProvider);
    }

    @Test
    void uploadRequiresXlsxFile() {
        MockMultipartFile csv = new MockMultipartFile("file", "data.csv", "text/csv", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> transferService.importFromUpload(csv));
        assertThrows(IllegalArgumentException.class, () -> transferService.importFromUpload(null));
        verifyNoInteractions(importService);
    }

    @Test
    void uploadDelegatesToImportService() {
        byte[] bytes = {1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("file", "Apollo_Elevator_Customer_Data.xlsx",
                CustomerWorkbookSchema.CONTENT_TYPE, bytes);
        transferService.importFromUpload(file);
        verify(importService).importWorkbook(bytes, "Apollo_Elevator_Customer_Data.xlsx", CustomerDataSource.UPLOAD,
                CustomerImportMode.MERGE);
        verifyNoInteractions(exportService);
    }

    @Test
    void overrideImportSavesBackupToDriveBeforeImporting() {
        byte[] bytes = {1, 2, 3};
        byte[] snapshot = {7, 7};
        Clock clock = Clock.fixed(Instant.parse("2025-03-04T10:15:30Z"), ZoneId.of("Asia/Kolkata"));
        transferService = new CustomerDataTransferService(importService, exportService, driveProvider, clock);
        when(driveProvider.getIfAvailable()).thenReturn(driveService);
        when(exportService.exportWorkbook(true)).thenReturn(new CustomerDataExportService.ExportedWorkbook(snapshot, 2));
        String backupName = "Apollo_Elevator_Customer_Data_20250304_154530_admin_user.xlsx";
        DriveFileMetadata backup = new DriveFileMetadata("backup-1", backupName,
                CustomerWorkbookSchema.CONTENT_TYPE, 2L, Instant.now(), Instant.now());
        when(driveService.uploadFileToRootFolder(any(), any(), any())).thenReturn(backup);
        CustomerImportResponse imported = new CustomerImportResponse("Apollo_Elevator_Customer_Data.xlsx",
                CustomerDataSource.UPLOAD, CustomerImportMode.OVERRIDE, 0, 0, 0, 0, 0, List.of(), List.of(), null);
        when(importService.importWorkbook(any(), any(), any(), eq(CustomerImportMode.OVERRIDE))).thenReturn(imported);
        MockMultipartFile file = new MockMultipartFile("file", "Apollo_Elevator_Customer_Data.xlsx",
                CustomerWorkbookSchema.CONTENT_TYPE, bytes);

        CustomerImportResponse response = transferService.importFromUpload(file, CustomerImportMode.OVERRIDE, "admin user");

        assertEquals(backup, response.backupFile());
        InOrder order = inOrder(driveService, importService);
        order.verify(driveService).uploadFileToRootFolder(eq(backupName), eq(CustomerWorkbookSchema.CONTENT_TYPE), eq(snapshot));
        order.verify(importService).importWorkbook(bytes, "Apollo_Elevator_Customer_Data.xlsx",
                CustomerDataSource.UPLOAD, CustomerImportMode.OVERRIDE);
    }

    @Test
    void overrideImportIsRejectedWhenDriveIsDisabled() {
        when(driveProvider.getIfAvailable()).thenReturn(null);
        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx", CustomerWorkbookSchema.CONTENT_TYPE, new byte[]{1});
        assertThrows(IllegalStateException.class,
                () -> transferService.importFromUpload(file, CustomerImportMode.OVERRIDE, "admin"));
        verifyNoInteractions(importService, exportService);
    }

    @Test
    void backupFileNameStripsExtensionAndSanitizes() {
        LocalDateTime time = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        assertEquals("My_Data_20250102_030405_ops_admin.xlsx",
                CustomerDataTransferService.backupFileName("C:\\tmp\\My Data.XLSX", "ops/admin", time));
        assertEquals("Apollo_Elevator_Customer_Data_20250102_030405_unknown.xlsx",
                CustomerDataTransferService.backupFileName(null, null, time));
    }

    @Test
    void driveOperationsFailWhenDriveIsDisabled() {
        when(driveProvider.getIfAvailable()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> transferService.importFromGoogleDrive());
        assertThrows(IllegalStateException.class, () -> transferService.exportToGoogleDrive(true));
    }

    @Test
    void driveImportFailsWhenFileIsMissing() {
        when(driveProvider.getIfAvailable()).thenReturn(driveService);
        when(driveService.findFileInRootFolder(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> transferService.importFromGoogleDrive());
    }

    @Test
    void driveExportUploadsWorkbookWithFixedName() {
        byte[] bytes = {9};
        when(driveProvider.getIfAvailable()).thenReturn(driveService);
        when(exportService.exportWorkbook(true)).thenReturn(new CustomerDataExportService.ExportedWorkbook(bytes, 3));
        DriveFileMetadata uploaded = new DriveFileMetadata("file-1", CustomerWorkbookSchema.FILE_NAME,
                CustomerWorkbookSchema.CONTENT_TYPE, 1L, Instant.now(), Instant.now());
        when(driveService.uploadFileToRootFolder(any(), any(), any())).thenReturn(uploaded);

        CustomerExportResponse response = transferService.exportToGoogleDrive(true);

        assertEquals(uploaded, response.driveFile());
        assertEquals(CustomerWorkbookSchema.FILE_NAME, response.fileName());
        assertEquals(3, response.customerCount());
        verify(driveService).uploadFileToRootFolder(eq("Apollo_Elevator_Customer_Data.xlsx"),
                eq(CustomerWorkbookSchema.CONTENT_TYPE), eq(bytes));
    }
}
