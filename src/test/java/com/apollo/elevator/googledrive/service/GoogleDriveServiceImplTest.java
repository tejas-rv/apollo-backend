package com.apollo.elevator.googledrive.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apollo.elevator.customer.repository.CustomerRepository;
import com.apollo.elevator.googledrive.config.GoogleDriveProperties;
import com.apollo.elevator.googledrive.model.DriveDocumentCategory;
import com.apollo.elevator.googledrive.model.dto.DriveFileMetadata;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoogleDriveServiceImplTest {

    private Drive drive;
    private Drive.Files files;
    private Drive.Files.List listRequest;
    private GoogleDriveServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        drive = mock(Drive.class);
        files = mock(Drive.Files.class);
        listRequest = mock(Drive.Files.List.class);

        GoogleDriveProperties properties = new GoogleDriveProperties();
        properties.setFolderId("configured-folder");
        service = new GoogleDriveServiceImpl(
                drive,
                properties,
                mock(CustomerRepository.class)
        );

        when(drive.files()).thenReturn(files);
        when(files.list()).thenReturn(listRequest);
        when(listRequest.setQ(contains("'configured-folder' in parents")))
                .thenReturn(listRequest);
        when(listRequest.setSpaces("drive")).thenReturn(listRequest);
        when(listRequest.setCorpora("allDrives")).thenReturn(listRequest);
        when(listRequest.setIncludeItemsFromAllDrives(true)).thenReturn(listRequest);
        when(listRequest.setSupportsAllDrives(true)).thenReturn(listRequest);
        when(listRequest.setPageSize(1000)).thenReturn(listRequest);
        when(listRequest.setPageToken(null)).thenReturn(listRequest);
        when(listRequest.setFields(contains("nextPageToken"))).thenReturn(listRequest);
    }

    @Test
    void listFilesRestrictsQueryToConfiguredFolderAndExcludesTrashedItems() throws IOException {
        com.google.api.services.drive.model.File driveFile =
                new com.google.api.services.drive.model.File()
                        .setId("file-1")
                        .setName("bill_27Sep2026.pdf")
                        .setMimeType("application/pdf")
                        .setSize(128L);
        when(listRequest.execute()).thenReturn(new FileList().setFiles(List.of(driveFile)));

        List<DriveFileMetadata> result = service.listFiles();

        assertEquals(1, result.size());
        assertEquals("file-1", result.get(0).id());
        verify(listRequest).setQ(
                "'configured-folder' in parents and trashed = false");
    }

    @Test
    void appliesYearSuffixToAmcContractFilename() {
        String result = service.buildStoredFileName(
                "annual-contract.pdf",
                DriveDocumentCategory.AMC_CONTRACT,
                LocalDate.of(2026, 9, 27)
        );

        assertEquals("annual-contract_2026.pdf", result);
    }

    @Test
    void doesNotDuplicateSuffixWhenGeneratedFilenameAlreadyEndsWithIt() {
        String result = service.buildStoredFileName(
                "TEST-001_apollo_amc_2026.pdf",
                DriveDocumentCategory.AMC_CONTRACT,
                LocalDate.of(2026, 9, 27)
        );

        assertEquals("TEST-001_apollo_amc_2026.pdf", result);
    }

    @Test
    void appliesDateSuffixToBillAndServiceHistoryFilenames() {
        LocalDate documentDate = LocalDate.of(2026, 9, 27);

        assertEquals(
                "invoice_27Sep2026.pdf",
                service.buildStoredFileName(
                        "invoice.pdf", DriveDocumentCategory.BILL, documentDate)
        );
        assertEquals(
                "service-report_27Sep2026.pdf",
                service.buildStoredFileName(
                        "service-report.pdf",
                        DriveDocumentCategory.SERVICE_HISTORY,
                        documentDate
                )
        );
    }
}
