package com.apollo.elevator.googledrive.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apollo.elevator.googledrive.model.dto.CustomerDriveGenerationResponse;
import com.apollo.elevator.googledrive.model.dto.DriveFileMetadata;
import com.apollo.elevator.googledrive.service.CustomerDriveDocumentService;
import com.apollo.elevator.googledrive.service.GoogleDriveService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GoogleDriveControllerTest {

    private GoogleDriveService googleDriveService;
    private CustomerDriveDocumentService customerDriveDocumentService;
    private GoogleDriveController controller;

    @BeforeEach
    void setUp() {
        googleDriveService = mock(GoogleDriveService.class);
        customerDriveDocumentService = mock(CustomerDriveDocumentService.class);
        controller = new GoogleDriveController(googleDriveService, customerDriveDocumentService);
    }

    @Test
    void generatesAndUploadsCustomerDocumentsFromCustomerIdAlone() {
        DriveFileMetadata contractMetadata = metadata("contract-id", "contract_2026.pdf");
        DriveFileMetadata billMetadata = metadata("bill-id", "bill_27Sep2026.pdf");
        DriveFileMetadata serviceMetadata = metadata("service-id", "service_report_5_27Sep2026.pdf");
        CustomerDriveGenerationResponse serviceResponse = new CustomerDriveGenerationResponse(
                7L, contractMetadata, billMetadata, null, null, List.of(serviceMetadata));

        when(customerDriveDocumentService.generateAndUploadCustomerDocuments(7L))
                .thenReturn(serviceResponse);

        ResponseEntity<CustomerDriveGenerationResponse> response =
                controller.generateAndUploadCustomerDocuments(7L);

        assertEquals(7L, response.getBody().customerId());
        assertEquals("contract-id", response.getBody().contract().id());
        assertEquals("bill-id", response.getBody().bill().id());
        assertEquals(1, response.getBody().serviceHistory().size());
        assertEquals("service-id", response.getBody().serviceHistory().get(0).id());
        verify(customerDriveDocumentService).generateAndUploadCustomerDocuments(7L);
    }

    @Test
    void returnsPartialResponseWhenSomeDocumentsCannotBeGenerated() {
        DriveFileMetadata billMetadata = metadata("bill-id", "bill_27Sep2026.pdf");
        CustomerDriveGenerationResponse serviceResponse =
                new CustomerDriveGenerationResponse(7L, null, billMetadata, null, null, List.of());

        when(customerDriveDocumentService.generateAndUploadCustomerDocuments(7L))
                .thenReturn(serviceResponse);

        CustomerDriveGenerationResponse response =
                controller.generateAndUploadCustomerDocuments(7L).getBody();

        assertNull(response.contract());
        assertEquals("bill-id", response.bill().id());
        assertEquals(0, response.serviceHistory().size());
    }

    private DriveFileMetadata metadata(String id, String name) {
        return new DriveFileMetadata(id, name, "application/pdf", 11L, null, null);
    }
}
