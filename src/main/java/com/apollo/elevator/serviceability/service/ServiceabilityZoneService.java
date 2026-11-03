package com.apollo.elevator.serviceability.service;

import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.serviceability.model.dto.AssignmentDecision;
import com.apollo.elevator.serviceability.model.dto.DailyPlanResponse;
import com.apollo.elevator.serviceability.model.dto.ServiceabilityZoneRequest;
import com.apollo.elevator.serviceability.model.dto.ServiceabilityZoneResponse;
import com.apollo.elevator.serviceability.model.entity.ServiceabilityZone;
import com.apollo.elevator.serviceability.repository.EngineerZoneAssignmentRepository;
import com.apollo.elevator.serviceability.repository.ServiceabilityZoneRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceabilityZoneService {

    private final ServiceabilityZoneRepository zoneRepository;
    private final EngineerZoneAssignmentRepository engineerZoneAssignmentRepository;
    private final ServiceAllocationService allocationService;

    @Transactional
    public List<ServiceabilityZoneResponse> list() {
        Map<String, ZoneWorkload> workloads = workloadsByZone();
        List<ServiceabilityZoneResponse> zones = zoneRepository.findAll().stream()
                .sorted((left, right) -> left.getZoneId().compareTo(right.getZoneId()))
                .map(zone -> response(zone, workloads.getOrDefault(zone.getZoneId(), new ZoneWorkload())))
                .toList();
        log.debug("Serviceability zones loaded. count={}", zones.size());
        return zones;
    }

    @Transactional
    public ServiceabilityZoneResponse get(String zoneId) {
        ServiceabilityZone zone = findZone(zoneId);
        return response(zone, workloadsByZone().getOrDefault(zoneId, new ZoneWorkload()));
    }

    @Transactional
    public ServiceabilityZoneResponse save(ServiceabilityZoneRequest request, boolean create) {
        ServiceabilityZone zone;
        if (create) {
            if (zoneRepository.existsById(request.zoneId())) {
                throw new ConflictException("Serviceability zone already exists: " + request.zoneId());
            }
            zone = new ServiceabilityZone();
            zone.setZoneId(request.zoneId());
        } else {
            zone = findZone(request.zoneId());
        }
        zone.setZoneName(request.zoneName().trim());
        zone.setDescription(clean(request.description()));
        zone.setActive(request.active());
        ServiceabilityZone savedZone = zoneRepository.save(zone);
        ServiceabilityZoneResponse saved = response(
                savedZone, workloadsByZone().getOrDefault(savedZone.getZoneId(), new ZoneWorkload()));
        log.info("Serviceability zone {}. zoneId={}, active={}",
                create ? "created" : "updated", saved.zoneId(), saved.active());
        return saved;
    }

    public ServiceabilityZone findZone(String zoneId) {
        return zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Serviceability zone not found: " + zoneId));
    }

    private ServiceabilityZoneResponse response(ServiceabilityZone zone, ZoneWorkload workload) {
        LocalDate today = LocalDate.now();
        var currentAssignments = engineerZoneAssignmentRepository.findCurrent(today, zone.getZoneId(), null);
        long primaryCount = currentAssignments.stream()
                .filter(assignment -> assignment.getAssignmentType().name().equals("PRIMARY")).count();
        long backupCount = currentAssignments.stream()
                .filter(assignment -> assignment.getAssignmentType().name().equals("BACKUP")).count();
        return new ServiceabilityZoneResponse(
                zone.getZoneId(), zone.getZoneName(), zone.getDescription(), zone.isActive(),
                primaryCount, backupCount, workload.assigned(), workload.pending());
    }

    private Map<String, ZoneWorkload> workloadsByZone() {
        DailyPlanResponse plan = allocationService.dailyPlan(LocalDate.now());
        Map<String, ZoneWorkload> workloads = new HashMap<>();
        for (DailyPlanResponse.EngineerPlan engineer : plan.engineers()) {
            for (AssignmentDecision visit : engineer.visits()) {
                if (visit.zoneId() != null) {
                    workloads.computeIfAbsent(visit.zoneId(), ignored -> new ZoneWorkload()).addAssigned();
                }
            }
        }
        for (AssignmentDecision visit : plan.unassigned()) {
            if (visit.zoneId() != null) {
                workloads.computeIfAbsent(visit.zoneId(), ignored -> new ZoneWorkload()).addPending();
            }
        }
        return workloads;
    }

    private static final class ZoneWorkload {
        private long assigned;
        private long pending;

        private void addAssigned() {
            assigned++;
            pending++;
        }

        private void addPending() {
            pending++;
        }

        private long assigned() {
            return assigned;
        }

        private long pending() {
            return pending;
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
