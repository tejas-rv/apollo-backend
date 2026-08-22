package com.apollo.elevators.service;

import com.apollo.elevators.common.exception.ResourceNotFoundException;
import com.apollo.elevators.customer.AmcContract;
import com.apollo.elevators.customer.Customer;
import com.apollo.elevators.customer.Lift;
import com.apollo.elevators.dto.notification.ContractPdfRequest;
import com.apollo.elevators.enums.AmcStatus;
import com.apollo.elevators.notification.PdfTemplateService;
import com.apollo.elevators.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final CustomerRepository customerRepository;
    private final PdfTemplateService pdfTemplateService;

    /**
     * Generates the AMC contract PDF and file name in a single DB lookup.
     */
    @Transactional(readOnly = true)
    public AmcContractPdfResult generateAmcContractPdfResult(Long customerId) {
        log.info("Generating AMC contract PDF result. customerId={}", customerId);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        Lift lift = pickPrimaryLift(customer);
        AmcContract amc = pickBestAmcContract(lift, customerId);
        String fileName = customer.getCustomerCode() + "_apollo_amc_" + resolveAmcYear(amc) + ".pdf";

        log.info("Building PDF for contractNumber={}, fileName={}", amc.getContractNumber(), fileName);
        Map<String, Object> variables = buildTemplateVariables(customer, lift, amc);
        byte[] pdfBytes = pdfTemplateService.renderToPdf("pdf/amc-contract", variables);
        log.info("AMC contract PDF result ready. fileName={}, sizeBytes={}", fileName, pdfBytes.length);
        return new AmcContractPdfResult(pdfBytes, fileName);
    }

    public record AmcContractPdfResult(byte[] pdfBytes, String fileName) {}

    /**
     * Generates an AMC contract PDF for the given customer.
     * Picks the first lift and its most recent active AMC (falls back to latest by endDate).
     *
     * @param customerId DB primary key of the customer
     * @return PDF bytes
     */
    @Transactional(readOnly = true)
    public byte[] generateAmcContractPdf(Long customerId) {
        log.info("Generating AMC contract PDF. customerId={}", customerId);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        log.info("Customer fetched. customerCode={}, liftsCount={}", customer.getCustomerCode(), customer.getLifts().size());

        Lift lift = pickPrimaryLift(customer);
        AmcContract amc = pickBestAmcContract(lift, customerId);

        log.info("Selected lift and AMC for PDF. liftId={}, contractNumber={}, amcYear={}",
                lift.getId(), amc.getContractNumber(), resolveAmcYear(amc));

        Map<String, Object> variables = buildTemplateVariables(customer, lift, amc);
        return pdfTemplateService.renderToPdf("pdf/amc-contract", variables);
    }

    /**
     * Builds the PDF file name: {customerCode}_apollo_amc_{amcYear}.pdf
     */
    @Transactional(readOnly = true)
    public String buildPdfFileName(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
        Lift lift = pickPrimaryLift(customer);
        AmcContract amc = pickBestAmcContract(lift, customerId);
        String year = String.valueOf(resolveAmcYear(amc));
        return customer.getCustomerCode() + "_apollo_amc_" + year + ".pdf";
    }

    private Lift pickPrimaryLift(Customer customer) {
        List<Lift> lifts = customer.getLifts();
        if (lifts == null || lifts.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No lifts found for customer: " + customer.getCustomerCode());
        }
        return lifts.get(0);
    }

    private AmcContract pickBestAmcContract(Lift lift, Long customerId) {
        List<AmcContract> contracts = lift.getAmcContracts();
        if (contracts == null || contracts.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No AMC contracts found for customer id: " + customerId);
        }
        // Prefer ACTIVE, then fall back to the latest by endDate
        return contracts.stream()
                .filter(c -> c.getStatus() == AmcStatus.ACTIVE)
                .findFirst()
                .orElseGet(() -> contracts.stream()
                        .max(Comparator.comparing(
                                c -> c.getEndDate() != null ? c.getEndDate() : LocalDate.MIN))
                        .orElseThrow());
    }

    private int resolveAmcYear(AmcContract amc) {
        if (amc.getStartDate() != null) return amc.getStartDate().getYear();
        if (amc.getEndDate() != null) return amc.getEndDate().getYear();
        return LocalDate.now().getYear();
    }

    private Map<String, Object> buildTemplateVariables(Customer customer, Lift lift, AmcContract amc) {
        ContractPdfRequest.CustomerDetails customerDetails = new ContractPdfRequest.CustomerDetails(
                customer.getCustomerName(),
                customer.getAddress(),
                customer.getCity(),
                customer.getState(),
                customer.getPincode(),
                customer.getMobileNumber(),
                customer.getEmail(),
                customer.getCustomerCode()
        );

        int remaining = 0;
        if (amc.getTotalServices() != null && amc.getCompletedServices() != null) {
            remaining = Math.max(0, amc.getTotalServices() - amc.getCompletedServices());
        }

        ContractPdfRequest.ContractDetails contractDetails = new ContractPdfRequest.ContractDetails(
                amc.getContractNumber(),
                lift.getLiftType() != null ? lift.getLiftType().name() : null,
                lift.getDriveType() != null ? lift.getDriveType().name() : null,
                lift.getBrand(),
                lift.getLiftModel(),
                lift.getSerialNumber(),
                lift.getNumberOfFloors(),
                lift.getCapacityInPersons(),
                lift.getCapacityInKg(),
                lift.getInstallationType(),
                lift.getYearOfInstallation(),
                amc.getAmcType(),
                amc.getStatus() != null ? amc.getStatus().name() : null,
                formatDate(amc.getStartDate()),
                formatDate(amc.getEndDate()),
                amc.getAmcAmount() != null ? amc.getAmcAmount().doubleValue() : null,
                amc.getPaymentFrequency(),
                formatDate(amc.getNextPaymentDate()),
                formatDate(amc.getNextServiceDate()),
                amc.getTotalServices(),
                amc.getCompletedServices(),
                remaining,
                normalizeTermsAndConditions(amc.getTermsAndConditions()),
                amc.getRemarks()
        );

        Map<String, Object> vars = new HashMap<>();
        vars.put("customer", customerDetails);
        vars.put("contract", contractDetails);
        vars.put("serviceHistory", Collections.emptyList());
        vars.put("generatedDate", LocalDate.now().format(DISPLAY_DATE_FMT));
        return vars;
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : null;
    }

    private String normalizeTermsAndConditions(String termsAndConditions) {
        if (termsAndConditions == null) {
            return null;
        }
        String normalized = termsAndConditions.trim();
        if (normalized.isEmpty()
                || normalized.equalsIgnoreCase("na")
                || normalized.equalsIgnoreCase("n/a")
                || normalized.equalsIgnoreCase("null")) {
            return null;
        }
        return normalized;
    }
}
