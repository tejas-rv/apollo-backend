package com.apollo.elevator.billing.service;

import com.apollo.elevator.billing.model.dto.AmcBillingStatusResponse;
import com.apollo.elevator.billing.model.dto.BillDraftRequest;
import com.apollo.elevator.billing.model.dto.BillDraftResponse;
import com.apollo.elevator.billing.model.dto.BillResponse;
import com.apollo.elevator.billing.model.dto.BillSummaryResponse;
import com.apollo.elevator.billing.model.dto.CreateBillRequest;
import com.apollo.elevator.billing.model.dto.MarkBillPaidRequest;
import com.apollo.elevator.billing.model.dto.NewAmcContractRequest;
import com.apollo.elevator.billing.model.entity.Bill;
import com.apollo.elevator.billing.model.enums.BillStatus;
import com.apollo.elevator.billing.model.enums.BillType;
import com.apollo.elevator.billing.model.enums.PaymentFrequency;
import com.apollo.elevator.billing.repository.BillRepository;
import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.customer.model.entity.AmcContract;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.model.entity.Lift;
import com.apollo.elevator.customer.model.enums.AmcStatus;
import com.apollo.elevator.customer.model.enums.ContractType;
import com.apollo.elevator.customer.repository.AmcContractRepository;
import com.apollo.elevator.customer.repository.CustomerRepository;
import com.apollo.elevator.documents.model.dto.BillRequest;
import com.apollo.elevator.documents.service.DocumentService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Persists generated bills and enforces AMC billing rules:
 * <ul>
 *   <li>AMC bills are priced from the plan (tier) price, split into instalments by payment frequency.</li>
 *   <li>Each instalment of an AMC year (contract period) can be billed only once (cancelled bills excluded).</li>
 *   <li>The plan and frequency are locked once the first instalment of an AMC year is billed.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    static final String DEFAULT_HSN = "99611";
    static final String TEMPLATE_BILL = "pdf/gst-bill";
    static final String TEMPLATE_INSTALMENT = "pdf/gst-bill-instalment";
    private static final DateTimeFormatter BILL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MAX_SUMMARY_MONTHS = 36;

    private final CustomerRepository customerRepository;
    private final AmcContractRepository amcContractRepository;
    private final BillRepository billRepository;
    private final AmcPlanPriceService planPriceService;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    /** Resolved AMC billing context for the next unbilled instalment of a contract's AMC year. */
    record AmcPlan(Lift lift, AmcContract contract, ContractType tier, PaymentFrequency frequency, BigDecimal planAmount,
                   LocalDate yearStart, LocalDate yearEnd, int instalmentNo, boolean locked, List<Bill> existing) {

        int totalInstalments() {
            return frequency.instalments();
        }

        LocalDate periodStart() {
            return instalmentStart(yearStart, frequency, instalmentNo);
        }

        LocalDate periodEnd() {
            return instalmentEnd(yearStart, yearEnd, frequency, instalmentNo);
        }

        BigDecimal instalmentAmount() {
            return BillingService.instalmentAmount(planAmount, frequency.instalments(), instalmentNo);
        }
    }

    // -------------------------------------------------------------------------
    // AMC status / draft
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AmcBillingStatusResponse> amcBillingStatus(Long customerId) {
        Customer customer = loadCustomer(customerId);
        List<AmcBillingStatusResponse> result = new ArrayList<>();
        for (Lift lift : customer.getLifts()) {
            for (AmcContract amc : lift.getAmcContracts()) {
                PaymentFrequency frequency = PaymentFrequency.parse(amc.getPaymentFrequency());
                if (amc.getStartDate() == null) {
                    result.add(new AmcBillingStatusResponse(amc.getId(), amc.getContractNumber(), lift.getSerialNumber(),
                            amc.getStatus(), null, amc.getEndDate(), amc.getContractType(), amc.getAmcAmount(),
                            frequency, 0, 0, null, false));
                    continue;
                }
                LocalDate yearStart = amc.getStartDate();
                List<Bill> existing = activeBillsFor(amc.getId(), yearStart);
                if (!existing.isEmpty()) {
                    frequency = firstBill(existing).getPaymentFrequency();
                }
                PaymentFrequency effective = frequency != null ? frequency : PaymentFrequency.YEARLY;
                Integer next = nextInstalmentNo(existing, effective.instalments());
                result.add(new AmcBillingStatusResponse(amc.getId(), amc.getContractNumber(), lift.getSerialNumber(),
                        amc.getStatus(), yearStart, yearEnd(amc), amc.getContractType(), amc.getAmcAmount(),
                        effective, existing.size(), effective.instalments(), next, next == null));
            }
        }
        return result;
    }

    /**
     * Adds an AMC contract to a customer's lift with plan-derived defaults, so a customer without an
     * ACTIVE AMC can be billed without editing the customer record. Returns the new contract's billing status.
     */
    @Transactional
    public AmcBillingStatusResponse createAmcContract(Long customerId, NewAmcContractRequest request, String user) {
        Customer customer = loadCustomer(customerId);
        Lift lift = customer.getLifts().stream()
                .filter(l -> Objects.equals(l.getId(), request.liftId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Lift " + request.liftId() + " not found for customer " + customerId));
        if (lift.getAmcContracts().stream().anyMatch(a -> a.getStatus() == AmcStatus.ACTIVE)) {
            throw new ConflictException("Lift " + Objects.requireNonNullElse(lift.getSerialNumber(), "#" + lift.getId())
                    + " already has an ACTIVE AMC contract.");
        }

        AmcContract contract = newAmcContract(request, planPriceService.priceOf(request.contractType()), LocalDate.now());
        String number = trimToNull(request.contractNumber());
        if (number != null && amcContractRepository.existsByContractNumberIgnoreCase(number)) {
            throw new ConflictException("Contract number " + number + " already exists.");
        }
        contract.setContractNumber(number != null ? number : nextContractNumber(customer));
        lift.getAmcContracts().add(contract);
        customerRepository.saveAndFlush(customer);

        // saveAndFlush may merge into a managed copy, leaving this instance without an id; match on the unique number.
        String contractNumber = contract.getContractNumber();
        AmcBillingStatusResponse created = amcBillingStatus(customerId).stream()
                .filter(s -> contractNumber.equalsIgnoreCase(s.contractNumber()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created AMC contract " + contractNumber + " not found"));
        log.info("AMC contract created from billing. customerId={}, liftId={}, contractId={}, contractNumber={}, tier={}, by={}",
                customerId, lift.getId(), created.amcContractId(), contractNumber, contract.getContractType(), user);
        return created;
    }

    /** Contract with plan-derived defaults: one-year period, first payment at start, first service a month in. */
    static AmcContract newAmcContract(NewAmcContractRequest request, BigDecimal planAmount, LocalDate today) {
        LocalDate start = request.startDate() != null ? request.startDate() : today;
        PaymentFrequency frequency = Objects.requireNonNullElse(request.paymentFrequency(), PaymentFrequency.YEARLY);
        return AmcContract.builder()
                .status(start.isAfter(today) ? AmcStatus.PENDING : AmcStatus.ACTIVE)
                .contractType(request.contractType())
                .amcAmount(planAmount)
                .paymentFrequency(frequency.name())
                .startDate(start)
                .endDate(start.plusYears(1).minusDays(1))
                .nextPaymentDate(start)
                .nextServiceDate(start.plusMonths(1))
                .totalServices(0)
                .completedServices(0)
                .build();
    }

    private String nextContractNumber(Customer customer) {
        String prefix = "AMC-" + Objects.requireNonNullElse(trimToNull(customer.getCustomerCode()), "C" + customer.getId()) + "-";
        int n = (int) customer.getLifts().stream().mapToLong(l -> l.getAmcContracts().size()).sum();
        String candidate;
        do {
            candidate = prefix + String.format("%02d", ++n);
        } while (amcContractRepository.existsByContractNumberIgnoreCase(candidate));
        return candidate;
    }

    @Transactional(readOnly = true)
    public BillDraftResponse draft(BillDraftRequest request) {
        Customer customer = loadCustomer(request.customerId());
        String invoiceNumber = nextInvoiceNumber();

        if (request.billType() == BillType.MAINTENANCE) {
            List<BillRequest.BillLineItem> items = List.of(
                    new BillRequest.BillLineItem(1, "", DEFAULT_HSN, "1", null, null));
            BillRequest bill = documentService.buildBillRequest(customer, invoiceNumber, items);
            return new BillDraftResponse(BillType.MAINTENANCE, customer.getId(), null, null, null, null,
                    null, null, null, null, null, null, null, null, false, bill);
        }

        AmcPlan plan = resolveAmcPlan(customer, request.amcContractId(), request.contractType(), request.paymentFrequency(), false);
        List<BillRequest.BillLineItem> items = List.of(amcLineItem(plan, null, null));
        BillRequest base = documentService.buildBillRequest(customer, invoiceNumber, items);
        BillRequest bill = withJobNumber(base, plan.contract().getContractNumber());
        return toDraft(customer, plan, bill);
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Transactional
    public BillResponse create(CreateBillRequest request, String user) {
        Customer customer = loadCustomer(request.customerId());
        BillRequest billRequest = request.billRequest();
        String invoiceNumber = billRequest.bill().invoiceNumber().trim();
        if (billRepository.existsByInvoiceNumberIgnoreCase(invoiceNumber)) {
            throw new ConflictException("Invoice number " + invoiceNumber + " already exists. Use a different invoice number.");
        }
        LocalDate invoiceDate = parseBillDate(billRequest.bill().invoiceDate());

        Bill.BillBuilder bill = Bill.builder()
                .invoiceNumber(invoiceNumber)
                .customerId(customer.getId())
                .customerName(billRequest.billTo().name())
                .invoiceDate(invoiceDate)
                .status(BillStatus.GENERATED)
                .createdBy(user)
                .updatedBy(user);

        AmcPlan plan = null;
        BillRequest finalRequest;
        if (request.billType() == BillType.MAINTENANCE) {
            finalRequest = withInvoiceNumber(billRequest, invoiceNumber);
            bill.billType(BillType.MAINTENANCE);
        } else {
            plan = resolveAmcPlan(customer, request.amcContractId(), request.contractType(), request.paymentFrequency(), true);
            finalRequest = withInvoiceNumber(withFixedAmcLine(billRequest, plan), invoiceNumber);
            bill.billType(plan.instalmentNo() == 1 ? BillType.AMC : BillType.AMC_INSTALMENT)
                    .amcContractId(plan.contract().getId())
                    .contractType(plan.tier())
                    .paymentFrequency(plan.frequency())
                    .instalmentNo(plan.instalmentNo())
                    .totalInstalments(plan.totalInstalments())
                    .periodStart(plan.periodStart())
                    .periodEnd(plan.periodEnd())
                    .amcPeriodStart(plan.yearStart())
                    .planAmount(plan.planAmount())
                    .amcBaseAmount(plan.instalmentAmount());
        }

        DocumentService.BillTotals totals = documentService.computeBillTotals(finalRequest);
        bill.taxableAmount(totals.taxableAmount())
                .gstAmount(totals.gstAmount())
                .totalAmount(totals.totalAmount())
                .gstPercentage(BigDecimal.valueOf(Objects.requireNonNullElse(finalRequest.bill().gstPercentage(), 18.0))
                        .setScale(2, RoundingMode.HALF_UP))
                .requestJson(objectMapper.writeValueAsString(finalRequest));

        Bill saved;
        try {
            saved = billRepository.saveAndFlush(bill.build());
        } catch (DataIntegrityViolationException ex) {
            log.warn("Bill insert rejected by DB constraint. invoiceNumber={}, reason={}",
                    invoiceNumber, com.apollo.elevator.common.util.LogMaskingUtil.mask(
                            "reason", ex.getMostSpecificCause().getMessage()));
            throw new ConflictException("This bill already exists (duplicate invoice number or AMC instalment already billed).");
        }

        if (plan != null) {
            updateContractAfterBilling(plan);
        }
        log.info("Bill created. id={}, invoiceNumber={}, type={}, customerId={}, total={}, createdBy={}",
                saved.getId(), saved.getInvoiceNumber(), saved.getBillType(), saved.getCustomerId(),
                saved.getTotalAmount(),
                com.apollo.elevator.common.util.LogMaskingUtil.mask("username", user));
        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Read / list / PDF
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<BillResponse> list(BillStatus status, BillType billType, Long customerId, String query, Pageable pageable) {
        Specification<Bill> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (billType != null) predicates.add(cb.equal(root.get("billType"), billType));
            if (customerId != null) predicates.add(cb.equal(root.get("customerId"), customerId));
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("invoiceNumber")), like),
                        cb.like(cb.lower(root.get("customerName")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return billRepository.findAll(spec, pageable).map(BillingService::toResponse);
    }

    @Transactional(readOnly = true)
    public BillResponse get(Long id) {
        return toResponse(loadBill(id));
    }

    @Transactional(readOnly = true)
    public BillRequest billRequestOf(Long id) {
        return objectMapper.readValue(loadBill(id).getRequestJson(), BillRequest.class);
    }

    @Transactional(readOnly = true)
    public DocumentService.PdfResult renderPdf(Long id) {
        Bill bill = loadBill(id);
        BillRequest request = objectMapper.readValue(bill.getRequestJson(), BillRequest.class);
        if (bill.getBillType() != BillType.AMC_INSTALMENT) {
            return documentService.generateBillPdf(request, TEMPLATE_BILL, Map.of());
        }
        return documentService.generateBillPdf(request, TEMPLATE_INSTALMENT, instalmentVariables(bill));
    }

    // -------------------------------------------------------------------------
    // Status changes
    // -------------------------------------------------------------------------

    @Transactional
    public BillResponse markSent(Long id, String user) {
        Bill bill = loadBill(id);
        if (bill.getStatus() == BillStatus.GENERATED) {
            bill.setStatus(BillStatus.SENT);
            bill.setUpdatedBy(user);
        }
        return toResponse(billRepository.save(bill));
    }

    @Transactional
    public BillResponse markPaid(Long id, MarkBillPaidRequest request, String user) {
        Bill bill = loadBill(id);
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new ConflictException("A cancelled bill cannot be marked as paid.");
        }
        if (request.paidDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Paid date cannot be in the future.");
        }
        bill.setStatus(BillStatus.PAID);
        bill.setPaidDate(request.paidDate());
        bill.setPaymentReference(trimToNull(request.paymentReference()));
        bill.setUpdatedBy(user);
        log.info("Bill marked paid. id={}, invoiceNumber={}, paidDate={}, by={}",
                id, bill.getInvoiceNumber(), request.paidDate(),
                com.apollo.elevator.common.util.LogMaskingUtil.mask("username", user));
        return toResponse(billRepository.save(bill));
    }

    /** Reverts a PAID bill back to unpaid (e.g. payment recorded by mistake / cheque bounced). */
    @Transactional
    public BillResponse markUnpaid(Long id, String user) {
        Bill bill = loadBill(id);
        if (bill.getStatus() != BillStatus.PAID) {
            throw new ConflictException("Only a paid bill can be marked as unpaid.");
        }
        bill.setStatus(BillStatus.SENT);
        bill.setPaidDate(null);
        bill.setPaymentReference(null);
        bill.setUpdatedBy(user);
        return toResponse(billRepository.save(bill));
    }

    /** Cancels an unpaid bill; for AMC bills this frees the instalment so it can be billed again. */
    @Transactional
    public BillResponse cancel(Long id, String user) {
        Bill bill = loadBill(id);
        if (bill.getStatus() == BillStatus.PAID) {
            throw new ConflictException("A paid bill cannot be cancelled. Mark it as unpaid first.");
        }
        bill.setStatus(BillStatus.CANCELLED);
        bill.setUpdatedBy(user);
        log.info("Bill cancelled. id={}, invoiceNumber={}, by={}",
                id, bill.getInvoiceNumber(), com.apollo.elevator.common.util.LogMaskingUtil.mask("username", user));
        return toResponse(billRepository.save(bill));
    }

    // -------------------------------------------------------------------------
    // Dashboard summary
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public BillSummaryResponse summary(LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : financialYearStart(LocalDate.now());
        LocalDate end = to != null ? to : start.plusYears(1).minusDays(1);
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("'to' date must be on or after 'from' date.");
        }

        List<Bill> bills = billRepository.findByStatusNot(BillStatus.CANCELLED);
        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        long billCount = 0;
        long paidCount = 0;

        Map<YearMonth, BigDecimal[]> monthly = new LinkedHashMap<>();
        YearMonth first = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        for (YearMonth m = first; !m.isAfter(last) && monthly.size() < MAX_SUMMARY_MONTHS; m = m.plusMonths(1)) {
            monthly.put(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }

        for (Bill b : bills) {
            if (inRange(b.getInvoiceDate(), start, end)) {
                billed = billed.add(b.getTotalAmount());
                billCount++;
                if (b.getStatus() != BillStatus.PAID) {
                    outstanding = outstanding.add(b.getTotalAmount());
                }
                BigDecimal[] point = monthly.get(YearMonth.from(b.getInvoiceDate()));
                if (point != null) point[0] = point[0].add(b.getTotalAmount());
            }
            if (b.getStatus() == BillStatus.PAID && inRange(b.getPaidDate(), start, end)) {
                paid = paid.add(b.getTotalAmount());
                paidCount++;
                BigDecimal[] point = monthly.get(YearMonth.from(b.getPaidDate()));
                if (point != null) point[1] = point[1].add(b.getTotalAmount());
            }
        }

        BigDecimal projectedInstalments = projectedUnbilledInstalments(bills, start, end);
        List<BillSummaryResponse.MonthPoint> points = monthly.entrySet().stream()
                .map(e -> new BillSummaryResponse.MonthPoint(e.getKey().toString(), e.getValue()[0], e.getValue()[1]))
                .toList();

        return new BillSummaryResponse(billed, paid, outstanding, projectedInstalments,
                outstanding.add(projectedInstalments), billCount, paidCount, points);
    }

    /** Sum (incl. GST) of not-yet-billed instalments, of AMC years already being billed, whose period starts in range. */
    BigDecimal projectedUnbilledInstalments(List<Bill> bills, LocalDate start, LocalDate end) {
        Map<String, List<Bill>> byAmcYear = bills.stream()
                .filter(b -> b.getAmcContractId() != null && b.getAmcPeriodStart() != null && b.getInstalmentNo() != null)
                .collect(Collectors.groupingBy(b -> b.getAmcContractId() + "|" + b.getAmcPeriodStart()));

        BigDecimal total = BigDecimal.ZERO;
        for (List<Bill> group : byAmcYear.values()) {
            Bill head = firstBill(group);
            if (head.getPaymentFrequency() == null || head.getPlanAmount() == null) continue;
            Set<Integer> billedNos = group.stream().map(Bill::getInstalmentNo).collect(Collectors.toSet());
            int count = head.getPaymentFrequency().instalments();
            BigDecimal gstFactor = BigDecimal.ONE.add(head.getGstPercentage().divide(HUNDRED, 6, RoundingMode.HALF_UP));
            for (int n = 1; n <= count; n++) {
                if (billedNos.contains(n)) continue;
                LocalDate due = instalmentStart(head.getAmcPeriodStart(), head.getPaymentFrequency(), n);
                if (inRange(due, start, end)) {
                    total = total.add(instalmentAmount(head.getPlanAmount(), count, n).multiply(gstFactor));
                }
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Invoice numbers
    // -------------------------------------------------------------------------

    /** Suggests the next invoice number in the form {@code AE/2026-27/0001} (per financial year). */
    @Transactional(readOnly = true)
    public String nextInvoiceNumber() {
        LocalDate fyStart = financialYearStart(LocalDate.now());
        String prefix = "AE/" + fyStart.getYear() + "-" + String.format("%02d", (fyStart.getYear() + 1) % 100) + "/";
        int next = billRepository.findTopByInvoiceNumberStartingWithOrderByIdDesc(prefix)
                .map(b -> parseSequence(b.getInvoiceNumber().substring(prefix.length())) + 1)
                .orElse(1);
        String candidate = prefix + String.format("%04d", next);
        while (billRepository.existsByInvoiceNumberIgnoreCase(candidate)) {
            next++;
            candidate = prefix + String.format("%04d", next);
        }
        return candidate;
    }

    // -------------------------------------------------------------------------
    // AMC helpers
    // -------------------------------------------------------------------------

    AmcPlan resolveAmcPlan(Customer customer, Long amcContractId, ContractType requestedTier,
                           PaymentFrequency requestedFrequency, boolean strict) {
        if (amcContractId == null) {
            throw new IllegalArgumentException("Select the AMC contract to bill.");
        }
        Lift lift = null;
        AmcContract contract = null;
        for (Lift l : customer.getLifts()) {
            for (AmcContract a : l.getAmcContracts()) {
                if (Objects.equals(a.getId(), amcContractId)) {
                    lift = l;
                    contract = a;
                }
            }
        }
        if (contract == null) {
            throw new ResourceNotFoundException("AMC contract " + amcContractId + " not found for customer " + customer.getId());
        }
        if (contract.getStartDate() == null) {
            throw new IllegalArgumentException("AMC contract " + label(contract) + " has no start date. Set the AMC period before billing.");
        }

        LocalDate yearStart = contract.getStartDate();
        LocalDate yearEnd = yearEnd(contract);
        List<Bill> existing = activeBillsFor(contract.getId(), yearStart);

        ContractType tier;
        PaymentFrequency frequency;
        BigDecimal planAmount;
        boolean locked = !existing.isEmpty();
        if (locked) {
            Bill head = firstBill(existing);
            tier = head.getContractType();
            frequency = head.getPaymentFrequency();
            planAmount = head.getPlanAmount();
            if (strict && ((requestedTier != null && requestedTier != tier)
                    || (requestedFrequency != null && requestedFrequency != frequency))) {
                throw new ConflictException("AMC " + period(yearStart, yearEnd) + " for contract " + label(contract)
                        + " is already being billed as " + tier + " (" + frequency.label()
                        + "). Remaining instalments must use the same plan and frequency.");
            }
        } else {
            tier = requestedTier != null ? requestedTier : contract.getContractType();
            if (tier == null) {
                throw new IllegalArgumentException("Choose an AMC plan (SILVER / GOLD / PLATINUM).");
            }
            frequency = requestedFrequency != null ? requestedFrequency
                    : Objects.requireNonNullElse(PaymentFrequency.parse(contract.getPaymentFrequency()), PaymentFrequency.YEARLY);
            planAmount = planPriceService.priceOf(tier);
        }

        Integer next = nextInstalmentNo(existing, frequency.instalments());
        if (next == null) {
            throw new ConflictException("AMC " + period(yearStart, yearEnd) + " for contract " + label(contract)
                    + " is already billed (" + existing.size() + " of " + frequency.instalments()
                    + " instalments). Renew the contract period to bill the next AMC year.");
        }
        return new AmcPlan(lift, contract, tier, frequency, planAmount, yearStart, yearEnd, next, locked, existing);
    }

    private void updateContractAfterBilling(AmcPlan plan) {
        AmcContract contract = plan.contract();
        contract.setContractType(plan.tier());
        contract.setAmcAmount(plan.planAmount());
        contract.setPaymentFrequency(plan.frequency().name());
        int n = plan.instalmentNo();
        contract.setNextPaymentDate(n < plan.totalInstalments()
                ? instalmentStart(plan.yearStart(), plan.frequency(), n + 1) : null);
        amcContractRepository.save(contract);
    }

    private BillRequest.BillLineItem amcLineItem(AmcPlan plan, String description, String hsn) {
        BigDecimal amount = plan.instalmentAmount();
        String text = description != null && !description.isBlank() ? description : defaultAmcDescription(plan);
        return new BillRequest.BillLineItem(1, text, hsn != null && !hsn.isBlank() ? hsn : DEFAULT_HSN,
                "01 No", amount.doubleValue(), amount.doubleValue());
    }

    static String defaultAmcDescription(AmcPlan plan) {
        StringBuilder sb = new StringBuilder("Annual Maintenance Contract - ").append(plan.tier()).append(" plan");
        if (plan.lift() != null && plan.lift().getSerialNumber() != null && !plan.lift().getSerialNumber().isBlank()) {
            sb.append(" (Lift Sl. No. ").append(plan.lift().getSerialNumber()).append(')');
        }
        sb.append(" for the period ").append(period(plan.periodStart(), plan.periodEnd()));
        if (plan.totalInstalments() > 1) {
            sb.append(" - Instalment ").append(plan.instalmentNo()).append(" of ").append(plan.totalInstalments())
                    .append(" (").append(plan.frequency().label()).append(')');
        }
        return sb.toString();
    }

    /** Forces line item 1 to the AMC instalment amount (the client may only change its description / HSN). */
    private BillRequest withFixedAmcLine(BillRequest request, AmcPlan plan) {
        List<BillRequest.BillLineItem> items = new ArrayList<>();
        List<BillRequest.BillLineItem> incoming = request.lineItems() != null ? request.lineItems() : List.of();
        BillRequest.BillLineItem firstItem = incoming.isEmpty() ? null : incoming.get(0);
        items.add(amcLineItem(plan, firstItem != null ? firstItem.description() : null, firstItem != null ? firstItem.hsnCode() : null));
        for (int i = 1; i < incoming.size(); i++) {
            if (incoming.get(i) != null) items.add(incoming.get(i));
        }
        return new BillRequest(request.entity(), request.bill(), request.billTo(), items);
    }

    private Map<String, Object> instalmentVariables(Bill bill) {
        Map<String, Object> vars = new HashMap<>();
        PaymentFrequency frequency = bill.getPaymentFrequency();
        int count = bill.getTotalInstalments() != null ? bill.getTotalInstalments() : 1;
        AmcContract contract = bill.getAmcContractId() != null
                ? amcContractRepository.findById(bill.getAmcContractId()).orElse(null) : null;
        LocalDate yearEnd = contract != null && Objects.equals(contract.getStartDate(), bill.getAmcPeriodStart())
                ? yearEnd(contract) : bill.getAmcPeriodStart().plusYears(1).minusDays(1);

        Map<Integer, Bill> billedByNo = activeBillsFor(bill.getAmcContractId(), bill.getAmcPeriodStart()).stream()
                .collect(Collectors.toMap(Bill::getInstalmentNo, b -> b, (a, b) -> a));
        List<Map<String, Object>> schedule = new ArrayList<>();
        for (int n = 1; n <= count; n++) {
            Bill b = billedByNo.get(n);
            String status;
            if (n == bill.getInstalmentNo()) status = "This invoice";
            else if (b == null) status = "Upcoming";
            else if (b.getStatus() == BillStatus.PAID) status = "Paid";
            else status = "Billed - " + b.getInvoiceNumber();
            Map<String, Object> row = new HashMap<>();
            row.put("no", n);
            row.put("period", period(instalmentStart(bill.getAmcPeriodStart(), frequency, n),
                    instalmentEnd(bill.getAmcPeriodStart(), yearEnd, frequency, n)));
            row.put("amount", instalmentAmount(bill.getPlanAmount(), count, n));
            row.put("status", status);
            schedule.add(row);
        }

        vars.put("instalmentNo", bill.getInstalmentNo());
        vars.put("totalInstalments", count);
        vars.put("frequencyLabel", frequency != null ? frequency.label() : "");
        vars.put("planName", bill.getContractType() != null ? bill.getContractType().name() : "");
        vars.put("planAmount", bill.getPlanAmount());
        vars.put("contractNumber", contract != null ? contract.getContractNumber() : null);
        vars.put("instalmentPeriod", period(bill.getPeriodStart(), bill.getPeriodEnd()));
        vars.put("amcYear", period(bill.getAmcPeriodStart(), yearEnd));
        vars.put("schedule", schedule);
        return vars;
    }

    private BillDraftResponse toDraft(Customer customer, AmcPlan plan, BillRequest bill) {
        BillType type = plan.instalmentNo() == 1 ? BillType.AMC : BillType.AMC_INSTALMENT;
        return new BillDraftResponse(type, customer.getId(), plan.contract().getId(), plan.contract().getContractNumber(),
                plan.tier(), plan.frequency(), plan.instalmentNo(), plan.totalInstalments(), plan.periodStart(),
                plan.periodEnd(), plan.yearStart(), plan.yearEnd(), plan.planAmount(), plan.instalmentAmount(),
                plan.locked(), bill);
    }

    static LocalDate instalmentStart(LocalDate yearStart, PaymentFrequency frequency, int n) {
        return yearStart.plusMonths((long) (n - 1) * frequency.monthsPerInstalment());
    }

    static LocalDate instalmentEnd(LocalDate yearStart, LocalDate yearEnd, PaymentFrequency frequency, int n) {
        if (n >= frequency.instalments()) {
            return yearEnd;
        }
        LocalDate end = yearStart.plusMonths((long) n * frequency.monthsPerInstalment()).minusDays(1);
        return yearEnd != null && end.isAfter(yearEnd) ? yearEnd : end;
    }

    /** Equal split rounded to paise; the last instalment absorbs the rounding difference. */
    static BigDecimal instalmentAmount(BigDecimal planAmount, int count, int n) {
        BigDecimal each = planAmount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        if (n < count) {
            return each;
        }
        return planAmount.subtract(each.multiply(BigDecimal.valueOf(count - 1L))).setScale(2, RoundingMode.HALF_UP);
    }

    private static Integer nextInstalmentNo(List<Bill> existing, int count) {
        Set<Integer> billed = existing.stream().map(Bill::getInstalmentNo).filter(Objects::nonNull).collect(Collectors.toSet());
        for (int n = 1; n <= count; n++) {
            if (!billed.contains(n)) return n;
        }
        return null;
    }

    private List<Bill> activeBillsFor(Long contractId, LocalDate yearStart) {
        if (contractId == null || yearStart == null) return List.of();
        return billRepository.findByAmcContractIdAndAmcPeriodStartAndStatusNot(contractId, yearStart, BillStatus.CANCELLED);
    }

    private static Bill firstBill(List<Bill> bills) {
        return bills.stream()
                .min(Comparator.comparing(Bill::getInstalmentNo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Bill::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow();
    }

    private static LocalDate yearEnd(AmcContract contract) {
        if (contract.getEndDate() != null) return contract.getEndDate();
        return contract.getStartDate() != null ? contract.getStartDate().plusYears(1).minusDays(1) : null;
    }

    // -------------------------------------------------------------------------
    // Generic helpers
    // -------------------------------------------------------------------------

    private Customer loadCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
    }

    private Bill loadBill(Long id) {
        return billRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + id));
    }

    private static BillRequest withInvoiceNumber(BillRequest r, String invoiceNumber) {
        BillRequest.BillDetails d = r.bill();
        return new BillRequest(r.entity(), new BillRequest.BillDetails(invoiceNumber, d.invoiceDate(), d.state(), d.pincode(),
                d.mobile(), d.panNumber(), d.gstin(), d.gstPercentage(), d.sgstPercentage(), d.cgstPercentage()),
                r.billTo(), r.lineItems());
    }

    private static BillRequest withJobNumber(BillRequest r, String jobNumber) {
        BillRequest.BillToParty t = r.billTo();
        return new BillRequest(r.entity(), r.bill(), new BillRequest.BillToParty(t.name(), t.address(), t.poNumber(),
                jobNumber, t.projectName(), t.gstin(), t.state()), r.lineItems());
    }

    private static LocalDate parseBillDate(String value) {
        try {
            return LocalDate.parse(value, BILL_DATE);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new IllegalArgumentException("Invoice date must be in dd/MM/yyyy format.");
        }
    }

    static LocalDate financialYearStart(LocalDate date) {
        int year = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return LocalDate.of(year, 4, 1);
    }

    private static boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
        return date != null && !date.isBefore(start) && !date.isAfter(end);
    }

    private static int parseSequence(String text) {
        try {
            return Integer.parseInt(text.replaceAll("\\D", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String period(LocalDate start, LocalDate end) {
        return (start != null ? start.format(BILL_DATE) : "?") + " to " + (end != null ? end.format(BILL_DATE) : "?");
    }

    private static String label(AmcContract contract) {
        return contract.getContractNumber() != null ? contract.getContractNumber() : "#" + contract.getId();
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static BillResponse toResponse(Bill b) {
        return new BillResponse(b.getId(), b.getInvoiceNumber(), b.getCustomerId(), b.getCustomerName(), b.getAmcContractId(),
                b.getBillType(), b.getContractType(), b.getPaymentFrequency(), b.getInstalmentNo(), b.getTotalInstalments(),
                b.getPeriodStart(), b.getPeriodEnd(), b.getInvoiceDate(), b.getTaxableAmount(), b.getGstAmount(),
                b.getTotalAmount(), b.getStatus(), b.getPaidDate(), b.getPaymentReference(), b.getCreatedBy(), b.getCreatedAt());
    }
}
