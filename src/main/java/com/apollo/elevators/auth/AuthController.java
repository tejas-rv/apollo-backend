package com.apollo.elevators.auth;

import com.apollo.elevators.auth.dto.CurrentUserResponse;
import com.apollo.elevators.auth.dto.LoginRequest;
import com.apollo.elevators.auth.dto.LoginResponse;
import com.apollo.elevators.auth.dto.RefreshTokenRequest;
import com.apollo.elevators.auth.dto.RefreshTokenResponse;
import com.apollo.elevators.dto.PasswordResetRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authService.getCurrentUser(authentication.getName()));
    }

    @PostMapping("/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> resetPassword(
            @Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);

        return ResponseEntity.ok("Password reset successfully");
    }
}
