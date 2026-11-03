package com.apollo.elevator.serviceability.service;

import com.apollo.elevator.authorization.model.entity.User;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.common.util.LogMaskingUtil;
import com.apollo.elevator.customer.model.entity.ServiceHistory;
import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.serviceability.config.ServiceabilityProperties;
import com.apollo.elevator.serviceability.model.dto.AssignmentBatchRequest;
import com.apollo.elevator.serviceability.model.dto.AssignmentBatchResponse;
import com.apollo.elevator.serviceability.model.dto.AssignmentDecision;
import com.apollo.elevator.serviceability.model.dto.DailyPlanResponse;
import com.apollo.elevator.serviceability.model.dto.EngineerServiceabilityResponse;
import com.apollo.elevator.serviceability.model.dto.ManualServiceAssignmentRequest;
import com.apollo.elevator.serviceability.model.dto.ReassignServiceRequest;
import com.apollo.elevator.serviceability.model.dto.ServiceAssignmentResponse;
import com.apollo.elevator.serviceability.model.dto.ServiceabilityDashboardResponse;
import com.apollo.elevator.serviceability.model.entity.EngineerServiceability;
import com.apollo.elevator.serviceability.model.entity.EngineerZoneAssignment;
import com.apollo.elevator.serviceability.model.entity.PincodeServiceability;
import com.apollo.elevator.serviceability.model.entity.ServiceAssignment;
import com.apollo.elevator.serviceability.model.enums.AssignmentMethod;
import com.apollo.elevator.serviceability.model.enums.AssignmentStatus;
import com.apollo.elevator.serviceability.model.enums.EngineerZoneType;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceAllocationService {

    private final ServiceabilityVisitQueryRepository visitQueryRepository;
    private final PincodeServiceabilityRepository pincodeRepository;
    private final ServiceabilityZoneRepository zoneRepository;
    private final EngineerZoneAssignmentRepository engineerZoneRepository;
    private final EngineerServiceabilityRepository engineerConfigRepository;
    private final EngineerServiceabilityStatsRepository engineerStatsRepository;
    private final ServiceAssignmentRepository assignmentRepository;
    private final ServiceHistoryRepository historyRepository;
    private final ServiceabilityProperties properties;
    private final EngineerServiceabilityService engineerService;

    @Transactional
    public AssignmentBatchResponse allocate(AssignmentBatchRequest request, String actor) {
        LocalDate today = LocalDate.now();
        LocalDate plannedDate = request.plannedDate() == null ? today : request.plannedDate();
        List<VisitContext> visits = visitQueryRepository.findUnassignedScheduledThrough(
                LocalDate.now().plusYears(100), request.serviceVisitIds());
        Map<Long, VisitContext> byId = visits.stream()
                .collect(Collectors.toMap(VisitContext::visitId, Function.identity()));
        List<VisitContext> orderedVisits = request.serviceVisitIds().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((VisitContext visit) -> priority(visit.dueDate(), today).ordinal())
                        .thenComparing(VisitContext::dueDate)
                        .thenComparing(VisitContext::visitId))
                .toList();

        Map<String, Integer> loads = existingLoads(orderedVisits, request.plannedDate());
        List<AssignmentDecision> decisions = new ArrayList<>();
        for (Long visitId : request.serviceVisitIds().stream().distinct().toList()) {
            VisitContext visit = byId.get(visitId);
            if (visit == null) {
                decisions.add(blocked(visitId, "Service visit was not found, is not scheduled, or is already assigned."));
            }
        }
        for (VisitContext visit : orderedVisits) {
            LocalDate visitDate = request.plannedDate() == null
                    ? (visit.dueDate().isBefore(today) ? today : visit.dueDate())
                    : plannedDate;
            AssignmentDecision decision = suggest(visit, visitDate, today, loads);
            if (request.confirm() && decision.assignable()) {
                ServiceAssignment saved = persist(visit, decision, visitDate, actor, AssignmentMethod.AUTOMATIC);
                decision = withAssignmentId(decision, saved.getId());
                incrementLoad(loads, visitDate, decision.engineerId());
            }
            decisions.add(decision);
        }
        int assignedCount = (int) decisions.stream().filter(d -> d.assignmentId() != null).count();
        int blockedCount = (int) decisions.stream().filter(d -> !d.assignable()).count();
        log.info("Service allocation {}. requested={}, assigned={}, blocked={}, actor={}",
                request.confirm() ? "confirmed" : "previewed",
                request.serviceVisitIds().size(), assignedCount, blockedCount,
                LogMaskingUtil.mask("username", actor));
        return new AssignmentBatchResponse(plannedDate, request.confirm(), assignedCount, blockedCount, decisions);
    }

    @Transactional
    public ServiceAssignmentResponse assignManually(ManualServiceAssignmentRequest request, String actor) {
        VisitContext visit = visitQueryRepository.findById(request.serviceVisitId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service visit not found: " + request.serviceVisitId()));
        ServiceHistory history = scheduledHistory(visit.visitId());
        if (history.getAssignedEngineerId() != null
                || assignmentRepository.findByServiceVisitIdAndStatus(visit.visitId(), AssignmentStatus.ACTIVE)
                        .isPresent()) {
            throw new IllegalArgumentException("Service visit already has an active engineer assignment");
        }
        LocalDate today = LocalDate.now();
        LocalDate scheduledDate = request.scheduledDate() == null
                ? (visit.dueDate().isBefore(today) ? today : visit.dueDate())
                : request.scheduledDate();
        MappingResolution mapping = resolveMapping(visit, scheduledDate);
        String zoneId = request.zoneId() == null || request.zoneId().isBlank()
                ? mapping.zoneId()
                : request.zoneId().trim();
        if (zoneId == null) {
            throw new IllegalArgumentException("A verified service zone is required for manual assignment");
        }
        var zone = zoneRepository.findById(zoneId)
                .filter(z -> z.isActive())
                .orElseThrow(() -> new IllegalArgumentException("Service zone is missing or inactive: " + zoneId));
        if (request.zoneId() != null && !request.zoneId().isBlank()
                && (!Objects.equals(zoneId, mapping.zoneId()) || !mapping.assignable())
                && (request.remarks() == null || request.remarks().isBlank())) {
            throw new IllegalArgumentException("Supervisor zone overrides require an assignment remark");
        }
        User engineer = engineerService.findEngineer(request.engineerId());
        ServicePriority priority = priority(visit.dueDate(), LocalDate.now());
        AssignmentDecision decision = new AssignmentDecision(
                visit.visitId(), visit.customerName(), visit.address(), visit.city(), visit.pincode(),
                visit.liftSerialNumber(), visit.dueDate(), priority, zone.getZoneId(), zone.getZoneName(),
                engineer.getId(), engineerDisplayName(engineer.getId()), true,
                request.remarks() == null || request.remarks().isBlank()
                        ? "Manually assigned by supervisor."
                        : request.remarks().trim(),
                null);
        ServiceAssignment saved = persist(visit, decision, scheduledDate, actor, AssignmentMethod.MANUAL);
        return response(saved);
    }

    @Transactional
    public ServiceAssignmentResponse reassign(
            Long assignmentId,
            ReassignServiceRequest request,
            String actor
    ) {
        ServiceAssignment current = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Service assignment not found: " + assignmentId));
        if (current.getStatus() != AssignmentStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active service assignments can be reassigned");
        }
        ServiceHistory history = scheduledHistory(current.getServiceVisitId());
        User engineer = engineerService.findEngineer(request.engineerId());
        LocalDate scheduledDate = request.scheduledDate() == null
                ? current.getScheduledDate()
                : request.scheduledDate();
        VisitContext visit = visitQueryRepository.findById(current.getServiceVisitId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service visit not found: " + current.getServiceVisitId()));
        MappingResolution mapping = resolveMapping(visit, scheduledDate);
        String zoneId = request.zoneId() == null || request.zoneId().isBlank()
                ? current.getZoneId()
                : request.zoneId().trim();
        if (request.zoneId() != null && !request.zoneId().isBlank()
                && (!Objects.equals(zoneId, mapping.zoneId()) || !mapping.assignable())
                && (request.remarks() == null || request.remarks().isBlank())) {
            throw new IllegalArgumentException("Supervisor zone overrides require an assignment remark");
        }
        var zone = zoneRepository.findById(zoneId)
                .filter(value -> value.isActive())
                .orElseThrow(() -> new IllegalArgumentException("Service zone is missing or inactive: " + zoneId));

        current.setStatus(AssignmentStatus.REASSIGNED);
        assignmentRepository.save(current);
        history.setAssignedEngineerId(engineer.getId());
        history.setEngineerName(engineerDisplayName(engineer.getId()));
        history.setServiceDate(scheduledDate);
        ServiceHistory savedHistory = historyRepository.save(history);
        ServiceAssignment replacement = assignmentRepository.save(ServiceAssignment.builder()
                .serviceVisitId(savedHistory.getId())
                .engineerId(engineer.getId())
                .zoneId(zone.getZoneId())
                .assignmentDate(LocalDate.now())
                .serviceDueDate(current.getServiceDueDate())
                .scheduledDate(scheduledDate)
                .priority(priority(current.getServiceDueDate(), LocalDate.now()))
                .status(AssignmentStatus.ACTIVE)
                .assignedBy(actor)
                .assignmentMethod(AssignmentMethod.MANUAL)
                .assignmentRemarks(request.remarks())
                .visitSequence(0)
                .build());
        log.info("Service assignment reassigned. previousAssignmentId={}, newAssignmentId={}, visitId={}, engineerId={}, actor={}",
                current.getId(), replacement.getId(), savedHistory.getId(), engineer.getId(),
                LogMaskingUtil.mask("username", actor));
        return response(replacement);
    }

    @Transactional
    public ServiceAssignmentResponse reassignVisit(
            Long visitId,
            ReassignServiceRequest request,
            String actor
    ) {
        ServiceAssignment active = assignmentRepository
                .findByServiceVisitIdAndStatus(visitId, AssignmentStatus.ACTIVE)
                .orElse(null);
        if (active != null) {
            return reassign(active.getId(), request, actor);
        }
        ServiceHistory history = scheduledHistory(visitId);
        if (history.getAssignedEngineerId() == null) {
            return assignManually(new ManualServiceAssignmentRequest(
                    visitId, request.engineerId(), request.zoneId(), request.scheduledDate(), request.remarks()), actor);
        }
        VisitContext visit = visitQueryRepository.findById(visitId)
                .orElseThrow(() -> new ResourceNotFoundException("Service visit not found: " + visitId));
        LocalDate scheduledDate = request.scheduledDate() == null ? history.getServiceDate() : request.scheduledDate();
        MappingResolution mapping = resolveMapping(visit, scheduledDate);
        String zoneId = request.zoneId() == null || request.zoneId().isBlank()
                ? mapping.zoneId()
                : request.zoneId().trim();
        if (zoneId == null) {
            throw new IllegalArgumentException("A verified service zone is required to record the reassignment");
        }
        if (!Objects.equals(zoneId, mapping.zoneId())
                && (request.remarks() == null || request.remarks().isBlank())) {
            throw new IllegalArgumentException("Supervisor zone overrides require an assignment remark");
        }
        var zone = zoneRepository.findById(zoneId)
                .filter(value -> value.isActive())
                .orElseThrow(() -> new IllegalArgumentException("Service zone is missing or inactive: " + zoneId));
        User engineer = engineerService.findEngineer(request.engineerId());
        LocalDate serviceDueDate = history.getServiceDate();
        history.setAssignedEngineerId(engineer.getId());
        history.setEngineerName(engineerDisplayName(engineer.getId()));
        history.setServiceDate(scheduledDate);
        historyRepository.save(history);
        ServiceAssignment saved = assignmentRepository.save(ServiceAssignment.builder()
                .serviceVisitId(visitId)
                .engineerId(engineer.getId())
                .zoneId(zoneId)
                .assignmentDate(LocalDate.now())
                .serviceDueDate(serviceDueDate)
                .scheduledDate(scheduledDate)
                .priority(priority(serviceDueDate, LocalDate.now()))
                .status(AssignmentStatus.ACTIVE)
                .assignedBy(actor)
                .assignmentMethod(AssignmentMethod.MANUAL)
                .assignmentRemarks(request.remarks() == null || request.remarks().isBlank()
                        ? "Legacy service visit reassigned by supervisor."
                        : request.remarks().trim())
                .visitSequence(0)
                .build());
        log.info("Legacy service visit reassignment recorded. assignmentId={}, visitId={}, engineerId={}, zoneId={}",
                saved.getId(), visitId, engineer.getId(), zone.getZoneId());
        return response(saved);
    }

    @Transactional
    public void syncRescheduledVisit(Long visitId, LocalDate scheduledDate, Long engineerId, String actor) {
        ServiceAssignment current = assignmentRepository
                .findByServiceVisitIdAndStatus(visitId, AssignmentStatus.ACTIVE)
                .orElse(null);
        if (current == null) {
            return;
        }
        if (engineerId == null) {
            current.setStatus(AssignmentStatus.CANCELLED);
            assignmentRepository.save(current);
            return;
        }
        User engineer = engineerService.findEngineer(engineerId);
        VisitContext visit = visitQueryRepository.findById(visitId)
                .orElseThrow(() -> new ResourceNotFoundException("Service visit not found: " + visitId));
        current.setStatus(AssignmentStatus.REASSIGNED);
        assignmentRepository.save(current);
        ServiceAssignment updated = assignmentRepository.save(ServiceAssignment.builder()
                .serviceVisitId(visitId)
                .engineerId(engineerId)
                .zoneId(current.getZoneId())
                .assignmentDate(LocalDate.now())
                .serviceDueDate(scheduledDate)
                .scheduledDate(scheduledDate)
                .priority(priority(scheduledDate, LocalDate.now()))
                .status(AssignmentStatus.ACTIVE)
                .assignedBy(actor == null ? "system" : actor)
                .assignmentMethod(AssignmentMethod.MANUAL)
                .assignmentRemarks("Updated by service-visit rescheduling.")
                .visitSequence(0)
                .build());
        log.info("Service assignment synchronized after visit reschedule. previousAssignmentId={}, newAssignmentId={}, visitId={}, engineerId={}",
                current.getId(), updated.getId(), visit.visitId(), engineer.getId());
    }

    @Transactional
    public ServiceAssignmentResponse updateSequence(Long assignmentId, int sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("Visit sequence must be at least 1");
        }
        ServiceAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Service assignment not found: " + assignmentId));
        if (assignment.getStatus() != AssignmentStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active assignments can be reordered");
        }
        assignment.setVisitSequence(sequence);
        return response(assignmentRepository.save(assignment));
    }

    @Transactional
    public void markVisitCompleted(Long visitId) {
        assignmentRepository.findByServiceVisitIdAndStatus(visitId, AssignmentStatus.ACTIVE)
                .ifPresent(assignment -> {
                    assignment.setStatus(AssignmentStatus.COMPLETED);
                    assignmentRepository.save(assignment);
                    log.info("Service assignment completed from engineer service report. assignmentId={}, visitId={}",
                            assignment.getId(), visitId);
                });
    }

    @Transactional(readOnly = true)
    public Page<ServiceAssignmentResponse> assignments(
            LocalDate date,
            Long engineerId,
            String zoneId,
            AssignmentStatus status,
            Pageable pageable
    ) {
        return assignmentRepository.search(date, engineerId, zoneId, status, pageable)
                .map(this::response);
    }

    @Transactional
    public DailyPlanResponse dailyPlan(LocalDate date) {
        LocalDate planDate = date == null ? LocalDate.now() : date;
        List<VisitContext> visits = visitQueryRepository.findScheduledThroughDate(planDate);
        List<ServiceAssignment> activeAssignments =
                assignmentRepository
                        .findAllByStatusAndScheduledDateLessThanEqualOrderByEngineerIdAscVisitSequenceAscIdAsc(
                                AssignmentStatus.ACTIVE, planDate);
        Map<Long, ServiceAssignment> assignments = activeAssignments.stream()
                .collect(Collectors.toMap(ServiceAssignment::getServiceVisitId, Function.identity()));
        List<EngineerServiceabilityResponse> engineers = engineerService.engineers(null);
        Map<Long, EngineerServiceabilityResponse> engineerInfo = engineers.stream()
                .collect(Collectors.toMap(EngineerServiceabilityResponse::engineerId, Function.identity()));
        Map<Long, List<AssignmentDecision>> visitsByEngineer = new LinkedHashMap<>();
        List<AssignmentDecision> unassigned = new ArrayList<>();

        for (VisitContext visit : visits) {
            ServiceAssignment assignment = assignments.get(visit.visitId());
            Long engineerId = assignment == null ? visit.assignedEngineerId() : assignment.getEngineerId();
            MappingResolution mapping = resolveMapping(visit, planDate);
            if (engineerId == null) {
                AssignmentDecision suggestion = suggest(visit, planDate, LocalDate.now(), new HashMap<>());
                String reason = suggestion.assignable()
                        ? "Not assigned; eligible for " + suggestion.engineerName() + "."
                        : suggestion.reason();
                unassigned.add(new AssignmentDecision(
                        visit.visitId(), visit.customerName(), visit.address(), visit.city(), visit.pincode(),
                        visit.liftSerialNumber(), visit.dueDate(), priority(visit.dueDate(), LocalDate.now()),
                        mapping.zoneId(), mapping.zoneName(), null, null, false, reason, null));
                continue;
            }
            EngineerServiceabilityResponse info = engineerInfo.get(engineerId);
            String engineerName = info == null
                    ? (visit.engineerName() == null ? "Engineer " + engineerId : visit.engineerName())
                    : info.engineerName();
            String zoneId = assignment == null ? mapping.zoneId() : assignment.getZoneId();
            String zoneName = assignment == null ? mapping.zoneName() : zoneName(zoneId);
            String reason = assignment == null
                    ? "Existing service visit assignment not recorded in allocation history."
                    : null;
            visitsByEngineer.computeIfAbsent(engineerId, ignored -> new ArrayList<>()).add(
                    new AssignmentDecision(
                            visit.visitId(), visit.customerName(), visit.address(), visit.city(), visit.pincode(),
                            visit.liftSerialNumber(),
                            assignment == null ? visit.dueDate() : assignment.getServiceDueDate(),
                            assignment == null ? priority(visit.dueDate(), LocalDate.now()) : assignment.getPriority(),
                            zoneId, zoneName, engineerId, engineerName, true, reason,
                            assignment == null ? null : assignment.getId()));
        }

        List<DailyPlanResponse.EngineerPlan> plans = new ArrayList<>();
        for (EngineerServiceabilityResponse engineer : engineers) {
            List<AssignmentDecision> plannedVisits = visitsByEngineer.getOrDefault(engineer.engineerId(), List.of())
                    .stream()
                    .sorted(Comparator.comparing((AssignmentDecision d) -> {
                        ServiceAssignment assignment = assignments.get(d.serviceVisitId());
                        return assignment == null ? Integer.MAX_VALUE : assignment.getVisitSequence();
                    }).thenComparing(AssignmentDecision::dueDate))
                    .toList();
            boolean overloaded = plannedVisits.size() > engineer.dailyCapacity();
            String primaryZone = engineer.primaryZones().stream().findFirst().orElse(null);
            plans.add(new DailyPlanResponse.EngineerPlan(
                    engineer.engineerId(), engineer.engineerName(), primaryZone, zoneName(primaryZone),
                    engineer.dailyCapacity(), plannedVisits.size(),
                    planDate.equals(LocalDate.now()) ? (int) engineer.completedToday() : 0,
                    overloaded, plannedVisits));
        }
        plans.sort(Comparator.comparing(DailyPlanResponse.EngineerPlan::engineerName));
        return new DailyPlanResponse(
                planDate, plans, unassigned,
                (int) plans.stream().mapToLong(plan -> plan.visits().size()).sum(),
                (int) plans.stream().filter(DailyPlanResponse.EngineerPlan::overloaded).count());
    }

    @Transactional(readOnly = true)
    public ServiceabilityDashboardResponse dashboard() {
        LocalDate today = LocalDate.now();
        List<EngineerServiceabilityResponse> engineers = engineerService.engineers(null);
        DailyPlanResponse plan = dailyPlan(today);
        Map<Long, Integer> plannedCounts = plan.engineers().stream()
                .collect(Collectors.toMap(
                        DailyPlanResponse.EngineerPlan::engineerId,
                        DailyPlanResponse.EngineerPlan::scheduledCount));
        Map<String, List<AssignmentDecision>> zoneVisits = plan.engineers().stream()
                .flatMap(engineer -> engineer.visits().stream())
                .filter(visit -> visit.zoneId() != null)
                .collect(Collectors.groupingBy(AssignmentDecision::zoneId));
        plan.unassigned().stream().filter(visit -> visit.zoneId() != null)
                .forEach(visit -> zoneVisits.computeIfAbsent(visit.zoneId(), ignored -> new ArrayList<>()).add(visit));
        List<ServiceabilityDashboardResponse.ZoneWorkload> zones = zoneRepository.findAll().stream()
                .filter(zone -> zone.isActive())
                .map(zone -> {
                    List<AssignmentDecision> jobs = zoneVisits.getOrDefault(zone.getZoneId(), List.of());
                    return new ServiceabilityDashboardResponse.ZoneWorkload(
                            zone.getZoneId(), zone.getZoneName(), jobs.size(),
                            jobs.stream().filter(job -> job.priority() == ServicePriority.CRITICAL).count(),
                            jobs.stream().filter(job -> job.engineerId() == null).count());
                }).toList();
        List<ServiceabilityDashboardResponse.EngineerWorkload> workloads = engineers.stream()
                .map(engineer -> new ServiceabilityDashboardResponse.EngineerWorkload(
                        engineer.engineerId(), engineer.engineerName(),
                        engineer.primaryZones().stream().findFirst().orElse(null),
                        engineer.dailyCapacity(), plannedCounts.getOrDefault(engineer.engineerId(), 0),
                        engineer.completedToday(),
                        engineer.overdue(), engineer.available() && engineer.accountEnabled()))
                .toList();
        Object[] counts = engineerStatsRepository.dashboardCounts(today, today.plusDays(3));
        return new ServiceabilityDashboardResponse(
                today, number(counts[0]), number(counts[1]), number(counts[2]),
                number(counts[3]), number(counts[4]),
                engineers.stream().filter(EngineerServiceabilityResponse::accountEnabled).count(),
                zones, workloads);
    }

    private AssignmentDecision suggest(
            VisitContext visit,
            LocalDate scheduledDate,
            LocalDate today,
            Map<String, Integer> loads
    ) {
        MappingResolution mapping = resolveMapping(visit, scheduledDate);
        ServicePriority priority = priority(visit.dueDate(), today);
        if (!mapping.assignable()) {
            return decision(visit, priority, mapping, null, false, mapping.reason(), null);
        }
        List<EngineerZoneAssignment> zoneAssignments =
                engineerZoneRepository.findCurrent(scheduledDate, mapping.zoneId(), null);
        List<EngineerZoneAssignment> primary = zoneAssignments.stream()
                .filter(a -> a.getAssignmentType() == EngineerZoneType.PRIMARY
                        && a.getEngineer().isEnabled())
                .toList();
        List<EngineerZoneAssignment> backup = zoneAssignments.stream()
                .filter(a -> a.getAssignmentType() == EngineerZoneType.BACKUP
                        && a.getEngineer().isEnabled())
                .toList();
        Map<Long, EngineerServiceability> configs = engineerConfigRepository.findAllByEngineerIdIn(
                        zoneAssignments.stream().map(a -> a.getEngineer().getId()).distinct().toList()).stream()
                .collect(Collectors.toMap(EngineerServiceability::getEngineerId, Function.identity()));
        List<EngineerZoneAssignment> availablePrimary = available(primary, configs, loads, scheduledDate);
        List<EngineerZoneAssignment> availableBackup = available(backup, configs, loads, scheduledDate);
        List<EngineerZoneAssignment> eligible = availablePrimary.isEmpty() ? availableBackup : availablePrimary;
        if (eligible.isEmpty()) {
            boolean configured = !primary.isEmpty() || !backup.isEmpty();
            return decision(visit, priority, mapping, null, false,
                    configured
                            ? "No primary or backup engineer is available within daily capacity."
                            : "No active primary or backup engineer is configured for this service zone.",
                    null);
        }
        EngineerZoneAssignment chosen = eligible.stream()
                .min(Comparator.<EngineerZoneAssignment>comparingInt(
                                a -> load(loads, scheduledDate, a.getEngineer().getId()))
                        .thenComparing(a -> a.getEngineer().getId()))
                .orElseThrow();
        return decision(visit, priority, mapping, chosen.getEngineer(),
                true, availablePrimary.isEmpty() ? "Assigned to an available backup engineer." : "Assigned to the zone's primary engineer.",
                null);
    }

    private List<EngineerZoneAssignment> available(
            List<EngineerZoneAssignment> assignments,
            Map<Long, EngineerServiceability> configs,
            Map<String, Integer> loads,
            LocalDate date
    ) {
        return assignments.stream().filter(assignment -> {
            EngineerServiceability config = configs.get(assignment.getEngineer().getId());
            int capacity = config == null ? properties.getDefaultDailyCapacity() : config.getDailyCapacity();
            return (config == null || config.isAvailable())
                    && load(loads, date, assignment.getEngineer().getId()) < capacity;
        }).toList();
    }

    private MappingResolution resolveMapping(VisitContext visit, LocalDate onDate) {
        if (visit.pincode() == null || !visit.pincode().matches("[0-9]{6}")) {
            return MappingResolution.blocked("Customer PIN code is missing or invalid.");
        }
        List<PincodeServiceability> mappings = pincodeRepository.findCurrentMappings(visit.pincode(), onDate);
        if (mappings.isEmpty()) {
            return MappingResolution.blocked("No active PIN-code mapping is configured for this date.");
        }
        String locality = visit.city() == null ? "" : visit.city().trim().toLowerCase();
        List<PincodeServiceability> localityMatches = mappings.stream()
                .filter(mapping -> !locality.isBlank()
                        && (locality.equals(mapping.getLocality().toLowerCase())
                        || locality.contains(mapping.getLocality().toLowerCase())
                        || mapping.getLocality().toLowerCase().contains(locality)))
                .toList();
        List<PincodeServiceability> candidates = localityMatches.isEmpty() ? mappings : localityMatches;
        if (candidates.stream().anyMatch(mapping ->
                mapping.getVerificationStatus() != PostalVerificationStatus.VERIFIED)) {
            return MappingResolution.blocked("PIN-code mapping is not verified by a supervisor.");
        }
        Set<String> zoneIds = candidates.stream()
                .map(mapping -> mapping.getZone() == null ? null : mapping.getZone().getZoneId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (zoneIds.size() != 1 || candidates.stream().anyMatch(mapping -> mapping.getZone() == null)) {
            return MappingResolution.blocked("PIN code maps to multiple or unresolved service zones.");
        }
        String zoneId = zoneIds.iterator().next();
        var zone = zoneRepository.findById(zoneId).orElse(null);
        if (zone == null || !zone.isActive()) {
            return MappingResolution.blocked("Mapped service zone is missing or inactive.");
        }
        return new MappingResolution(zoneId, zone.getZoneName(), true, "Verified PIN-code service zone.");
    }

    private Map<String, Integer> existingLoads(List<VisitContext> visits, LocalDate plannedDate) {
        Map<String, Integer> loads = new HashMap<>();
        List<LocalDate> dates = visits.stream()
            .map(visit -> plannedDate == null
                    ? (visit.dueDate().isBefore(LocalDate.now()) ? LocalDate.now() : visit.dueDate())
                    : plannedDate)
            .distinct().toList();
        for (LocalDate date : dates) {
            visitQueryRepository.findScheduledForDate(date).stream()
                    .filter(visit -> visit.assignedEngineerId() != null)
                    .forEach(visit -> incrementLoad(loads, date, visit.assignedEngineerId()));
        }
        return loads;
    }

    private ServiceAssignment persist(
            VisitContext visit,
            AssignmentDecision decision,
            LocalDate scheduledDate,
            String actor,
            AssignmentMethod method
    ) {
        if (assignmentRepository.findByServiceVisitIdAndStatus(visit.visitId(), AssignmentStatus.ACTIVE).isPresent()) {
            throw new IllegalArgumentException("Service visit already has an active assignment: " + visit.visitId());
        }
        ServiceHistory history = scheduledHistory(visit.visitId());
        if (history.getAssignedEngineerId() != null) {
            throw new IllegalArgumentException("Service visit already has a legacy engineer assignment: " + visit.visitId());
        }
        history.setAssignedEngineerId(decision.engineerId());
        history.setEngineerName(decision.engineerName());
        history.setServiceDate(scheduledDate);
        historyRepository.save(history);
        return assignmentRepository.save(ServiceAssignment.builder()
                .serviceVisitId(visit.visitId())
                .engineerId(decision.engineerId())
                .zoneId(decision.zoneId())
                .assignmentDate(LocalDate.now())
                .serviceDueDate(visit.dueDate())
                .scheduledDate(scheduledDate)
                .priority(decision.priority())
                .status(AssignmentStatus.ACTIVE)
                .assignedBy(actor)
                .assignmentMethod(method)
                .assignmentRemarks(decision.reason())
                .visitSequence(0)
                .build());
    }

    private ServiceHistory scheduledHistory(Long visitId) {
        ServiceHistory history = historyRepository.findById(visitId)
                .orElseThrow(() -> new ResourceNotFoundException("Service visit not found: " + visitId));
        if (history.getStatus() != ServiceVisitStatus.SCHEDULED) {
            throw new IllegalArgumentException("Only scheduled service visits can be assigned");
        }
        return history;
    }

    private ServiceAssignmentResponse response(ServiceAssignment assignment) {
        var zone = zoneRepository.findById(assignment.getZoneId()).orElse(null);
        return new ServiceAssignmentResponse(
                assignment.getId(), assignment.getServiceVisitId(), assignment.getEngineerId(),
                engineerDisplayName(assignment.getEngineerId()), assignment.getZoneId(),
                zone == null ? assignment.getZoneId() : zone.getZoneName(),
                assignment.getAssignmentDate(), assignment.getServiceDueDate(),
                assignment.getScheduledDate(), assignment.getPriority(),
                assignment.getStatus(), assignment.getAssignmentMethod(), assignment.getAssignedBy(),
                assignment.getAssignmentRemarks(), assignment.getVisitSequence(), assignment.getCreatedAt());
    }

    private AssignmentDecision decision(
            VisitContext visit,
            ServicePriority priority,
            MappingResolution mapping,
            User engineer,
            boolean assignable,
            String reason,
            Long assignmentId
    ) {
        return new AssignmentDecision(
                visit.visitId(), visit.customerName(), visit.address(), visit.city(), visit.pincode(),
                visit.liftSerialNumber(), visit.dueDate(), priority, mapping.zoneId(), mapping.zoneName(),
                engineer == null ? null : engineer.getId(),
                engineer == null ? null : engineerDisplayName(engineer.getId()),
                assignable, reason, assignmentId);
    }

    private static AssignmentDecision blocked(Long visitId, String reason) {
        return new AssignmentDecision(
                visitId, null, null, null, null, null, null, null,
                null, null, null, null, false, reason, null);
    }

    private static AssignmentDecision withAssignmentId(AssignmentDecision decision, Long assignmentId) {
        return new AssignmentDecision(
                decision.serviceVisitId(), decision.customerName(), decision.address(), decision.city(),
                decision.pincode(), decision.liftSerialNumber(), decision.dueDate(), decision.priority(),
                decision.zoneId(), decision.zoneName(), decision.engineerId(), decision.engineerName(),
                decision.assignable(), decision.reason(), assignmentId);
    }

    private String engineerDisplayName(Long engineerId) {
        return engineerService.engineers(null).stream()
                .filter(engineer -> engineer.engineerId().equals(engineerId))
                .map(EngineerServiceabilityResponse::engineerName)
                .findFirst()
                .orElseGet(() -> engineerService.findEngineer(engineerId).getUsername());
    }

    private String zoneName(String zoneId) {
        return zoneId == null ? null : zoneRepository.findById(zoneId)
                .map(zone -> zone.getZoneName()).orElse(zoneId);
    }

    private ServicePriority priority(LocalDate dueDate, LocalDate today) {
        if (dueDate.isBefore(today)) {
            return ServicePriority.CRITICAL;
        }
        if (dueDate.equals(today)) {
            return ServicePriority.HIGH;
        }
        long days = ChronoUnit.DAYS.between(today, dueDate);
        if (days <= properties.getMediumDueWithinDays()) {
            return ServicePriority.MEDIUM;
        }
        if (days <= properties.getNormalDueWithinDays()) {
            return ServicePriority.NORMAL;
        }
        return ServicePriority.LOW;
    }

    private static int load(Map<String, Integer> loads, LocalDate date, Long engineerId) {
        return loads.getOrDefault(loadKey(date, engineerId), 0);
    }

    private static void incrementLoad(Map<String, Integer> loads, LocalDate date, Long engineerId) {
        loads.merge(loadKey(date, engineerId), 1, Integer::sum);
    }

    private static String loadKey(LocalDate date, Long engineerId) {
        return date + ":" + engineerId;
    }

    private static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private record MappingResolution(String zoneId, String zoneName, boolean assignable, String reason) {
        private static MappingResolution blocked(String reason) {
            return new MappingResolution(null, null, false, reason);
        }
    }
}
