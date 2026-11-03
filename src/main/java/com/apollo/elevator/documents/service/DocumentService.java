package com.apollo.elevator.documents.service;

import com.apollo.elevator.common.exception.InvalidGstPercentageException;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.common.util.LogMaskingUtil;
import com.apollo.elevator.common.exception.UnsupportedDocumentTypeException;
import com.apollo.elevator.customer.model.entity.AmcContract;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.model.entity.Lift;
import com.apollo.elevator.customer.model.entity.ServiceHistory;
import com.apollo.elevator.customer.model.enums.AmcStatus;
import com.apollo.elevator.customer.repository.CustomerRepository;
import com.apollo.elevator.documents.model.dto.BillPreviewResponse;
import com.apollo.elevator.documents.model.dto.BillRequest;
import com.apollo.elevator.documents.model.dto.ContractPdfRequest;
import com.apollo.elevator.documents.model.enums.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
    private static final BigDecimal GST_PERCENTAGE = BigDecimal.valueOf(18);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 2;

    private final CustomerRepository customerRepository;
    private final PdfTemplateService pdfTemplateService;
    private final UpiQrCodeService upiQrCodeService;

    /**
     * @param documentDate the real-world date the document pertains to (e.g. invoice date,
     *                     AMC contract start date) — not the generation timestamp. Used by
     *                     callers (e.g. Google Drive uploads) that need to date-stamp the
     *                     stored filename with the document's own date rather than "today".
     */
    public record PdfResult(byte[] pdfBytes, String fileName, LocalDate documentDate) {}

    /**
     * Generates a customer document (AMC_CONTRACT currently) based on documentType.
     */
    @Transactional(readOnly = true)
    public PdfResult generateCustomerDocument(Long customerId, DocumentType documentType) {
        return switch (documentType) {
            case AMC_CONTRACT -> {
                AmcContractPdfResult r = generateAmcContractPdfResult(customerId);
                yield new PdfResult(r.pdfBytes(), r.fileName(), r.documentDate());
            }
            default -> throw new IllegalArgumentException(
                    "documentType " + documentType + " is not supported for customer documents. Use POST /bills instead.");
        };
    }

    /**
     * Generates a GST bill PDF from the supplied BillRequest.
     */
    public PdfResult generateBillPdf(DocumentType documentType, BillRequest billRequest) {
        validateDocumentType(documentType);
        return generateBillPdf(billRequest, "pdf/gst-bill", Map.of());
    }

    /** Taxable (before-tax), GST and grand-total amounts of a bill, computed exactly as printed on the PDF. */
    public record BillTotals(BigDecimal taxableAmount, BigDecimal gstAmount, BigDecimal totalAmount) {}

    public BillTotals computeBillTotals(BillRequest billRequest) {
        validateBillRequest(billRequest);
        double gstPercentage = validateAndNormalizeGstPercentage(billRequest.bill().gstPercentage());
        BigDecimal taxable = sumLineItemAmounts(normalizeLineItems(billRequest.lineItems()));
        BigDecimal gst = taxable.multiply(BigDecimal.valueOf(gstPercentage)).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal half = gst.divide(BigDecimal.valueOf(2), MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal roundedGst = half.add(gst.subtract(half));
        return new BillTotals(taxable, roundedGst, taxable.add(roundedGst).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * Renders a GST bill using the given Thymeleaf template; {@code extraVariables} are
     * added on top of the standard bill variables (e.g. instalment details).
     */
    public PdfResult generateBillPdf(BillRequest billRequest, String templateName, Map<String, Object> extraVariables) {
        validateBillRequest(billRequest);

        Map<String, Object> variables = buildBillTemplateVariables(billRequest);
        if (extraVariables != null) {
            variables.putAll(extraVariables);
        }
        byte[] pdfBytes = pdfTemplateService.renderToPdf(templateName, variables);

        String entityPrefix = billRequest.entity() == BillRequest.BillingEntity.APOLLO_ELEVATOR
                ? "apollo_elevator" : "apollo_elevator_services";
        String fileName = entityPrefix + "_bill_" + sanitize(billRequest.bill().invoiceNumber()) + ".pdf";
        LocalDate invoiceDate = parseInvoiceDate(billRequest.bill().invoiceDate());

        return new PdfResult(pdfBytes, fileName, invoiceDate);
    }

    /**
     * Parses the bill's display-formatted invoice date ("dd/MM/yyyy") back into a
     * {@link LocalDate}, falling back to today's date if it is missing or unparseable
     * (the invoice must already have passed validation by this point, so this is defensive).
     */
    private LocalDate parseInvoiceDate(String invoiceDate) {
        if (invoiceDate == null || invoiceDate.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(invoiceDate, DATE_FMT);
        } catch (java.time.format.DateTimeParseException exception) {
            log.warn("Could not parse invoice date '{}', defaulting to today", invoiceDate);
            return LocalDate.now();
        }
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
     */
    @Transactional(readOnly = true)
    public BillPreviewResponse buildBillPreview(Long customerId, DocumentType documentType) {
        validateDocumentType(documentType);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        Lift lift = pickPrimaryLift(customer);
        AmcContract amc = pickBestAmcContract(lift, customerId);

        log.info("Building bill preview. customerId={}, contractNumber={}, documentType={}",
                customerId, amc.getContractNumber(), documentType);

        BillRequest billRequest = mapAmcToBillRequest(customer, amc);
        return new BillPreviewResponse(documentType, billRequest);
    }

    /**
     * Maps a Customer + AmcContract to a GST bill preview payload.
     */
    private BillRequest mapAmcToBillRequest(Customer customer, AmcContract amc) {
        double gstPercentage = 18.0;
        double sgstPct = gstPercentage / 2.0;
        double cgstPct = gstPercentage / 2.0;

        BillRequest.BillingEntity entity = BillRequest.BillingEntity.APOLLO_ELEVATOR;
        String invoiceNumber = amc.getContractNumber() != null ? amc.getContractNumber() : "BILL-001";

        BillRequest.BillDetails billDetails = new BillRequest.BillDetails(
                invoiceNumber,
                formatDate(LocalDate.now()),
                "Karnataka",
                "560 058",
                "8971974009",
                "ABPFA4107Q",
                "29ABPFA4107Q1ZU",
                gstPercentage,
                sgstPct,
                cgstPct
        );

        String address = buildAddress(customer);
        BillRequest.BillToParty billTo = new BillRequest.BillToParty(
                customer.getCustomerName(),
                address,
                null,
                null,
                null,
                null,
                customer.getState() != null ? customer.getState() : "Karnataka"
        );

        List<BillRequest.BillLineItem> lineItems = buildBillLineItems(amc, true);

        return new BillRequest(entity, billDetails, billTo, lineItems);
    }

    /**
     * Builds a GST bill payload for the customer with Apollo's standard seller details,
     * today's invoice date, 18% GST and the given line items.
     */
    public BillRequest buildBillRequest(Customer customer, String invoiceNumber, List<BillRequest.BillLineItem> lineItems) {
        BillRequest base = mapAmcToBillRequest(customer, new AmcContract());
        BillRequest.BillDetails d = base.bill();
        BillRequest.BillDetails details = new BillRequest.BillDetails(invoiceNumber, d.invoiceDate(), d.state(), d.pincode(),
                d.mobile(), d.panNumber(), d.gstin(), d.gstPercentage(), d.sgstPercentage(), d.cgstPercentage());
        return new BillRequest(base.entity(), details, base.billTo(), lineItems);
    }

    public static String formatBillDate(LocalDate date) {
        return date == null ? "" : date.format(DATE_FMT);
    }

    private List<BillRequest.BillLineItem> buildBillLineItems(AmcContract amc, boolean isGst) {
        double baseAmount = amc.getAmcAmount() != null ? amc.getAmcAmount().doubleValue() : 0.0;

        String periodLabel = buildPeriodLabel(amc);
        String description = "Periodical Maintenance of Lift for the period" + periodLabel;

        return List.of(new BillRequest.BillLineItem(
                1,
                description,
                isGst ? "99611" : null,
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

    private Map<String, Object> buildBillTemplateVariables(BillRequest req) {
        Map<String, Object> vars = new HashMap<>();
        double gstPercentage = validateAndNormalizeGstPercentage(req.bill().gstPercentage());
        List<BillRequest.BillLineItem> lineItems = normalizeLineItems(req.lineItems());
        BigDecimal taxableAmount = sumLineItemAmounts(lineItems);
        BigDecimal gstAmount = taxableAmount.multiply(BigDecimal.valueOf(gstPercentage)).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal sgstAmount = gstAmount.divide(BigDecimal.valueOf(2), MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal cgstAmount = gstAmount.subtract(sgstAmount);
        BigDecimal totalAfterTax = taxableAmount.add(sgstAmount).add(cgstAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalBeforeTax = taxableAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        double halfRate = gstPercentage / 2.0;
        BillRequest.BillDetails adjustedBill = new BillRequest.BillDetails(
                req.bill().invoiceNumber(),
                req.bill().invoiceDate(),
                req.bill().state(),
                req.bill().pincode(),
                req.bill().mobile(),
                req.bill().panNumber(),
                req.bill().gstin(),
                gstPercentage,
                halfRate,
                halfRate
        );

        vars.put("bill", adjustedBill);
        vars.put("billTo", req.billTo());
        vars.put("lineItems", lineItems);
        vars.put("halfGstRateLabel", formatRate(halfRate) + "%");
        vars.put("entity", req.entity());
        vars.put("isGstBill", true);
        vars.put("generatedDate", LocalDate.now().format(DISPLAY_DATE_FMT));
        vars.put("totalBeforeTax", totalBeforeTax.doubleValue());
        vars.put("sgstAmount", sgstAmount.doubleValue());
        vars.put("cgstAmount", cgstAmount.doubleValue());
        vars.put("totalAfterTax", totalAfterTax.doubleValue());
        vars.put("amountInWords", numberToWords(totalAfterTax.doubleValue()));

// UPI payment QR — uses the BigDecimal, not the double, to avoid float drift
        vars.put("upiQrDataUri", upiQrCodeService.buildUpiQrDataUri(req.bill().invoiceNumber(), totalAfterTax));
        vars.put("upiVpa", upiQrCodeService.getVpa());

        return vars;
    }

    private void validateDocumentType(DocumentType documentType) {
        if (documentType != DocumentType.GST_BILL) {
            throw new UnsupportedDocumentTypeException("Only GST_BILL is supported.");
        }
    }

    private void validateBillRequest(BillRequest billRequest) {
        if (billRequest == null || billRequest.bill() == null) {
            throw new IllegalArgumentException("Bill details are required.");
        }
        if (billRequest.bill().gstPercentage() == null) {
            throw new InvalidGstPercentageException("GST percentage is required.");
        }
        double gstPercentage = validateAndNormalizeGstPercentage(billRequest.bill().gstPercentage());
        if (billRequest.lineItems() == null || billRequest.lineItems().isEmpty()) {
            throw new IllegalArgumentException("At least one line item is required.");
        }
        for (BillRequest.BillLineItem item : billRequest.lineItems()) {
            if (item == null) {
                continue;
            }
            if (item.amount() != null && item.amount() < 0) {
                throw new IllegalArgumentException("Line item amount cannot be negative.");
            }
            if (item.rate() != null && item.rate() < 0) {
                throw new IllegalArgumentException("Line item rate cannot be negative.");
            }
            if (item.quantity() != null && !item.quantity().isBlank()) {
                try {
                    double quantity = Double.parseDouble(leadingNumericToken(item.quantity()));
                    if (quantity < 0) {
                        throw new IllegalArgumentException("Line item quantity cannot be negative.");
                    }
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Line item quantity must be numeric.");
                }
            }
        }
    }

    /**
     * Extracts the leading numeric token from a display-style quantity value
     * (e.g. "01 No" -&gt; "01") so unit suffixes used for PDF display don't
     * break numeric validation.
     */
    private String leadingNumericToken(String quantity) {
        String trimmed = quantity.trim();
        int splitIndex = trimmed.indexOf(' ');
        return splitIndex > 0 ? trimmed.substring(0, splitIndex) : trimmed;
    }

    private double validateAndNormalizeGstPercentage(Double gstPercentage) {
        if (gstPercentage == null) {
            throw new InvalidGstPercentageException("GST percentage is required.");
        }
        if (gstPercentage < 0 || gstPercentage > 100) {
            throw new InvalidGstPercentageException("GST percentage must be between 0 and 100.");
        }
        return gstPercentage;
    }

    /**
     * Drops empty rows, renumbers Sl. No. sequentially and recomputes each amount as
     * quantity x rate so the PDF totals can never disagree with the line items.
     * When quantity is not numeric or rate is missing, the supplied amount is kept.
     */
    List<BillRequest.BillLineItem> normalizeLineItems(List<BillRequest.BillLineItem> lineItems) {
        if (lineItems == null) {
            return List.of();
        }
        List<BillRequest.BillLineItem> result = new java.util.ArrayList<>();
        for (BillRequest.BillLineItem item : lineItems) {
            if (item == null) {
                continue;
            }
            Double amount = item.amount();
            BigDecimal quantity = parseQuantity(item.quantity());
            if (quantity != null && item.rate() != null) {
                amount = quantity.multiply(BigDecimal.valueOf(item.rate()))
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP).doubleValue();
            }
            result.add(new BillRequest.BillLineItem(result.size() + 1, item.description(), item.hsnCode(),
                    item.quantity(), item.rate(), amount));
        }
        return result;
    }

    private BigDecimal parseQuantity(String quantity) {
        if (quantity == null || quantity.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(leadingNumericToken(quantity));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String formatRate(double rate) {
        return BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString();
    }

    private BigDecimal sumLineItemAmounts(List<BillRequest.BillLineItem> lineItems) {
        BigDecimal total = BigDecimal.ZERO;
        if (lineItems == null) {
            return total;
        }
        for (BillRequest.BillLineItem item : lineItems) {
            if (item == null || item.amount() == null) {
                continue;
            }
            total = total.add(BigDecimal.valueOf(item.amount()));
        }
        return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
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
        LocalDate documentDate = amc.getStartDate() != null ? amc.getStartDate()
                : amc.getEndDate() != null ? amc.getEndDate() : LocalDate.now();

        log.info("Building PDF for contractNumber={}, fileName={}",
                amc.getContractNumber(), LogMaskingUtil.mask("fileName", fileName));
        Map<String, Object> variables = buildTemplateVariables(customer, lift, amc);
        byte[] pdfBytes = pdfTemplateService.renderToPdf("pdf/amc-contract", variables);
        log.info("AMC contract PDF result ready. fileName={}, sizeBytes={}",
                LogMaskingUtil.mask("fileName", fileName), pdfBytes.length);
        return new AmcContractPdfResult(pdfBytes, fileName, documentDate);
    }

    /** Generates the PDF for one specific AMC contract of the customer (any lift, any status). */
    @Transactional(readOnly = true)
    public AmcContractPdfResult generateAmcContractPdfResult(Long customerId, Long amcContractId) {
        log.info("Generating AMC contract PDF for contract. customerId={}, amcContractId={}", customerId, amcContractId);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
        for (Lift lift : customer.getLifts()) {
            for (AmcContract amc : lift.getAmcContracts()) {
                if (amcContractId.equals(amc.getId())) {
                    String suffix = amc.getContractNumber() != null ? sanitize(amc.getContractNumber()) : String.valueOf(amc.getId());
                    String fileName = customer.getCustomerCode() + "_apollo_amc_" + resolveAmcYear(amc) + "_" + suffix + ".pdf";
                    LocalDate documentDate = amc.getStartDate() != null ? amc.getStartDate()
                            : amc.getEndDate() != null ? amc.getEndDate() : LocalDate.now();
                    byte[] pdfBytes = pdfTemplateService.renderToPdf("pdf/amc-contract", buildTemplateVariables(customer, lift, amc));
                    log.info("AMC contract PDF ready. fileName={}, sizeBytes={}",
                            LogMaskingUtil.mask("fileName", fileName), pdfBytes.length);
                    return new AmcContractPdfResult(pdfBytes, fileName, documentDate);
                }
            }
        }
        throw new ResourceNotFoundException("AMC contract " + amcContractId + " not found for customer " + customerId);
    }

    /**
     * @param documentDate the AMC contract's own start (or end) date — not today's date.
     */
    public record AmcContractPdfResult(byte[] pdfBytes, String fileName, LocalDate documentDate) {}

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
