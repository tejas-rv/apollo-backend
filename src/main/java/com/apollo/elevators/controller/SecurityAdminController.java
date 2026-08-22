package com.apollo.elevators.controller;

import com.apollo.elevators.common.api.ApiErrorResponse;
import com.apollo.elevators.security.SecurityConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sits under /api/admin/** so SecurityConfig's hasRole("ADMIN") matcher
 * protects it automatically — a caller needs a valid ADMIN JWT to reach this.
 */
@RestController
@RequestMapping("/api/admin/security")
@RequiredArgsConstructor
@Tag(name = "Security Configuration", description = "Security admin operations")
public class SecurityAdminController {

    private final SecurityConfigService securityConfigService;

    /**
     * Call after rotating a value in the system_secret table (e.g. new JWT
     * secret) to reload it into memory without restarting the app.
     */
    @PostMapping("/config/refresh")
    @Operation(
            summary = "Refresh runtime security configuration",
            description = "Reloads JWT/security configuration from system_secret without restarting the app"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Configuration refreshed successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden (ADMIN role required)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> refreshSecurityConfig() {
        securityConfigService.refresh();
        return ResponseEntity.noContent().build();
    }
}
