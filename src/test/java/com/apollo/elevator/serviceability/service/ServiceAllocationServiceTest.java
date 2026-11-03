package com.apollo.elevator.serviceability.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apollo.elevator.authorization.model.entity.User;
import com.apollo.elevator.customer.model.entity.ServiceHistory;
import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.serviceability.config.ServiceabilityProperties;
import com.apollo.elevator.serviceability.model.dto.AssignmentBatchRequest;
import com.apollo.elevator.serviceability.model.dto.EngineerServiceabilityResponse;
import com.apollo.elevator.serviceability.model.dto.ManualServiceAssignmentRequest;
import com.apollo.elevator.serviceability.model.entity.EngineerServiceability;
import com.apollo.elevator.serviceability.model.entity.EngineerZoneAssignment;
import com.apollo.elevator.serviceability.model.entity.PincodeServiceability;
import com.apollo.elevator.serviceability.model.entity.ServiceabilityZone;
import com.apollo.elevator.serviceability.model.enums.EngineerZoneType;
import com.apollo.elevator.serviceability.model.enums.AssignmentStatus;
import com.apollo.elevator.serviceability.model.enums.PostalVerificationStatus;
import com.apollo.elevator.serviceability.model.enums.ServicePriority;
import com.apollo.elevator.serviceability.repository.EngineerServiceabilityRepository;
import com.apollo.elevator.serviceability.repository.EngineerServiceabilityStatsRepository;
import com.apollo.elevator.serviceability.repository.EngineerZoneAssignmentRepository;
import com.apollo.elevator.serviceability.repository.PincodeServiceabilityRepository;
import com.apollo.elevator.serviceability.repository.ServiceAssignmentRepository;
import com.apollo.elevator.serviceability.repository.ServiceHistoryRepository;
import com.apollo.elevator.serviceability.repository.ServiceabilityVisitQueryRepository;
import com.apollo.elevator.serviceability.repository.ServiceabilityVisitQueryRepository.VisitContext;
import com.apollo.elevator.serviceability.repository.ServiceabilityZoneRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ServiceAllocationServiceTest {

    private final LocalDate today = LocalDate.now();

    @Mock private ServiceabilityVisitQueryRepository visitRepository;
    @Mock private PincodeServiceabilityRepository pincodeRepository;
    @Mock private ServiceabilityZoneRepository zoneRepository;
    @Mock private EngineerZoneAssignmentRepository engineerZoneRepository;
    @Mock private EngineerServiceabilityRepository engineerConfigRepository;
    @Mock private EngineerServiceabilityStatsRepository statsRepository;
    @Mock private ServiceAssignmentRepository assignmentRepository;
    @Mock private ServiceHistoryRepository historyRepository;
    @Mock private EngineerServiceabilityService engineerService;

    private ServiceAllocationService service;
    private VisitContext visit;
    private ServiceabilityZone east;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ServiceAllocationService(
                visitRepository, pincodeRepository, zoneRepository, engineerZoneRepository,
                engineerConfigRepository, statsRepository, assignmentRepository, historyRepository,
                new ServiceabilityProperties(), engineerService);
        visit = new VisitContext(23L, today.minusDays(1), null, null,
                "Customer", "Address", "Whitefield", "560066", "LIFT-23");
        east = ServiceabilityZone.builder().zoneId("BLR-EAST").zoneName("Bengaluru East").active(true).build();
        when(visitRepository.findUnassignedScheduledThrough(any(), any())).thenReturn(List.of(visit));
        when(visitRepository.findScheduledForDate(any())).thenReturn(List.of());
        when(pincodeRepository.findCurrentMappings(eq("560066"), any())).thenReturn(List.of(mapping(east, "Whitefield")));
        when(zoneRepository.findById("BLR-EAST")).thenReturn(Optional.of(east));
        when(engineerConfigRepository.findAllByEngineerIdIn(anyList())).thenReturn(List.of());
    }

    @Test
    void blocksAutomaticAssignmentWhenVerifiedPincodeSpansMultipleZones() {
        ServiceabilityZone south = ServiceabilityZone.builder()
                .zoneId("BLR-SOUTH").zoneName("Bengaluru South").active(true).build();
        visit = new VisitContext(23L, today.minusDays(1), null, null,
                "Customer", "Address", "Mahadevapura", "560066", "LIFT-23");
        when(visitRepository.findUnassignedScheduledThrough(any(), any())).thenReturn(List.of(visit));
        when(pincodeRepository.findCurrentMappings(eq("560066"), any()))
                .thenReturn(List.of(mapping(east, "Whitefield"), mapping(south, "Koramangala")));
        when(zoneRepository.findById("BLR-SOUTH")).thenReturn(Optional.of(south));

        var result = service.allocate(new AssignmentBatchRequest(List.of(23L), today, false), "supervisor");

        assertEquals(1, result.blockedCount());
        assertFalse(result.decisions().getFirst().assignable());
        assertTrue(result.decisions().getFirst().reason().contains("multiple or unresolved"));
    }

    @Test
    void prefersAvailableBackupWhenPrimaryEngineerHasReachedDailyCapacity() {
        User primaryUser = engineer(4L, "Primary");
        User backupUser = engineer(7L, "Backup");
        when(engineerZoneRepository.findCurrent(today, "BLR-EAST", null)).thenReturn(List.of(
                assignment(primaryUser, EngineerZoneType.PRIMARY),
                assignment(backupUser, EngineerZoneType.BACKUP)));
        when(engineerConfigRepository.findAllByEngineerIdIn(anyList())).thenReturn(List.of(
                EngineerServiceability.builder().engineerId(4L).dailyCapacity(1).available(true).build()));
        when(visitRepository.findScheduledForDate(today))
                .thenReturn(List.of(assignedVisit(30L, 4L)));
        when(engineerService.engineers(null)).thenReturn(List.of(
                engineerResponse(4L, "Primary"), engineerResponse(7L, "Backup")));

        var result = service.allocate(new AssignmentBatchRequest(List.of(23L), null, false), "supervisor");
        var decision = result.decisions().getFirst();

        assertEquals(ServicePriority.CRITICAL, decision.priority());
        assertEquals(7L, decision.engineerId());
        assertTrue(decision.reason().contains("backup"));
        verify(assignmentRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void preventsDuplicateActiveAssignmentForTheSameVisit() {
        when(visitRepository.findById(23L)).thenReturn(Optional.of(visit));
        when(historyRepository.findById(23L)).thenReturn(Optional.of(ServiceHistory.builder()
                .id(23L).serviceDate(today).status(ServiceVisitStatus.SCHEDULED).build()));
        when(assignmentRepository.findByServiceVisitIdAndStatus(23L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(com.apollo.elevator.serviceability.model.entity.ServiceAssignment.builder()
                        .id(99L).serviceVisitId(23L).status(AssignmentStatus.ACTIVE).build()));

        assertThrows(IllegalArgumentException.class, () -> service.assignManually(
                new ManualServiceAssignmentRequest(23L, 7L, "BLR-EAST", today, "Supervisor assignment"),
                "supervisor"));
        verify(assignmentRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void dashboardCountsOverdueAssignedVisitsInEngineerAndZoneWorkloads() {
        when(engineerService.engineers(null)).thenReturn(List.of(engineerResponse(4L, "Ramesh")));
        when(visitRepository.findScheduledThroughDate(today)).thenReturn(List.of(assignedVisit(23L, 4L)));
        when(zoneRepository.findAll()).thenReturn(List.of(east));
        when(statsRepository.dashboardCounts(today, today.plusDays(3)))
                .thenReturn(new Object[]{1L, 1L, 0L, 0L, 0L});

        var dashboard = service.dashboard();

        assertEquals(1, dashboard.engineers().getFirst().scheduled());
        assertEquals(1, dashboard.zones().getFirst().total());
    }

    private static PincodeServiceability mapping(ServiceabilityZone zone, String locality) {
        return PincodeServiceability.builder()
                .pincode("560066")
                .locality(locality)
                .zone(zone)
                .active(true)
                .effectiveFrom(LocalDate.now().minusDays(30))
                .verificationStatus(PostalVerificationStatus.VERIFIED)
                .build();
    }

    private static User engineer(Long id, String username) {
        return User.builder().id(id).username(username).enabled(true).build();
    }

    private static EngineerZoneAssignment assignment(User engineer, EngineerZoneType type) {
        return EngineerZoneAssignment.builder()
                .engineer(engineer)
                .zone(ServiceabilityZone.builder().zoneId("BLR-EAST").zoneName("Bengaluru East").build())
                .assignmentType(type)
                .active(true)
                .effectiveFrom(LocalDate.now().minusDays(10))
                .build();
    }

    private static EngineerServiceabilityResponse engineerResponse(Long id, String name) {
        return new EngineerServiceabilityResponse(
                id, name, null, true, true, 5, List.of("BLR-EAST"), List.of(),
                0, 0, 0);
    }

    private static VisitContext assignedVisit(Long id, Long engineerId) {
        return new VisitContext(id, LocalDate.now(), engineerId, "Engineer",
                "Customer", "Address", "Whitefield", "560066", "LIFT");
    }
}
