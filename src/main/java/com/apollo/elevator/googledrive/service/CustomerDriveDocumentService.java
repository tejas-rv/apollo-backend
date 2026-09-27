package com.apollo.elevator.googledrive.service;

import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.repository.CustomerRepository;
import com.apollo.elevator.documents.model.dto.BillPreviewResponse;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Orchestrates generation of a customer's AMC contract, bill, sample work order, sample
 * quotation, and service-history PDFs from existing document-generation services (or, for
 * work orders/quotations which have no dedicated generation module yet, a placeholder sample
 * document), and uploads each generated document to its category folder in Google Drive — so
 * callers only need to supply a {@code customerId}, without uploading any files themselves.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "google.drive", name = "enabled", havingValue = "true")
public class CustomerDriveDocumentService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final CustomerRepository customerRepository;
    private final DocumentService documentService;
    private final EngineerService engineerService;
    private final ServiceReportRepository serviceReportRepository;
    private final GoogleDriveService googleDriveService;
    private final PdfTemplateService pdfTemplateService;

    public CustomerDriveGenerationResponse generateAndUploadCustomerDocuments(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        DriveFileMetadata contract = generateAndUploadContract(customerId);
        DriveFileMetadata bill = generateAndUploadBill(customerId);
        DriveFileMetadata workOrder = generateAndUploadWorkOrderSample(customer);
        DriveFileMetadata quotation = generateAndUploadQuotationSample(customer);
        List<DriveFileMetadata> serviceHistory = generateAndUploadServiceHistory(customerId);

        log.info(
                "Generated and uploaded customer documents to Google Drive. customerId={}, "
                        + "contractUploaded={}, billUploaded={}, workOrderUploaded={}, "
                        + "quotationUploaded={}, serviceHistoryCount={}",
                customerId, contract != null, bill != null, workOrder != null, quotation != null,
                serviceHistory.size()
        );
        return new CustomerDriveGenerationResponse(
                customerId, contract, bill, workOrder, quotation, serviceHistory);
    }

    private DriveFileMetadata generateAndUploadContract(Long customerId) {
        DocumentService.AmcContractPdfResult result;
        try {
            result = documentService.generateAmcContractPdfResult(customerId);
        } catch (ResourceNotFoundException | IllegalArgumentException exception) {
            log.warn("Skipping AMC contract generation for customerId={}: {}", customerId, exception.getMessage());
            return null;
        }
        return googleDriveService.uploadGeneratedFile(
                customerId,
                DriveDocumentCategory.AMC_CONTRACT,
                result.documentDate(),
                result.fileName(),
                PDF_CONTENT_TYPE,
                result.pdfBytes()
        );
    }

    private DriveFileMetadata generateAndUploadBill(Long customerId) {
        DocumentService.PdfResult result;
        try {
            BillPreviewResponse preview = documentService.buildBillPreview(customerId, DocumentType.GST_BILL);
            result = documentService.generateBillPdf(DocumentType.GST_BILL, preview.billRequest());
        } catch (ResourceNotFoundException | IllegalArgumentException exception) {
            log.warn("Skipping bill generation for customerId={}: {}", customerId, exception.getMessage());
            return null;
        }
        return googleDriveService.uploadGeneratedFile(
                customerId,
                DriveDocumentCategory.BILL,
                result.documentDate(),
                result.fileName(),
                PDF_CONTENT_TYPE,
                result.pdfBytes()
        );
    }

    /**
     * Generates a placeholder work order PDF and uploads it to the customer's Work Orders
     * folder. No dedicated work-order generation module exists yet, so this produces a
     * clearly-labelled sample document that can be replaced once one is built.
     */
    private DriveFileMetadata generateAndUploadWorkOrderSample(Customer customer) {
        LocalDate today = LocalDate.now();
        String workOrderNumber = "WO-" + customer.getId() + "-" + today.format(DateTimeFormatter.BASIC_ISO_DATE);
        Map<String, Object> variables = new HashMap<>();
        variables.put("workOrderNumber", workOrderNumber);
        variables.put("generatedDate", today.format(DISPLAY_DATE_FORMAT));
        variables.put("customerName", customer.getCustomerName());
        variables.put("customerCode", customer.getCustomerCode());
        variables.put("customerAddress", customer.getAddress());

        byte[] pdfBytes = pdfTemplateService.renderToPdf("pdf/work-order-sample", variables);
        String fileName = customer.getCustomerCode() + "_work_order.pdf";

        return googleDriveService.uploadGeneratedFile(
                customer.getId(),
                DriveDocumentCategory.WORK_ORDER,
                today,
                fileName,
                PDF_CONTENT_TYPE,
                pdfBytes
        );
    }

    /**
     * Generates a placeholder quotation PDF and uploads it to the customer's Quotations
     * folder. No dedicated quotation generation module exists yet, so this produces a
     * clearly-labelled sample document that can be replaced once one is built.
     */
    private DriveFileMetadata generateAndUploadQuotationSample(Customer customer) {
        LocalDate today = LocalDate.now();
        String quotationNumber = "QT-" + customer.getId() + "-" + today.format(DateTimeFormatter.BASIC_ISO_DATE);
        Map<String, Object> variables = new HashMap<>();
        variables.put("quotationNumber", quotationNumber);
        variables.put("generatedDate", today.format(DISPLAY_DATE_FORMAT));
        variables.put("customerName", customer.getCustomerName());
        variables.put("customerCode", customer.getCustomerCode());
        variables.put("customerAddress", customer.getAddress());

        byte[] pdfBytes = pdfTemplateService.renderToPdf("pdf/quotation-sample", variables);
        String fileName = customer.getCustomerCode() + "_quotation.pdf";

        return googleDriveService.uploadGeneratedFile(
                customer.getId(),
                DriveDocumentCategory.QUOTATION,
                today,
                fileName,
                PDF_CONTENT_TYPE,
                pdfBytes
        );
    }

    private List<DriveFileMetadata> generateAndUploadServiceHistory(Long customerId) {
        List<ServiceReport> reports = serviceReportRepository.findByCustomerIdOrderByVisitDateDesc(customerId);
        List<DriveFileMetadata> uploaded = new ArrayList<>();
        for (ServiceReport report : reports) {
            byte[] pdfBytes;
            try {
                pdfBytes = engineerService.generateReportPdf(report.getId());
            } catch (ResourceNotFoundException | IllegalArgumentException exception) {
                log.warn(
                        "Skipping service history generation. reportId={}, customerId={}: {}",
                        report.getId(), customerId, exception.getMessage()
                );
                continue;
            }
            String fileName = "service_report_" + report.getId() + ".pdf";
            uploaded.add(googleDriveService.uploadGeneratedFile(
                    customerId,
                    DriveDocumentCategory.SERVICE_HISTORY,
                    report.getVisitDate(),
                    fileName,
                    PDF_CONTENT_TYPE,
                    pdfBytes
            ));
        }
        return uploaded;
    }
}

