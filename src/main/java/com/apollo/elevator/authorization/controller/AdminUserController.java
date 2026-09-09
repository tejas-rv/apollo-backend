package com.apollo.elevator.authorization.controller;

import com.apollo.elevator.authorization.model.dto.CreateEngineerRequest;
import com.apollo.elevator.authorization.model.dto.EngineerUserResponse;
import com.apollo.elevator.authorization.service.AuthService;
import com.apollo.elevator.common.api.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin User Management", description = "Admin-only user management endpoints")
public class AdminUserController {

    private final AuthService authService;

    @PostMapping("/engineers")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create engineer account", description = "Creates a new ENGINEER user. Requires ADMIN JWT.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Engineer created successfully",
            content = @Content(schema = @Schema(implementation = EngineerUserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden (ADMIN role required)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Username already exists",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<EngineerUserResponse> createEngineer(@Valid @RequestBody CreateEngineerRequest request) {
        log.info("Admin engineer creation request received. username={}", request.username());
        EngineerUserResponse response = authService.createEngineer(request);
        log.info("Admin engineer creation completed. userId={}, username={}, role={}", response.id(), response.username(), response.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
