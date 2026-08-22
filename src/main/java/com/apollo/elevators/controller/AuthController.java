package com.apollo.elevators.controller;

import com.apollo.elevators.common.api.ApiErrorResponse;
import com.apollo.elevators.auth.AuthService;
import com.apollo.elevators.dto.PasswordResetRequest;
import com.apollo.elevators.dto.auth.CurrentUserResponse;
import com.apollo.elevators.dto.auth.LoginRequest;
import com.apollo.elevators.dto.auth.LoginResponse;
import com.apollo.elevators.dto.auth.RefreshTokenRequest;
import com.apollo.elevators.dto.auth.RefreshTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication and user session endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticates user and returns access and refresh tokens")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid username or password",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Auth login request received. username={}", request.username());
        LoginResponse response = authService.login(request);
        log.info("Auth login completed. username={}, role={}", response.username(), response.role());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Rotates refresh token and returns a new access token pair")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
            content = @Content(schema = @Schema(implementation = RefreshTokenResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Refresh token invalid or expired",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        int tokenLength = request.refreshToken() == null ? 0 : request.refreshToken().length();
        log.info("Auth refresh request received. refreshTokenLength={}", tokenLength);
        RefreshTokenResponse response = authService.refresh(request);
        log.info("Auth refresh completed. refreshTokenIssued=true");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns details of currently authenticated user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Current user returned",
            content = @Content(schema = @Schema(implementation = CurrentUserResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CurrentUserResponse> me(Authentication authentication) {
        String username = authentication.getName();
        log.info("Auth me request received. username={}", username);
        CurrentUserResponse response = authService.getCurrentUser(username);
        log.info("Auth me completed. username={}, role={}", response.username(), response.role());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reset user password", description = "ADMIN-only endpoint to reset password for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password reset successful"),
        @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden (ADMIN role required)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<String> resetPassword(
            @Valid @RequestBody PasswordResetRequest request) {
        log.info("Auth reset-password request received. username={}", request.getUsername());
        authService.resetPassword(request);
        log.info("Auth reset-password completed. username={}", request.getUsername());

        return ResponseEntity.ok("Password reset successfully");
    }
}
