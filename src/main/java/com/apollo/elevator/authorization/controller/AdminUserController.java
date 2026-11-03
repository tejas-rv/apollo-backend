package com.apollo.elevator.authorization.controller;

import com.apollo.elevator.common.util.LogMaskingUtil;
import com.apollo.elevator.authorization.model.dto.CreateUserRequest;
import com.apollo.elevator.authorization.model.dto.UpdateUserRequest;
import com.apollo.elevator.authorization.model.dto.UpdateUserStatusRequest;
import com.apollo.elevator.authorization.model.dto.UserDetailResponse;
import com.apollo.elevator.authorization.model.dto.UserSummaryResponse;
import com.apollo.elevator.authorization.model.enums.UserType;
import com.apollo.elevator.authorization.service.UserManagementService;
import com.apollo.elevator.common.api.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin User Management",
        description = "CRUD for staff users. ADMIN and OFFICE users sign in with the ADMIN role; ENGINEER users with the ENGINEER role.")
public class AdminUserController {

    private final UserManagementService userManagementService;

    @GetMapping
    @Operation(summary = "List staff users",
            description = "Optionally filter by userType. Engineers include upcoming/overdue/completed service counts.")
    public List<UserSummaryResponse> listUsers(
            @Parameter(description = "ADMIN, OFFICE or ENGINEER") @RequestParam(required = false) UserType type) {
        return userManagementService.listUsers(type);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user details", description = "Profile; engineers also get workload statistics and visits.")
    public UserDetailResponse getUser(@PathVariable Long id) {
        return userManagementService.getUser(id);
    }

    @PostMapping
    @Operation(summary = "Create user", description = "Creates the login and profile for an ADMIN, OFFICE or ENGINEER user.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User created",
            content = @Content(schema = @Schema(implementation = UserDetailResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden (ADMIN role required)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Username or employee number already exists",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserDetailResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Admin user creation request received. username={}, userType={}",
                LogMaskingUtil.mask("username", request.username()), request.userType());
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.createUser(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user type and profile",
            description = "Changing the type updates the security role and signs the user out. "
                    + "You cannot change your own type or demote the last active admin.")
    public UserDetailResponse updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
                                         Authentication authentication) {
        return userManagementService.updateUser(id, request, authentication.getName());
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate user", description = "Deactivation blocks login and revokes refresh tokens.")
    public UserDetailResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request,
                                           Authentication authentication) {
        return userManagementService.updateStatus(id, request.enabled(), authentication.getName());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user",
            description = "Permanently deletes the user. Users with service reports must be deactivated instead (409).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "User deleted"),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Own account, last admin, or user has service reports",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication authentication) {
        userManagementService.deleteUser(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
