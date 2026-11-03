package com.apollo.elevator.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apollo.elevator.billing.model.dto.BillResponse;
import com.apollo.elevator.billing.model.dto.CreateBillRequest;
import com.apollo.elevator.billing.model.entity.Bill;
import com.apollo.elevator.billing.model.enums.BillStatus;
import com.apollo.elevator.billing.model.enums.BillType;
import com.apollo.elevator.billing.model.enums.PaymentFrequency;
import com.apollo.elevator.billing.repository.BillRepository;
import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.customer.model.entity.AmcContract;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.model.entity.Lift;
import com.apollo.elevator.customer.model.enums.ContractType;
import com.apollo.elevator.customer.repository.AmcContractRepository;
import com.apollo.elevator.customer.repository.CustomerRepository;
import com.apollo.elevator.documents.model.dto.BillRequest;
import com.apollo.elevator.documents.service.DocumentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class BillingServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private AmcContractRepository amcContractRepository;
    @Mock private BillRepository billRepository;
    @Mock private AmcPlanPriceService planPriceService;
    @Mock private DocumentService documentService;

    private BillingService service;
    private Customer customer;
    private AmcContract contract;

    private static final LocalDate YEAR_START = LocalDate.of(2026, 4, 1);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ObjectMapper mapper = JsonMapper.builder().build();
        service = new BillingService(customerRepository, amcContractRepository, billRepository, planPriceService,
                documentService, mapper);

        contract = new AmcContract();
        contract.setId(50L);
        contract.setContractNumber("AMC-50");
        contract.setStartDate(YEAR_START);
        contract.setEndDate(LocalDate.of(2027, 3, 31));
        contract.setContractType(ContractType.SILVER);
        contract.setPaymentFrequency("QUARTERLY");

        Lift lift = new Lift();
        lift.setId(5L);
        lift.setSerialNumber("LIFT-1");
        lift.setAmcContracts(new ArrayList<>(List.of(contract)));

        customer = new Customer();
        customer.setId(1L);
        customer.setCustomerName("Acme Towers");
        customer.setLifts(new ArrayList<>(List.of(lift)));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(planPriceService.priceOf(ContractType.GOLD)).thenReturn(new BigDecimal("12000.00"));
        when(planPriceService.priceOf(ContractType.SILVER)).thenReturn(new BigDecimal("8000.00"));
        when(planPriceService.priceOf(ContractType.PLATINUM)).thenReturn(new BigDecimal("20000.00"));
        when(documentService.computeBillTotals(any())).thenAnswer(inv -> {
            BillRequest r = inv.getArgument(0);
            BigDecimal taxable = r.lineItems().stream().map(i -> BigDecimal.valueOf(i.amount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal gst = taxable.multiply(new BigDecimal("0.18"));
            return new DocumentService.BillTotals(taxable, gst, taxable.add(gst));
        });
        when(billRepository.saveAndFlush(any(Bill.class))).thenAnswer(inv -> {
            Bill b = inv.getArgument(0);
            b.setId(99L);
            return b;
        });
    }

    @Test
    void instalmentAmount_splitsEquallyAndLastAbsorbsRemainder() {
        BigDecimal plan = new BigDecimal("10000.00");
        assertEquals(new BigDecimal("3333.33"), BillingService.instalmentAmount(plan, 3, 1));
        assertEquals(new BigDecimal("3333.34"), BillingService.instalmentAmount(plan, 3, 3));
        assertEquals(new BigDecimal("1000.00"), BillingService.instalmentAmount(new BigDecimal("12000"), 12, 12));
    }

    @Test
    void instalmentPeriods_coverTheAmcYear() {
        LocalDate end = LocalDate.of(2027, 3, 31);
        assertEquals(LocalDate.of(2026, 7, 1), BillingService.instalmentStart(YEAR_START, PaymentFrequency.QUARTERLY, 2));
        assertEquals(LocalDate.of(2026, 9, 30), BillingService.instalmentEnd(YEAR_START, end, PaymentFrequency.QUARTERLY, 2));
        assertEquals(end, BillingService.instalmentEnd(YEAR_START, end, PaymentFrequency.QUARTERLY, 4));
    }

    @Test
    void newAmcContract_autofillsFromPlanAndStartDate() {
        LocalDate today = LocalDate.of(2026, 5, 10);
        AmcContract c = BillingService.newAmcContract(new com.apollo.elevator.billing.model.dto.NewAmcContractRequest(
                1L, ContractType.PLATINUM, null, null, null), new BigDecimal("20000.00"), today);

        assertEquals(com.apollo.elevator.customer.model.enums.AmcStatus.ACTIVE, c.getStatus());
        assertEquals(new BigDecimal("20000.00"), c.getAmcAmount());
        assertEquals("YEARLY", c.getPaymentFrequency());
        assertEquals(today, c.getStartDate());
        assertEquals(LocalDate.of(2027, 5, 9), c.getEndDate());
        assertEquals(today, c.getNextPaymentDate());
        assertEquals(LocalDate.of(2026, 6, 10), c.getNextServiceDate());

        AmcContract future = BillingService.newAmcContract(new com.apollo.elevator.billing.model.dto.NewAmcContractRequest(
                1L, ContractType.GOLD, PaymentFrequency.QUARTERLY, today.plusDays(5), null), BigDecimal.TEN, today);
        assertEquals(com.apollo.elevator.customer.model.enums.AmcStatus.PENDING, future.getStatus());
        assertEquals("QUARTERLY", future.getPaymentFrequency());
    }

    @Test
    void resolvePlan_firstBill_usesRequestedTierAndFrequency() {
        noExistingBills();
        BillingService.AmcPlan plan = service.resolveAmcPlan(customer, 50L, ContractType.GOLD, PaymentFrequency.HALF_YEARLY, true);

        assertEquals(ContractType.GOLD, plan.tier());
        assertEquals(1, plan.instalmentNo());
        assertEquals(2, plan.totalInstalments());
        assertEquals(new BigDecimal("6000.00"), plan.instalmentAmount());
        assertEquals(LocalDate.of(2026, 9, 30), plan.periodEnd());
    }

    @Test
    void resolvePlan_defaultsToContractTierAndFrequency() {
        noExistingBills();
        BillingService.AmcPlan plan = service.resolveAmcPlan(customer, 50L, null, null, false);

        assertEquals(ContractType.SILVER, plan.tier());
        assertEquals(PaymentFrequency.QUARTERLY, plan.frequency());
        assertEquals(new BigDecimal("2000.00"), plan.instalmentAmount());
    }

    @Test
    void resolvePlan_afterFirstInstalment_locksPlanAndReturnsNextInstalment() {
        existingBills(bill(1, ContractType.GOLD, PaymentFrequency.QUARTERLY, "12000.00"));

        BillingService.AmcPlan plan = service.resolveAmcPlan(customer, 50L, null, null, true);

        assertTrue(plan.locked());
        assertEquals(ContractType.GOLD, plan.tier());
        assertEquals(2, plan.instalmentNo());
        assertEquals(new BigDecimal("3000.00"), plan.instalmentAmount());
        assertEquals(LocalDate.of(2026, 7, 1), plan.periodStart());
    }

    @Test
    void resolvePlan_changingLockedPlan_isRejected() {
        existingBills(bill(1, ContractType.GOLD, PaymentFrequency.QUARTERLY, "12000.00"));

        assertThrows(ConflictException.class,
                () -> service.resolveAmcPlan(customer, 50L, ContractType.PLATINUM, null, true));
    }

    @Test
    void resolvePlan_fullyBilledAmcYear_isRejected() {
        existingBills(bill(1, ContractType.GOLD, PaymentFrequency.YEARLY, "12000.00"));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.resolveAmcPlan(customer, 50L, ContractType.GOLD, PaymentFrequency.YEARLY, true));
        assertTrue(ex.getMessage().contains("already billed"));
    }

    @Test
    void create_amcBill_forcesPlanAmount_savesBill_andUpdatesContract() {
        noExistingBills();
        when(billRepository.existsByInvoiceNumberIgnoreCase(anyString())).thenReturn(false);

        BillRequest request = billRequest("AE/2026-27/0001",
                new BillRequest.BillLineItem(1, "My AMC text", "99611", "1", 1.0, 1.0),
                new BillRequest.BillLineItem(2, "Door sensor replacement", "8431", "1", 1500.0, 1500.0));
        BillResponse response = service.create(new CreateBillRequest(1L, BillType.AMC, 50L, ContractType.GOLD,
                PaymentFrequency.HALF_YEARLY, request), "admin");

        ArgumentCaptor<Bill> saved = ArgumentCaptor.forClass(Bill.class);
        verify(billRepository).saveAndFlush(saved.capture());
        Bill bill = saved.getValue();
        assertEquals(BillType.AMC, bill.getBillType());
        assertEquals(1, bill.getInstalmentNo());
        assertEquals(new BigDecimal("6000.00"), bill.getAmcBaseAmount());
        assertEquals(0, new BigDecimal("7500").compareTo(bill.getTaxableAmount()));
        assertTrue(bill.getRequestJson().contains("My AMC text"));
        assertEquals(99L, response.id());

        assertEquals(ContractType.GOLD, contract.getContractType());
        assertEquals(new BigDecimal("12000.00"), contract.getAmcAmount());
        assertEquals("HALF_YEARLY", contract.getPaymentFrequency());
        assertEquals(LocalDate.of(2026, 10, 1), contract.getNextPaymentDate());
        verify(amcContractRepository).save(contract);
    }

    @Test
    void create_duplicateInvoiceNumber_isRejected() {
        when(billRepository.existsByInvoiceNumberIgnoreCase("AE/2026-27/0001")).thenReturn(true);
        BillRequest request = billRequest("AE/2026-27/0001", new BillRequest.BillLineItem(1, "Repair", "", "1", 10.0, 10.0));

        assertThrows(ConflictException.class, () -> service.create(
                new CreateBillRequest(1L, BillType.MAINTENANCE, null, null, null, request), "admin"));
        verify(billRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_maintenanceBill_doesNotTouchContract() {
        when(billRepository.existsByInvoiceNumberIgnoreCase(anyString())).thenReturn(false);
        BillRequest request = billRequest("AE/2026-27/0002", new BillRequest.BillLineItem(1, "Rope change", "", "1", 5000.0, 5000.0));

        BillResponse response = service.create(new CreateBillRequest(1L, BillType.MAINTENANCE, null, null, null, request), "admin");

        assertEquals(BillType.MAINTENANCE, response.billType());
        verify(amcContractRepository, never()).save(any());
    }

    @Test
    void summary_countsPaidByPaidDateAndProjectsUnbilledInstalments() {
        Bill paid = bill(1, ContractType.GOLD, PaymentFrequency.QUARTERLY, "12000.00");
        paid.setStatus(BillStatus.PAID);
        paid.setPaidDate(LocalDate.of(2026, 4, 10));
        paid.setInvoiceDate(LocalDate.of(2026, 4, 2));
        paid.setTotalAmount(new BigDecimal("3540.00"));
        when(billRepository.findByStatusNot(BillStatus.CANCELLED)).thenReturn(List.of(paid));

        var summary = service.summary(YEAR_START, LocalDate.of(2027, 3, 31));

        assertEquals(new BigDecimal("3540.00"), summary.paid());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.outstanding()));
        assertEquals(new BigDecimal("10620.00"), summary.projectedInstalments());
        assertEquals(12, summary.monthly().size());
    }

    // -------------------------------------------------------------------------

    private void noExistingBills() {
        when(billRepository.findByAmcContractIdAndAmcPeriodStartAndStatusNot(eq(50L), eq(YEAR_START), eq(BillStatus.CANCELLED)))
                .thenReturn(List.of());
    }

    private void existingBills(Bill... bills) {
        when(billRepository.findByAmcContractIdAndAmcPeriodStartAndStatusNot(eq(50L), eq(YEAR_START), eq(BillStatus.CANCELLED)))
                .thenReturn(List.of(bills));
    }

    private static Bill bill(int instalmentNo, ContractType tier, PaymentFrequency frequency, String planAmount) {
        return Bill.builder()
                .id((long) instalmentNo)
                .amcContractId(50L)
                .amcPeriodStart(YEAR_START)
                .instalmentNo(instalmentNo)
                .totalInstalments(frequency.instalments())
                .contractType(tier)
                .paymentFrequency(frequency)
                .planAmount(new BigDecimal(planAmount))
                .gstPercentage(new BigDecimal("18.00"))
                .status(BillStatus.GENERATED)
                .build();
    }

    private static BillRequest billRequest(String invoiceNumber, BillRequest.BillLineItem... items) {
        return new BillRequest(null,
                new BillRequest.BillDetails(invoiceNumber, "15/04/2026", "Karnataka", null, null, null, null, 18.0, 9.0, 9.0),
                new BillRequest.BillToParty("Acme Towers", "Bangalore", null, null, null, null, "Karnataka"),
                List.of(items));
    }
}
