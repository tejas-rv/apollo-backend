package com.apollo.elevator.engineer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apollo.elevator.customer.model.entity.AmcContract;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.model.entity.Lift;
import com.apollo.elevator.customer.model.entity.ServiceHistory;
import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.engineer.model.entity.ServiceCheckItem;
import com.apollo.elevator.engineer.model.entity.ServiceReport;
import com.apollo.elevator.engineer.model.enums.AnswerType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServiceVisitCompletionTest {

    private static final LocalDate VISIT_DATE = LocalDate.of(2025, 3, 12);

    @Test
    void completesTheEngineersOwnScheduledVisit() {
        ServiceHistory other = scheduled(LocalDate.of(2025, 3, 12), 9L, "Suresh");
        ServiceHistory own = scheduled(LocalDate.of(2025, 3, 1), 7L, "Ramesh");
        AmcContract contract = contract(other, own);

        ServiceHistory result = ServiceVisitCompletion.complete(customer(contract), report(), Set.of("ramesh"))
                .orElseThrow();

        assertSame(own, result);
        assertEquals(ServiceVisitStatus.COMPLETED, own.getStatus());
        assertEquals(VISIT_DATE, own.getServiceDate());
        assertEquals(ServiceVisitStatus.SCHEDULED, other.getStatus());
        assertEquals(1, contract.getCompletedServices());
        assertEquals(2, contract.getTotalServices());
        assertEquals(LocalDate.of(2025, 3, 12), contract.getNextServiceDate());
    }

    @Test
    void linksAnUnassignedVisitCarryingTheEngineersName() {
        ServiceHistory legacy = scheduled(LocalDate.of(2025, 3, 10), null, " Ramesh ");
        AmcContract contract = contract(legacy);

        ServiceVisitCompletion.complete(customer(contract), report(), Set.of("ramesh"));

        assertEquals(ServiceVisitStatus.COMPLETED, legacy.getStatus());
        assertEquals(7L, legacy.getAssignedEngineerId());
        assertEquals("Ramesh Kumar", legacy.getEngineerName());
    }

    @Test
    void addsACompletedVisitWhenNoneWasScheduled() {
        ServiceHistory otherEngineers = scheduled(LocalDate.of(2025, 3, 12), 9L, "Suresh");
        AmcContract contract = contract(otherEngineers);

        ServiceHistory created = ServiceVisitCompletion.complete(customer(contract), report(), Set.of("ramesh"))
                .orElseThrow();

        assertEquals(2, contract.getServiceHistory().size());
        assertEquals(ServiceVisitStatus.COMPLETED, created.getStatus());
        assertTrue(created.getWorkDone().startsWith("Report #42: Replaced door sensor"), created.getWorkDone());
        assertEquals(ServiceVisitStatus.SCHEDULED, otherEngineers.getStatus());
    }

    @Test
    void ignoresReportsWithoutAContract() {
        ServiceReport report = report();
        report.setAmcContractId(null);

        assertTrue(ServiceVisitCompletion.complete(customer(contract()), report, Set.of()).isEmpty());
    }

    private static ServiceReport report() {
        ServiceCheckItem description = ServiceCheckItem.builder()
                .itemOrder(22).answerType(AnswerType.DESCRIPTIVE).answerText("Replaced door sensor").build();
        return ServiceReport.builder()
                .id(42L).amcContractId(5L).engineerUserId(7L).engineerName("Ramesh Kumar")
                .visitDate(VISIT_DATE).checkItems(new ArrayList<>(List.of(description))).build();
    }

    private static ServiceHistory scheduled(LocalDate date, Long engineerId, String name) {
        ServiceHistory history = new ServiceHistory();
        history.setServiceDate(date);
        history.setAssignedEngineerId(engineerId);
        history.setEngineerName(name);
        history.setWorkDone("Planned");
        history.setStatus(ServiceVisitStatus.SCHEDULED);
        return history;
    }

    private static AmcContract contract(ServiceHistory... visits) {
        AmcContract contract = new AmcContract();
        contract.setId(5L);
        contract.setServiceHistory(new ArrayList<>(List.of(visits)));
        return contract;
    }

    private static Customer customer(AmcContract contract) {
        Lift lift = new Lift();
        lift.setAmcContracts(new ArrayList<>(List.of(contract)));
        Customer customer = new Customer();
        customer.setLifts(new ArrayList<>(List.of(lift)));
        return customer;
    }
}
