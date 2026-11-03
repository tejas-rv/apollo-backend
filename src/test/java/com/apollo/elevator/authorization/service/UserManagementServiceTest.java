package com.apollo.elevator.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.engineer.model.dto.EngineerScheduledVisit;
import com.apollo.elevator.engineer.repository.EngineerScheduleRepository;
import com.apollo.elevator.engineer.repository.ServiceReportRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserManagementServiceTest {

    private UserRepository userRepository;
    private UserProfileRepository profileRepository;
    private ServiceReportRepository reportRepository;
    private EngineerScheduleRepository scheduleRepository;
    private AuthService authService;
    private RefreshTokenService refreshTokenService;
    private UserManagementService service;

    private final User ravi = User.builder().id(7L).username("ravi").role(Role.ENGINEER).enabled(true).build();
    private final UserProfile raviProfile = UserProfile.builder()
            .userId(7L).employeeNumber("EMP-007").fullName("Ravi Kumar").phone("9876543210").designation("Senior Technician").build();
    private final User admin = User.builder().id(1L).username("admin").role(Role.ADMIN).userType(UserType.ADMIN).enabled(true).build();
    private final User ceo = User.builder().id(2L).username("ceo").role(Role.ADMIN).userType(UserType.OFFICE).enabled(true).build();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        profileRepository = mock(UserProfileRepository.class);
        reportRepository = mock(ServiceReportRepository.class);
        scheduleRepository = mock(EngineerScheduleRepository.class);
        authService = mock(AuthService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        service = new UserManagementService(userRepository, profileRepository, reportRepository,
                scheduleRepository, authService, refreshTokenService);
    }

    @Test
    void list_combines_profile_completed_reports_and_scheduled_visits_for_engineers() {
        when(userRepository.findByRoleInOrderByUsernameAsc(any())).thenReturn(List.of(ravi, ceo));
        when(profileRepository.findAllById(List.of(7L, 2L))).thenReturn(List.of(raviProfile));
        LocalDate lastVisit = LocalDate.of(2026, 1, 10);
        when(reportRepository.summarizeByEngineer(UserManagementService.COMPLETED_STATUSES))
                .thenReturn(List.<Object[]>of(new Object[]{7L, 12L, lastVisit}));
        when(scheduleRepository.countScheduled(any())).thenReturn(new EngineerScheduleRepository.ScheduledCounts(
                Map.of(7L, new long[]{2, 1}, 2L, new long[]{9, 9}),
                Map.of("ravi kumar", new long[]{1, 0}, "ravi", new long[]{1, 0}, "ceo", new long[]{9, 9})));

        List<UserSummaryResponse> users = service.listUsers(null);
        UserSummaryResponse engineer = users.stream().filter(u -> u.id() == 7L).findFirst().orElseThrow();
        UserSummaryResponse office = users.stream().filter(u -> u.id() == 2L).findFirst().orElseThrow();

        assertEquals(UserType.ENGINEER, engineer.userType());
        assertEquals("EMP-007", engineer.employeeNumber());
        assertEquals(4, engineer.upcomingServices());
        assertEquals(1, engineer.overdueServices());
        assertEquals(12, engineer.completedServices());
        assertEquals(lastVisit, engineer.lastServiceDate());
        assertEquals(UserType.OFFICE, office.userType());
        assertEquals("ADMIN", office.role());
        assertEquals(0, office.upcomingServices());
    }

    @Test
    void list_filters_by_user_type_and_skips_workload_queries_without_engineers() {
        when(userRepository.findByRoleInOrderByUsernameAsc(any())).thenReturn(List.of(admin, ceo, ravi));

        List<UserSummaryResponse> office = service.listUsers(UserType.OFFICE);

        assertEquals(1, office.size());
        assertEquals("ceo", office.get(0).username());
        verify(scheduleRepository, never()).countScheduled(any());
    }

    @Test
    void engineer_detail_splits_scheduled_visits_into_upcoming_and_overdue() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(ravi));
        when(profileRepository.findById(7L)).thenReturn(Optional.of(raviProfile));
        LocalDate today = LocalDate.now();
        when(scheduleRepository.findScheduledVisits(eq(7L), any(), anyInt())).thenReturn(List.of(
                new EngineerScheduledVisit(1L, today.minusDays(2), "Missed", 5L, "Acme", "Pune", "AMC-1", 11L, "Otis", "S1"),
                new EngineerScheduledVisit(2L, today, "Today", 5L, "Acme", "Pune", "AMC-1", 11L, "Otis", "S1"),
                new EngineerScheduledVisit(3L, today.plusDays(5), "Later", 6L, "Beta", "Pune", "AMC-2", 12L, "Kone", "S2")));

        UserDetailResponse detail = service.getUser(7L);

        assertEquals(2, detail.stats().upcomingServices());
        assertEquals(1, detail.stats().overdueServices());
        assertEquals(1L, detail.overdueServices().get(0).id());
        assertEquals("Ravi Kumar", detail.profile().fullName());
    }

    @Test
    void office_detail_has_no_workload() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(ceo));
        when(profileRepository.findById(2L)).thenReturn(Optional.empty());

        UserDetailResponse detail = service.getUser(2L);

        assertEquals(UserType.OFFICE, detail.userType());
        assertNull(detail.stats());
        verify(scheduleRepository, never()).findScheduledVisits(any(), any(), anyInt());
    }

    @Test
    void customer_logins_are_not_exposed() {
        User customer = User.builder().id(9L).username("cust").role(Role.CUSTOMER).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(customer));

        assertThrows(ResourceNotFoundException.class, () -> service.getUser(9L));
    }

    @Test
    void match_names_are_normalised_and_deduplicated() {
        UserProfile sameName = UserProfile.builder().userId(7L).fullName("  RAVI ").build();
        assertEquals(Set.of("ravi"), UserManagementService.matchNames(ravi, sameName));
    }

    @Test
    void create_rejects_duplicate_employee_number_before_creating_login() {
        when(profileRepository.existsByEmployeeNumberIgnoreCase("EMP-007")).thenReturn(true);
        CreateUserRequest request = new CreateUserRequest("new.user", "Password123", UserType.OFFICE, profile("EMP-007"));

        assertThrows(ConflictException.class, () -> service.createUser(request));
        verify(authService, never()).createAccount(anyString(), anyString(), any());
    }

    @Test
    void create_passes_user_type_to_account_creation() {
        User created = User.builder().id(5L).username("cfo").role(Role.ADMIN).userType(UserType.OFFICE).enabled(true).build();
        when(authService.createAccount("cfo", "Password123", UserType.OFFICE)).thenReturn(created);
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserDetailResponse detail = service.createUser(
                new CreateUserRequest("cfo", "Password123", UserType.OFFICE, profile("EMP-100")));

        assertEquals(UserType.OFFICE, detail.userType());
        assertEquals("ADMIN", detail.role());
        assertEquals("EMP-100", detail.profile().employeeNumber());
    }

    @Test
    void changing_engineer_to_office_promotes_role_and_revokes_tokens() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(ravi));
        when(profileRepository.findById(7L)).thenReturn(Optional.of(raviProfile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserDetailResponse detail = service.updateUser(7L, new UpdateUserRequest(UserType.OFFICE, profile("EMP-007")), "admin");

        assertEquals(UserType.OFFICE, detail.userType());
        assertEquals(Role.ADMIN, ravi.getRole());
        verify(refreshTokenService).revokeAllActiveTokens(ravi);
    }

    @Test
    void admin_to_office_keeps_role_without_revoking_tokens() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(ceo));
        when(profileRepository.findById(2L)).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateUser(2L, new UpdateUserRequest(UserType.ADMIN, profile("EMP-002")), "admin");

        assertEquals(UserType.ADMIN, ceo.getUserType());
        assertEquals(Role.ADMIN, ceo.getRole());
        verify(refreshTokenService, never()).revokeAllActiveTokens(any());
    }

    @Test
    void cannot_change_own_type() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(ConflictException.class,
                () -> service.updateUser(1L, new UpdateUserRequest(UserType.OFFICE, profile("EMP-001")), "admin"));
    }

    @Test
    void cannot_demote_last_active_admin() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(ceo));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThrows(ConflictException.class,
                () -> service.updateUser(2L, new UpdateUserRequest(UserType.ENGINEER, profile("EMP-002")), "admin"));
        assertEquals(Role.ADMIN, ceo.getRole());
    }

    @Test
    void deactivating_user_revokes_refresh_tokens() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(ravi));
        when(profileRepository.findById(7L)).thenReturn(Optional.empty());

        UserDetailResponse detail = service.updateStatus(7L, false, "admin");

        assertFalse(detail.enabled());
        verify(refreshTokenService).revokeAllActiveTokens(ravi);
    }

    @Test
    void cannot_deactivate_self_or_last_admin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(ceo));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> service.updateStatus(1L, false, "admin"));
        assertThrows(ConflictException.class, () -> service.updateStatus(2L, false, "admin"));
    }

    @Test
    void engineer_with_assigned_visits_cannot_change_type_or_be_deleted() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(ravi));
        when(scheduleRepository.countScheduledAssignedTo(7L)).thenReturn(2L);

        assertThrows(ConflictException.class,
                () -> service.updateUser(7L, new UpdateUserRequest(UserType.OFFICE, profile("EMP-007")), "admin"));
        assertThrows(ConflictException.class, () -> service.deleteUser(7L, "admin"));
        assertEquals(Role.ENGINEER, ravi.getRole());
        verify(userRepository, never()).delete(any());
    }

    @Test
    void updating_engineer_profile_renames_assigned_scheduled_visits() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(ravi));
        when(profileRepository.findById(7L)).thenReturn(Optional.of(raviProfile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateUser(7L, new UpdateUserRequest(UserType.ENGINEER, profile("EMP-007")), "admin");

        verify(scheduleRepository).renameScheduledVisits(eq(7L), anyString());
    }

    @Test
    void delete_is_blocked_when_user_has_service_reports() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(ravi));
        when(reportRepository.existsByEngineerUserId(7L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.deleteUser(7L, "admin"));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void delete_removes_profile_and_user() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(ceo));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(2L);
        UserProfile ceoProfile = UserProfile.builder().userId(2L).fullName("Chief").build();
        when(profileRepository.findById(2L)).thenReturn(Optional.of(ceoProfile));

        service.deleteUser(2L, "admin");

        verify(refreshTokenService).revokeAllActiveTokens(ceo);
        verify(profileRepository).delete(ceoProfile);
        verify(userRepository).delete(ceo);
    }

    @Test
    void cannot_delete_self() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(ConflictException.class, () -> service.deleteUser(1L, "admin"));
    }

    private UserProfileRequest profile(String employeeNumber) {
        return new UserProfileRequest(employeeNumber, "New User", null, null, null, null, null, null, null, null, null);
    }
}
