package com.apollo.elevator.customer.dataexchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apollo.elevator.customer.model.entity.AmcContract;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.model.entity.Lift;
import com.apollo.elevator.customer.model.entity.ServiceHistory;
import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.customer.repository.CustomerRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class CustomerDataExportServiceTest {

    private final CustomerRepository repository = mock(CustomerRepository.class);

    @Test
    void includesAllVisitStatusesByDefault() throws IOException {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(customerWithVisits()));

        assertEquals(3, historyRows(new CustomerDataExportService(repository).exportWorkbook(true).content()));
    }

    @Test
    void canLeaveCompletedVisitsOut() throws IOException {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(customerWithVisits()));

        assertEquals(2, historyRows(new CustomerDataExportService(repository).exportWorkbook(false).content()));
    }

    private static int historyRows(byte[] content) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            return workbook.getSheet(CustomerWorkbookSchema.SERVICE_HISTORY_SHEET).getLastRowNum();
        }
    }

    private static Customer customerWithVisits() {
        AmcContract contract = new AmcContract();
        contract.setId(5L);
        contract.setServiceHistory(new ArrayList<>(List.of(
                visit(ServiceVisitStatus.COMPLETED),
                visit(ServiceVisitStatus.SCHEDULED),
                visit(ServiceVisitStatus.CANCELLED))));
        Lift lift = new Lift();
        lift.setId(3L);
        lift.setAmcContracts(new ArrayList<>(List.of(contract)));
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setLifts(new ArrayList<>(List.of(lift)));
        customer.setClientRepresentatives(new ArrayList<>());
        return customer;
    }

    private static ServiceHistory visit(ServiceVisitStatus status) {
        ServiceHistory history = new ServiceHistory();
        history.setServiceDate(LocalDate.of(2025, 1, 10));
        history.setEngineerName("Ramesh");
        history.setWorkDone("Checked");
        history.setStatus(status);
        return history;
    }
}
