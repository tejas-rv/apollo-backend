package com.apollo.elevators.documents.service;

import com.apollo.elevators.common.exception.ResourceNotFoundException;
import com.apollo.elevators.customer.model.entity.AmcContract;
import com.apollo.elevators.customer.model.entity.Customer;
import com.apollo.elevators.customer.model.entity.Lift;
import com.apollo.elevators.customer.model.entity.ServiceHistory;
import com.apollo.elevators.documents.model.dto.BillPreviewResponse;
import com.apollo.elevators.documents.model.dto.BillRequest;
import com.apollo.elevators.documents.model.dto.ContractPdfRequest;
import com.apollo.elevators.customer.model.enums.AmcStatus;
import com.apollo.elevators.documents.model.enums.DocumentType;
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

    public record PdfResult(byte[] pdfBytes, String fileName) {}

    /**
     * Generates a customer document (AMC_CONTRACT currently) based on documentType.
     */
    @Transactional(readOnly = true)
    public PdfResult generateCustomerDocument(Long customerId, DocumentType documentType) {
        return switch (documentType) {
            case AMC_CONTRACT -> {
                AmcContractPdfResult r = generateAmcContractPdfResult(customerId);
                yield new PdfResult(r.pdfBytes(), r.fileName());
            }
            default -> throw new IllegalArgumentException(
                    "documentType " + documentType + " is not supported for customer documents. Use POST /bills instead.");
        };
    }

    /**
     * Generates a bill PDF (GST or Without-GST) from the supplied BillRequest.
     */
    public PdfResult generateBillPdf(DocumentType documentType, BillRequest billRequest) {
        if (documentType != DocumentType.GST_BILL && documentType != DocumentType.WITHOUT_GST_BILL) {
            throw new IllegalArgumentException("documentType must be GST_BILL or WITHOUT_GST_BILL for bill generation.");
        }

        Map<String, Object> variables = buildBillTemplateVariables(billRequest, documentType);
        String template = documentType == DocumentType.GST_BILL ? "pdf/gst-bill" : "pdf/without-gst-bill";
        byte[] pdfBytes = pdfTemplateService.renderToPdf(template, variables);

        String entityPrefix = billRequest.entity() == BillRequest.BillingEntity.APOLLO_ELEVATOR
                ? "apollo_elevator" : "apollo_elevator_services";
        String fileName = entityPrefix + "_bill_" + sanitize(billRequest.bill().invoiceNumber()) + ".pdf";

        return new PdfResult(pdfBytes, fileName);
    }

    private String sanitize(String s) {
        return s == null ? "bill" : s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    // -------------------------------------------------------------------------
    // BILL PREVIEW  (DB → JSON for UI review)
    // -------------------------------------------------------------------------

    /**
     * Fetches the customer's AMC from the database and builds a pre-filled
     * {@link BillRequest} that the UI can display, edit, and POST back to
     * generate the final PDF.
     *
     * <p>Entity defaults:
     * <ul>
     *   <li>{@code GST_BILL}         → {@code APOLLO_ELEVATOR}         (GSTIN bill)</li>
     *   <li>{@code WITHOUT_GST_BILL} → {@code APOLLO_ELEVATOR_SERVICES} (plain bill)</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public BillPreviewResponse buildBillPreview(Long customerId, DocumentType documentType) {
        if (documentType != DocumentType.GST_BILL && documentType != DocumentType.WITHOUT_GST_BILL) {
            throw new IllegalArgumentException(
                    "documentType must be GST_BILL or WITHOUT_GST_BILL for bill preview.");
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        Lift lift = pickPrimaryLift(customer);
        AmcContract amc = pickBestAmcContract(lift, customerId);

        log.info("Building bill preview. customerId={}, contractNumber={}, documentType={}",
                customerId, amc.getContractNumber(), documentType);

        BillRequest billRequest = mapAmcToBillRequest(customer, amc, documentType);
        return new BillPreviewResponse(documentType, billRequest);
    }

    /**
     * Maps a Customer + AmcContract to a BillRequest suitable for either a GST
     * bill (Apollo Elevator) or a non-GST bill (Apollo Elevator Services).
     */
    private BillRequest mapAmcToBillRequest(Customer customer, AmcContract amc, DocumentType documentType) {
        boolean isGst = documentType == DocumentType.GST_BILL;

        // ---- Entity / bill details ----
        BillRequest.BillingEntity entity = isGst
                ? BillRequest.BillingEntity.APOLLO_ELEVATOR
                : BillRequest.BillingEntity.APOLLO_ELEVATOR_SERVICES;

        // Auto-generate a sequential-style invoice number when the contract number is available
        String invoiceNumber = amc.getContractNumber() != null ? amc.getContractNumber() : "BILL-001";

        Double sgstPct = isGst ? 9.0 : null;
        Double cgstPct = isGst ? 9.0 : null;

        BillRequest.BillDetails billDetails = new BillRequest.BillDetails(
                invoiceNumber,
                formatDate(LocalDate.now()),   // default to today; UI can change
                "Karnataka",
                "560 058",
                "8971974009",
                isGst ? "ABPFA4107Q" : null,
                isGst ? "29ABPFA4107Q1ZU" : null,
                sgstPct,
                cgstPct
        );

        // ---- Bill-to party ----
        String address = buildAddress(customer);
        BillRequest.BillToParty billTo = new BillRequest.BillToParty(
                customer.getCustomerName(),
                address,
                null,   // PO number — not stored in DB, UI can fill
                null,   // Job number
                null,   // Project name
                null,   // Customer GSTIN — not stored, UI can fill
                customer.getState() != null ? customer.getState() : "Karnataka"
        );

        // ---- Line items — one entry per AMC period ----
        List<BillRequest.BillLineItem> lineItems = buildBillLineItems(amc, isGst);

        return new BillRequest(entity, billDetails, billTo, lineItems);
    }

    private List<BillRequest.BillLineItem> buildBillLineItems(AmcContract amc, boolean isGst) {
        double baseAmount = amc.getAmcAmount() != null ? amc.getAmcAmount().doubleValue() : 0.0;

        String periodLabel = buildPeriodLabel(amc);
        String description = "Periodical Maintenance of Lift for the period" + periodLabel;

        return List.of(new BillRequest.BillLineItem(
                1,
                description,
                isGst ? "99611" : null,   // SAC code for maintenance services
                "01 No",
                baseAmount,
                baseAmount
        ));
    }

    private String buildPeriodLabel(AmcContract amc) {
        if (amc.getStartDate() != null && amc.getEndDate() != null) {
            return " " + formatDate(amc.getStartDate()) + " to " + formatDate(amc.getEndDate());
        }
        if (amc.getStartDate() != null) return " from " + formatDate(amc.getStartDate());
        if (amc.getEndDate() != null) return " up to " + formatDate(amc.getEndDate());
        return "";
    }

    private String buildAddress(Customer customer) {
        StringBuilder sb = new StringBuilder();
        if (customer.getAddress() != null) sb.append(customer.getAddress());
        if (customer.getCity() != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(customer.getCity());
        }
        if (customer.getState() != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(customer.getState());
        }
        if (customer.getPincode() != null) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(customer.getPincode());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    // -------------------------------------------------------------------------

    private Map<String, Object> buildBillTemplateVariables(BillRequest req, DocumentType documentType) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("bill", req.bill());
        vars.put("billTo", req.billTo());
        vars.put("lineItems", req.lineItems() != null ? req.lineItems() : List.of());
        vars.put("entity", req.entity());
        vars.put("isGstBill", documentType == DocumentType.GST_BILL);
        vars.put("generatedDate", LocalDate.now().format(DISPLAY_DATE_FMT));

        // Compute totals
        if (req.lineItems() != null && !req.lineItems().isEmpty()) {
            double totalBeforeTax = req.lineItems().stream()
                    .filter(item -> item.amount() != null)
                    .mapToDouble(BillRequest.BillLineItem::amount)
                    .sum();
            vars.put("totalBeforeTax", totalBeforeTax);

            if (documentType == DocumentType.GST_BILL && req.bill().sgstPercentage() != null) {
                double sgst = Math.round(totalBeforeTax * req.bill().sgstPercentage() / 100 * 100.0) / 100.0;
                double cgst = req.bill().cgstPercentage() != null
                        ? Math.round(totalBeforeTax * req.bill().cgstPercentage() / 100 * 100.0) / 100.0
                        : sgst;
                vars.put("sgstAmount", sgst);
                vars.put("cgstAmount", cgst);
                vars.put("totalAfterTax", totalBeforeTax + sgst + cgst);
            } else {
                vars.put("totalAfterTax", totalBeforeTax);
            }
            vars.put("amountInWords", numberToWords(totalBeforeTax));
        }

        return vars;
    }

    /** Simple rupee amount-in-words helper (covers values up to crores). */
    private String numberToWords(double amount) {
        long paise = Math.round(amount * 100);
        long rupees = paise / 100;
        long paiseRemainder = paise % 100;

        String words = convertToWords(rupees) + " Rupees";
        if (paiseRemainder > 0) {
            words += " and " + convertToWords(paiseRemainder) + " Paise";
        }
        return words + " Only";
    }

    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private String convertToWords(long n) {
        if (n == 0) return "Zero";
        if (n < 20) return ONES[(int) n];
        if (n < 100) return TENS[(int) (n / 10)] + (n % 10 != 0 ? " " + ONES[(int) (n % 10)] : "");
        if (n < 1000) return ONES[(int) (n / 100)] + " Hundred" + (n % 100 != 0 ? " " + convertToWords(n % 100) : "");
        if (n < 100000) return convertToWords(n / 1000) + " Thousand" + (n % 1000 != 0 ? " " + convertToWords(n % 1000) : "");
        if (n < 10000000) return convertToWords(n / 100000) + " Lakh" + (n % 100000 != 0 ? " " + convertToWords(n % 100000) : "");
        return convertToWords(n / 10000000) + " Crore" + (n % 10000000 != 0 ? " " + convertToWords(n % 10000000) : "");
    }

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
