package com.apollo.elevators.documents.service;

import com.apollo.elevators.common.exception.ResourceNotFoundException;
import com.apollo.elevators.customer.model.entity.AmcContract;
import com.apollo.elevators.customer.model.entity.Customer;
import com.apollo.elevators.customer.model.entity.Lift;
import com.apollo.elevators.customer.model.entity.ServiceHistory;
import com.apollo.elevators.documents.model.dto.ContractPdfRequest;
import com.apollo.elevators.customer.model.enums.AmcStatus;
import com.apollo.elevators.documents.service.PdfTemplateService;
import com.apollo.elevators.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
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
    private static final java.math.BigDecimal GST_PERCENTAGE = java.math.BigDecimal.valueOf(18);

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

        Double gstPercentage = null;
        Double gstAmount = null;
        Double totalAmount = null;
        if (amc.getAmcAmount() != null) {
            java.math.BigDecimal base = amc.getAmcAmount();
            java.math.BigDecimal gst = base.multiply(GST_PERCENTAGE)
                    .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            gstPercentage = GST_PERCENTAGE.doubleValue();
            gstAmount = gst.doubleValue();
            totalAmount = base.add(gst).doubleValue();
        }

        ContractPdfRequest.ContractDetails contractDetails = new ContractPdfRequest.ContractDetails(
                amc.getContractNumber(),
                lift.getLiftType() != null ? lift.getLiftType().name() : null,
                lift.getDriveType() != null ? lift.getDriveType().name() : null,
                lift.getDoorType() != null ? lift.getDoorType().name() : null,
                lift.getBrand(),
                lift.getLiftModel(),
                lift.getSerialNumber(),
                lift.getNumberOfFloors(),
                lift.getCapacityInPersons(),
                lift.getCapacityInKg(),
                lift.getInstallationType(),
                lift.getYearOfInstallation(),
                lift.getMachineType(),
                lift.getMachineName(),
                toDouble(lift.getKw()),
                toDouble(lift.getAmps()),
                toDouble(lift.getSpeed()),
                toDouble(lift.getVoltage()),
                toDouble(lift.getFrequency()),
                lift.getOsgType(),
                toDouble(lift.getRatedSpeed()),
                toDouble(lift.getTrippingSpeed()),
                lift.getIsUpsPresent(),
                lift.getUpsType(),
                toDouble(lift.getKva()),
                amc.getContractType() != null ? amc.getContractType().name() : null,
                amc.getStatus() != null ? amc.getStatus().name() : null,
                formatDate(amc.getStartDate()),
                formatDate(amc.getEndDate()),
                toDouble(amc.getAmcAmount()),
                gstPercentage,
                gstAmount,
                totalAmount,
                amc.getPaymentFrequency(),
                formatDate(amc.getNextPaymentDate()),
                formatDate(amc.getNextServiceDate()),
                amc.getTotalServices(),
                amc.getCompletedServices(),
                remaining
        );

        Map<String, Object> vars = new HashMap<>();
        vars.put("customer", customerDetails);
        vars.put("contract", contractDetails);
        vars.put("serviceHistory", buildServiceHistory(amc));
        vars.put("generatedDate", LocalDate.now().format(DISPLAY_DATE_FMT));
        return vars;
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : null;
    }

    private Double toDouble(java.math.BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private List<ContractPdfRequest.ServiceHistoryEntry> buildServiceHistory(AmcContract amc) {
        if (amc.getServiceHistory() != null && !amc.getServiceHistory().isEmpty()) {
            return amc.getServiceHistory().stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(
                    ServiceHistory::getServiceDate,
                    Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .map(this::toServiceHistoryEntry)
                .toList();
        }

        Integer totalServices = amc.getTotalServices();
        if (totalServices == null || totalServices <= 0) {
            return List.of();
        }

        LocalDate startDate = amc.getStartDate();
        LocalDate endDate = amc.getEndDate();
        if (startDate == null && endDate == null) {
            return List.of();
        }

        LocalDate effectiveStartDate = startDate != null ? startDate : endDate;
        LocalDate effectiveEndDate = endDate != null ? endDate : startDate;
        long daySpan = Math.max(0, ChronoUnit.DAYS.between(effectiveStartDate, effectiveEndDate));
        int completedServices = amc.getCompletedServices() == null
                ? 0
                : Math.max(0, Math.min(amc.getCompletedServices(), totalServices));

        return java.util.stream.IntStream.range(0, totalServices)
                .mapToObj(index -> toServiceHistoryEntry(amc, effectiveStartDate, daySpan, totalServices, completedServices, index))
                .toList();
    }

    private ContractPdfRequest.ServiceHistoryEntry toServiceHistoryEntry(
            AmcContract amc,
            LocalDate effectiveStartDate,
            long daySpan,
            int totalServices,
            int completedServices,
            int index
    ) {
        LocalDate serviceDate;
        if (totalServices == 1) {
            serviceDate = effectiveStartDate;
        } else {
            long offsetDays = Math.round((double) daySpan * index / (totalServices - 1));
            serviceDate = effectiveStartDate.plusDays(offsetDays);
        }

        String status = index < completedServices ? "COMPLETED" : "PENDING";
        String engineerName = index < completedServices ? "Apollo Service Team" : "Scheduled Visit";
        String workDone = "AMC service #" + (index + 1)
                + (amc.getContractType() != null ? " - " + amc.getContractType().name() : "");

        return new ContractPdfRequest.ServiceHistoryEntry(
                formatDate(serviceDate),
                engineerName,
                workDone,
                status
        );
    }

    private ContractPdfRequest.ServiceHistoryEntry toServiceHistoryEntry(
        ServiceHistory serviceHistory
    ) {
        return new ContractPdfRequest.ServiceHistoryEntry(
            formatDate(serviceHistory.getServiceDate()),
            serviceHistory.getEngineerName(),
            serviceHistory.getWorkDone(),
            serviceHistory.getStatus() != null ? serviceHistory.getStatus().name() : null
        );
    }
}
