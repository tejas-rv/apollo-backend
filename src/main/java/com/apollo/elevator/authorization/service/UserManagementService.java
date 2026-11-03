package com.apollo.elevator.authorization.service;

import com.apollo.elevator.authorization.model.dto.CreateUserRequest;
import com.apollo.elevator.authorization.model.dto.UpdateUserRequest;
import com.apollo.elevator.authorization.model.dto.UserDetailResponse;
import com.apollo.elevator.authorization.model.dto.UserProfileRequest;
import com.apollo.elevator.authorization.model.dto.UserSummaryResponse;
import com.apollo.elevator.authorization.model.entity.User;
import com.apollo.elevator.authorization.model.entity.UserProfile;
import com.apollo.elevator.authorization.model.enums.Role;
import com.apollo.elevator.authorization.model.enums.UserType;
import com.apollo.elevator.authorization.repository.UserProfileRepository;
import com.apollo.elevator.authorization.repository.UserRepository;
import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.common.util.LogMaskingUtil;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.engineer.model.dto.EngineerScheduledVisit;
import com.apollo.elevator.engineer.model.entity.ServiceReport;
import com.apollo.elevator.engineer.model.enums.ReportStatus;
import com.apollo.elevator.engineer.repository.EngineerScheduleRepository;
import com.apollo.elevator.engineer.repository.ServiceReportRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-facing CRUD for staff accounts (ADMIN, OFFICE, ENGINEER) and their profiles.
 * Engineers additionally get service workload figures.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserManagementService {

    static final Set<ReportStatus> COMPLETED_STATUSES = Set.of(ReportStatus.SUBMITTED, ReportStatus.PDF_SENT);
    private static final List<Role> STAFF_ROLES = List.of(Role.ADMIN, Role.ENGINEER);
    private static final int VISIT_LIST_LIMIT = 200;

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final ServiceReportRepository reportRepository;
    private final EngineerScheduleRepository scheduleRepository;
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listUsers(UserType type) {
        List<User> users = userRepository.findByRoleInOrderByUsernameAsc(STAFF_ROLES).stream()
                .filter(user -> user.resolvedUserType() != null)
                .filter(user -> type == null || user.resolvedUserType() == type)
                .toList();
        if (users.isEmpty()) {
            return List.of();
        }
        Map<Long, UserProfile> profiles = profileRepository
                .findAllById(users.stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity()));

        boolean anyEngineer = users.stream().anyMatch(UserManagementService::isEngineer);
        Map<Long, Object[]> completed = new HashMap<>();
        EngineerScheduleRepository.ScheduledCounts scheduled = null;
        if (anyEngineer) {
            for (Object[] row : reportRepository.summarizeByEngineer(COMPLETED_STATUSES)) {
                completed.put((Long) row[0], row);
            }
            scheduled = scheduleRepository.countScheduled(today());
        }

        List<UserSummaryResponse> result = new ArrayList<>(users.size());
        for (User user : users) {
            UserProfile profile = profiles.get(user.getId());
            long upcoming = 0;
            long overdue = 0;
            Object[] done = null;
            if (isEngineer(user)) {
                long[] counts = scheduled.forEngineer(user.getId(), matchNames(user, profile));
                upcoming = counts[0];
                overdue = counts[1];
                done = completed.get(user.getId());
            }
            result.add(new UserSummaryResponse(
                    user.getId(),
                    user.getUsername(),
                    user.resolvedUserType(),
                    user.getRole().name(),
                    user.isEnabled(),
                    profile == null ? null : profile.getEmployeeNumber(),
                    profile == null ? null : profile.getFullName(),
                    profile == null ? null : profile.getPhone(),
                    profile == null ? null : profile.getEmail(),
                    profile == null ? null : profile.getDesignation(),
                    upcoming,
                    overdue,
                    done == null ? 0 : ((Number) done[1]).longValue(),
                    done == null ? null : (LocalDate) done[2]));
        }
        result.sort(Comparator.comparing(
                (UserSummaryResponse s) -> s.fullName() == null ? s.username() : s.fullName(),
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Transactional(readOnly = true)
    public UserDetailResponse getUser(Long id) {
        User user = findManagedUser(id);
        return toDetail(user, profileRepository.findById(id).orElse(null));
    }

    @Transactional
    public UserDetailResponse createUser(CreateUserRequest request) {
        String employeeNumber = clean(request.profile().employeeNumber());
        if (employeeNumber != null && profileRepository.existsByEmployeeNumberIgnoreCase(employeeNumber)) {
            throw new ConflictException("Employee number already exists: " + employeeNumber);
        }
        User user = authService.createAccount(request.username(), request.password(), request.userType());
        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        apply(profile, request.profile());
        UserProfile saved = profileRepository.save(profile);
        log.info("User profile created. userId={}, userType={}, employeeNumber={}",
                user.getId(), request.userType(),
                LogMaskingUtil.mask("employeeNumber", saved.getEmployeeNumber()));
        return toDetail(user, saved);
    }

    @Transactional
    public UserDetailResponse updateUser(Long id, UpdateUserRequest request, String actorUsername) {
        User user = findManagedUser(id);
        UserProfileRequest profileRequest = request.profile();
        String employeeNumber = clean(profileRequest.employeeNumber());
        if (employeeNumber != null && profileRepository.existsByEmployeeNumberIgnoreCaseAndUserIdNot(employeeNumber, id)) {
            throw new ConflictException("Employee number already exists: " + employeeNumber);
        }

        UserType newType = request.userType();
        if (newType != user.resolvedUserType()) {
            changeType(user, newType, actorUsername);
        }

        UserProfile profile = profileRepository.findById(id).orElseGet(() -> {
            UserProfile created = new UserProfile();
            created.setUserId(id);
            return created;
        });
        apply(profile, profileRequest);
        UserProfile saved = profileRepository.save(profile);
        if (isEngineer(user)) {
            String name = saved.getFullName() == null ? user.getUsername() : saved.getFullName();
            int renamed = scheduleRepository.renameScheduledVisits(id, name);
            if (renamed > 0) {
                log.info("Scheduled visits renamed for engineer. userId={}, visits={}", id, renamed);
            }
        }
        log.info("User updated. userId={}, userType={}", id, user.resolvedUserType());
        return toDetail(user, saved);
    }

    @Transactional
    public UserDetailResponse updateStatus(Long id, boolean enabled, String actorUsername) {
        User user = findManagedUser(id);
        if (user.isEnabled() != enabled) {
            if (!enabled) {
                requireNotSelf(user, actorUsername, "deactivate your own account");
                requireAnotherActiveAdmin(user, "deactivate");
            }
            user.setEnabled(enabled);
            userRepository.save(user);
            if (!enabled) {
                refreshTokenService.revokeAllActiveTokens(user);
            }
            log.info("User status changed. userId={}, enabled={}", id, enabled);
        }
        return toDetail(user, profileRepository.findById(id).orElse(null));
    }

    @Transactional
    public void deleteUser(Long id, String actorUsername) {
        User user = findManagedUser(id);
        requireNotSelf(user, actorUsername, "delete your own account");
        requireAnotherActiveAdmin(user, "delete");
        if (reportRepository.existsByEngineerUserId(id)) {
            throw new ConflictException(
                    "User has service reports and cannot be deleted. Deactivate the account instead.");
        }
        requireNoAssignedVisits(user, "deleting the account");
        refreshTokenService.revokeAllActiveTokens(user);
        profileRepository.findById(id).ifPresent(profileRepository::delete);
        userRepository.delete(user);
        log.info("User deleted. userId={}, username={}, by={}",
                id,
                LogMaskingUtil.mask("username", user.getUsername()),
                LogMaskingUtil.mask("username", actorUsername));
    }

    /** Name recorded on new service reports: the profile's full name when present. */
    @Transactional(readOnly = true)
    public String displayName(User user) {
        return profileRepository.findById(user.getId())
                .map(UserProfile::getFullName)
                .filter(name -> !name.isBlank())
                .orElse(user.getUsername());
    }

    private void changeType(User user, UserType newType, String actorUsername) {
        requireNotSelf(user, actorUsername, "change your own user type");
        if (newType != UserType.ENGINEER) {
            requireNoAssignedVisits(user, "changing the user type");
        }
        Role newRole = newType.securityRole();
        if (user.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
            requireAnotherActiveAdmin(user, "change the user type of");
        }
        boolean roleChanged = user.getRole() != newRole;
        user.setUserType(newType);
        user.setRole(newRole);
        userRepository.save(user);
        if (roleChanged) {
            // Existing sessions carry the old role; force a fresh login.
            refreshTokenService.revokeAllActiveTokens(user);
        }
        log.info("User type changed. userId={}, userType={}, role={}", user.getId(), newType, newRole);
    }

    private void requireNotSelf(User user, String actorUsername, String action) {
        if (actorUsername != null && actorUsername.equals(user.getUsername())) {
            throw new ConflictException("You cannot " + action + ".");
        }
    }

    private void requireNoAssignedVisits(User user, String action) {
        if (!isEngineer(user)) {
            return;
        }
        long assigned = scheduleRepository.countScheduledAssignedTo(user.getId());
        if (assigned > 0) {
            throw new ConflictException("Engineer has " + assigned
                    + " scheduled service visit(s). Reassign them to another engineer before " + action + ".");
        }
    }

    private void requireAnotherActiveAdmin(User user, String action) {
        if (user.getRole() == Role.ADMIN && user.isEnabled()
                && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ConflictException("Cannot " + action + " the last active admin user.");
        }
    }

    private UserDetailResponse toDetail(User user, UserProfile profile) {
        UserDetailResponse.Profile profileDto = profile == null ? null : toProfile(profile);
        if (!isEngineer(user)) {
            return new UserDetailResponse(user.getId(), user.getUsername(), user.resolvedUserType(),
                    user.getRole().name(), user.isEnabled(), profileDto, null, List.of(), List.of(), List.of());
        }

        LocalDate today = today();
        List<EngineerScheduledVisit> upcoming = new ArrayList<>();
        List<EngineerScheduledVisit> overdue = new ArrayList<>();
        for (EngineerScheduledVisit visit : scheduleRepository.findScheduledVisits(
                user.getId(), matchNames(user, profile), VISIT_LIST_LIMIT)) {
            if (visit.serviceDate() != null && visit.serviceDate().isBefore(today)) {
                overdue.add(visit);
            } else {
                upcoming.add(visit);
            }
        }

        List<ServiceReport> recentReports = reportRepository
                .findTop10ByEngineerUserIdAndStatusInOrderByVisitDateDescIdDesc(user.getId(), COMPLETED_STATUSES);
        long completed = reportRepository.countByEngineerUserIdAndStatusIn(user.getId(), COMPLETED_STATUSES);
        long completedThisMonth = reportRepository.countByEngineerUserIdAndStatusInAndVisitDateBetween(
                user.getId(), COMPLETED_STATUSES, today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));

        UserDetailResponse.Stats stats = new UserDetailResponse.Stats(
                upcoming.size(),
                overdue.size(),
                completed,
                completedThisMonth,
                recentReports.isEmpty() ? null : recentReports.get(0).getVisitDate());

        return new UserDetailResponse(
                user.getId(),
                user.getUsername(),
                user.resolvedUserType(),
                user.getRole().name(),
                user.isEnabled(),
                profileDto,
                stats,
                upcoming,
                overdue,
                recentReports.stream()
                        .map(r -> new UserDetailResponse.CompletedService(
                                r.getId(), r.getVisitDate(), r.getCustomerId(), r.getCustomerName(), r.getStatus(), r.getSubmittedAt()))
                        .toList());
    }

    /** SCHEDULED AMC visits assigned to this engineer, oldest first (overdue visits come first). */
    /** Lower-cased names that legacy (unassigned) visits may use for this user. */
    @Transactional(readOnly = true)
    public Set<String> visitMatchNames(User user) {
        return matchNames(user, profileRepository.findById(user.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<EngineerScheduledVisit> scheduledVisitsFor(Long userId) {
        User user = findManagedUser(userId);
        UserProfile profile = profileRepository.findById(userId).orElse(null);
        return scheduleRepository.findScheduledVisits(userId, matchNames(user, profile), VISIT_LIST_LIMIT);
    }

    /** Staff accounts only; customer logins are never exposed here. */
    private User findManagedUser(Long id) {
        return userRepository.findById(id)
                .filter(user -> user.resolvedUserType() != null)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private static boolean isEngineer(User user) {
        return user.resolvedUserType() == UserType.ENGINEER;
    }

    private LocalDate today() {
        return LocalDate.now();
    }

    /** Names that legacy (unassigned) scheduled AMC visits may use for this engineer (normalised). */
    static Set<String> matchNames(User user, UserProfile profile) {
        Set<String> names = new LinkedHashSet<>();
        addName(names, user.getUsername());
        if (profile != null) {
            addName(names, profile.getFullName());
        }
        return names;
    }

    private static void addName(Set<String> names, String value) {
        if (value != null && !value.isBlank()) {
            names.add(value.trim().toLowerCase(Locale.ROOT));
        }
    }

    private static void apply(UserProfile profile, UserProfileRequest request) {
        profile.setEmployeeNumber(clean(request.employeeNumber()));
        profile.setFullName(clean(request.fullName()));
        profile.setPhone(clean(request.phone()));
        profile.setEmail(clean(request.email()));
        profile.setAddress(clean(request.address()));
        profile.setDesignation(clean(request.designation()));
        profile.setDateOfJoining(request.dateOfJoining());
        profile.setSpecialization(clean(request.specialization()));
        profile.setEmergencyContactName(clean(request.emergencyContactName()));
        profile.setEmergencyContactPhone(clean(request.emergencyContactPhone()));
        profile.setNotes(clean(request.notes()));
    }

    private static UserDetailResponse.Profile toProfile(UserProfile p) {
        return new UserDetailResponse.Profile(
                p.getEmployeeNumber(), p.getFullName(), p.getPhone(), p.getEmail(), p.getAddress(),
                p.getDesignation(), p.getDateOfJoining(), p.getSpecialization(),
                p.getEmergencyContactName(), p.getEmergencyContactPhone(), p.getNotes(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
