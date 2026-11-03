package com.apollo.elevator.servicevisit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.customer.model.entity.AmcContract;
import com.apollo.elevator.customer.model.entity.ServiceHistory;
import com.apollo.elevator.customer.model.enums.AmcStatus;
import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.customer.repository.AmcContractRepository;
import com.apollo.elevator.engineer.service.EngineerDirectory;
import com.apollo.elevator.servicevisit.model.dto.RescheduleVisitRequest;
import com.apollo.elevator.servicevisit.model.dto.ServiceVisitRow;
import com.apollo.elevator.servicevisit.model.entity.ServiceVisitReschedule;
import com.apollo.elevator.servicevisit.model.enums.IssueRaisedBy;
import com.apollo.elevator.servicevisit.repository.ServiceVisitQueryRepository;
import com.apollo.elevator.servicevisit.repository.ServiceVisitRescheduleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ServiceVisitServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

    @Mock private ServiceVisitQueryRepository queryRepository;
    @Mock private ServiceVisitRescheduleRepository rescheduleRepository;
    @Mock private AmcContractRepository amcContractRepository;
    @Mock private EngineerDirectory engineerDirectory;

    private ServiceVisitService service;
    private AmcContract contract;
    private ServiceHistory completedVisit;
    private ServiceHistory scheduledVisit;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new ServiceVisitService(queryRepository, rescheduleRepository, amcContractRepository,
                engineerDirectory, clock);

        completedVisit = ServiceHistory.builder().id(10L).serviceDate(TODAY.minusDays(5))
                .engineerName("Ravi").assignedEngineerId(7L).workDone("Routine service")
                .status(ServiceVisitStatus.COMPLETED).build();
        scheduledVisit = ServiceHistory.builder().id(11L).serviceDate(TODAY.plusDays(3))
                .engineerName("Ravi").assignedEngineerId(7L).workDone("Routine service")
                .status(ServiceVisitStatus.SCHEDULED).build();
        contract = AmcContract.builder().id(5L).status(AmcStatus.ACTIVE)
                .nextServiceDate(TODAY.plusDays(3))
                .serviceHistory(new ArrayList<>(List.of(completedVisit, scheduledVisit)))
                .totalServices(2).completedServices(1).build();

        when(queryRepository.findContractIdOfVisit(anyLong())).thenReturn(Optional.of(5L));
        when(amcContractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(queryRepository.findRow(anyLong())).thenReturn(Optional.of(new ServiceVisitRow(1L, TODAY,
                ServiceVisitStatus.SCHEDULED, "Ravi", 7L, "x", 5L, "C-1", "ACTIVE", 2L, null, null, 3L, "CUS1",
                "Customer", "City", 0, null, null, null, null)));
    }

    @Test
    void completedVisitWithIssueGetsFollowUpVisit() {
        assignIdOnFlush(99L);

        service.reschedule(10L, request(TODAY.plusDays(1), null), "admin");

        assertEquals(3, contract.getServiceHistory().size());
        ServiceHistory followUp = contract.getServiceHistory().get(2);
        assertEquals(ServiceVisitStatus.SCHEDULED, followUp.getStatus());
        assertEquals(TODAY.plusDays(1), followUp.getServiceDate());
        assertEquals("Ravi", followUp.getEngineerName());
        assertEquals("Re-service: Lift still jerking", followUp.getWorkDone());
        assertEquals(ServiceVisitStatus.COMPLETED, completedVisit.getStatus());
        assertEquals(3, contract.getTotalServices());
        assertEquals(1, contract.getCompletedServices());
        assertEquals(TODAY.plusDays(1), contract.getNextServiceDate());

        ArgumentCaptor<ServiceVisitReschedule> audit = ArgumentCaptor.forClass(ServiceVisitReschedule.class);
        verify(rescheduleRepository).save(audit.capture());
        assertEquals(10L, audit.getValue().getServiceVisitId());
        assertEquals(99L, audit.getValue().getNewServiceVisitId());
        assertEquals(IssueRaisedBy.CUSTOMER, audit.getValue().getRaisedBy());
        assertEquals(ServiceVisitStatus.COMPLETED, audit.getValue().getPreviousStatus());
        assertEquals("admin", audit.getValue().getRescheduledBy());
        verify(queryRepository).findRow(99L);
    }

    @Test
    void scheduledVisitIsMovedInPlaceWithNewEngineer() {
        when(engineerDirectory.lookup()).thenReturn(new EngineerDirectory.Lookup(
                Map.of("suresh", Set.of(8L)), Map.of(7L, "Ravi", 8L, "Suresh")));

        service.reschedule(11L, request(TODAY.plusDays(7), 8L), "admin");

        assertEquals(2, contract.getServiceHistory().size());
        assertEquals(TODAY.plusDays(7), scheduledVisit.getServiceDate());
        assertEquals(8L, scheduledVisit.getAssignedEngineerId());
        assertEquals("Suresh", scheduledVisit.getEngineerName());
        assertEquals(TODAY.plusDays(7), contract.getNextServiceDate());

        ArgumentCaptor<ServiceVisitReschedule> audit = ArgumentCaptor.forClass(ServiceVisitReschedule.class);
        verify(rescheduleRepository).save(audit.capture());
        assertEquals(11L, audit.getValue().getNewServiceVisitId());
        assertEquals("Ravi", audit.getValue().getPreviousEngineerName());
        assertEquals("Suresh", audit.getValue().getNewEngineerName());
    }

    @Test
    void rejectsPastDate() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.reschedule(10L, request(TODAY.minusDays(1), null), "admin"));
        assertTrue(error.getMessage().contains("past"));
        verify(rescheduleRepository, never()).save(any());
    }

    @Test
    void rejectsUnchangedScheduledVisit() {
        assertThrows(IllegalArgumentException.class,
                () -> service.reschedule(11L, request(TODAY.plusDays(3), null), "admin"));
    }

    @Test
    void rejectsSecondFollowUpWhileOneIsOpen() {
        when(rescheduleRepository.findOpenFollowUp(10L)).thenReturn(java.util.Optional.of(42L));

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.reschedule(10L, request(TODAY.plusDays(1), null), "admin"));
        assertTrue(error.getMessage().contains("#42"));
        assertEquals(2, contract.getServiceHistory().size());
    }

    @Test
    void rejectsCancelledContract() {
        contract.setStatus(AmcStatus.CANCELLED);
        assertThrows(IllegalArgumentException.class,
                () -> service.reschedule(10L, request(TODAY.plusDays(1), null), "admin"));
    }

    @Test
    void rejectsUnknownEngineer() {
        when(engineerDirectory.lookup()).thenReturn(new EngineerDirectory.Lookup(Map.of(), Map.of(7L, "Ravi")));
        assertThrows(IllegalArgumentException.class,
                () -> service.reschedule(11L, request(TODAY.plusDays(7), 123L), "admin"));
        assertEquals(TODAY.plusDays(3), scheduledVisit.getServiceDate());
    }

    private void assignIdOnFlush(Long id) {
        doAnswer(invocation -> {
            contract.getServiceHistory().stream().filter(h -> h.getId() == null).forEach(h -> h.setId(id));
            return null;
        }).when(amcContractRepository).flush();
    }

    private static RescheduleVisitRequest request(LocalDate date, Long engineerId) {
        return new RescheduleVisitRequest(date, IssueRaisedBy.CUSTOMER, "  Lift still jerking  ", engineerId, null);
    }
}
