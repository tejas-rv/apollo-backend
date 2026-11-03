package com.apollo.elevator.serviceability.service;

import com.apollo.elevator.authorization.model.entity.User;
import com.apollo.elevator.authorization.model.entity.UserProfile;
import com.apollo.elevator.authorization.model.enums.Role;
import com.apollo.elevator.authorization.model.enums.UserType;
import com.apollo.elevator.authorization.repository.UserProfileRepository;
import com.apollo.elevator.authorization.repository.UserRepository;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.serviceability.config.ServiceabilityProperties;
import com.apollo.elevator.serviceability.model.dto.EngineerServiceabilityResponse;
import com.apollo.elevator.serviceability.model.dto.EngineerServiceabilityUpdateRequest;
import com.apollo.elevator.serviceability.model.dto.EngineerZoneAssignmentRequest;
import com.apollo.elevator.serviceability.model.dto.EngineerZoneAssignmentResponse;
import com.apollo.elevator.serviceability.model.entity.EngineerServiceability;
import com.apollo.elevator.serviceability.model.entity.EngineerZoneAssignment;
import com.apollo.elevator.serviceability.model.enums.EngineerZoneType;
import com.apollo.elevator.serviceability.repository.EngineerServiceabilityRepository;
import com.apollo.elevator.serviceability.repository.EngineerServiceabilityStatsRepository;
import com.apollo.elevator.serviceability.repository.EngineerZoneAssignmentRepository;
import com.apollo.elevator.serviceability.repository.ServiceabilityZoneRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EngineerServiceabilityService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final EngineerZoneAssignmentRepository zoneAssignmentRepository;
    private final EngineerServiceabilityRepository serviceabilityRepository;
    private final EngineerServiceabilityStatsRepository statsRepository;
    private final ServiceabilityZoneRepository zoneRepository;
    private final ServiceabilityProperties properties;

    @Transactional(readOnly = true)
    public List<EngineerServiceabilityResponse> engineers(String zoneId) {
        LocalDate today = LocalDate.now();
        List<EngineerZoneAssignment> assignments = zoneAssignmentRepository.findCurrent(today, zoneId, null);
        Map<Long, List<EngineerZoneAssignment>> assignmentsByEngineer = new HashMap<>();
        assignments.forEach(assignment ->
                assignmentsByEngineer.computeIfAbsent(assignment.getEngineer().getId(), ignored -> new ArrayList<>())
                        .add(assignment));

        List<User> engineers = userRepository.findByRoleOrderByUsernameAsc(Role.ENGINEER).stream()
                .filter(user -> user.resolvedUserType() == UserType.ENGINEER)
                .filter(user -> zoneId == null || assignmentsByEngineer.containsKey(user.getId()))
                .toList();
        Map<Long, UserProfile> profiles = profileRepository.findAllById(
                        engineers.stream().map(User::getId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(UserProfile::getUserId, profile -> profile));
        Map<Long, EngineerServiceability> configs = new HashMap<>();
        serviceabilityRepository.findAllById(engineers.stream().map(User::getId).toList())
                .forEach(config -> configs.put(config.getEngineerId(), config));
        Map<Long, long[]> counts = new HashMap<>();
        statsRepository.counts(today).forEach(row -> counts.put(
                ((Number) row[0]).longValue(),
                new long[]{number(row[1]), number(row[2]), number(row[3])}
        ));

        List<EngineerServiceabilityResponse> result = engineers.stream().map(engineer -> {
            UserProfile profile = profiles.get(engineer.getId());
            EngineerServiceability config = configs.get(engineer.getId());
            List<EngineerZoneAssignment> engineerAssignments =
                    assignmentsByEngineer.getOrDefault(engineer.getId(), List.of());
            long[] workload = counts.getOrDefault(engineer.getId(), new long[3]);
            return new EngineerServiceabilityResponse(
                    engineer.getId(),
                    profile == null ? engineer.getUsername() : profile.getFullName(),
                    profile == null ? null : profile.getEmployeeNumber(),
                    engineer.isEnabled(),
                    config == null || config.isAvailable(),
                    config == null ? properties.getDefaultDailyCapacity() : config.getDailyCapacity(),
                    engineerAssignments.stream()
                            .filter(assignment -> assignment.getAssignmentType() == EngineerZoneType.PRIMARY)
                            .map(assignment -> assignment.getZone().getZoneId()).distinct().toList(),
                    engineerAssignments.stream()
                            .filter(assignment -> assignment.getAssignmentType() == EngineerZoneType.BACKUP)
                            .map(assignment -> assignment.getZone().getZoneId()).distinct().toList(),
                    workload[0], workload[1], workload[2]);
        }).toList();
        log.debug("Serviceability engineers loaded. zoneId={}, engineerCount={}", zoneId, result.size());
        return result;
    }

    @Transactional
    public EngineerZoneAssignmentResponse createZoneAssignment(EngineerZoneAssignmentRequest request) {
        User engineer = findEngineer(request.engineerId());
        ensureAssignmentAvailable(engineer.getId(), request, null);
        var zone = zoneRepository.findById(request.zoneId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Serviceability zone not found: " + request.zoneId()));
        EngineerZoneAssignment assignment = new EngineerZoneAssignment();
        assignment.setEngineer(engineer);
        assignment.setZone(zone);
        apply(assignment, request);
        saveCapacityAndAvailability(engineer.getId(), request.dailyCapacity(), request.available());
        EngineerZoneAssignment saved = zoneAssignmentRepository.save(assignment);
        log.info("Engineer zone assignment created. engineerId={}, zoneId={}, assignmentType={}",
                engineer.getId(), zone.getZoneId(), saved.getAssignmentType());
        return response(saved);
    }

    @Transactional
    public EngineerZoneAssignmentResponse updateZoneAssignment(
            Long assignmentId,
            EngineerZoneAssignmentRequest request
    ) {
        EngineerZoneAssignment assignment = zoneAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Engineer zone assignment not found: " + assignmentId));
        if (!assignment.getEngineer().getId().equals(request.engineerId())) {
            throw new IllegalArgumentException("Engineer cannot be changed on an existing zone assignment");
        }
        ensureAssignmentAvailable(request.engineerId(), request, assignmentId);
        var zone = zoneRepository.findById(request.zoneId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Serviceability zone not found: " + request.zoneId()));
        assignment.setZone(zone);
        apply(assignment, request);
        saveCapacityAndAvailability(request.engineerId(), request.dailyCapacity(), request.available());
        EngineerZoneAssignment saved = zoneAssignmentRepository.save(assignment);
        log.info("Engineer zone assignment updated. assignmentId={}, engineerId={}, zoneId={}, active={}",
                assignmentId, request.engineerId(), zone.getZoneId(), saved.isActive());
        return response(saved);
    }

    @Transactional
    public EngineerServiceabilityResponse updateServiceability(
            Long engineerId,
            EngineerServiceabilityUpdateRequest request
    ) {
        User engineer = findEngineer(engineerId);
        saveCapacityAndAvailability(engineerId, request.dailyCapacity(), request.available());
        return engineers(null).stream()
                .filter(result -> result.engineerId().equals(engineer.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Updated engineer was not returned"));
    }

    @Transactional(readOnly = true)
    public EngineerZoneAssignmentResponse getZoneAssignment(Long id) {
        return zoneAssignmentRepository.findById(id)
                .map(this::response)
                .orElseThrow(() -> new ResourceNotFoundException("Engineer zone assignment not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<EngineerZoneAssignmentResponse> zoneAssignments(Long engineerId) {
        return zoneAssignmentRepository.searchAll(engineerId).stream().map(this::response).toList();
    }

    private void ensureAssignmentAvailable(
            Long engineerId,
            EngineerZoneAssignmentRequest request,
            Long currentId
    ) {
        if (!request.active()) {
            return;
        }
        boolean duplicate = currentId == null
                ? zoneAssignmentRepository.existsByEngineerIdAndZoneZoneIdAndAssignmentTypeAndActiveTrue(
                        engineerId, request.zoneId(), request.assignmentType())
                : zoneAssignmentRepository.existsByEngineerIdAndZoneZoneIdAndAssignmentTypeAndActiveTrueAndIdNot(
                        engineerId, request.zoneId(), request.assignmentType(), currentId);
        if (duplicate) {
            throw new ConflictException("This engineer already has an active assignment of that type in the zone.");
        }
        if (request.assignmentType() == EngineerZoneType.PRIMARY) {
            boolean anotherPrimary = currentId == null
                    ? zoneAssignmentRepository.existsByEngineerIdAndAssignmentTypeAndActiveTrue(
                            engineerId, EngineerZoneType.PRIMARY)
                    : zoneAssignmentRepository.existsByEngineerIdAndAssignmentTypeAndActiveTrueAndIdNot(
                            engineerId, EngineerZoneType.PRIMARY, currentId);
            if (anotherPrimary) {
                throw new ConflictException("Deactivate the current primary zone before assigning a new one.");
            }
        }
    }

    public User findEngineer(Long engineerId) {
        User user = userRepository.findById(engineerId)
                .orElseThrow(() -> new ResourceNotFoundException("Engineer not found: " + engineerId));
        if (user.resolvedUserType() != UserType.ENGINEER) {
            throw new IllegalArgumentException("Selected user is not an engineer: " + engineerId);
        }
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Selected engineer account is inactive: " + engineerId);
        }
        return user;
    }

    private void saveCapacityAndAvailability(Long engineerId, Integer dailyCapacity, Boolean available) {
        if (dailyCapacity == null && available == null) {
            return;
        }
        EngineerServiceability config = serviceabilityRepository.findById(engineerId)
                .orElseGet(() -> EngineerServiceability.builder()
                        .engineerId(engineerId)
                        .dailyCapacity(properties.getDefaultDailyCapacity())
                        .available(true)
                        .build());
        if (dailyCapacity != null) {
            if (dailyCapacity < 1 || dailyCapacity > 50) {
                throw new IllegalArgumentException("Daily capacity must be between 1 and 50");
            }
            config.setDailyCapacity(dailyCapacity);
        }
        if (available != null) {
            config.setAvailable(available);
        }
        serviceabilityRepository.save(config);
    }

    private static void apply(EngineerZoneAssignment assignment, EngineerZoneAssignmentRequest request) {
        assignment.setAssignmentType(request.assignmentType());
        assignment.setEffectiveFrom(request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom());
        assignment.setEffectiveTo(request.effectiveTo());
        assignment.setActive(request.active());
    }

    private EngineerZoneAssignmentResponse response(EngineerZoneAssignment assignment) {
        UserProfile profile = profileRepository.findById(assignment.getEngineer().getId()).orElse(null);
        EngineerServiceability config = serviceabilityRepository.findById(assignment.getEngineer().getId()).orElse(null);
        LocalDate today = LocalDate.now();
        long[] workload = statsRepository.counts(today).stream()
                .filter(row -> ((Number) row[0]).longValue() == assignment.getEngineer().getId())
                .map(row -> new long[]{number(row[1]), number(row[2]), number(row[3])})
                .findFirst()
                .orElse(new long[3]);
        return new EngineerZoneAssignmentResponse(
                assignment.getId(),
                assignment.getEngineer().getId(),
                profile == null ? assignment.getEngineer().getUsername() : profile.getFullName(),
                profile == null ? null : profile.getEmployeeNumber(),
                assignment.getZone().getZoneId(),
                assignment.getZone().getZoneName(),
                assignment.getAssignmentType(),
                assignment.getEffectiveFrom(),
                assignment.getEffectiveTo(),
                assignment.isActive(),
                config == null ? properties.getDefaultDailyCapacity() : config.getDailyCapacity(),
                config == null || config.isAvailable(),
                workload[0], workload[1], workload[2]
        );
    }

    private static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
