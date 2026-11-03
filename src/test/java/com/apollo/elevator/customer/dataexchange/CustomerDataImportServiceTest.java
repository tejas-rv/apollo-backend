package com.apollo.elevator.customer.dataexchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apollo.elevator.customer.dataexchange.dto.CustomerDataSource;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportMode;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportOutcome;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportResponse;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportStatus;
import com.apollo.elevator.customer.model.entity.AmcContract;
import com.apollo.elevator.customer.model.entity.ClientRepresentative;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.model.entity.Lift;
import com.apollo.elevator.customer.model.entity.ServiceHistory;
import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.customer.repository.CustomerRepository;
import com.apollo.elevator.engineer.service.EngineerDirectory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

class CustomerDataImportServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private EngineerDirectory engineerDirectory;

    @InjectMocks
    private CustomerDataImportService importService;

    private final List<Customer> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(engineerDirectory.lookup()).thenReturn(new EngineerDirectory.Lookup(Map.of(), Map.of()));
        AtomicLong ids = new AtomicLong(100);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            assignId(customer, Customer::getId, customer::setId, ids);
            for (Lift lift : customer.getLifts()) {
                assignId(lift, Lift::getId, lift::setId, ids);
                for (AmcContract contract : lift.getAmcContracts()) {
                    assignId(contract, AmcContract::getId, contract::setId, ids);
                    for (ServiceHistory history : contract.getServiceHistory()) {
                        assignId(history, ServiceHistory::getId, history::setId, ids);
                    }
                }
            }
            saved.add(customer);
            return customer;
        });
    }

    @Test
    void importsBundledSampleWorkbook() throws IOException {
        CustomerImportResponse response = importService.importWorkbook(sample(), "sample.xlsx", CustomerDataSource.UPLOAD);

        assertTrue(response.totalCustomers() > 0);
        assertEquals(response.totalCustomers(), response.importedCount(), () -> response.customers().toString());
        assertEquals(0, response.failedCount());
        assertTrue(response.unlinkedRowErrors().isEmpty(), () -> response.unlinkedRowErrors().toString());
        assertTrue(saved.stream().anyMatch(customer -> !customer.getLifts().isEmpty()));
    }

    @Test
    void reimportingUnchangedExportSkipsEveryCustomer() throws IOException {
        List<Customer> stored = importSampleAsStored();
        byte[] exported = export(stored);

        CustomerImportResponse response = importService.importWorkbook(exported, "export.xlsx", CustomerDataSource.UPLOAD);

        assertEquals(response.totalCustomers(), response.skippedCount(), () -> response.customers().toString());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void updatesLiftOfExistingCustomer() throws IOException {
        List<Customer> stored = importSampleAsStored();
        int liftsBefore = totalLifts(stored);
        byte[] content = modify(export(stored), workbook -> setCell(workbook, CustomerWorkbookSchema.LIFTS_SHEET,
                1, "brand", "Updated Brand"));

        CustomerImportResponse response = importService.importWorkbook(content, "export.xlsx", CustomerDataSource.UPLOAD);

        assertEquals(1, response.updatedCount(), () -> response.customers().toString());
        assertEquals(response.totalCustomers() - 1, response.skippedCount());
        assertEquals(0, response.importedCount());
        assertEquals(liftsBefore, totalLifts(stored));
        assertTrue(stored.stream().flatMap(customer -> customer.getLifts().stream())
                .anyMatch(lift -> "Updated Brand".equals(lift.getBrand())));
    }

    @Test
    void addsNewLiftToExistingCustomer() throws IOException {
        List<Customer> stored = importSampleAsStored();
        int liftsBefore = totalLifts(stored);
        byte[] content = modify(export(stored), workbook -> {
            Sheet lifts = workbook.getSheet(CustomerWorkbookSchema.LIFTS_SHEET);
            Row source = lifts.getRow(1);
            Row copy = lifts.createRow(lifts.getLastRowNum() + 1);
            for (int index = 0; index < source.getLastCellNum(); index++) {
                if (source.getCell(index) != null) {
                    copy.createCell(index).setCellValue(new org.apache.poi.ss.usermodel.DataFormatter()
                            .formatCellValue(source.getCell(index)));
                }
            }
            setCell(workbook, CustomerWorkbookSchema.LIFTS_SHEET, copy.getRowNum(), CustomerWorkbookSchema.LIFT_KEY, "NEW-1");
            setCell(workbook, CustomerWorkbookSchema.LIFTS_SHEET, copy.getRowNum(), "serialNumber", "SN-NEW-0001");
        });

        CustomerImportResponse response = importService.importWorkbook(content, "export.xlsx", CustomerDataSource.UPLOAD);

        assertEquals(1, response.updatedCount(), () -> response.customers().toString());
        assertEquals(liftsBefore + 1, totalLifts(stored));
    }

    @Test
    void invalidChangeToExistingCustomerFails() throws IOException {
        List<Customer> stored = importSampleAsStored();
        byte[] content = modify(export(stored), workbook -> setCell(workbook, CustomerWorkbookSchema.AMC_CONTRACTS_SHEET,
                1, "amcAmount", "-5"));

        CustomerImportResponse response = importService.importWorkbook(content, "export.xlsx", CustomerDataSource.UPLOAD);

        assertEquals(1, response.failedCount(), () -> response.customers().toString());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    private List<Customer> importSampleAsStored() throws IOException {
        importService.importWorkbook(sample(), "sample.xlsx", CustomerDataSource.UPLOAD);
        List<Customer> stored = List.copyOf(saved);
        saved.clear();
        org.mockito.Mockito.clearInvocations(customerRepository);
        when(customerRepository.findByCustomerCode(anyString())).thenAnswer(invocation -> stored.stream()
                .filter(customer -> customer.getCustomerCode().equals(invocation.getArgument(0)))
                .findFirst());
        return stored;
    }

    private byte[] export(List<Customer> customers) {
        when(customerRepository.findAll(any(Sort.class))).thenReturn(customers);
        return new CustomerDataExportService(customerRepository).exportWorkbook().content();
    }

    private static int totalLifts(List<Customer> customers) {
        return customers.stream().mapToInt(customer -> customer.getLifts().size()).sum();
    }

    @Test
    void failsOnlyTheCustomerWithInvalidMobile() throws IOException {
        byte[] content = modify(sample(), workbook -> setCell(workbook, CustomerWorkbookSchema.CUSTOMERS_SHEET,
                1, "mobileNumber", "12345"));

        CustomerImportResponse response = importService.importWorkbook(content, "sample.xlsx", CustomerDataSource.UPLOAD);

        assertEquals(1, response.failedCount());
        assertEquals(response.totalCustomers() - 1, response.importedCount());
        CustomerImportOutcome failed = response.customers().stream()
                .filter(outcome -> outcome.status() == CustomerImportStatus.FAILED)
                .findFirst().orElseThrow();
        assertTrue(failed.messages().stream().anyMatch(message -> message.contains("mobileNumber")),
                failed.messages()::toString);
    }

    @Test
    void reportsAmcRowsWithUnknownLift() throws IOException {
        byte[] content = modify(sample(), workbook -> setCell(workbook, CustomerWorkbookSchema.AMC_CONTRACTS_SHEET,
                1, CustomerWorkbookSchema.LIFT_KEY, "999999"));

        CustomerImportResponse response = importService.importWorkbook(content, "sample.xlsx", CustomerDataSource.UPLOAD);

        assertFalse(response.unlinkedRowErrors().isEmpty());
    }

    @Test
    void rejectsWorkbookWithoutCustomersSheet() throws IOException {
        byte[] content = modify(sample(), workbook -> workbook.removeSheetAt(
                workbook.getSheetIndex(CustomerWorkbookSchema.CUSTOMERS_SHEET)));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> importService.importWorkbook(content, "sample.xlsx", CustomerDataSource.UPLOAD));
    }

    @Test
    void exportedWorkbookCanBeImportedAgain() throws IOException {
        CustomerImportResponse first = importService.importWorkbook(sample(), "sample.xlsx", CustomerDataSource.UPLOAD);
        List<Customer> exportedCustomers = List.copyOf(saved);
        saved.clear();

        when(customerRepository.findAll(any(Sort.class))).thenReturn(exportedCustomers);
        CustomerDataExportService.ExportedWorkbook exported =
                new CustomerDataExportService(customerRepository).exportWorkbook();

        CustomerImportResponse second = importService.importWorkbook(
                exported.content(), CustomerWorkbookSchema.FILE_NAME, CustomerDataSource.UPLOAD);

        assertEquals(first.importedCount(), exported.customerCount());
        assertEquals(first.importedCount(), second.importedCount(), () -> second.customers().toString());
        assertTrue(second.unlinkedRowErrors().isEmpty(), () -> second.unlinkedRowErrors().toString());
        assertEquals(totalAmcContracts(exportedCustomers), totalAmcContracts(saved));
    }

    private static int totalAmcContracts(List<Customer> customers) {
        return customers.stream().flatMap(customer -> customer.getLifts().stream())
                .mapToInt(lift -> lift.getAmcContracts().size()).sum();
    }

    @Test
    void editingEngineerOfExportedVisitUpdatesItInsteadOfDuplicating() throws IOException {
        List<Customer> stored = importSampleAsStored();
        AmcContract contract = firstContract(stored);
        contract.getServiceHistory().clear();
        contract.getServiceHistory().add(visit(9001L, ServiceVisitStatus.SCHEDULED, "Ravi"));
        contract.getServiceHistory().add(visit(9002L, ServiceVisitStatus.COMPLETED, "Ravi"));
        byte[] content = modify(export(stored), workbook -> setCell(workbook, CustomerWorkbookSchema.SERVICE_HISTORY_SHEET,
                rowWith(workbook, CustomerWorkbookSchema.SERVICE_HISTORY_SHEET, CustomerWorkbookSchema.VISIT_KEY, "9001"),
                "engineerName", "Suresh"));

        CustomerImportResponse response = importService.importWorkbook(content, "export.xlsx", CustomerDataSource.UPLOAD);

        assertEquals(1, response.updatedCount(), () -> response.customers().toString());
        assertEquals(2, contract.getServiceHistory().size());
        assertEquals("Suresh", visitById(contract, 9001L).getEngineerName());
        assertEquals("Ravi", visitById(contract, 9002L).getEngineerName());
    }

    @Test
    void mergeKeepsRowsMissingFromFile() throws IOException {
        List<Customer> stored = importSampleAsStored();
        AmcContract contract = firstContract(stored);
        Customer owner = ownerOf(stored, contract);
        byte[] exported = export(stored);
        contract.getServiceHistory().add(visit(9003L, ServiceVisitStatus.SCHEDULED, "Ravi"));
        owner.getClientRepresentatives().add(ClientRepresentative.builder().name("Extra Rep").mobileNumber("9876500000").build());
        int visitsBefore = contract.getServiceHistory().size();
        int repsBefore = owner.getClientRepresentatives().size();

        importService.importWorkbook(exported, "export.xlsx", CustomerDataSource.UPLOAD, CustomerImportMode.MERGE);

        assertEquals(visitsBefore, contract.getServiceHistory().size());
        assertEquals(repsBefore, owner.getClientRepresentatives().size());
    }

    @Test
    void overrideRemovesVisitsAndRepresentativesMissingFromFile() throws IOException {
        List<Customer> stored = importSampleAsStored();
        AmcContract contract = firstContract(stored);
        Customer owner = ownerOf(stored, contract);
        contract.getServiceHistory().clear();
        contract.getServiceHistory().add(visit(9001L, ServiceVisitStatus.SCHEDULED, "Ravi"));
        contract.getServiceHistory().add(visit(9002L, ServiceVisitStatus.COMPLETED, "Ravi"));
        byte[] exported = export(stored);
        contract.getServiceHistory().add(visit(9003L, ServiceVisitStatus.SCHEDULED, "Ravi"));
        owner.getClientRepresentatives().add(ClientRepresentative.builder().name("Extra Rep").mobileNumber("9876500000").build());
        int repsInFile = owner.getClientRepresentatives().size() - 1;

        CustomerImportResponse response = importService.importWorkbook(exported, "export.xlsx",
                CustomerDataSource.UPLOAD, CustomerImportMode.OVERRIDE);

        assertEquals(CustomerImportMode.OVERRIDE, response.mode());
        assertEquals(List.of(9001L, 9002L), contract.getServiceHistory().stream().map(ServiceHistory::getId).toList());
        assertEquals(repsInFile, owner.getClientRepresentatives().size());
        assertFalse(owner.getClientRepresentatives().stream().anyMatch(rep -> "Extra Rep".equals(rep.getName())));
    }

    @Test
    void overrideKeepsCompletedVisitsWhenFileWasExportedWithoutThem() throws IOException {
        List<Customer> stored = importSampleAsStored();
        AmcContract contract = firstContract(stored);
        contract.getServiceHistory().clear();
        contract.getServiceHistory().add(visit(9001L, ServiceVisitStatus.SCHEDULED, "Ravi"));
        contract.getServiceHistory().add(visit(9002L, ServiceVisitStatus.COMPLETED, "Ravi"));
        when(customerRepository.findAll(any(Sort.class))).thenReturn(stored);
        byte[] exported = new CustomerDataExportService(customerRepository).exportWorkbook(false).content();
        contract.getServiceHistory().add(visit(9003L, ServiceVisitStatus.SCHEDULED, "Ravi"));

        importService.importWorkbook(exported, "export.xlsx", CustomerDataSource.UPLOAD, CustomerImportMode.OVERRIDE);

        assertEquals(List.of(9001L, 9002L), contract.getServiceHistory().stream().map(ServiceHistory::getId).toList());
    }

    private static <T> void assignId(T entity, java.util.function.Function<T, Long> getter,
                                     java.util.function.Consumer<Long> setter, AtomicLong ids) {
        if (getter.apply(entity) == null) {
            setter.accept(ids.incrementAndGet());
        }
    }

    private static AmcContract firstContract(List<Customer> customers) {
        return customers.stream().flatMap(customer -> customer.getLifts().stream())
                .flatMap(lift -> lift.getAmcContracts().stream())
                .findFirst().orElseThrow();
    }

    private static Customer ownerOf(List<Customer> customers, AmcContract contract) {
        return customers.stream().filter(customer -> customer.getLifts().stream()
                .anyMatch(lift -> lift.getAmcContracts().contains(contract))).findFirst().orElseThrow();
    }

    private static ServiceHistory visit(Long id, ServiceVisitStatus status, String engineer) {
        ServiceHistory visit = new ServiceHistory();
        visit.setId(id);
        visit.setServiceDate(java.time.LocalDate.of(2025, 6, 1).plusDays(id % 100));
        visit.setEngineerName(engineer);
        visit.setWorkDone("Routine maintenance");
        visit.setStatus(status);
        return visit;
    }

    private static ServiceHistory visitById(AmcContract contract, Long id) {
        return contract.getServiceHistory().stream().filter(visit -> id.equals(visit.getId())).findFirst().orElseThrow();
    }

    private static int rowWith(Workbook workbook, String sheetName, String column, String value) {
        Sheet sheet = workbook.getSheet(sheetName);
        Row header = sheet.getRow(0);
        org.apache.poi.ss.usermodel.DataFormatter formatter = new org.apache.poi.ss.usermodel.DataFormatter();
        for (int index = 0; index < header.getLastCellNum(); index++) {
            if (column.equalsIgnoreCase(header.getCell(index).getStringCellValue().trim())) {
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row != null && value.equals(formatter.formatCellValue(row.getCell(index)))) {
                        return rowIndex;
                    }
                }
            }
        }
        throw new IllegalStateException("Row not found: " + column + "=" + value);
    }

    private static byte[] sample() throws IOException {
        try (InputStream input = CustomerDataImportServiceTest.class.getResourceAsStream(
                "/samples/customer-import-sample.xlsx")) {
            return input.readAllBytes();
        }
    }

    private interface WorkbookChange {
        void apply(Workbook workbook);
    }

    private static byte[] modify(byte[] content, WorkbookChange change) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(content));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            change.apply(workbook);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void setCell(Workbook workbook, String sheetName, int rowIndex, String column, String value) {
        Sheet sheet = workbook.getSheet(sheetName);
        Row header = sheet.getRow(0);
        for (int index = 0; index < header.getLastCellNum(); index++) {
            if (column.equalsIgnoreCase(header.getCell(index).getStringCellValue().trim())) {
                Row row = sheet.getRow(rowIndex);
                (row.getCell(index) == null ? row.createCell(index) : row.getCell(index)).setCellValue(value);
                return;
            }
        }
        throw new IllegalStateException("Column not found: " + column);
    }
}
