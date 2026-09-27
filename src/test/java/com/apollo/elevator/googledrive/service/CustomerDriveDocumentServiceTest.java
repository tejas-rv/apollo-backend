package com.apollo.elevator.googledrive.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.repository.CustomerRepository;
import com.apollo.elevator.documents.model.dto.BillPreviewResponse;
import com.apollo.elevator.documents.model.dto.BillRequest;
import com.apollo.elevator.documents.model.enums.DocumentType;
import com.apollo.elevator.documents.service.DocumentService;
import com.apollo.elevator.documents.service.PdfTemplateService;
import com.apollo.elevator.engineer.model.entity.ServiceReport;
import com.apollo.elevator.engineer.repository.ServiceReportRepository;
import com.apollo.elevator.engineer.service.EngineerService;
import com.apollo.elevator.googledrive.model.DriveDocumentCategory;
import com.apollo.elevator.googledrive.model.dto.CustomerDriveGenerationResponse;
import com.apollo.elevator.googledrive.model.dto.DriveFileMetadata;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerDriveDocumentServiceTest {

    private CustomerRepository customerRepository;
    private DocumentService documentService;
    private EngineerService engineerService;
    private ServiceReportRepository serviceReportRepository;
    private GoogleDriveService googleDriveService;
    private PdfTemplateService pdfTemplateService;
    private CustomerDriveDocumentService service;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        documentService = mock(DocumentService.class);
        engineerService = mock(EngineerService.class);
        serviceReportRepository = mock(ServiceReportRepository.class);
        googleDriveService = mock(GoogleDriveService.class);
        pdfTemplateService = mock(PdfTemplateService.class);
        when(pdfTemplateService.renderToPdf(any(), any())).thenReturn("sample-bytes".getBytes());
        service = new CustomerDriveDocumentService(
                customerRepository, documentService, engineerService, serviceReportRepository,
                googleDriveService, pdfTemplateService);

        Customer customer = new Customer();
        customer.setId(7L);
        customer.setCustomerCode("CUST-7");
        customer.setCustomerName("Test Customer");
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
    }

    @Test
    void throwsWhenCustomerDoesNotExist() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.generateAndUploadCustomerDocuments(99L));
    }

    @Test
    void generatesAndUploadsContractBillAndServiceHistory() {
        DocumentService.AmcContractPdfResult contractResult =
                new DocumentService.AmcContractPdfResult(
                        "contract-bytes".getBytes(), "CUST_apollo_amc_2026.pdf", LocalDate.of(2026, 8, 22));
        when(documentService.generateAmcContractPdfResult(7L)).thenReturn(contractResult);

        BillRequest billRequest = mock(BillRequest.class);
        BillPreviewResponse preview = new BillPreviewResponse(DocumentType.GST_BILL, billRequest);
        when(documentService.buildBillPreview(7L, DocumentType.GST_BILL)).thenReturn(preview);
        DocumentService.PdfResult billResult =
                new DocumentService.PdfResult(
                        "bill-bytes".getBytes(), "apollo_elevator_bill_INV1.pdf", LocalDate.of(2026, 9, 20));
        when(documentService.generateBillPdf(DocumentType.GST_BILL, billRequest)).thenReturn(billResult);

        ServiceReport report = ServiceReport.builder()
                .id(5L)
                .customerId(7L)
                .visitDate(LocalDate.of(2026, 9, 27))
                .build();
        when(serviceReportRepository.findByCustomerIdOrderByVisitDateDesc(7L)).thenReturn(List.of(report));
        when(engineerService.generateReportPdf(5L)).thenReturn("report-bytes".getBytes());

        DriveFileMetadata contractMetadata = metadata("contract-id", "CUST_apollo_amc_2026.pdf");
        DriveFileMetadata billMetadata = metadata("bill-id", "apollo_elevator_bill_INV1_27Sep2026.pdf");
        DriveFileMetadata serviceMetadata = metadata("service-id", "service_report_5_27Sep2026.pdf");

        when(googleDriveService.uploadGeneratedFile(
                eq(7L), eq(DriveDocumentCategory.AMC_CONTRACT), any(LocalDate.class),
                eq("CUST_apollo_amc_2026.pdf"), eq("application/pdf"), eq(contractResult.pdfBytes())))
                .thenReturn(contractMetadata);
        when(googleDriveService.uploadGeneratedFile(
                eq(7L), eq(DriveDocumentCategory.BILL), any(LocalDate.class),
                eq("apollo_elevator_bill_INV1.pdf"), eq("application/pdf"), eq(billResult.pdfBytes())))
                .thenReturn(billMetadata);
        when(googleDriveService.uploadGeneratedFile(
                eq(7L), eq(DriveDocumentCategory.SERVICE_HISTORY), eq(LocalDate.of(2026, 9, 27)),
                eq("service_report_5.pdf"), eq("application/pdf"), any(byte[].class)))
                .thenReturn(serviceMetadata);

        CustomerDriveGenerationResponse response = service.generateAndUploadCustomerDocuments(7L);

        assertEquals(7L, response.customerId());
        assertEquals("contract-id", response.contract().id());
        assertEquals("bill-id", response.bill().id());
        assertEquals(1, response.serviceHistory().size());
        assertEquals("service-id", response.serviceHistory().get(0).id());
    }

/*    @Test
    void skipsContractAndBillWhenNoDataExistsAndReturnsEmptyServiceHistory() {
        when(documentService.generateAmcContractPdfResult(7L))
                .thenThrow(new ResourceNotFoundException("No AMC contracts found for customer id: 7"));
        when(documentService.buildBillPreview(7L, DocumentType.GST_BILL))
                .thenThrow(new ResourceNotFoundException("No AMC contracts found for customer id: 7"));
        when(serviceReportRepository.findByCustomerIdOrderByVisitDateDesc(7L)).thenReturn(List.of());

        CustomerDriveGenerationResponse response = service.generateAndUploadCustomerDocuments(7L);

        assertNull(response.contract());
        assertNull(response.bill());
        assertEquals(0, response.serviceHistory().size());
        verify(googleDriveService, never()).uploadGeneratedFile(
                anyLong(), any(), any(), any(), any(), any());
    }*/

    private DriveFileMetadata metadata(String id, String name) {
        return new DriveFileMetadata(id, name, "application/pdf", 11L, null, null);
    }
}
